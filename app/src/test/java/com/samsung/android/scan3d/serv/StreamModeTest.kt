package com.samsung.android.scan3d.serv

import com.samsung.android.scan3d.rtc.RtcVideoCodec
import org.junit.Assert.*
import org.junit.Test

class StreamModeTest {
    @Test fun existingPreferencesNeverOptIntoHevc() {
        assertEquals(StreamMode.JPEG, StreamMode.fromPreference(null))
        assertEquals(StreamMode.JPEG, StreamMode.fromPreference("unknown"))
        assertEquals(StreamMode.JPEG, StreamMode.fromPreference("JPEG"))
        assertEquals(RtcVideoCodec.H264, StreamMode.fromPreference("WEBRTC").rtcCodec)
    }

    @Test fun onlyExplicitHevcPreferenceRestoresExperimentalMode() {
        val restored = StreamMode.fromPreference(StreamMode.WEBRTC_H265.name)
        assertEquals(RtcVideoCodec.H265, restored.rtcCodec)
        assertTrue(restored.isWebRtc)
        assertTrue(StreamMode.WEBRTC.isWebRtc)
        assertFalse(StreamMode.JPEG.isWebRtc)
        assertNull(StreamMode.JPEG.rtcCodec)
    }
}
