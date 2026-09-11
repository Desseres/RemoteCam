package com.samsung.android.scan3d.http

/** Small, bounded LAN signaling protocol. SDP and ICE travel over the existing HTTP server. */
sealed interface RtcSignal {
    data class Offer(val sdp: String) : RtcSignal
    data class Ice(val mid: String, val index: Int, val candidate: String) : RtcSignal
    companion object {
        fun parse(text: String): RtcSignal {
            require(text.length <= 131072) { "Signaling message too large" }
            if (text.startsWith("offer\n")) {
                val sdp = text.substringAfter('\n')
                require(sdp.startsWith("v=0") && sdp.contains("m=video ")) { "A video offer is required" }
                val media = sdp.lineSequence().filter { it.startsWith("m=") }.toList()
                require(media.size == 1 && media[0].startsWith("m=video ") && sdp.lineSequence().any { it.trim() == "a=recvonly" }) { "Only a single receive-only video track is supported" }
                return Offer(sdp)
            }
            val parts = text.split('\n', limit = 4)
            require(parts.size == 4 && parts[0] == "ice") { "Unknown signaling message" }
            val index = parts[2].toIntOrNull()
            require(index != null && index in 0..32 && parts[1].length <= 64 && parts[3].startsWith("candidate:")) { "Invalid ICE candidate" }
            return Ice(parts[1], index, parts[3])
        }
    }
}

interface RtcSignaling {
    suspend fun open(offer: String, emit: (String) -> Unit): RtcSession
    suspend fun openHttp(offer: String): RtcSession = open(offer) {}
}
interface RtcSession {
    val answer: String
    val closed: Boolean
    suspend fun completeAnswer(): String
    suspend fun addIce(candidate: RtcSignal.Ice)
    suspend fun close()
}
