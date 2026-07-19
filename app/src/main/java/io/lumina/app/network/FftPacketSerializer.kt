package io.lumina.app.network

import java.nio.ByteBuffer

object FftPacketSerializer {
    private const val HEADER_FFT: Byte = 0x02
    fun encode(targetTimestamp: Long, bass: Float, mid: Float, treble: Float): ByteArray {
        val buffer = ByteBuffer.allocate(12)
        buffer.put(HEADER_FFT)
        buffer.putLong(targetTimestamp)
        buffer.put((bass.coerceIn(0f, 1f) * 255).toInt().toByte())
        buffer.put((mid.coerceIn(0f, 1f) * 255).toInt().toByte())
        buffer.put((treble.coerceIn(0f, 1f) * 255).toInt().toByte())
        return buffer.array()
    }
    fun decode(bytes: ByteArray): SyncedFftFrame? {
        if (bytes.size < 12 || bytes[0] != HEADER_FFT) return null
        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(1)
        return SyncedFftFrame(
            buffer.long,
            (buffer.get().toInt() and 0xFF) / 255f,
            (buffer.get().toInt() and 0xFF) / 255f,
            (buffer.get().toInt() and 0xFF) / 255f
        )
    }
}
