package com.samsung.android.scan3d.http

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class VideoFrame(val sequence: Long, val jpeg: ByteArray)

/** One waiting frame per viewer; a producer never waits for a slow viewer. No replay on connect. */
class LatestFrames {
    private val sequence = AtomicLong()
    private val viewers = ConcurrentHashMap<Int, Channel<VideoFrame>>()
    fun subscribe(id: Int): Channel<VideoFrame> = Channel<VideoFrame>(Channel.CONFLATED).also { viewers[id] = it }
    fun unsubscribe(id: Int) { viewers.remove(id)?.close() }
    fun publish(jpeg: ByteArray) {
        val frame = VideoFrame(sequence.incrementAndGet(), jpeg)
        viewers.values.forEach { it.trySend(frame) }
    }
    fun close() { viewers.keys.toList().forEach(::unsubscribe) }
}
