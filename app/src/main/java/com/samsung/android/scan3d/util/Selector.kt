package com.samsung.android.scan3d.util

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

object Selector {
    data class SensorDesc(val title: String, val cameraId: String)

    // Only IDs advertised as independently openable by the device. Logical cameras are valid.
    fun enumerateCameras(manager: CameraManager): List<SensorDesc> =
        manager.cameraIdList.mapNotNull { id ->
            val c = manager.getCameraCharacteristics(id)
            val sizes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
            if (sizes.isNullOrEmpty()) return@mapNotNull null
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                else -> "External"
            }
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull()?.let { " ${it}mm" }.orEmpty()
            SensorDesc("$facing$focal (ID $id)", id)
        }
}
