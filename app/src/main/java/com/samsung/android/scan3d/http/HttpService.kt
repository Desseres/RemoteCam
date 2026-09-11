package com.samsung.android.scan3d.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import kotlin.time.Duration.Companion.seconds

class HttpService(private val port: Int = 8080) {
    @Volatile var rtc: RtcSignaling? = null
    private val frames = LatestFrames()
    private val whep = WhepSessions()
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
            maxFrameSize = 256L * 1024 // Incoming ACK/signaling only; outgoing JPEGs are not limited by this.
        }
        routing {
            get("/cam") { call.respondText("Ok") }
            get("/licenses") {
                val notices = listOf("/licenses/WEBRTC-SDK.txt", "/licenses/WEBRTC.md").joinToString("\n\n") { path ->
                    checkNotNull(javaClass.getResourceAsStream(path)).bufferedReader().use { it.readText() }
                }
                call.respondText(notices, ContentType.Text.Plain)
            }
            get("/view") {
                call.response.header("Cache-Control", "no-store")
                call.respondText(viewerHtml, ContentType.Text.Html)
            }
            get("/webrtc") {
                call.response.header("Cache-Control", "no-store")
                val html = checkNotNull(javaClass.getResourceAsStream("/webrtc.html")).bufferedReader().use { it.readText() }
                call.respondText(html, ContentType.Text.Html)
            }
            get("/whep") {
                call.response.header("Allow", "POST")
                call.respondText("WebRTC signaling endpoint. In go2rtc use webrtc:http://PHONE_IP:8080/whep. For browser playback open /webrtc.",
                    status = HttpStatusCode.MethodNotAllowed)
            }
            post("/whep") {
                if (call.request.contentType().withoutParameters() != ContentType.parse("application/sdp")) {
                    call.respondText("Expected application/sdp", status = HttpStatusCode.UnsupportedMediaType)
                    return@post
                }
                var sessionId: String? = null
                var delivered = false
                try {
                    val channel = call.receiveChannel()
                    val bytes = ByteArray(WhepSessions.MAX_OFFER_BYTES + 1)
                    var length = 0
                    withTimeout(10000) {
                        while (length < bytes.size) {
                            val count = channel.readAvailable(bytes, length, bytes.size - length)
                            if (count == -1) break
                            length += count
                        }
                    }
                    if (length > WhepSessions.MAX_OFFER_BYTES) {
                        call.respondText("SDP offer too large", status = HttpStatusCode.PayloadTooLarge)
                        return@post
                    }
                    val (id, answer) = withTimeout(12000) {
                        whep.open(checkNotNull(rtc) { "WebRTC is unavailable" }, String(bytes, 0, length, Charsets.UTF_8))
                            .also { sessionId = it.first }
                    }
                    call.response.header("Location", "/whep/$id")
                    call.response.header("Cache-Control", "no-store")
                    call.respondText(answer, ContentType.parse("application/sdp"), HttpStatusCode.Created)
                    delivered = true
                } catch (e: TimeoutCancellationException) {
                    call.respondText("WebRTC negotiation timed out", status = HttpStatusCode.GatewayTimeout)
                } catch (e: CancellationException) { throw e }
                catch (e: IllegalArgumentException) {
                    call.respondText(e.message ?: "Invalid SDP", status = HttpStatusCode.BadRequest)
                } catch (e: Exception) {
                    call.respondText(e.message ?: "WebRTC unavailable", status = HttpStatusCode.ServiceUnavailable)
                } finally {
                    if (!delivered) sessionId?.let { withContext(NonCancellable) { whep.delete(it) } }
                }
            }
            delete("/whep/{id}") {
                val removed = whep.delete(call.parameters["id"].orEmpty())
                call.respond(if (removed) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }
            webSocket("/signal") {
                val output = Channel<String>(64)
                var peer: RtcSession? = null
                val writer = launch { for (message in output) send(Frame.Text(message)) }
                try {
                    val initial = withTimeout(10000) { incoming.receive() }
                    require(initial is Frame.Text) { "Expected a video offer" }
                    val offer = RtcSignal.parse(initial.readText()) as? RtcSignal.Offer
                        ?: error("Expected a video offer")
                    peer = withTimeout(10000) {
                        checkNotNull(rtc) { "WebRTC is unavailable" }.open(offer.sdp) { message ->
                            if (output.trySend(message).isFailure) output.close(IllegalStateException("Signaling receiver too slow"))
                        }
                    }
                    output.send("answer\n${peer.answer}")
                    for (frame in incoming) {
                        require(frame is Frame.Text) { "Expected ICE candidate" }
                        val candidate = RtcSignal.parse(frame.readText()) as? RtcSignal.Ice
                            ?: error("Only one offer per connection is allowed")
                        peer.addIce(candidate)
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { send(Frame.Text("error\n${e.message ?: "WebRTC connection failed"}")) }
                finally {
                    withContext(NonCancellable) { peer?.close() }
                    output.close(); writer.cancel()
                }
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
    fun stop() {
        streaming = false; frames.close(); server.stop(0, 1000)
        runBlocking { whep.close() }
    }
}
