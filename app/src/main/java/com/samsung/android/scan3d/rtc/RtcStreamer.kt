package com.samsung.android.scan3d.rtc

import android.content.Context
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.Display
import com.samsung.android.scan3d.http.RtcSession
import com.samsung.android.scan3d.http.RtcSignal
import com.samsung.android.scan3d.http.RtcSignaling
import com.samsung.android.scan3d.http.ReceiveOffer
import org.webrtc.audio.JavaAudioDeviceModule
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RtcStatus(
    val clients: Int = 0, val connected: Int = 0, val mbps: Double = 0.0,
    val fps: Double = 0.0, val rttMs: Double? = null,
    val encoder: String = "", val limitation: String = "", val error: String? = null,
    val audioCapturing: Boolean = false, val audioError: String? = null
)

/** Native WebRTC owns encoding and RTP; CamEngine remains the only camera owner. */
class RtcStreamer(private val context: Context) : RtcSignaling {
    private val thread = HandlerThread("RemoteCam-rtc").apply { start() }
    private val handler = Handler(thread.looper)
    private val dispatcher = handler.asCoroutineDispatcher()
    private var egl: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var helper: SurfaceTextureHelper? = null
    private var source: VideoSource? = null
    private var track: VideoTrack? = null
    private var audioDevice: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var audioMuted = false
    private var audioCapturing = false
    private var audioError: String? = null
    private var audioEpoch = 0
    private var surface: Surface? = null
    private var bitrate = 12_000_000
    @Volatile private var rotationDegrees = -1
    private var disposed = false
    @Volatile var enabled = false
    @Volatile var status = RtcStatus(); private set
    private val peers = mutableSetOf<Peer>()

    private fun initialize(size: Size) {
        if (factory != null) return
        initializeLibrary(context)
        val shared = EglBase.create()
        egl = shared
        // Baseline H.264 avoids frame reordering. Never silently substitute a software encoder.
        val hardware = HardwareVideoEncoderFactory(shared.eglBaseContext, false, false) { info ->
            supports(info, size)
        }
        val h264 = object : VideoEncoderFactory {
            override fun getSupportedCodecs() = hardware.supportedCodecs.filter { it.name == "H264" }.toTypedArray()
            override fun createEncoder(info: VideoCodecInfo): VideoEncoder? =
                if (info.name == "H264") hardware.createEncoder(info)?.let(::PeriodicKeyFrameEncoder) else null
        }
        check(h264.supportedCodecs.isNotEmpty()) { "No hardware H.264 encoder supports this resolution. Select JPEG or another resolution." }
        val epoch = ++audioEpoch
        fun recordError(message: String) { handler.post {
            if (epoch == audioEpoch && !disposed) {
                audioError = "Microphone unavailable: $message. Toggle audio off and on to retry."
                audioCapturing = false
                audioDevice?.setMicrophoneMute(true)
                audioTrack?.setEnabled(false)
                updateStatus()
            }
        } }
        audioDevice = JavaAudioDeviceModule.builder(context)
            .setUseStereoInput(false)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(message: String) = recordError(message)
                override fun onWebRtcAudioRecordStartError(code: JavaAudioDeviceModule.AudioRecordStartErrorCode, message: String) = recordError(message)
                override fun onWebRtcAudioRecordError(message: String) = recordError(message)
            })
            .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                override fun onWebRtcAudioRecordStart() { handler.post {
                    if (epoch == audioEpoch && !disposed) { audioCapturing = true; updateStatus() }
                } }
                override fun onWebRtcAudioRecordStop() { handler.post {
                    if (epoch == audioEpoch && !disposed) { audioCapturing = false; updateStatus() }
                } }
            }).createAudioDeviceModule()
        audioDevice!!.setMicrophoneMute(true)
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioDevice).setVideoEncoderFactory(h264)
            .setVideoDecoderFactory(HardwareVideoDecoderFactory(shared.eglBaseContext)).createPeerConnectionFactory()
    }

    /** Called on the camera worker after closing its previous capture session. */
    fun startCapture(size: Size, limitMbps: Int, sensorOrientation: Int, frontFacing: Boolean,
                     manualRotation: Int,
                     onFrame: () -> Unit): Surface = runBlocking(dispatcher) {
        check(!disposed)
        stopCaptureInternal()
        // The hardware factory's size filter is specific to this capture configuration.
        factory?.dispose(); factory = null
        audioDevice?.release(); audioDevice = null
        egl?.release(); egl = null
        initialize(size)
        bitrate = limitMbps * 1_000_000
        setRotation(manualRotation)
        val videoSource = factory!!.createVideoSource(false)
        source = videoSource
        track = factory!!.createVideoTrack("remotecam-video", videoSource)
        val texture = checkNotNull(SurfaceTextureHelper.create("RemoteCam-texture", egl!!.eglBaseContext))
        helper = texture
        texture.setTextureSize(size.width, size.height)
        // Camera2's SurfaceTexture already carries a sensor rotation (and front-camera
        // mirror). Undo those in texture space, then send rotation as frame metadata,
        // as WebRTC's Camera2Session does. Otherwise portrait pixels are squeezed into
        // the landscape buffer dimensions before encoding; CSS cannot repair that.
        val cameraTransform = Matrix().apply {
            preTranslate(0.5f, 0.5f)
            if (frontFacing) preScale(-1f, 1f)
            preRotate(-sensorOrientation.toFloat())
            preTranslate(-0.5f, -0.5f)
        }
        val displays = context.getSystemService(DisplayManager::class.java)
        videoSource.capturerObserver.onCapturerStarted(true)
        texture.startListening { frame ->
            val deviceRotation = when (displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val buffer = frame.buffer as TextureBufferImpl
            val corrected = VideoFrame(
                buffer.applyTransformMatrix(cameraTransform, buffer.width, buffer.height),
                CameraFrameOrientation.rotation(sensorOrientation, deviceRotation, frontFacing, rotationDegrees),
                frame.timestampNs)
            try {
                videoSource.capturerObserver.onFrameCaptured(corrected)
                onFrame()
            } finally { corrected.release() }
        }
        Surface(texture.surfaceTexture).also { surface = it }
    }

    fun setRotation(degrees: Int) {
        rotationDegrees = degrees.takeIf { it in listOf(0, 90, 180, 270) } ?: -1
    }

    /** Mute changes only samples; adding/removing a track requires new SDP, not a new camera. */
    fun setAudio(enable: Boolean, muted: Boolean) = runBlocking(dispatcher) {
        if (disposed) return@runBlocking
        val allowed = enable && enabled && factory != null &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (allowed != (audioTrack != null)) {
            audioDevice?.setMicrophoneMute(true)
            audioTrack?.setEnabled(false)
            peers.toList().forEach { peer -> peer.emit("reset\nAudio settings changed; reconnecting"); peer.dispose() }
            audioTrack?.dispose(); audioTrack = null
            audioSource?.dispose(); audioSource = null
            audioCapturing = false
            audioError = null
            if (allowed) {
                try {
                    val source = factory!!.createAudioSource(MediaConstraints())
                    audioSource = source
                    audioTrack = factory!!.createAudioTrack("remotecam-audio", source)
                } catch (error: RuntimeException) {
                    audioSource?.dispose(); audioSource = null
                    audioError = "Microphone unavailable: ${error.message}. Toggle audio off and on to retry."
                }
            }
        }
        if (!enable) audioError = null
        audioMuted = muted
        audioDevice?.setMicrophoneMute(!allowed || muted || audioError != null)
        audioTrack?.setEnabled(!muted && audioError == null)
        updateStatus()
    }

    fun stopCapture() = runBlocking(dispatcher) { stopCaptureInternal() }
    private fun stopCaptureInternal() {
        audioDevice?.setMicrophoneMute(true)
        audioTrack?.setEnabled(false)
        peers.toList().forEach { peer -> peer.emit("reset\nCamera settings changed or stream stopped"); peer.dispose() }
        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null
        audioCapturing = false; audioError = null
        helper?.stopListening()
        source?.capturerObserver?.onCapturerStopped()
        surface?.release(); surface = null
        helper?.dispose(); helper = null
        track?.dispose(); track = null
        source?.dispose(); source = null
        status = RtcStatus()
    }

    fun setBitrate(limitMbps: Int) { handler.post {
        bitrate = limitMbps * 1_000_000
        peers.toList().forEach { peer -> runCatching { peer.configureSender() }.onFailure {
            peer.emit("error\nBitrate update failed. Reconnect or choose a lower limit.")
            peer.dispose()
        } }
    } }

    override suspend fun open(offer: String, emit: (String) -> Unit): RtcSession = openPeer(offer, emit, false)
    override suspend fun openHttp(offer: String): RtcSession = openPeer(offer, {}, true)
    private suspend fun openPeer(offer: String, emit: (String) -> Unit, gatherOnce: Boolean): RtcSession = withContext(dispatcher) {
        val wantsAudio = ReceiveOffer.validate(offer)
        check(enabled && track != null && !disposed) { "Select H.264 + WebRTC and enable Stream on the phone." }
        check(peers.size < 4) { "Maximum of four WebRTC receivers reached." }
        val peer = Peer(emit)
        // Also covers cancellation while the completed peer is being returned to the HTTP worker.
        currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) handler.post { peer.dispose() } }
        try {
            val config = PeerConnection.RTCConfiguration(emptyList()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = if (gatherOnce) PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
                    else PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }
            peer.pc = checkNotNull(factory!!.createPeerConnection(config, peer))
            peer.pc.setAudioPlayout(false)
            peers.add(peer)
            peer.sender = peer.pc.addTrack(track!!, listOf("remotecam"))
            if (wantsAudio && audioTrack != null) peer.audioSender = peer.pc.addTrack(audioTrack!!, listOf("remotecam"))
            peer.setDescription(SessionDescription(SessionDescription.Type.OFFER, offer), false)
            if (peer.audioSender != null) {
                val opus = factory!!.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)
                    .codecs.filter { it.name.equals("opus", ignoreCase = true) }
                peer.pc.transceivers.filter { it.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO }
                    .forEach { it.setCodecPreferences(opus).throwError() }
            }
            val answer = peer.createAnswer()
            check(answer.description.contains("H264/90000", ignoreCase = true)) { "Receiver did not negotiate H.264." }
            peer.setDescription(answer, true)
            peer.answer = answer.description
            peer.configureSender()
            handler.postDelayed({ if (!peer.closed && !peer.connected) peer.dispose() }, 30_000)
            updateStatus()
            peer
        } catch (e: Exception) {
            peer.dispose()
            status = status.copy(error = e.message)
            throw e
        }
    }

    private inner class Peer(val emit: (String) -> Unit) : PeerConnection.Observer, RtcSession {
        lateinit var pc: PeerConnection
        var sender: RtpSender? = null
        var audioSender: RtpSender? = null
        override var answer = ""
        @Volatile override var closed = false
        private val iceGathered = CompletableDeferred<Unit>()
        var connected = false
        var previousBytes = 0.0
        var previousTime = 0.0
        var mbps = 0.0
        var fps = 0.0
        var rttMs: Double? = null
        var encoder = ""
        var limitation = ""
        fun configureSender() {
            if (closed) return
            sender?.let { output ->
                val params = output.parameters
                params.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
                params.encodings.forEach { it.maxBitrateBps = bitrate; it.maxFramerate = 30; it.scaleResolutionDownBy = 1.0 }
                check(output.setParameters(params)) { "Encoder bitrate configuration was rejected" }
            }
            audioSender?.let { output ->
                val params = output.parameters
                params.encodings.forEach { it.maxBitrateBps = 64_000 }
                check(output.setParameters(params)) { "Audio bitrate configuration was rejected" }
            }
            pc.setBitrate(300_000, minOf(4_000_000, bitrate), bitrate + if (audioSender != null) 64_000 else 0)
        }
        override suspend fun addIce(candidate: RtcSignal.Ice) = withContext(dispatcher) {
            if (!closed) pc.addIceCandidate(IceCandidate(candidate.mid, candidate.index, candidate.candidate))
            Unit
        }
        override suspend fun completeAnswer(): String = withContext(dispatcher) {
            withTimeout(8000) { iceGathered.await() }
            check(!closed) { "WebRTC session closed while gathering candidates" }
            checkNotNull(pc.localDescription).description
        }
        override suspend fun close() { if (!closed) withContext(dispatcher) { dispose() } }
        fun dispose() {
            if (closed) return
            closed = true; peers.remove(this)
            iceGathered.completeExceptionally(IllegalStateException("WebRTC session closed"))
            if (::pc.isInitialized) { pc.close(); pc.dispose() }
            updateStatus()
        }
        suspend fun setDescription(sdp: SessionDescription, local: Boolean) = suspendCancellableCoroutine<Unit> { continuation ->
            check(!closed) { "Camera configuration changed; reconnect" }
            val observer = object : SdpObserver {
                override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
                override fun onCreateSuccess(sdp: SessionDescription) = Unit
                override fun onCreateFailure(error: String) = Unit
            }
            if (local) pc.setLocalDescription(observer, sdp) else pc.setRemoteDescription(observer, sdp)
        }
        suspend fun createAnswer() = suspendCancellableCoroutine<SessionDescription> { continuation ->
            check(!closed) { "Camera configuration changed; reconnect" }
            pc.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) { if (continuation.isActive) continuation.resume(sdp) }
                override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error)) }
                override fun onSetSuccess() = Unit
                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
        }
        override fun onIceCandidate(candidate: IceCandidate) { emit("ice\n${candidate.sdpMid}\n${candidate.sdpMLineIndex}\n${candidate.sdp}") }
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { handler.post {
            if (!closed) {
                connected = state == PeerConnection.PeerConnectionState.CONNECTED
                if (state == PeerConnection.PeerConnectionState.FAILED || state == PeerConnection.PeerConnectionState.CLOSED) {
                    emit("reset\nConnection failed; reconnecting")
                    dispose()
                } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED) {
                    handler.postDelayed({ if (!closed && !connected) dispose() }, 30_000)
                }
                updateStatus()
            }
        } }
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) iceGathered.complete(Unit)
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) { channel.close(); channel.dispose() }
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
    }

    private fun updateStatus() {
        status = RtcStatus(peers.size, peers.count { it.connected }, peers.sumOf { it.mbps },
            peers.filter { it.connected }.minOfOrNull { it.fps } ?: 0.0,
            peers.mapNotNull { it.rttMs }.maxOrNull(), peers.map { it.encoder }.filter { it.isNotEmpty() }.distinct().joinToString(),
            peers.map { it.limitation }.filter { it.isNotEmpty() && it != "none" }.distinct().joinToString(),
            audioCapturing = audioCapturing && audioTrack != null && !audioMuted && audioError == null,
            audioError = audioError)
    }
    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            peers.toList().forEach { peer -> peer.pc.getStats { report -> handler.post {
                if (!peer.closed) {
                    report.statsMap.values.forEach { stat ->
                        fun number(key: String) = (stat.members[key] as? Number)?.toDouble() ?: 0.0
                        if (stat.type == "outbound-rtp" && stat.members["kind"] == "video") {
                            val bytes = number("bytesSent")
                            if (peer.previousTime > 0 && stat.timestampUs > peer.previousTime)
                                peer.mbps = ((bytes - peer.previousBytes) * 8 / (stat.timestampUs - peer.previousTime)).coerceAtLeast(0.0)
                            peer.previousBytes = bytes; peer.previousTime = stat.timestampUs
                            peer.fps = number("framesPerSecond")
                            peer.encoder = stat.members["encoderImplementation"]?.toString().orEmpty()
                            peer.limitation = stat.members["qualityLimitationReason"]?.toString().orEmpty()
                        }
                        if (stat.type == "candidate-pair" && stat.members["state"] == "succeeded" && stat.members.containsKey("currentRoundTripTime"))
                            peer.rttMs = number("currentRoundTripTime") * 1000
                    }
                    updateStatus()
                }
            } } }
            handler.postDelayed(this, 1000)
        }
    }
    init { handler.post(poll) }
    fun destroy() = runBlocking(dispatcher) {
        if (!disposed) {
            enabled = false
            stopCaptureInternal()
            disposed = true
            handler.removeCallbacks(poll)
            factory?.dispose(); factory = null
            ++audioEpoch
            audioDevice?.release(); audioDevice = null
            egl?.release(); egl = null
            thread.quitSafely()
        }
    }
    companion object {
        private var initialized = false
        fun initializeLibrary(context: Context) {
            synchronized(RtcStreamer::class.java) {
                if (!initialized) {
                    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
                    initialized = true
                }
            }
        }
        private fun supports(info: MediaCodecInfo, size: Size): Boolean = runCatching {
            info.isEncoder && info.supportedTypes.any { it.equals("video/avc", true) } &&
                (if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else info.name.startsWith("OMX.qcom.") || info.name.startsWith("OMX.Exynos.")) &&
                info.getCapabilitiesForType("video/avc").videoCapabilities?.areSizeAndRateSupported(size.width, size.height, 30.0) == true
        }.getOrDefault(false)
        fun supportedSizes(sizes: List<Size>): List<Size> {
            val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            return sizes.filter { size -> codecs.any { supports(it, size) } }
        }
    }
}
