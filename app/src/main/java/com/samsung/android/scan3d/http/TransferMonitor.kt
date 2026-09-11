package com.samsung.android.scan3d.http

data class TransferStats(
    val clients: Int = 0, val confirmedClients: Int = 0,
    val outputMbps: Double = 0.0, val ackMs: Double? = null,
    val skippedPercent: Double = 0.0, val stalled: Boolean = false,
    val enoughSamples: Boolean = false
) {
    enum class Health { UNKNOWN, GOOD, BUSY, SLOW }
    fun health(sourceFps: Int): Health = when {
        stalled -> Health.SLOW
        clients == 0 || confirmedClients == 0 || !enoughSamples || sourceFps <= 0 -> Health.UNKNOWN
        (ackMs ?: 0.0) > 2000.0 / sourceFps || skippedPercent >= 20.0 -> Health.SLOW
        (ackMs ?: 0.0) > 800.0 / sourceFps || skippedPercent >= 2.0 -> Health.BUSY
        else -> Health.GOOD
    }
}

/** Receiver feedback, not a Wi-Fi speed test or a measurement of OBS's final output. */
class TransferMonitor(private val clock: () -> Long = System::nanoTime) {
    private data class Sample(val at: Long, val bytes: Int, val ackMs: Double, val skipped: Long)
    private class Client(val confirmed: Boolean, val since: Long) {
        val samples = ArrayDeque<Sample>()
        var pending: Long? = null
        var lastSequence: Long? = null
    }
    private val clients = mutableMapOf<Int, Client>()
    private var nextId = 0
    private var lastTimeout: Long? = null

    @Synchronized fun connect(confirmed: Boolean): Int {
        val id = ++nextId
        clients[id] = Client(confirmed, clock())
        return id
    }
    @Synchronized fun begin(id: Int) { clients[id]?.pending = clock() }
    @Synchronized fun complete(id: Int, bytes: Int, sequence: Long) {
        val client = clients[id] ?: return
        val now = clock()
        val duration = (now - (client.pending ?: now)) / 1_000_000.0
        val skipped = client.lastSequence?.let { (sequence - it - 1).coerceAtLeast(0) } ?: 0L
        client.lastSequence = sequence
        client.pending = null
        client.samples.addLast(Sample(now, bytes, duration, skipped))
        prune(client, now)
    }
    @Synchronized fun disconnect(id: Int, timedOut: Boolean = false) {
        clients.remove(id)
        if (timedOut) lastTimeout = clock()
    }
    @Synchronized fun snapshot(): TransferStats {
        val now = clock()
        clients.values.forEach { prune(it, now) }
        val confirmed = clients.values.filter { it.confirmed }
        val ackMs = confirmed.mapNotNull { c -> c.samples.takeIf { it.isNotEmpty() }?.map { it.ackMs }?.average() }.maxOrNull()
        val skipped = confirmed.maxOfOrNull { c ->
            val lost = c.samples.sumOf { it.skipped }
            if (c.samples.isEmpty()) 0.0 else 100.0 * lost / (lost + c.samples.size)
        } ?: 0.0
        return TransferStats(
            clients = clients.size, confirmedClients = confirmed.size,
            outputMbps = clients.values.sumOf { c ->
                val elapsed = (now - c.since).coerceIn(1_000_000L, WINDOW_NS) / 1_000_000_000.0
                c.samples.sumOf { it.bytes.toLong() } * 8.0 / elapsed / 1_000_000.0
            },
            ackMs = ackMs, skippedPercent = skipped,
            stalled = clients.values.any { c -> c.pending?.let { now - it > 500_000_000L } == true }
                || lastTimeout?.let { now - it < 5_000_000_000L } == true,
            enoughSamples = confirmed.isNotEmpty() && confirmed.all { it.samples.size >= 5 }
        )
    }
    private fun prune(client: Client, now: Long) {
        while (client.samples.firstOrNull()?.let { now - it.at > WINDOW_NS } == true) client.samples.removeFirst()
    }
    companion object { private const val WINDOW_NS = 2_000_000_000L }
}
