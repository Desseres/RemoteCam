package com.samsung.android.scan3d.rtc

import org.junit.Assert.*
import org.junit.Test
import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoEncoder
import org.webrtc.VideoFrame
import java.lang.reflect.Proxy

class PeriodicKeyFrameEncoderTest {
    @Test fun lateJoiningReceiversGetKeyFramesAndFailedRequestsAreRetried() {
        val key = EncodedImage.FrameType.VideoFrameKey
        val delta = EncodedImage.FrameType.VideoFrameDelta
        val seen = mutableListOf<EncodedImage.FrameType>()
        var result = VideoCodecStatus.OK
        val delegate = Proxy.newProxyInstance(VideoEncoder::class.java.classLoader, arrayOf(VideoEncoder::class.java)) { _, method, args ->
            if (method.name == "encode") seen.add((args!![1] as VideoEncoder.EncodeInfo).frameTypes.single())
            result
        } as VideoEncoder
        val buffer = Proxy.newProxyInstance(VideoFrame.Buffer::class.java.classLoader, arrayOf(VideoFrame.Buffer::class.java)) { _, _, _ -> null } as VideoFrame.Buffer
        val encoder = PeriodicKeyFrameEncoder(delegate)
        fun encode(time: Long, type: EncodedImage.FrameType = delta) {
            encoder.encode(VideoFrame(buffer, 0, time), VideoEncoder.EncodeInfo(arrayOf(type)))
        }
        encode(0)
        encode(1_999_999_999)
        encode(2_000_000_000)
        encode(2_500_000_000, key) // Preserve receiver PLI and restart the interval.
        encode(4_000_000_000)
        result = VideoCodecStatus.ERROR
        encode(4_500_000_000)
        result = VideoCodecStatus.OK
        encode(4_600_000_000)
        encode(100) // Capture timestamp resets after a source restart.
        assertEquals(listOf(key, delta, key, key, delta, key, key, key), seen)
    }
}
