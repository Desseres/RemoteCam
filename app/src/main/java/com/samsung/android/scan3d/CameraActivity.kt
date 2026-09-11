package com.samsung.android.scan3d

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.hardware.camera2.CaptureResult
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.SeekBar
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
import com.samsung.android.scan3d.serv.StreamMode
import com.samsung.android.scan3d.serv.FocusMode
import com.samsung.android.scan3d.serv.CameraControlLimits
import com.samsung.android.scan3d.http.TransferStats
import com.samsung.android.scan3d.util.BandwidthDialog
import com.samsung.android.scan3d.util.AboutDialog
import com.samsung.android.scan3d.util.ClipboardUtil
import com.samsung.android.scan3d.util.IpUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: FragmentCameraBinding
    private var service: Cam? = null
    private var bound = false
    private var collection: Job? = null
    private var rendering = false
    private var previewInitialized = false
    private var lastError: String? = null
    private val qualities = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
    private val bitrates = listOf(2, 4, 6, 8, 12, 16, 24, 32, 40)
    private val rotations = listOf(-1, 0, 90, 180, 270)
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
        override fun onServiceDisconnected(name: ComponentName) {
            releasePreview()
            service = null; collection?.cancel()
        }
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
        listOf(binding.textView6, binding.mjpegAddress, binding.rtcAddress, binding.go2rtcAddress).forEach { address ->
            address.setOnClickListener {
                if (address.text.toString().startsWith("http") || address.text.toString().startsWith("webrtc:http")) {
                    ClipboardUtil.copyToClipboard(this, "RemoteCam", address.text.toString())
                    Toast.makeText(this, R.string.address_copied, Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.buttonKill.setOnClickListener { stopService(Intent(this, Cam::class.java)); finish() }
        binding.buttonBandwidth.setOnClickListener {
            service?.engine?.status?.value?.let { BandwidthDialog.show(this, it) }
        }
        binding.buttonInfo.setOnClickListener { AboutDialog.show(this) }
        binding.buttonRefocus.setOnClickListener { service?.engine?.refocus() }
        binding.buttonResetZoom.setOnClickListener { service?.engine?.let {
            it.configure(it.status.value.config.copy(tuning = it.status.value.config.tuning.copy(zoom = 1f)))
        } }
        binding.switch1.setOnCheckedChangeListener { _, checked ->
            if (!rendering) service?.engine?.let { it.configure(it.status.value.config.copy(preview = checked)) }
        }
        binding.switch2.setOnCheckedChangeListener { _, checked ->
            if (!rendering) service?.engine?.let { it.configure(it.status.value.config.copy(stream = checked)) }
        }
        binding.viewFinder.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
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
        binding.mjpegAddress.text = IpUtil.getLocalIpAddress()?.let { "http://$it:8080/cam.mjpeg" } ?: getString(R.string.connect_wifi)
        binding.rtcAddress.text = IpUtil.getLocalIpAddress()?.let { "http://$it:8080/webrtc" } ?: getString(R.string.connect_wifi)
        binding.go2rtcAddress.text = IpUtil.getLocalIpAddress()?.let { "webrtc:http://$it:8080/whep" } ?: getString(R.string.connect_wifi)
        if (hasRequiredPermissions()) connect()
    }
    override fun onResume() { super.onResume(); if (hasRequiredPermissions()) connect(); updateSurface() }
    override fun onStop() {
        releasePreview()
        collection?.cancel()
        if (bound) { unbindService(connection); bound = false }
        service = null
        super.onStop()
    }
    private fun updateSurface() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            val engine = service?.engine ?: return
            if (!previewInitialized) {
                binding.viewFinder.init(engine.previewContext, null)
                previewInitialized = true
            }
            engine.setPreview(binding.viewFinder)
        }
    }
    private fun releasePreview() {
        service?.engine?.setPreview(null)
        if (previewInitialized) {
            binding.viewFinder.release()
            previewInitialized = false
        }
    }
    private fun render(status: CameraStatus) {
        if (status.stopped) { finish(); return }
        rendering = true
        binding.switch1.isChecked = status.config.preview
        binding.switch2.isChecked = status.config.stream
        renderCameraControls(status)
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
        val webRtc = status.config.mode == StreamMode.WEBRTC
        binding.jpegQualityRow.visibility = if (webRtc) View.GONE else View.VISIBLE
        binding.rtcBitrateRow.visibility = if (webRtc) View.VISIBLE else View.GONE
        binding.rtcRotationHint.setText(if (webRtc) R.string.rotation_hint else R.string.rotation_jpeg_hint)
        binding.rateHint.setText(if (webRtc) R.string.rtc_rate_hint else R.string.generated_rate_hint)
        if (webRtc) {
            val state = status.rtc
            val rtcColor = when {
                state.clients == 0 || state.connected == 0 || state.fps <= 0 -> R.color.transfer_unknown
                state.limitation.isNotEmpty() || state.fps < status.fps * 0.8 -> R.color.transfer_busy
                else -> R.color.transfer_good
            }
            binding.qualFeedback.text = getString(R.string.rate, state.mbps)
            binding.qualFeedback.setTextColor(ContextCompat.getColor(this, rtcColor))
            binding.transferFeedback.text = getString(R.string.rtc_summary, state.connected, state.clients, state.fps,
                state.rttMs?.let { getString(R.string.rtc_rtt, it) } ?: getString(R.string.rtc_connecting),
                if (state.limitation.isEmpty()) getString(R.string.rtc_stable) else getString(R.string.rtc_adapting, state.limitation))
            binding.transferFeedback.setTextColor(ContextCompat.getColor(this, rtcColor))
        }
        setOptions(binding.spinnerMode, listOf(getString(R.string.mode_jpeg), getString(R.string.mode_rtc)), status.config.mode.ordinal) { index ->
            service?.engine?.let { it.configure(it.status.value.config.copy(mode = StreamMode.entries[index])) }
        }
        setOptions(binding.spinnerBitrate, bitrates.map { getString(R.string.bitrate_option, it) }, bitrates.indexOf(status.config.bitrateMbps)) { index ->
            service?.engine?.let { it.configure(it.status.value.config.copy(bitrateMbps = bitrates[index])) }
        }
        setOptions(binding.spinnerRotation, rotations.map {
            if (it == -1) getString(R.string.rotation_auto) else getString(R.string.rotation_option, it)
        }, rotations.indexOf(status.config.rotationDegrees)) { index ->
            service?.engine?.let { it.configure(it.status.value.config.copy(rotationDegrees = rotations[index])) }
        }
        binding.previewContainer.visibility = if (status.config.preview) View.VISIBLE else View.GONE
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

    private fun renderCameraControls(status: CameraStatus) {
        val limits = status.controls
        val tuning = status.config.tuning
        val modes = limits.focusModes
        binding.focusRow.visibility = if (modes.size > 1) View.VISIBLE else View.GONE
        setOptions(binding.spinnerFocus, modes.map { getString(when (it) {
            FocusMode.AUTO -> R.string.focus_auto
            FocusMode.LOCKED -> R.string.focus_locked
            FocusMode.MANUAL -> R.string.focus_manual
        }) }, modes.indexOf(tuning.focusMode)) { index ->
            service?.engine?.let { engine ->
                val current = engine.status.value
                val mode = modes[index]
                // Entering manual focus starts at the observed lens position, so it
                // can also freeze existing focus without a new autofocus sweep.
                val distance = if (mode == FocusMode.MANUAL) current.focusDistance ?: current.config.tuning.focusDistance
                    else current.config.tuning.focusDistance
                engine.configure(current.config.copy(tuning = current.config.tuning.copy(focusMode = mode, focusDistance = distance)))
            }
        }
        binding.buttonRefocus.visibility = if (tuning.focusMode != FocusMode.MANUAL && limits.focusLock) View.VISIBLE else View.GONE
        binding.manualFocusPanel.visibility = if (tuning.focusMode == FocusMode.MANUAL) View.VISIBLE else View.GONE
        fun focusValue(progress: Int) = limits.maxFocusDistance * progress / 1000f
        val focusProgress = if (limits.maxFocusDistance > 0) (tuning.focusDistance / limits.maxFocusDistance * 1000).toInt() else 0
        bindSlider(binding.seekFocus, focusProgress, { progress ->
            binding.manualFocusValue.text = focusDistanceLabel(focusValue(progress), limits)
        }) { progress -> service?.engine?.let {
            it.configure(it.status.value.config.copy(tuning = it.status.value.config.tuning.copy(focusDistance = focusValue(progress))))
        } }
        val focusMessage = when {
            limits.maxFocusDistance == 0f -> R.string.focus_fixed
            tuning.focusMode == FocusMode.MANUAL -> R.string.focus_manual_hint
            status.focusState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> R.string.focus_lock_failed
            status.focusState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> R.string.focus_lock_ok
            tuning.focusMode == FocusMode.LOCKED -> R.string.focus_lock_scanning
            else -> R.string.focus_auto_hint
        }
        binding.focusStatus.text = getString(focusMessage)
        val span = limits.maxZoom - limits.minZoom
        binding.seekZoom.isEnabled = span > 0f
        binding.buttonResetZoom.isEnabled = span > 0f
        fun zoomValue(progress: Int) = limits.minZoom + span * progress / 1000f
        bindSlider(binding.seekZoom, if (span > 0) ((tuning.zoom - limits.minZoom) / span * 1000).toInt() else 0, { progress ->
            binding.zoomValue.text = getString(R.string.zoom_value, zoomValue(progress), limits.minZoom, limits.maxZoom)
        }) { progress -> service?.engine?.let {
            it.configure(it.status.value.config.copy(tuning = it.status.value.config.tuning.copy(zoom = zoomValue(progress))))
        } }
        if (!binding.seekZoom.isPressed)
            binding.zoomValue.text = getString(R.string.zoom_value, tuning.zoom, limits.minZoom, limits.maxZoom)
    }

    private fun focusDistanceLabel(distance: Float, limits: CameraControlLimits): String = when {
        distance <= 0f -> getString(R.string.focus_infinity)
        limits.distanceCalibrated -> getString(R.string.focus_centimeters, 100f / distance)
        else -> getString(R.string.focus_percent, (distance / limits.maxFocusDistance * 100).toInt())
    }

    private fun bindSlider(slider: SeekBar, progress: Int, label: (Int) -> Unit, apply: (Int) -> Unit) {
        if (!slider.isPressed) { slider.progress = progress.coerceIn(0, 1000); label(slider.progress) }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                label(value)
                val now = SystemClock.uptimeMillis()
                val lastUpdate = bar.tag as? Long ?: 0L
                if (now - lastUpdate >= 100) { bar.tag = now; apply(value) }
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) { apply(bar.progress) }
        })
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
