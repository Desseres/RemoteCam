package com.samsung.android.scan3d.serv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.Display
import com.samsung.android.scan3d.http.HttpService
import com.samsung.android.scan3d.http.TransferStats
import com.samsung.android.scan3d.rtc.RtcStreamer
import com.samsung.android.scan3d.rtc.RtcStatus
import com.samsung.android.scan3d.rtc.CameraFrameOrientation
import com.samsung.android.scan3d.util.JpegRotation
import com.samsung.android.scan3d.util.Selector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import org.webrtc.VideoSink

data class CameraConfig(
    val cameraId: String = "", val resolution: Size? = null,
    val quality: Int = 80, val preview: Boolean = true, val stream: Boolean = false,
    val mode: StreamMode = StreamMode.JPEG, val bitrateMbps: Int = 12,
    val rotationDegrees: Int = -1, val tuning: CameraTuning = CameraTuning(),
    val audioEnabled: Boolean = false, val audioMuted: Boolean = false
)
data class CameraStatus(
    val config: CameraConfig = CameraConfig(),
    val sensors: List<Selector.SensorDesc> = emptyList(),
    val sizes: List<Size> = emptyList(),
    val fps: Int = 0, val sourceMbps: Double = 0.0, val averageFrameBytes: Double = 0.0,
    val transfer: TransferStats = TransferStats(), val error: String? = null,
    val rtc: RtcStatus = RtcStatus(),
    val controls: CameraControlLimits = CameraControlLimits(),
    val focusState: Int? = null, val focusDistance: Float? = null, val actualZoom: Float? = null,
    val stopped: Boolean = false, val captureGeneration: Int = 0
)

/** All camera ownership and callbacks are serialized on one handler, never the UI thread. */
class CamEngine(context: Context, private val http: HttpService) {
    private val preview = CameraPreview(context.applicationContext)
    val previewContext get() = preview.sharedContext
    private val rtc = RtcStreamer(context.applicationContext)
    private val manager = context.getSystemService(CameraManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)
    private val settings = CameraSettings(context)
    private val thread = HandlerThread("RemoteCam-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val mutableStatus = MutableStateFlow(CameraStatus())
    val status = mutableStatus.asStateFlow()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var generation = 0
    @Volatile private var destroyed = false
    private var lastStats = SystemClock.elapsedRealtime()
    private var frameCount = 0
    @Volatile var capturedFrames = 0L
        private set
    private var byteCount = 0L
    private var repeatingRequest: CaptureRequest.Builder? = null
    private var cameraCharacteristics: CameraCharacteristics? = null
    private var captureCallback: CameraCaptureSession.CaptureCallback? = null
    private var lastFocusState: Int? = null
    private var lastFocusDistance: Float? = null
    private var lastZoom: Float? = null
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY && !destroyed &&
                mutableStatus.value.config.rotationDegrees == -1) updateJpegOrientation()
        }
    }

    init {
        displays.registerDisplayListener(displayListener, handler)
        http.rtc = rtc
        handler.post {
            try {
                val sensors = Selector.enumerateCameras(manager)
                check(sensors.isNotEmpty()) { "No JPEG camera is available" }
                mutableStatus.value = CameraStatus(
                    config = settings.load(sensors.map { it.cameraId }), sensors = sensors)
                restart()
            } catch (e: Exception) { fail(e) }
        }
    }

    fun configure(config: CameraConfig) {
        handler.post {
            if (destroyed || config == mutableStatus.value.config) return@post
            if (mutableStatus.value.sensors.none { it.cameraId == config.cameraId }) return@post
            val previous = mutableStatus.value.config
            val tuning = if (previous.cameraId != config.cameraId) settings.tuningFor(config.cameraId)
                else mutableStatus.value.controls.normalize(config.tuning)
            val selected = config.copy(quality = config.quality.coerceIn(1, 100), bitrateMbps = config.bitrateMbps.coerceIn(2, 40), tuning = tuning,
                rotationDegrees = config.rotationDegrees.takeIf { it in listOf(0, 90, 180, 270) } ?: -1,
                resolution = if (previous.mode != config.mode) settings.resolutionFor(config.cameraId, config.mode) ?: config.resolution else config.resolution)
            mutableStatus.value = mutableStatus.value.copy(config = selected)
            preview.enabled = selected.preview
            if (previous.copy(bitrateMbps = selected.bitrateMbps, rotationDegrees = selected.rotationDegrees, tuning = selected.tuning,
                    preview = selected.preview, audioEnabled = selected.audioEnabled, audioMuted = selected.audioMuted) == selected &&
                (selected.stream || previous.preview == selected.preview)) {
                settings.save(selected)
                rtc.setAudio(selected.stream && selected.mode.isWebRtc && selected.audioEnabled, selected.audioMuted)
                if (previous.bitrateMbps != selected.bitrateMbps) rtc.setBitrate(selected.bitrateMbps)
                rtc.setRotation(selected.rotationDegrees)
                if (previous.rotationDegrees != selected.rotationDegrees) updateJpegOrientation()
                if (previous.tuning != selected.tuning) updateTuning(previous.tuning.focusMode != selected.tuning.focusMode)
                return@post
            }
            restart()
        }
    }

    fun setPreview(sink: VideoSink?) {
        // Detach synchronously before the activity releases its renderer. The renderer
        // also safely discards a frame already in flight when release is called.
        preview.sink = sink
        handler.post {
            if (destroyed || mutableStatus.value.config.stream) return@post
            val needsCamera = mutableStatus.value.config.preview && preview.sink != null
            if (needsCamera != (cameraCharacteristics != null)) restart()
        }
    }

    private fun closeCamera() {
        generation++
        repeatingRequest = null
        cameraCharacteristics = null
        captureCallback = null
        lastFocusState = null; lastFocusDistance = null; lastZoom = null
        runCatching { session?.stopRepeating() }
        session?.close()
        session = null
        camera?.close()
        camera = null
        reader?.close()
        reader = null
        preview.stop()
        rtc.enabled = false
        rtc.stopCapture()
    }

    @SuppressLint("MissingPermission") // The activity grants CAMERA before starting the private service.
    private fun restart() {
        closeCamera()
        if (destroyed) return
        try {
            var config = mutableStatus.value.config
            preview.enabled = config.preview
            if (config.cameraId.isEmpty()) return
            val c = manager.getCameraCharacteristics(config.cameraId)
            cameraCharacteristics = c
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = (if (config.mode == StreamMode.JPEG) map?.getOutputSizes(ImageFormat.JPEG)?.toList().orEmpty()
                else RtcStreamer.supportedSizes(map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty(), checkNotNull(config.mode.rtcCodec)))
                .sortedBy { it.width.toLong() * it.height }
            mutableStatus.value = mutableStatus.value.copy(sizes = sizes)
            check(sizes.isNotEmpty()) {
                if (config.mode == StreamMode.WEBRTC_H265)
                    "H.265 test mode is unavailable for this camera: no compatible hardware HEVC WebRTC encoder at 30 fps. Select H.264 + WebRTC or JPEG."
                else "No supported output sizes for this mode. Select JPEG or another camera."
            }
            val size = config.resolution?.takeIf { it in sizes }
                ?: sizes.lastOrNull { it.width <= 1920 && it.height <= 1080 } ?: sizes.first()
            val controls = CameraControls.limits(c)
            config = config.copy(resolution = size, tuning = controls.normalize(config.tuning))
            settings.save(config)
            mutableStatus.value = mutableStatus.value.copy(config = config, sizes = sizes, error = null,
                fps = 0, sourceMbps = 0.0, averageFrameBytes = 0.0, transfer = TransferStats(), rtc = RtcStatus(),
                controls = controls, focusState = null, focusDistance = null, actualZoom = null, captureGeneration = generation)
            http.streaming = config.stream && config.mode == StreamMode.JPEG
            if (!config.stream && (!config.preview || preview.sink == null)) {
                cameraCharacteristics = null
                return
            }
            val previewOutput = preview.start(CameraPreview.chooseSize(
                map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty(), size),
                c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT)
            val ticket = generation
            captureCallback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    if (ticket != generation || destroyed) return
                    lastFocusState = result.get(CaptureResult.CONTROL_AF_STATE)
                    lastFocusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                    if (android.os.Build.VERSION.SDK_INT >= 30) lastZoom = result.get(CaptureResult.CONTROL_ZOOM_RATIO)
                }
            }
            val imageReader = if (config.mode == StreamMode.JPEG) ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 3) else null
            reader = imageReader
            imageReader?.setOnImageAvailableListener({ source ->
                if (ticket != generation || destroyed) return@setOnImageAvailableListener
                try {
                    source.acquireLatestImage()?.use { image ->
                        val buffer = image.planes[0].buffer
                        val bytes = JpegRotation.normalize(ByteArray(buffer.remaining()).also { buffer.get(it) })
                        http.publish(bytes)
                        recordFrame(bytes.size)
                    }
                } catch (e: Exception) { if (ticket == generation) fail(e) }
            }, handler)
            val rtcSurface = if (config.mode.isWebRtc) rtc.startCapture(size, config.bitrateMbps,
                c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT,
                config.rotationDegrees, checkNotNull(config.mode.rtcCodec)) {
                handler.post { if (ticket == generation && !destroyed) recordFrame(0) }
            } else null
            rtc.enabled = config.stream && config.mode.isWebRtc
            rtc.setAudio(rtc.enabled && config.audioEnabled, config.audioMuted)
            manager.openCamera(config.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (ticket != generation || destroyed) { device.close(); return }
                    camera = device
                    try {
                        val outputs = listOfNotNull(imageReader?.surface, rtcSurface, previewOutput)
                        device.createCaptureSession(SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR, outputs.map(::OutputConfiguration),
                            executor, object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(captureSession: CameraCaptureSession) {
                                    if (ticket != generation || destroyed) { captureSession.close(); return }
                                    session = captureSession
                                    try {
                                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                            outputs.forEach(::addTarget)
                                            set(CaptureRequest.JPEG_QUALITY, config.quality.toByte())
                                            if (config.mode == StreamMode.JPEG)
                                                set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation(c))
                                            if (config.mode.isWebRtc) {
                                                val ranges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
                                                ranges.filter { it.upper <= 30 }.maxWithOrNull(compareBy({ it.upper }, { it.lower }))?.let {
                                                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                                                }
                                            }
                                            CameraControls.apply(this, c, mutableStatus.value.config.tuning)
                                        }
                                        lastStats = SystemClock.elapsedRealtime(); frameCount = 0; byteCount = 0
                                        repeatingRequest = request
                                        captureSession.setRepeatingRequest(request.build(), captureCallback, handler)
                                        startFocusIfNeeded()
                                    } catch (e: Exception) { fail(e) }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    s.close()
                                    if (ticket == generation) fail(IllegalStateException("Unsupported camera output combination; try a lower resolution"))
                                }
                            }))
                    } catch (e: Exception) { fail(e) }
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (ticket == generation) fail(IllegalStateException("Camera disconnected"))
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (ticket == generation) fail(IllegalStateException("Camera error $error; check that another app is not using it"))
                }
            }, handler)
        } catch (e: Exception) { fail(e) }
    }

    private fun jpegOrientation(c: CameraCharacteristics): Int {
        val displayDegrees = when (displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return CameraFrameOrientation.rotation(c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
            displayDegrees, c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT,
            mutableStatus.value.config.rotationDegrees)
    }

    private fun updateJpegOrientation() {
        if (mutableStatus.value.config.mode != StreamMode.JPEG) return
        val request = repeatingRequest ?: return
        val c = cameraCharacteristics ?: return
        val degrees = jpegOrientation(c)
        if (request.get(CaptureRequest.JPEG_ORIENTATION) == degrees) return
        try {
            request.set(CaptureRequest.JPEG_ORIENTATION, degrees)
            session?.setRepeatingRequest(request.build(), captureCallback, handler)
        } catch (e: Exception) { fail(e) }
    }

    fun refocus() { handler.post {
        if (!destroyed && mutableStatus.value.config.tuning.focusMode != FocusMode.MANUAL) updateTuning(true)
    } }

    private fun updateTuning(restartFocus: Boolean) {
        val request = repeatingRequest ?: return
        val c = cameraCharacteristics ?: return
        val captureSession = session ?: return
        try {
            if (restartFocus && mutableStatus.value.controls.focusLock) {
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
                captureSession.capture(request.build(), captureCallback, handler)
            }
            CameraControls.apply(request, c, mutableStatus.value.config.tuning)
            captureSession.setRepeatingRequest(request.build(), captureCallback, handler)
            if (restartFocus) startFocusIfNeeded()
        } catch (e: Exception) { fail(e) }
    }

    private fun startFocusIfNeeded() {
        val request = repeatingRequest ?: return
        val c = cameraCharacteristics ?: return
        if (CameraControls.autofocusMode(c, mutableStatus.value.config.tuning) != CaptureRequest.CONTROL_AF_MODE_AUTO) return
        // START is a single request. Repeating it would continuously restart focusing.
        request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
        try { session?.capture(request.build(), captureCallback, handler) }
        finally { request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE) }
    }

    private fun recordFrame(bytes: Int) {
        capturedFrames++
        frameCount++; byteCount += bytes
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastStats
        if (elapsed >= 1000) {
            mutableStatus.value = mutableStatus.value.copy(
                fps = (frameCount * 1000L / elapsed).toInt(), sourceMbps = byteCount * 8.0 / elapsed / 1000.0,
                averageFrameBytes = byteCount.toDouble() / frameCount,
                transfer = http.transfers.snapshot(), rtc = rtc.status,
                focusState = lastFocusState, focusDistance = lastFocusDistance, actualZoom = lastZoom)
            lastStats = now; frameCount = 0; byteCount = 0
        }
    }

    private fun fail(e: Exception) {
        Log.e("RemoteCam", "Camera failed", e)
        closeCamera()
        http.streaming = false
        mutableStatus.value = mutableStatus.value.copy(error = e.message ?: "Camera error",
            config = mutableStatus.value.config.copy(stream = false), fps = 0, sourceMbps = 0.0,
            averageFrameBytes = 0.0, transfer = TransferStats(), rtc = RtcStatus())
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        displays.unregisterDisplayListener(displayListener)
        http.streaming = false
        handler.post {
            closeCamera()
            rtc.destroy()
            preview.destroy()
            mutableStatus.value = mutableStatus.value.copy(stopped = true)
            thread.quitSafely()
        }
    }
}
