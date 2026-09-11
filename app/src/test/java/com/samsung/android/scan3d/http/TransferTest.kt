package com.samsung.android.scan3d.http

import org.junit.Assert.*
import org.junit.Test

class TransferTest {
    @Test fun slowViewerKeepsOnlyLatestAndDoesNotBlockFastViewer() {
        val frames = LatestFrames()
        val fast = frames.subscribe(1)
        val slow = frames.subscribe(2)
        repeat(100) {
            frames.publish(byteArrayOf(it.toByte()))
            assertEquals((it + 1).toLong(), fast.tryReceive().getOrThrow().sequence)
        }
        val latest = slow.tryReceive().getOrThrow()
        assertEquals(100L, latest.sequence)
        assertArrayEquals(byteArrayOf(99), latest.jpeg)
        assertTrue(slow.tryReceive().isFailure)
        frames.unsubscribe(2)
        frames.publish(byteArrayOf(100))
        assertEquals(101L, fast.tryReceive().getOrThrow().sequence)
        frames.close()
        assertTrue(fast.tryReceive().isClosed)
    }

    @Test fun ratesUseDecimalMegabitsAndHeadroomIsExplicit() {
        assertEquals(128.0, Bandwidth.megabitsPerSecond(1_600_000.0, 10.0), 0.001)
        assertEquals(256.0, Bandwidth.recommendedMbps(1_600_000.0, 10.0), 0.001)
    }

    @Test fun receiverFeedbackCountsSkippedFramesAndExpires() {
        var now = 0L
        val monitor = TransferMonitor { now }
        val id = monitor.connect(true)
        assertEquals(TransferStats.Health.UNKNOWN, monitor.snapshot().health(30))
        repeat(5) { index ->
            monitor.begin(id)
            now += 10_000_000L
            monitor.complete(id, 100_000, (index + 1).toLong())
            now += 10_000_000L
        }
        val good = monitor.snapshot()
        assertEquals(TransferStats.Health.GOOD, good.health(30))
        assertEquals(40.0, good.outputMbps, 0.001)
        assertEquals(10.0, good.ackMs!!, 0.001)
        monitor.begin(id)
        now += 10_000_000L
        monitor.complete(id, 100_000, 10)
        assertEquals(40.0, monitor.snapshot().skippedPercent, 0.001)
        assertEquals(TransferStats.Health.SLOW, monitor.snapshot().health(30))
        now += 3_000_000_000L
        assertEquals(0.0, monitor.snapshot().outputMbps, 0.001)
        assertEquals(TransferStats.Health.UNKNOWN, monitor.snapshot().health(30))
        monitor.begin(id)
        now += 501_000_000L
        assertTrue(monitor.snapshot().stalled)
        monitor.disconnect(id, timedOut = true)
        assertEquals(TransferStats.Health.SLOW, monitor.snapshot().health(30))
        now += 5_000_000_000L
        assertEquals(TransferStats.Health.UNKNOWN, monitor.snapshot().health(30))
    }

    @Test fun legacySocketWritesCannotProduceGreenAndSlowestConfirmedViewerWins() {
        var now = 0L
        val monitor = TransferMonitor { now }
        val legacy = monitor.connect(false)
        repeat(5) {
            monitor.begin(legacy); now += 1_000_000L
            monitor.complete(legacy, 1000, it.toLong())
        }
        assertEquals(TransferStats.Health.UNKNOWN, monitor.snapshot().health(30))
        val fast = monitor.connect(true)
        val slow = monitor.connect(true)
        repeat(5) {
            monitor.begin(fast); monitor.begin(slow)
            now += 10_000_000L; monitor.complete(fast, 1000, it.toLong())
            now += 20_000_000L; monitor.complete(slow, 1000, it.toLong())
        }
        assertEquals(TransferStats.Health.BUSY, monitor.snapshot().health(30))
        monitor.disconnect(slow)
        assertEquals(TransferStats.Health.GOOD, monitor.snapshot().health(30))
    }
}
