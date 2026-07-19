package com.example.Lumina.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClockSyncEngine(private val sendPayload: (String, ByteArray) -> Unit) {
    private var currentOffset: Long = 0L
    private val rttSamples = mutableListOf<Pair<Long, Long>>()
    private var isSyncing = false

    fun getCurrentOffset(): Long = currentOffset

    fun startSyncBurst(hostId: String, scope: CoroutineScope) {
        if (isSyncing) return
        isSyncing = true
        rttSamples.clear()
        scope.launch(Dispatchers.IO) {
            sendClockRequests(hostId)
            delay(500)
            calculateOffset()
            isSyncing = false
        }
    }

    fun onMessageReceived(senderId: String, payloadBytes: ByteArray) {
        val message = String(payloadBytes)
        handleClockMessage(senderId, message)
    }

    fun hostTimeToLocalTime(hostTimestamp: Long) = hostTimestamp - currentOffset

    private suspend fun sendClockRequests(hostId: String) {
        repeat(8) {
            sendPayload(hostId, "REQ:${System.currentTimeMillis()}".toByteArray())
            delay(50)
        }
    }

    private fun calculateOffset() {
        synchronized(rttSamples) {
            rttSamples.minByOrNull { it.first }?.let { currentOffset = it.second }
        }
    }

    private fun handleClockMessage(senderId: String, message: String) {
        val parts = message.split(":")
        when (parts.getOrNull(0)) {
            "REQ" -> handleClockRequest(senderId, parts)
            "RESP" -> handleClockResponse(parts)
        }
    }

    private fun handleClockRequest(senderId: String, parts: List<String>) {
        val t1 = System.currentTimeMillis()
        val t0 = parts.getOrNull(1)?.toLongOrNull() ?: return
        val t2 = System.currentTimeMillis()
        sendPayload(senderId, "RESP:$t0:$t1:$t2".toByteArray())
    }

    private fun handleClockResponse(parts: List<String>) {
        val t3 = System.currentTimeMillis()
        val t0 = parts.getOrNull(1)?.toLongOrNull() ?: return
        val t1 = parts.getOrNull(2)?.toLongOrNull() ?: return
        val t2 = parts.getOrNull(3)?.toLongOrNull() ?: return

        val roundTripTime = (t3 - t0) - (t2 - t1)
        if (roundTripTime < 100) {
            synchronized(rttSamples) {
                val offset = ((t1 - t0) + (t2 - t3)) / 2
                rttSamples.add(Pair(roundTripTime, offset))
            }
        }
    }
}
