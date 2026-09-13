package com.samsung.android.scan3d.serv

import android.content.Context
import android.util.Log
import android.util.Size

/** Read and written only on CamEngine's background handler. */
class CameraSettings(private val context: Context) {
    private val preferences = context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
    private var lastSaved: CameraConfig? = null

    fun load(availableCameraIds: List<String>): CameraConfig {
        require(availableCameraIds.isNotEmpty())
        val savedCamera = preferences.getString("camera_id", null)
        val cameraId = savedCamera?.takeIf { it in availableCameraIds } ?: availableCameraIds.first()
        val width = preferences.getInt("width", 0)
        val height = preferences.getInt("height", 0)
        return CameraConfig(
            cameraId = cameraId,
            // A resolution belongs to the saved sensor. The engine also checks its supported sizes.
            resolution = if (cameraId == savedCamera && width > 0 && height > 0) Size(width, height) else null,
            quality = preferences.getInt("quality", 80).coerceIn(1, 100),
            preview = preferences.getBoolean("preview", true),
            stream = preferences.getBoolean("stream", false),
            mode = runCatching { StreamMode.valueOf(preferences.getString("mode", "JPEG")!!) }.getOrDefault(StreamMode.JPEG),
            bitrateMbps = preferences.getInt("bitrate_mbps", 12).coerceIn(2, 40),
            rotationDegrees = preferences.getInt("stream_rotation", preferences.getInt("rtc_rotation", -1))
                .takeIf { it in listOf(0, 90, 180, 270) } ?: -1,
            tuning = tuningFor(cameraId),
            audioEnabled = preferences.getBoolean("audio_enabled", false) && hasMicrophonePermission(),
            audioMuted = preferences.getBoolean("audio_muted", false)
        )
    }

    fun hasMicrophonePermission() = androidx.core.content.ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun restoreMicrophoneService() = hasMicrophonePermission() &&
        preferences.getBoolean("audio_enabled", false) && preferences.getBoolean("stream", false) &&
        preferences.getString("mode", "JPEG") == "WEBRTC"

    fun tuningFor(cameraId: String) = CameraTuning(
        runCatching { FocusMode.valueOf(preferences.getString("focus_mode_$cameraId", "AUTO")!!) }.getOrDefault(FocusMode.AUTO),
        preferences.getFloat("focus_distance_$cameraId", 0f), preferences.getFloat("zoom_$cameraId", 1f))

    fun resolutionFor(cameraId: String, mode: StreamMode): Size? {
        val key = "${cameraId}_${mode.name}"
        val width = preferences.getInt("width_$key", 0)
        val height = preferences.getInt("height_$key", 0)
        return if (width > 0 && height > 0) Size(width, height) else null
    }

    fun save(config: CameraConfig) {
        if (config == lastSaved) return
        val size = config.resolution ?: return
        // Commit before publishing the applied configuration so even immediate process termination
        // preserves it. This never blocks the main/UI thread.
        val saved = preferences.edit()
            .putString("camera_id", config.cameraId)
            .putInt("width", size.width)
            .putInt("height", size.height)
            .putInt("quality", config.quality)
            .putBoolean("preview", config.preview)
            .putBoolean("stream", config.stream)
            .putBoolean("audio_enabled", config.audioEnabled)
            .putBoolean("audio_muted", config.audioMuted)
            .putString("mode", config.mode.name)
            .putInt("bitrate_mbps", config.bitrateMbps)
            .putInt("stream_rotation", config.rotationDegrees)
            .putString("focus_mode_${config.cameraId}", config.tuning.focusMode.name)
            .putFloat("focus_distance_${config.cameraId}", config.tuning.focusDistance)
            .putFloat("zoom_${config.cameraId}", config.tuning.zoom)
            .remove("rtc_rotation")
            .putInt("width_${config.cameraId}_${config.mode.name}", size.width)
            .putInt("height_${config.cameraId}_${config.mode.name}", size.height)
            .commit()
        if (saved) lastSaved = config
        else Log.w("RemoteCam", "Could not save camera configuration")
    }
}
