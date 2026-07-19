package com.example.Lumina.network

import com.example.Lumina.models.SyncedFftFrame
import java.nio.ByteBuffer

object FftPacketSerializer {
    private const val HEADER_FFT: Byte = 0x02

    fun encode(
        targetTimestamp: Long,
        bass: Float,
        mid: Float,
        treble: Float,
        visualMode: Int
    ): ByteArray {
        val buffer = ByteBuffer.allocate(13)
        buffer.put(HEADER_FFT)
        buffer.putLong(targetTimestamp)
        buffer.put((bass.coerceIn(0f, 1f) * 255).toInt().toByte())
        buffer.put((mid.coerceIn(0f, 1f) * 255).toInt().toByte())
        buffer.put((treble.coerceIn(0f, 1f) * 255).toInt().toByte())
        buffer.put(visualMode.toByte())
        return buffer.array()
    }

    fun decode(bytes: ByteArray): SyncedFftFrame? {
        if (bytes.size < 13 || bytes[0] != HEADER_FFT) return null
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(1)
        return SyncedFftFrame(
            buffer.long,
            (buffer.get().toInt() and 0xFF) / 255f,
            (buffer.get().toInt() and 0xFF) / 255f,
            (buffer.get().toInt() and 0xFF) / 255f,
            buffer.get().toInt()
        )
    }

    fun isFFTPacket(bytes: ByteArray): Boolean = bytes.isNotEmpty() && bytes[0] == HEADER_FFT
}
