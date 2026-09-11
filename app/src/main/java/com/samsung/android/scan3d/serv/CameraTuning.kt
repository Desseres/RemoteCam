package com.samsung.android.scan3d.serv

enum class FocusMode { AUTO, LOCKED, MANUAL }

data class CameraTuning(val focusMode: FocusMode = FocusMode.AUTO,
                        val focusDistance: Float = 0f, val zoom: Float = 1f)

data class CameraControlLimits(
    val minZoom: Float = 1f, val maxZoom: Float = 1f,
    val maxFocusDistance: Float = 0f, val manualFocus: Boolean = false,
    val focusLock: Boolean = false, val distanceCalibrated: Boolean = false
) {
    val focusModes: List<FocusMode> get() = buildList {
        add(FocusMode.AUTO)
        if (focusLock) add(FocusMode.LOCKED)
        if (manualFocus) add(FocusMode.MANUAL)
    }

    fun normalize(value: CameraTuning) = CameraTuning(
        value.focusMode.takeIf { it in focusModes } ?: FocusMode.AUTO,
        if (manualFocus && value.focusDistance.isFinite()) value.focusDistance.coerceIn(0f, maxFocusDistance) else 0f,
        (value.zoom.takeIf { it.isFinite() } ?: 1f).coerceIn(minZoom, maxZoom))
}
