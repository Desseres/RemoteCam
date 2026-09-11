package com.samsung.android.scan3d.http

object Bandwidth {
    fun megabitsPerSecond(frameBytes: Double, fps: Double) = frameBytes * fps * 8.0 / 1_000_000.0
    // Planning headroom for variable JPEG sizes and Wi-Fi contention, not a guaranteed requirement.
    fun recommendedMbps(frameBytes: Double, fps: Double) = megabitsPerSecond(frameBytes, fps) * 2.0
}
