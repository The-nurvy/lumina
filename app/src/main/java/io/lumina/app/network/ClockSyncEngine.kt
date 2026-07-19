package io.lumina.app.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClockSyncEngine(private val sendPayload: (String, ByteArray) -> Unit) {
    private var currentOffset: Long = 0L
    private val rttSamples = mutableListOf<Pair<Long, Long>>()
    private var isSyncing = false

    fun startSyncBurst(hostId: String, scope: CoroutineScope) {
        if (isSyncing) return
        isSyncing = true
        rttSamples.clear()
        scope.launch(Dispatchers.IO) {
            repeat(8) {
                sendPayload(hostId, "REQ:${System.currentTimeMillis()}".toByteArray())
                delay(50)
            }
            delay(500)
            synchronized(rttSamples) {
                rttSamples.minByOrNull { it.first }?.let { currentOffset = it.second }
            }
            isSyncing = false
        }
    }

    fun onMessageReceived(senderId: String, payloadBytes: ByteArray) {
        val parts = String(payloadBytes).split(":")
        when (parts[0]) {
            "REQ" -> {
                val t1 = System.currentTimeMillis()
                val t0 = parts[1].toLong()
                val t2 = System.currentTimeMillis()
                sendPayload(senderId, "RESP:$t0:$t1:$t2".toByteArray())
            }
            "RESP" -> {
                val t3 = System.currentTimeMillis()
                val roundTripTime = (t3 - parts[1].toLong()) - (parts[3].toLong() - parts[2].toLong())
                if (roundTripTime < 100) {
                    synchronized(rttSamples) {
                        rttSamples.add(Pair(roundTripTime, ((parts[2].toLong() - parts[1].toLong()) + (parts[3].toLong() - t3)) / 2))
                    }
                }
            }
        }
    }
    fun hostTimeToLocalTime(hostTimestamp: Long) = hostTimestamp - currentOffset
}
