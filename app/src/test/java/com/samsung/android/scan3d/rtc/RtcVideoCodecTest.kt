package com.samsung.android.scan3d.rtc

import com.samsung.android.scan3d.http.ReceiveOffer
import com.samsung.android.scan3d.http.RtcSignal
import com.samsung.android.scan3d.http.WhepSessions
import org.junit.Assert.*
import org.junit.Test

class RtcVideoCodecTest {
    private fun sdp(codec: String = "H265", direction: String = "recvonly", port: Int = 9) =
        "v=0\r\nm=video $port UDP/TLS/RTP/SAVPF 96 97\r\na=$direction\r\n" +
            "a=rtpmap:96 $codec/90000\r\na=rtpmap:97 rtx/90000\r\na=fmtp:97 apt=96\r\n"

    @Test fun acceptsHevcThroughBothSignalingPathsWithOptionalAudio() {
        for (audio in listOf("", "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=recvonly\r\na=rtpmap:111 opus/48000/2\r\n")) {
            val offer = sdp() + audio
            assertEquals(audio.isNotEmpty(), ReceiveOffer.validate(offer))
            assertEquals(offer, (RtcSignal.parse("offer\n$offer") as RtcSignal.Offer).sdp)
            WhepSessions.validateOffer(offer + "a=candidate:1 1 UDP 1 192.168.1.2 9000 typ host\r\n")
            assertTrue(RtcVideoCodec.H265.isInActiveVideo(offer))
            assertFalse(RtcVideoCodec.H264.isInActiveVideo(offer))
        }
    }

    @Test fun preservesAvcAndRequiresTheSelectedCodec() {
        assertTrue(RtcVideoCodec.H264.isInActiveVideo(sdp("H264")))
        assertFalse(RtcVideoCodec.H265.isInActiveVideo(sdp("H264")))
        assertTrue(RtcVideoCodec.H265.isInActiveVideo(sdp("h265")))
        val both = sdp().replace("96 97", "96 97 98") + "a=rtpmap:98 H264/90000\r\n"
        assertTrue(RtcVideoCodec.H264.isInActiveVideo(both))
        assertTrue(RtcVideoCodec.H265.isInActiveVideo(both))
    }

    @Test fun rejectsCodecMappingsOutsideTheActiveVideoPayloads() {
        val wrong = listOf(sdp(port = 0), sdp(direction = "inactive"),
            sdp().replace("SAVPF 96 97", "SAVPF 97"), sdp().replace("H265/90000", "H265/8000"),
            sdp("H264") + "m=audio 9 UDP/TLS/RTP/SAVPF 98\r\na=rtpmap:98 H265/90000\r\n",
            sdp("H264") + "m=video 0 UDP/TLS/RTP/SAVPF 96\r\na=rtpmap:96 H265/90000\r\n",
            "v=0\r\na=rtpmap:96 H265/90000\r\n", sdp().replace("H265/90000", "H265/90000junk"))
        wrong.forEach { assertFalse(it, RtcVideoCodec.H265.isInActiveVideo(it)) }
    }

    @Test fun answerMustActuallySendSelectedVideo() {
        for (codec in RtcVideoCodec.entries) {
            assertTrue(codec.isInActiveVideo(sdp(codec.sdpName, "sendonly"), sending = true))
            assertFalse(codec.isInActiveVideo(sdp(codec.sdpName, "recvonly"), sending = true))
            assertFalse(codec.isInActiveVideo(sdp(codec.sdpName, "inactive"), sending = true))
            assertFalse(codec.isInActiveVideo(sdp(codec.sdpName, "sendonly", 0), sending = true))
            val inherited = sdp(codec.sdpName).replace("v=0\r\n", "v=0\r\na=sendonly\r\n")
                .replace("a=recvonly\r\n", "")
            assertTrue(codec.isInActiveVideo(inherited, sending = true))
        }
    }
}
