package com.samsung.android.scan3d.serv

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.samsung.android.scan3d.CameraActivity
import com.samsung.android.scan3d.R
import com.samsung.android.scan3d.http.HttpService
import java.util.concurrent.Executors

class Cam : Service() {
    private val binder = LocalBinder()
    private var started = false
    private var microphoneForeground = false
    private val http = HttpService()
    lateinit var engine: CamEngine
        private set
    inner class LocalBinder : Binder() { val service get() = this@Cam }

    override fun onBind(intent: Intent): IBinder = binder

    /** Called by the visible activity, before the camera worker can enable capture. */
    fun configure(config: CameraConfig) {
        val allowed = CameraSettings(this).hasMicrophonePermission()
        val selected = config.copy(audioEnabled = config.audioEnabled && allowed)
        if (selected.stream && selected.mode.isWebRtc && selected.audioEnabled && !microphoneForeground)
            promoteForeground(true)
        engine.configure(selected)
    }

    private fun promoteForeground(microphone: Boolean) {
        val channel = NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, CameraActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, Cam::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_linked_camera)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(if (microphone) R.string.notification_audio_text else R.string.notification_text))
            .setContentIntent(open).setOngoing(true)
            .addAction(R.drawable.ic_close, getString(R.string.stop), stop).build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
            (if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0) else 0
        ServiceCompat.startForeground(this, 123, notification, type)
        // Keep the declared capability until the service stops; releasing the actual microphone
        // happens on the RTC worker. Do not race asynchronous capture shutdown with FGS demotion.
        microphoneForeground = microphone
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (::engine.isInitialized) engine.destroy()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            promoteForeground(CameraSettings(this).restoreMicrophoneService())
            // Restore saved capture only after camera/microphone foreground types are active.
            engine = CamEngine(this, http)
            http.start()
            started = true
        }
        // A camera must never be silently restarted from the background after process death.
        return START_NOT_STICKY
    }

    override fun dump(fd: java.io.FileDescriptor?, writer: java.io.PrintWriter?, args: Array<out String>?) {
        if (!::engine.isInitialized) return
        val state = engine.status.value
        // Local ADB diagnostics: verify camera-reported values, not only requested settings.
        writer?.println("config=${state.config}")
        writer?.println("controls=${state.controls}")
        writer?.println("focusState=${state.focusState} focusDistance=${state.focusDistance} actualZoom=${state.actualZoom} fps=${state.fps}")
        writer?.println("error=${state.error}")
        writer?.println("captureGeneration=${state.captureGeneration} capturedFrames=${engine.capturedFrames} rtc=${state.rtc}")
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Executors.newSingleThreadExecutor().apply {
            execute { try { http.stop() } finally { shutdown() } }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "pl.remotecam.app.STOP"
        private const val CHANNEL = "REMOTE_CAM"
    }
}
