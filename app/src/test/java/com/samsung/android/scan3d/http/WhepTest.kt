package com.samsung.android.scan3d.http

import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger

class WhepTest {
    private val offer = "v=0\r\nm=video 9 UDP/TLS/RTP/SAVPF 96\r\na=recvonly\r\na=rtpmap:96 H264/90000\r\na=candidate:test 1 udp 1 127.0.0.1 1234 typ host\r\n"
    private val audio = "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=recvonly\r\na=rtpmap:111 opus/48000/2\r\n"

    @Test fun acceptsOptionalReceiveOnlyAudioButRejectsUploadsAndTrickleOnly() {
        WhepSessions.validateOffer(offer)
        WhepSessions.validateOffer(offer + audio)
        listOf(offer + audio.replace("recvonly", "sendrecv"),
            offer.replace("recvonly", "sendonly"), offer.substringBefore("a=candidate:"),
            offer + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n", offer + offer.substringAfter("v=0\r\n")).forEach {
            assertThrows(IllegalArgumentException::class.java) { WhepSessions.validateOffer(it) }
        }
    }

    @Test fun httpReturnsGatheredAnswerAndLocationAndDeletesOrClosesSessions() {
        val port = ServerSocket(0).use { it.localPort }
        val server = HttpService(port)
        val opened = AtomicInteger()
        val disposed = AtomicInteger()
        val gathered = offer.replace("a=recvonly", "a=sendonly")
        server.rtc = object : RtcSignaling {
            override suspend fun open(offer: String, emit: (String) -> Unit): RtcSession {
                opened.incrementAndGet()
                return object : RtcSession {
                    override val answer = "incomplete answer must not be sent"
                    override var closed = false
                    override suspend fun completeAnswer() = gathered
                    override suspend fun addIce(candidate: RtcSignal.Ice) = Unit
                    override suspend fun close() { if (!closed) { closed = true; disposed.incrementAndGet() } }
                }
            }
        }
        val client = HttpClient.newHttpClient()
        fun request(method: String, path: String, body: String = "", type: String = "application/sdp") = client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).header("Content-Type", type)
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString())
        try {
            server.start()
            assertEquals(405, request("GET", "/whep").statusCode())
            assertEquals(415, request("POST", "/whep", offer, "text/html").statusCode())
            assertEquals(400, request("POST", "/whep", "not SDP").statusCode())
            assertEquals(413, request("POST", "/whep", "x".repeat(WhepSessions.MAX_OFFER_BYTES + 1)).statusCode())
            assertEquals(0, opened.get())
            val response = request("POST", "/whep", offer + audio)
            assertEquals(201, response.statusCode())
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/sdp"))
            assertEquals(gathered, response.body())
            val location = response.headers().firstValue("Location").orElseThrow()
            assertTrue(location.startsWith("/whep/"))
            assertEquals(0, disposed.get()) // Ending POST must not end media delivery.
            assertEquals(204, request("DELETE", location).statusCode())
            assertEquals(404, request("DELETE", location).statusCode())
            assertEquals(1, disposed.get())
            assertEquals(201, request("POST", "/whep", offer).statusCode())
        } finally { server.stop() }
        assertEquals(2, disposed.get())
    }

    @Test fun failedGatheringClosesPeer() {
        val port = ServerSocket(0).use { it.localPort }
        val server = HttpService(port)
        val disposed = java.util.concurrent.atomic.AtomicBoolean()
        server.rtc = object : RtcSignaling {
            override suspend fun open(offer: String, emit: (String) -> Unit) = object : RtcSession {
                override val answer = ""
                override val closed get() = disposed.get()
                override suspend fun completeAnswer(): String = error("ICE gathering failed")
                override suspend fun addIce(candidate: RtcSignal.Ice) = Unit
                override suspend fun close() { disposed.set(true) }
            }
        }
        try {
            server.start()
            val response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/whep"))
                .header("Content-Type", "application/sdp").POST(HttpRequest.BodyPublishers.ofString(offer)).build(),
                HttpResponse.BodyHandlers.ofString())
            assertEquals(503, response.statusCode())
            assertTrue(disposed.get())
        } finally { server.stop() }
    }
}
