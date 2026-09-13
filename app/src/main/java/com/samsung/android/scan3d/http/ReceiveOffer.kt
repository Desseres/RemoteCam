package com.samsung.android.scan3d.http

/** RemoteCam is a sender: never accept a peer's microphone or camera. */
object ReceiveOffer {
    private val directions = setOf("a=sendrecv", "a=sendonly", "a=recvonly", "a=inactive")

    fun validate(sdp: String): Boolean {
        require(sdp.length <= 131072 && sdp.startsWith("v=0\r\n")) { "Invalid SDP offer" }
        val sections = sdp.split("\r\nm=")
        fun direction(section: String): String? {
            val values = section.lineSequence().filter { it in directions }.toList()
            require(values.size <= 1) { "Conflicting SDP directions" }
            return values.singleOrNull()
        }
        val sessionDirection = direction(sections.first())
        val media = sections.drop(1)
        require(media.count { it.startsWith("video ") } == 1 &&
            media.count { it.startsWith("audio ") } <= 1 &&
            media.all { it.startsWith("video ") || it.startsWith("audio ") }) {
            "Offer must contain one video track and at most one optional audio track"
        }
        var audio = false
        media.forEach { section ->
            val effective = direction(section) ?: sessionDirection
            val isAudio = section.startsWith("audio ")
            require(effective == "a=recvonly" || (isAudio && effective == "a=inactive")) {
                "Only receive-only clients are supported"
            }
            val port = section.substringBefore('\r').split(' ').getOrNull(1)?.toIntOrNull()
            require(port != null && port in 0..65535) { "Invalid media port" }
            if (!isAudio) require(port != 0) { "An active video track is required" }
            if (isAudio && port != 0 && effective == "a=recvonly") audio = true
        }
        return audio
    }
}
