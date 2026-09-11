package com.samsung.android.scan3d.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.seconds

class HttpService(private val port: Int = 8080) {
    private val frames = LatestFrames()
    val transfers = TransferMonitor()
    @Volatile var streaming = false
        set(value) {
            field = value
            if (!value) frames.close()
        }
    private val viewerHtml by lazy {
        checkNotNull(javaClass.getResourceAsStream("/viewer.html")).bufferedReader().use { it.readText() }
    }
    private val server = embeddedServer(CIO, host = "0.0.0.0", port = port) {
        install(WebSockets) {
            pingPeriod = 10.seconds
            timeout = 10.seconds
            maxFrameSize = 64L * 1024 * 1024
        }
        routing {
            get("/cam") { call.respondText("Ok") }
            get("/view") {
                call.response.header("Cache-Control", "no-store")
                call.respondText(viewerHtml, ContentType.Text.Html)
            }
            webSocket("/live") {
                if (!streaming) {
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Enable Stream in RemoteCam"))
                    return@webSocket
                }
                val id = transfers.connect(confirmed = true)
                val queue = frames.subscribe(id)
                var timedOut = false
                try {
                    for (frame in queue) {
                        if (!streaming) break
                        transfers.begin(id)
                        // At most one JPEG is in flight. Do not send the next until the receiver
                        // has decoded and drawn this one. Pending frames are replaced by the latest.
                        val ack = withTimeout(2000) {
                            val payload = ByteBuffer.allocate(8 + frame.jpeg.size)
                                .putLong(frame.sequence).put(frame.jpeg).array()
                            send(Frame.Binary(true, payload))
                            incoming.receive()
                        }
                        if (ack !is Frame.Text || ack.readText().toLongOrNull() != frame.sequence) {
                            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Invalid frame acknowledgement"))
                            break
                        }
                        transfers.complete(id, frame.jpeg.size, frame.sequence)
                    }
                } catch (e: TimeoutCancellationException) {
                    timedOut = true
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Receiver too slow; reconnect for the latest frame"))
                } finally {
                    frames.unsubscribe(id)
                    transfers.disconnect(id, timedOut)
                }
            }
            get("/cam.mjpeg") {
                if (!streaming) {
                    call.respondText("Enable Stream in RemoteCam", status = HttpStatusCode.ServiceUnavailable)
                    return@get
                }
                call.response.header("Cache-Control", "no-store")
                call.respondBytesWriter(ContentType.parse("multipart/x-mixed-replace; boundary=FRAME")) {
                    val id = transfers.connect(confirmed = false)
                    val queue = frames.subscribe(id)
                    var timedOut = false
                    try {
                        for (frame in queue) {
                            if (!streaming) break
                            transfers.begin(id)
                            withTimeout(2000) {
                                writeFully(Mjpeg.header(frame.jpeg.size))
                                writeFully(frame.jpeg)
                                writeFully(Mjpeg.terminator)
                                flush()
                            }
                            // MJPEG can report socket writes, but cannot confirm playback in OBS.
                            transfers.complete(id, frame.jpeg.size, frame.sequence)
                        }
                    } catch (e: TimeoutCancellationException) {
                        timedOut = true
                        throw e
                    } finally {
                        frames.unsubscribe(id)
                        transfers.disconnect(id, timedOut)
                    }
                }
            }
        }
    }
    fun start() { server.start(wait = false) }
    fun publish(bytes: ByteArray) { if (streaming) frames.publish(bytes) }
    fun stop() { streaming = false; frames.close(); server.stop(0, 1000) }
}
