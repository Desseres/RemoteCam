package com.samsung.android.scan3d.rtc

/** Camera2 sensor-to-display rotation; front and rear sensors turn in opposite directions. */
object CameraFrameOrientation {
    fun rotation(sensorDegrees: Int, displayDegrees: Int, frontFacing: Boolean,
                 manualDegrees: Int = -1): Int =
        if (manualDegrees in 0..270 && manualDegrees % 90 == 0) (sensorDegrees + manualDegrees) % 360
        else (sensorDegrees + if (frontFacing) displayDegrees else 360 - displayDegrees) % 360
}
