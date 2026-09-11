package com.samsung.android.scan3d.serv

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build

/** Translate supported, normalized controls to a Camera2 request shared by all outputs. */
object CameraControls {
    fun limits(c: CameraCharacteristics): CameraControlLimits {
        val modes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val distance = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val range = if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        val hasCrop = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) != null
        val maxZoom = range?.upper ?: if (hasCrop) c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f else 1f
        return CameraControlLimits(range?.lower ?: 1f, maxZoom.coerceAtLeast(1f), distance.coerceAtLeast(0f),
            distance > 0f && CaptureRequest.CONTROL_AF_MODE_OFF in modes &&
                CaptureRequest.LENS_FOCUS_DISTANCE in c.availableCaptureRequestKeys,
            distance > 0f && CaptureRequest.CONTROL_AF_MODE_AUTO in modes,
            c.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) in listOf(
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE,
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED))
    }

    fun autofocusMode(c: CameraCharacteristics, tuning: CameraTuning): Int {
        val modes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        return when (tuning.focusMode) {
            FocusMode.MANUAL -> CaptureRequest.CONTROL_AF_MODE_OFF
            FocusMode.LOCKED -> CaptureRequest.CONTROL_AF_MODE_AUTO
            FocusMode.AUTO -> listOf(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE, CaptureRequest.CONTROL_AF_MODE_AUTO,
                CaptureRequest.CONTROL_AF_MODE_OFF).firstOrNull { it in modes } ?: CaptureRequest.CONTROL_AF_MODE_OFF
        }
    }

    fun apply(request: CaptureRequest.Builder, c: CameraCharacteristics, tuning: CameraTuning) {
        request.set(CaptureRequest.CONTROL_AF_MODE, autofocusMode(c, tuning))
        request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        if (tuning.focusMode == FocusMode.MANUAL) request.set(CaptureRequest.LENS_FOCUS_DISTANCE, tuning.focusDistance)
        if (Build.VERSION.SDK_INT >= 30 && c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null) {
            request.set(CaptureRequest.CONTROL_ZOOM_RATIO, tuning.zoom)
        } else {
            c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.let { active ->
                val width = (active.width() / tuning.zoom).toInt().coerceIn(1, active.width())
                val height = (active.height() / tuning.zoom).toInt().coerceIn(1, active.height())
                val left = active.left + (active.width() - width) / 2
                val top = active.top + (active.height() - height) / 2
                request.set(CaptureRequest.SCALER_CROP_REGION, Rect(left, top, left + width, top + height))
            }
        }
    }
}
