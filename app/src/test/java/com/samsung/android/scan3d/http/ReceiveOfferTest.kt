package com.samsung.android.scan3d.http

import org.junit.Assert.*
import org.junit.Test

class ReceiveOfferTest {
    private val video = "m=video 9 UDP/TLS/RTP/SAVPF 96\r\na=recvonly\r\na=rtpmap:96 H264/90000\r\n"
    private val audio = "m=audio 9 UDP/TLS/RTP/SAVPF 111\r\na=recvonly\r\na=rtpmap:111 opus/48000/2\r\n"
    private fun offer(media: String) = "v=0\r\n$media"

    @Test fun acceptsAudioInEitherOrderAndLegacyVideoOnly() {
        assertFalse(ReceiveOffer.validate(offer(video)))
        for (media in listOf(video + audio, audio + video)) {
            assertTrue(ReceiveOffer.validate(offer(media)))
            assertEquals(offer(media), (RtcSignal.parse("offer\n" + offer(media)) as RtcSignal.Offer).sdp)
            WhepSessions.validateOffer(offer(media) + "a=candidate:1 1 UDP 1 192.168.1.2 9000 typ host\r\n")
        }
    }
    @Test fun respectsInactiveRejectedAndInheritedAudioDirections() {
        assertFalse(ReceiveOffer.validate(offer(video + audio.replace("recvonly", "inactive"))))
        assertFalse(ReceiveOffer.validate(offer(video + audio.replace("m=audio 9", "m=audio 0"))))
        assertTrue(ReceiveOffer.validate(offer("a=recvonly\r\n" + (audio + video).replace("a=recvonly\r\n", ""))))
    }
    @Test fun rejectsSendingAudioExtraTracksAndConflictingDirections() {
        for (media in listOf(video + audio.replace("recvonly", "sendrecv"),
            video + audio.replace("recvonly", "sendonly"), video + audio.replace("a=recvonly\r\n", ""),
            video + audio + audio, audio, video + video, video + "m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\na=recvonly\r\n",
            video + audio + "a=sendonly\r\n", video.replace("m=video 9", "m=video 0"))) {
            assertThrows(IllegalArgumentException::class.java) { ReceiveOffer.validate(offer(media)) }
        }
    }
}
