package com.samsung.android.scan3d.serv

import android.content.Context
import android.util.Log
import android.util.Size

/** Read and written only on CamEngine's background handler. */
class CameraSettings(context: Context) {
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
            stream = preferences.getBoolean("stream", false)
        )
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
            .commit()
        if (saved) lastSaved = config
        else Log.w("RemoteCam", "Could not save camera configuration")
    }
}
