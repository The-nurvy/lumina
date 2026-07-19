package io.lumina.app.network

data class SyncedFftFrame(
    val targetTimestamp: Long, val bass: Float, val mid: Float, val treble: Float
)
