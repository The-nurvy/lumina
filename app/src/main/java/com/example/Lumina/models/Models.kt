package com.example.Lumina.models

import androidx.compose.ui.geometry.Offset

data class AudioBands(val bass: Float, val mid: Float, val treble: Float, val visualMode: Int = 0)

data class SyncedFftFrame(
    val targetTimestamp: Long,
    val bass: Float,
    val mid: Float,
    val treble: Float,
    val visualMode: Int
)

data class Particle(
    var position: Offset,
    val velocity: Offset,
    val initialAlpha: Float,
    var alpha: Float,
    val lifeTimeMs: Int,
    var elapsedMs: Int = 0,
    val isSquare: Boolean = false
)

data class SyncState(
    val isActive: Boolean = false,
    val currentOffset: Long = 0L
)

enum class UIMode {
    IDLE, HOSTING, SEARCHING, CONNECTED
}
