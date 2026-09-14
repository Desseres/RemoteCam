package com.samsung.android.scan3d.rtc

/** A selected codec is strict: an HEVC test must never silently send AVC instead. */
enum class RtcVideoCodec(val sdpName: String, val mimeType: String, val label: String) {
    H264("H264", "video/avc", "H.264"),
    H265("H265", "video/hevc", "H.265");

    /** Check the active video payload list, not an unrelated rtpmap or rejected m-line. */
    fun isInActiveVideo(sdp: String, sending: Boolean = false): Boolean {
        val sections = sdp.split("\r\nm=")
        val video = sections.drop(1).singleOrNull { it.startsWith("video ") } ?: return false
        val header = video.substringBefore('\r').split(' ')
        if (header.getOrNull(1)?.toIntOrNull()?.let { it in 1..65535 } != true) return false
        val directions = setOf("a=sendonly", "a=recvonly", "a=sendrecv", "a=inactive")
        val direction = video.lineSequence().firstOrNull { it in directions }
            ?: sections.first().lineSequence().firstOrNull { it in directions }
        if (direction == "a=inactive" || (sending && direction != "a=sendonly")) return false
        val payloads = header.drop(3).toSet()
        val mapping = Regex("^a=rtpmap:(\\d+) ${sdpName}/90000$", RegexOption.IGNORE_CASE)
        return video.lineSequence().any { line ->
            mapping.matchEntire(line)?.groupValues?.get(1) in payloads
        }
    }
}
