package com.samsung.android.scan3d.rtc

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFrameOrientationTest {
    @Test fun manualRotationIsRelativeToPortraitAndIndependentOfDisplayAndLensFacing() {
        for (sensor in listOf(0, 90, 180, 270)) {
            for (manual in listOf(0, 90, 180, 270)) {
                for (display in listOf(0, 90, 180, 270)) {
                    for (front in listOf(false, true)) {
                        assertEquals((sensor + manual) % 360,
                            CameraFrameOrientation.rotation(sensor, display, front, manual))
                    }
                }
            }
        }
    }

    @Test fun invalidManualSelectionFallsBackToAutomaticOrientation() {
        for (invalid in listOf(-1, -90, 45, 360)) {
            assertEquals(0, CameraFrameOrientation.rotation(90, 90, false, invalid))
            assertEquals(180, CameraFrameOrientation.rotation(90, 90, true, invalid))
        }
    }

    @Test fun rearSensorStaysUprightAcrossDisplayRotations() {
        listOf(90, 0, 270, 180).forEachIndexed { index, expected ->
            assertEquals(expected, CameraFrameOrientation.rotation(90, index * 90, false))
        }
    }

    @Test fun frontSensorUsesOppositeRotationAndHandlesBothSensorMountings() {
        listOf(270, 0, 90, 180).forEachIndexed { index, expected ->
            assertEquals(expected, CameraFrameOrientation.rotation(270, index * 90, true))
        }
        assertEquals(180, CameraFrameOrientation.rotation(90, 90, true))
        assertEquals(0, CameraFrameOrientation.rotation(0, 0, false))
    }
}
