package com.samsung.android.scan3d.serv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import com.samsung.android.scan3d.http.HttpService
import com.samsung.android.scan3d.http.TransferStats
import com.samsung.android.scan3d.util.Selector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

data class CameraConfig(
    val cameraId: String = "", val resolution: Size? = null,
    val quality: Int = 80, val preview: Boolean = true, val stream: Boolean = false
)
data class CameraStatus(
    val config: CameraConfig = CameraConfig(),
    val sensors: List<Selector.SensorDesc> = emptyList(),
    val sizes: List<Size> = emptyList(),
    val fps: Int = 0, val sourceMbps: Double = 0.0, val averageFrameBytes: Double = 0.0,
    val transfer: TransferStats = TransferStats(), val error: String? = null,
    val stopped: Boolean = false
)

/** All camera ownership and callbacks are serialized on one handler, never the UI thread. */
class CamEngine(context: Context, private val http: HttpService) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val settings = CameraSettings(context)
    private val thread = HandlerThread("RemoteCam-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }
    private val mutableStatus = MutableStateFlow(CameraStatus())
    val status = mutableStatus.asStateFlow()
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var generation = 0
    @Volatile private var destroyed = false
    private var lastStats = SystemClock.elapsedRealtime()
    private var frameCount = 0
    private var byteCount = 0L

    init {
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
            mutableStatus.value = mutableStatus.value.copy(config = config.copy(quality = config.quality.coerceIn(1, 100)))
            restart()
        }
    }

    fun setPreview(surface: Surface?) {
        handler.post {
            if (destroyed || previewSurface === surface) return@post
            previewSurface = surface
            restart()
        }
    }

    private fun closeCamera() {
        generation++
        runCatching { session?.stopRepeating() }
        session?.close()
        session = null
        camera?.close()
        camera = null
        reader?.close()
        reader = null
    }

    @SuppressLint("MissingPermission") // The activity grants CAMERA before starting the private service.
    private fun restart() {
        closeCamera()
        if (destroyed) return
        try {
            var config = mutableStatus.value.config
            if (config.cameraId.isEmpty()) return
            val c = manager.getCameraCharacteristics(config.cameraId)
            val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)?.sortedBy { it.width.toLong() * it.height }
                .orEmpty()
            check(sizes.isNotEmpty()) { "Camera has no JPEG output sizes" }
            val size = config.resolution?.takeIf { it in sizes }
                ?: sizes.lastOrNull { it.width <= 1280 && it.height <= 720 } ?: sizes.first()
            config = config.copy(resolution = size)
            settings.save(config)
            mutableStatus.value = mutableStatus.value.copy(config = config, sizes = sizes, error = null,
                fps = 0, sourceMbps = 0.0, averageFrameBytes = 0.0, transfer = TransferStats())
            http.streaming = config.stream
            val preview = previewSurface?.takeIf { config.preview && it.isValid }
            if (!config.stream && preview == null) return
            val ticket = generation
            val imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 3)
            reader = imageReader
            imageReader.setOnImageAvailableListener({ source ->
                if (ticket != generation || destroyed) return@setOnImageAvailableListener
                try {
                    source.acquireLatestImage()?.use { image ->
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        http.publish(bytes)
                        frameCount++
                        byteCount += bytes.size
                        val now = SystemClock.elapsedRealtime()
                        val elapsed = now - lastStats
                        if (elapsed >= 1000) {
                            mutableStatus.value = mutableStatus.value.copy(
                                fps = (frameCount * 1000L / elapsed).toInt(),
                                sourceMbps = byteCount * 8.0 / elapsed / 1000.0,
                                averageFrameBytes = byteCount.toDouble() / frameCount,
                                transfer = http.transfers.snapshot())
                            lastStats = now; frameCount = 0; byteCount = 0
                        }
                    }
                } catch (e: Exception) { if (ticket == generation) fail(e) }
            }, handler)
            manager.openCamera(config.cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (ticket != generation || destroyed) { device.close(); return }
                    camera = device
                    try {
                        val outputs = listOfNotNull(imageReader.surface, preview)
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
                                            val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
                                            if (CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO in afModes)
                                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                        }
                                        lastStats = SystemClock.elapsedRealtime(); frameCount = 0; byteCount = 0
                                        captureSession.setRepeatingRequest(request.build(), null, handler)
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

    private fun fail(e: Exception) {
        Log.e("RemoteCam", "Camera failed", e)
        closeCamera()
        http.streaming = false
        mutableStatus.value = mutableStatus.value.copy(error = e.message ?: "Camera error",
            config = mutableStatus.value.config.copy(stream = false), fps = 0, sourceMbps = 0.0,
            averageFrameBytes = 0.0, transfer = TransferStats())
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        http.streaming = false
        handler.post {
            closeCamera()
            mutableStatus.value = mutableStatus.value.copy(stopped = true)
            thread.quitSafely()
        }
    }
}
