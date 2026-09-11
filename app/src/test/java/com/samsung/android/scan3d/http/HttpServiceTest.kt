package com.samsung.android.scan3d.http

import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HttpServiceTest {
    @Test fun multipartFramingHasExactLengthAndCrlf() {
        assertEquals("--FRAME\r\nContent-Type: image/jpeg\r\nContent-Length: 4\r\n\r\n",
            Mjpeg.header(4).toString(Charsets.US_ASCII))
        assertArrayEquals(byteArrayOf(13, 10), Mjpeg.terminator)
    }

    @Test fun disabledStreamReturns503AndTwoViewersReceiveFramesAndReconnect() {
        val port = ServerSocket(0).use { it.localPort }
        val server = HttpService(port)
        val producer = Executors.newSingleThreadScheduledExecutor()
        val clients = mutableListOf<HttpURLConnection>()
        val streams = mutableMapOf<HttpURLConnection, BufferedInputStream>()
        fun connect(path: String): HttpURLConnection =
            (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000; readTimeout = 3000; clients.add(this)
            }
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
        fun verifyFrame(connection: HttpURLConnection) {
            assertEquals(200, connection.responseCode)
            assertTrue(connection.contentType.contains("boundary=FRAME"))
            val input = streams.getOrPut(connection) { BufferedInputStream(connection.inputStream) }
            val expected = Mjpeg.header(jpeg.size) + jpeg + Mjpeg.terminator
            val actual = ByteArray(expected.size)
            var offset = 0
            while (offset < actual.size) {
                val count = input.read(actual, offset, actual.size - offset)
                check(count > 0)
                offset += count
            }
            assertArrayEquals(expected, actual)
        }
        try {
            server.start()
            assertEquals("Ok", connect("/cam").inputStream.bufferedReader().use { it.readText() })
            assertEquals(503, connect("/cam.mjpeg").responseCode)
            server.streaming = true
            producer.scheduleAtFixedRate({ server.publish(jpeg) }, 0, 50, TimeUnit.MILLISECONDS)
            val first = connect("/cam.mjpeg")
            verifyFrame(first)
            val second = connect("/cam.mjpeg")
            verifyFrame(second)
            // Opening and then disconnecting another viewer must not cancel the first collector.
            second.disconnect()
            verifyFrame(first)
            verifyFrame(connect("/cam.mjpeg"))
            server.streaming = false
            assertEquals(503, connect("/cam.mjpeg").responseCode)
        } finally {
            producer.shutdownNow()
            clients.forEach { it.disconnect() }
            server.stop()
        }
    }
}
