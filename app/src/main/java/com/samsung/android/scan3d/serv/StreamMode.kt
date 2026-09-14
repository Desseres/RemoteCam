package com.samsung.android.scan3d.serv

import com.samsung.android.scan3d.rtc.RtcVideoCodec

// Keep the existing names/order: saved WEBRTC preferences continue to mean H.264.
enum class StreamMode(val rtcCodec: RtcVideoCodec? = null) {
    JPEG,
    WEBRTC(RtcVideoCodec.H264),
    WEBRTC_H265(RtcVideoCodec.H265);

    val isWebRtc: Boolean get() = rtcCodec != null

    companion object {
        fun fromPreference(value: String?): StreamMode = entries.firstOrNull { it.name == value } ?: JPEG
    }
}
