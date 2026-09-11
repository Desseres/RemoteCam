package com.samsung.android.scan3d

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import com.samsung.android.scan3d.serv.Cam
import com.samsung.android.scan3d.serv.StreamMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in physical-camera regression: -e previewContinuity true. Restores configuration. */
object PreviewContinuityCheck {
    fun run(test: Instrumentation) {
        val context = test.targetContext
        val ready = CountDownLatch(1)
        var service: Cam? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as Cam.LocalBinder).service; ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
        }
        val launch = Intent(context, CameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = test.startActivitySync(launch)
        check(context.bindService(Intent(context, Cam::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            check(ready.await(10, TimeUnit.SECONDS)) { "Camera service did not bind" }
            val engine = checkNotNull(service).engine
            fun awaitCondition(message: String, predicate: () -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 15_000
                while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                check(predicate()) { "$message: ${engine.status.value}" }
            }
            awaitCondition("Initial camera did not start") { engine.status.value.fps > 0 }
            val original = engine.status.value.config
            try {
                for (mode in listOf(StreamMode.WEBRTC, StreamMode.JPEG)) {
                    val before = engine.capturedFrames
                    engine.configure(original.copy(mode = mode, stream = true, preview = true))
                    awaitCondition("Mode did not start") {
                        engine.status.value.config.mode == mode && engine.status.value.config.preview &&
                            engine.capturedFrames > before + 10 && engine.status.value.fps > 0
                    }
                    val generation = engine.status.value.captureGeneration
                    fun checkContinuity(action: () -> Unit) {
                        val frames = engine.capturedFrames
                        action()
                        SystemClock.sleep(1800)
                        check(engine.status.value.error == null) { "Camera error: ${engine.status.value.error}" }
                        check(engine.status.value.captureGeneration == generation) { "$mode: camera restarted" }
                        check(engine.capturedFrames > frames + 5) { "$mode: capture stalled" }
                    }
                    repeat(6) { index ->
                        checkContinuity {
                            test.runOnMainSync {
                                activity.findViewById<SwitchCompat>(R.id.switch1).performClick()
                            }
                        }
                        test.runOnMainSync {
                            val container = activity.findViewById<View>(R.id.preview_container)
                            val visible = index % 2 == 1
                            check(container.visibility == if (visible) View.VISIBLE else View.GONE)
                            if (!visible) {
                                val settings = activity.findViewById<View>(R.id.switch1).parent.parent.parent as View
                                check(settings.height > activity.window.decorView.height * 0.8) { "Settings did not expand" }
                            }
                        }
                    }
                    checkContinuity { test.runOnMainSync { activity.moveTaskToBack(true) } }
                    checkContinuity { test.runOnMainSync { activity.startActivity(launch) } }
                }
            } finally {
                val frames = engine.capturedFrames
                engine.configure(original)
                awaitCondition("Configuration was not restored") {
                    engine.status.value.config == original && engine.capturedFrames > frames + 5 && engine.status.value.fps > 0
                }
            }
        } finally { context.unbindService(connection) }
    }
}
