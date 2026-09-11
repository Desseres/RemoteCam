package com.samsung.android.scan3d.serv

import android.content.Context
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.util.Size
import android.view.Display
import android.view.Surface
import com.samsung.android.scan3d.rtc.CameraFrameOrientation
import com.samsung.android.scan3d.rtc.RtcStreamer
import org.webrtc.EglBase
import org.webrtc.SurfaceTextureHelper
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoSink

/** Camera output belongs to the service; the activity only subscribes to its frames. */
class CameraPreview(context: Context) {
    init { RtcStreamer.initializeLibrary(context) }
    private val egl = EglBase.create()
    val sharedContext: EglBase.Context get() = egl.eglBaseContext
    private val displays = context.getSystemService(DisplayManager::class.java)
    @Volatile var sink: VideoSink? = null
    @Volatile var enabled = true
    private var helper: SurfaceTextureHelper? = null
    private var surface: Surface? = null

    // Called only on the camera worker, after closing the previous capture session.
    fun start(size: Size, sensorOrientation: Int, frontFacing: Boolean): Surface {
        stop()
        val texture = checkNotNull(SurfaceTextureHelper.create("RemoteCam-preview", sharedContext))
        helper = texture
        texture.setTextureSize(size.width, size.height)
        val cameraTransform = Matrix().apply {
            preTranslate(0.5f, 0.5f)
            if (frontFacing) preScale(-1f, 1f)
            preRotate(-sensorOrientation.toFloat())
            preTranslate(-0.5f, -0.5f)
        }
        texture.startListening { frame ->
            // Always drain the camera output, even when no UI is visible. Never let
            // an abandoned/hidden activity surface stall the camera's other outputs.
            val output = sink?.takeIf { enabled } ?: return@startListening
            val displayRotation = when (displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val buffer = frame.buffer as TextureBufferImpl
            val corrected = VideoFrame(buffer.applyTransformMatrix(cameraTransform, buffer.width, buffer.height),
                CameraFrameOrientation.rotation(sensorOrientation, displayRotation, frontFacing), frame.timestampNs)
            try { output.onFrame(corrected) } finally { corrected.release() }
        }
        return Surface(texture.surfaceTexture).also { surface = it }
    }

    fun stop() {
        helper?.stopListening()
        surface?.release(); surface = null
        helper?.dispose(); helper = null
    }

    fun destroy() {
        sink = null
        stop()
        egl.release()
    }

    companion object {
        fun chooseSize(sizes: List<Size>, capture: Size): Size {
            require(sizes.isNotEmpty()) { "No camera preview size is available" }
            val bounded = sizes.filter { it.width.toLong() * it.height <= 1280 * 720 }
            val candidates = bounded.ifEmpty { sizes }
            return candidates.minWith(compareBy<Size> {
                kotlin.math.abs(it.width.toDouble() / it.height - capture.width.toDouble() / capture.height)
            }.thenBy { if (bounded.isEmpty()) it.width.toLong() * it.height else -it.width.toLong() * it.height })
        }
    }
}
