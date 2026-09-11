package com.samsung.android.scan3d.http

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RtcSignalingTest {
    private val sdp = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=recvonly\r\na=rtpmap:96 H264/90000\r\n"
    @Test fun preservesSdpAndParsesCandidate() {
        assertEquals(sdp, (RtcSignal.parse("offer\n$sdp") as RtcSignal.Offer).sdp)
        assertEquals(RtcSignal.Ice("0", 0, "candidate:abc"), RtcSignal.parse("ice\n0\n0\ncandidate:abc"))
    }
    @Test fun rejectsMalformedAndUnboundedInput() {
        listOf("offer\nv=0", "ice\n0\n-1\ncandidate:a", "ice\n0\nx\ncandidate:a",
            "ice\n0\n0\nnot-a-candidate", "unknown", "offer\n$sdp" + "m=audio 9 RTP/AVP 0\r\n",
            "offer\n" + sdp.replace("recvonly", "sendrecv"), "offer\n" + "x".repeat(131073)).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) { RtcSignal.parse(input) }
        }
    }
    @Test fun websocketRoutesOfferAndIceAndDisposesPeerOnDisconnect() {
        val port = ServerSocket(0).use { it.localPort }
        val server = HttpService(port)
        val ice = LinkedBlockingQueue<RtcSignal.Ice>()
        val messages = LinkedBlockingQueue<String>()
        val disposed = AtomicBoolean()
        server.rtc = object : RtcSignaling {
            override suspend fun open(offer: String, emit: (String) -> Unit): RtcSession {
                assertEquals(sdp, offer)
                emit("ice\n0\n0\ncandidate:server")
                return object : RtcSession {
                    override val answer = sdp
                    override val closed get() = disposed.get()
                    override suspend fun completeAnswer() = answer
                    override suspend fun addIce(candidate: RtcSignal.Ice) { ice.add(candidate) }
                    override suspend fun close() { disposed.set(true) }
                }
            }
        }
        var socket: WebSocket? = null
        try {
            server.start()
            socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI("ws://127.0.0.1:$port/signal"), object : WebSocket.Listener {
                private val text = StringBuilder()
                override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    text.append(data)
                    if (last) { messages.add(text.toString()); text.clear() }
                    webSocket.request(1); return null
                }
            }).get(3, TimeUnit.SECONDS)
            socket.sendText("offer\n$sdp", true).join()
            assertEquals("ice\n0\n0\ncandidate:server", messages.poll(3, TimeUnit.SECONDS))
            assertEquals("answer\n$sdp", messages.poll(3, TimeUnit.SECONDS))
            socket.sendText("ice\n0\n0\ncandidate:client", true).join()
            assertEquals(RtcSignal.Ice("0", 0, "candidate:client"), ice.poll(3, TimeUnit.SECONDS))
            socket.sendClose(1000, "done").join()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!disposed.get() && System.nanoTime() < deadline) Thread.sleep(5)
            assertTrue("A closed viewer must release its encoder/peer resources", disposed.get())
        } finally { socket?.abort(); server.stop() }
    }
}
