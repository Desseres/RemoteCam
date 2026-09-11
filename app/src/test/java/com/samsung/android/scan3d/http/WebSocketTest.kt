package com.samsung.android.scan3d.http

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class WebSocketTest {
    @Test fun nextJpegWaitsForDrawAcknowledgementAndSkipsToLatestWithoutReencoding() {
        val port = ServerSocket(0).use { it.localPort }
        val server = HttpService(port)
        val received = LinkedBlockingQueue<ByteArray>()
        val closed = CompletableFuture<Int>()
        val listener = object : WebSocket.Listener {
            private val parts = ByteArrayOutputStream()
            override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
            override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
                val bytes = ByteArray(data.remaining()); data.get(bytes); parts.write(bytes)
                if (last) { received.add(parts.toByteArray()); parts.reset() }
                webSocket.request(1)
                return null
            }
            override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                closed.complete(statusCode); return null
            }
            override fun onError(webSocket: WebSocket, error: Throwable) { closed.completeExceptionally(error) }
        }
        var socket: WebSocket? = null
        try {
            server.start(); server.streaming = true
            assertTrue(URL("http://127.0.0.1:$port/view").readText().contains("createImageBitmap"))
            socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI("ws://127.0.0.1:$port/live"), listener).get(3, TimeUnit.SECONDS)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (server.transfers.snapshot().clients == 0 && System.nanoTime() < deadline) Thread.sleep(5)
            assertEquals(1, server.transfers.snapshot().confirmedClients)
            val first = byteArrayOf(-1, -40, 1, 2, 3, -1, -39)
            server.publish(first)
            val packet = checkNotNull(received.poll(3, TimeUnit.SECONDS))
            assertArrayEquals(first, packet.copyOfRange(8, packet.size))
            val sequence = ByteBuffer.wrap(packet).long
            val latest = byteArrayOf(-1, -40, 99, -1, -39)
            repeat(20) { server.publish(latest) }
            assertNull("No second JPEG may be sent before ACK", received.poll(150, TimeUnit.MILLISECONDS))
            socket.sendText(sequence.toString(), true).join()
            val next = checkNotNull(received.poll(3, TimeUnit.SECONDS))
            assertEquals(sequence + 20, ByteBuffer.wrap(next).long)
            assertArrayEquals(latest, next.copyOfRange(8, next.size))
            // A mismatched ACK must not release another frame or leave the connection alive.
            socket.sendText("-1", true).join()
            assertEquals(1002, closed.get(3, TimeUnit.SECONDS).toInt())
        } finally { socket?.abort(); server.stop() }
    }
}
