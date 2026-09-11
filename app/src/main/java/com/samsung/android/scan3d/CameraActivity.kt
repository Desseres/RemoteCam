package com.samsung.android.scan3d

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Size
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.samsung.android.scan3d.databinding.FragmentCameraBinding
import com.samsung.android.scan3d.serv.Cam
import com.samsung.android.scan3d.serv.CameraStatus
import com.samsung.android.scan3d.http.TransferStats
import com.samsung.android.scan3d.util.BandwidthDialog
import com.samsung.android.scan3d.util.ClipboardUtil
import com.samsung.android.scan3d.util.IpUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: FragmentCameraBinding
    private var service: Cam? = null
    private var bound = false
    private var collection: Job? = null
    private var rendering = false
    private var lastError: String? = null
    private val qualities = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasRequiredPermissions()) connect()
        else AlertDialog.Builder(this).setTitle(R.string.permissions_title)
            .setMessage(R.string.permissions_message)
            .setPositiveButton(R.string.retry) { _, _ -> requestAccess() }
            .setNegativeButton(R.string.close) { _, _ -> finish() }.show()
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as Cam.LocalBinder).service
            collection?.cancel()
            collection = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    service?.engine?.status?.collect { render(it) }
                }
            }
            updateSurface()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; collection?.cancel() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = FragmentCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.textView6.setOnClickListener {
            ClipboardUtil.copyToClipboard(this, "RemoteCam", binding.textView6.text.toString())
            Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show()
        }
        binding.buttonKill.setOnClickListener { stopService(Intent(this, Cam::class.java)); finish() }
        binding.buttonBandwidth.setOnClickListener {
            service?.engine?.status?.value?.let { BandwidthDialog.show(this, it) }
        }
        binding.switch1.setOnCheckedChangeListener { _, checked ->
            if (!rendering) service?.engine?.let { it.configure(it.status.value.config.copy(preview = checked)) }
        }
        binding.switch2.setOnCheckedChangeListener { _, checked ->
            if (!rendering) service?.engine?.let { it.configure(it.status.value.config.copy(stream = checked)) }
        }
        binding.viewFinder.setAspectRatio(1280, 720)
        binding.viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { updateSurface() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { updateSurface() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { service?.engine?.setPreview(null) }
        })
        if (!hasRequiredPermissions()) requestAccess()
    }

    private fun requiredPermissions() = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 37) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
    private fun hasRequiredPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestAccess() {
        permissions.launch((requiredPermissions() + if (Build.VERSION.SDK_INT >= 33)
            listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()).toTypedArray())
    }
    private fun connect() {
        if (bound || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        ContextCompat.startForegroundService(this, Intent(this, Cam::class.java))
        bound = bindService(Intent(this, Cam::class.java), connection, Context.BIND_AUTO_CREATE)
    }
    override fun onStart() {
        super.onStart()
        binding.textView6.text = IpUtil.getLocalIpAddress()?.let { "http://$it:8080/view" }
            ?: getString(R.string.connect_wifi)
        if (hasRequiredPermissions()) connect()
    }
    override fun onResume() { super.onResume(); if (hasRequiredPermissions()) connect(); updateSurface() }
    override fun onStop() {
        service?.engine?.setPreview(null)
        collection?.cancel()
        if (bound) { unbindService(connection); bound = false }
        service = null
        super.onStop()
    }
    private fun updateSurface() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            service?.engine?.setPreview(binding.viewFinder.holder.surface.takeIf { it.isValid })
        }
    }
    private fun render(status: CameraStatus) {
        if (status.stopped) { finish(); return }
        rendering = true
        binding.switch1.isChecked = status.config.preview
        binding.switch2.isChecked = status.config.stream
        binding.ftFeedback.text = getString(R.string.fps, status.fps)
        binding.qualFeedback.text = getString(R.string.rate, status.sourceMbps)
        val transfer = status.transfer
        val health = transfer.health(status.fps)
        val color = when (health) {
            TransferStats.Health.UNKNOWN -> R.color.transfer_unknown
            TransferStats.Health.GOOD -> R.color.transfer_good
            TransferStats.Health.BUSY -> R.color.transfer_busy
            TransferStats.Health.SLOW -> R.color.transfer_slow
        }
        binding.qualFeedback.setTextColor(ContextCompat.getColor(this, color))
        val label = when {
            health == TransferStats.Health.SLOW -> R.string.transfer_slow
            transfer.clients == 0 -> R.string.transfer_no_viewers
            transfer.confirmedClients == 0 -> R.string.transfer_legacy
            health == TransferStats.Health.UNKNOWN -> R.string.transfer_warming
            health == TransferStats.Health.BUSY -> R.string.transfer_busy
            else -> R.string.transfer_good
        }
        binding.transferFeedback.text = getString(R.string.transfer_summary, getString(label),
            transfer.outputMbps, transfer.clients, transfer.confirmedClients,
            transfer.ackMs?.let { getString(R.string.transfer_ack, it, transfer.skippedPercent) }.orEmpty())
        binding.transferFeedback.setTextColor(ContextCompat.getColor(this, color))
        binding.viewFinder.visibility = if (status.config.preview) View.VISIBLE else View.INVISIBLE
        if (status.config.preview || status.config.stream) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setOptions(binding.spinnerCam, status.sensors.map { it.title },
            status.sensors.indexOfFirst { it.cameraId == status.config.cameraId }) { index ->
            service?.engine?.let { engine ->
                val camera = engine.status.value.sensors.getOrNull(index) ?: return@let
                engine.configure(engine.status.value.config.copy(cameraId = camera.cameraId, resolution = null))
            }
        }
        setOptions(binding.spinnerRes, status.sizes.map(::resolutionLabel),
            status.sizes.indexOf(status.config.resolution)) { index ->
            service?.engine?.let { engine ->
                val size = engine.status.value.sizes.getOrNull(index) ?: return@let
                engine.configure(engine.status.value.config.copy(resolution = size))
            }
        }
        setOptions(binding.spinnerQua, qualities.map(Int::toString), qualities.indexOf(status.config.quality)) { index ->
            service?.engine?.let { it.configure(it.status.value.config.copy(quality = qualities[index])) }
        }
        rendering = false
        if (status.error != null && status.error != lastError)
            Toast.makeText(this, status.error, Toast.LENGTH_LONG).show()
        lastError = status.error
    }

    private fun resolutionLabel(size: Size): String {
        var divisor = size.width
        var remainder = size.height
        while (remainder != 0) {
            val next = divisor % remainder
            divisor = remainder
            remainder = next
        }
        return getString(R.string.resolution_format, size.width, size.height,
            size.width / divisor, size.height / divisor)
    }

    private fun setOptions(spinner: Spinner, options: List<String>, selection: Int, selected: (Int) -> Unit) {
        val previous = spinner.tag as? List<*>
        if (previous != options) {
            spinner.onItemSelectedListener = null
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.tag = options
        }
        if (selection >= 0 && spinner.selectedItemPosition != selection) spinner.setSelection(selection)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!rendering && position != selection && position in options.indices) selected(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }
}
