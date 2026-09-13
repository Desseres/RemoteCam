package com.samsung.android.scan3d.http

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** HTTP SDP exchange with full ICE gathering (no trickle/PATCH), compatible with go2rtc. */
class WhepSessions {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, RtcSession>()

    suspend fun open(signaling: RtcSignaling, offer: String): Pair<String, String> = mutex.withLock {
        validateOffer(offer)
        sessions.entries.removeAll { it.value.closed }
        check(sessions.size < 4) { "Maximum of four HTTP WebRTC receivers reached" }
        val session = signaling.openHttp(offer)
        try {
            val answer = session.completeAnswer()
            val id = UUID.randomUUID().toString()
            sessions[id] = session
            id to answer
        } catch (error: Throwable) {
            withContext(NonCancellable) { session.close() }
            throw error
        }
    }

    suspend fun delete(id: String): Boolean = mutex.withLock {
        val session = sessions.remove(id) ?: return@withLock false
        withContext(NonCancellable) { session.close() }
        true
    }

    suspend fun close() = mutex.withLock {
        withContext(NonCancellable) { sessions.values.forEach { it.close() } }
        sessions.clear()
    }

    companion object {
        const val MAX_OFFER_BYTES = 131072
        fun validateOffer(offer: String) {
            ReceiveOffer.validate(offer)
            require(offer.lineSequence().any { it.startsWith("a=candidate:") }) {
                "Include gathered ICE candidates in the offer; trickle ICE is not supported on this endpoint"
            }
        }

    }
}
