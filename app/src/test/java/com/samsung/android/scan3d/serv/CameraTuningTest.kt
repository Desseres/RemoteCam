package com.samsung.android.scan3d.serv

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraTuningTest {
    @Test fun switchingToFixedFocusCameraDropsUnsupportedFocusAndClampsZoom() {
        val fixed = CameraControlLimits(maxZoom = 3f)
        assertEquals(CameraTuning(FocusMode.AUTO, 0f, 3f),
            fixed.normalize(CameraTuning(FocusMode.MANUAL, 9f, 10f)))
        assertEquals(listOf(FocusMode.AUTO), fixed.focusModes)
    }

    @Test fun variableLensPreservesManualDistanceButEnforcesItsPhysicalRange() {
        val lens = CameraControlLimits(0.5f, 10f, 9.09f, true, true, true)
        assertEquals(CameraTuning(FocusMode.MANUAL, 9.09f, 0.5f),
            lens.normalize(CameraTuning(FocusMode.MANUAL, 40f, 0.1f)))
        assertEquals(CameraTuning(FocusMode.LOCKED, 0f, 1f),
            lens.normalize(CameraTuning(FocusMode.LOCKED, Float.NaN, Float.NaN)))
        assertEquals(0f, lens.normalize(CameraTuning(FocusMode.MANUAL, -1f)).focusDistance, 0f)
    }

    @Test fun focusLockCanBeAvailableWithoutManualLensControl() {
        val lens = CameraControlLimits(maxFocusDistance = 5f, focusLock = true)
        assertEquals(listOf(FocusMode.AUTO, FocusMode.LOCKED), lens.focusModes)
        assertEquals(FocusMode.LOCKED, lens.normalize(CameraTuning(FocusMode.LOCKED)).focusMode)
        assertEquals(FocusMode.AUTO, lens.normalize(CameraTuning(FocusMode.MANUAL)).focusMode)
    }
}
