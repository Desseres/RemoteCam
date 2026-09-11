package com.samsung.android.scan3d.rtc

import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoFrame

/** Relays' MP4/MSE consumers may join later without sending WebRTC PLI feedback. */
class PeriodicKeyFrameEncoder(private val encoder: VideoEncoder) : VideoEncoder by encoder {
    private var lastKeyFrameNs: Long? = null

    override fun initEncode(settings: VideoEncoder.Settings, callback: VideoEncoder.Callback): VideoCodecStatus {
        lastKeyFrameNs = null
        return encoder.initEncode(settings, callback)
    }

    override fun encode(frame: VideoFrame, info: VideoEncoder.EncodeInfo): VideoCodecStatus {
        val last = lastKeyFrameNs
        val requested = info.frameTypes.any { it == EncodedImage.FrameType.VideoFrameKey }
        val due = last == null || frame.timestampNs < last || frame.timestampNs - last >= 2_000_000_000L
        val effective = if (due && !requested) VideoEncoder.EncodeInfo(arrayOf(EncodedImage.FrameType.VideoFrameKey)) else info
        val result = encoder.encode(frame, effective)
        if ((due || requested) && result == VideoCodecStatus.OK) lastKeyFrameNs = frame.timestampNs
        return result
    }
}
