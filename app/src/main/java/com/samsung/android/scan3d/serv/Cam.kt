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
    private val http = HttpService()
    lateinit var engine: CamEngine
        private set
    inner class LocalBinder : Binder() { val service get() = this@Cam }

    override fun onCreate() {
        super.onCreate()
        engine = CamEngine(this, http)
    }
    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.destroy()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            val channel = NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val open = PendingIntent.getActivity(this, 0, Intent(this, CameraActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val stop = PendingIntent.getService(this, 1, Intent(this, Cam::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_linked_camera)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open).setOngoing(true)
                .addAction(R.drawable.ic_close, getString(R.string.stop), stop).build()
            val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            ServiceCompat.startForeground(this, 123, notification, type)
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
        engine.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Executors.newSingleThreadExecutor().apply {
            execute { try { http.stop() } finally { shutdown() } }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.samsung.android.scan3d.STOP"
        private const val CHANNEL = "REMOTE_CAM"
    }
}
