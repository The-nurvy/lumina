package com.example.Lumina.sync

import com.example.Lumina.models.AudioBands
import com.example.Lumina.models.SyncedFftFrame
import com.example.Lumina.network.FftPacketSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.PriorityQueue

class JitterBuffer(private val clockSyncEngine: ClockSyncEngine) {
    private val _renderFlow = MutableStateFlow(AudioBands(0f, 0f, 0f, 0))
    val renderFlow: StateFlow<AudioBands> = _renderFlow
    private val frameQueue = PriorityQueue<SyncedFftFrame>(compareBy { it.targetTimestamp })
    private var isRunning = false

    fun startPlaybackLoop(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        scope.launch(Dispatchers.Default) {
            playbackLoop()
        }
    }

    fun onPacketReceived(payloadBytes: ByteArray) {
        val frame = FftPacketSerializer.decode(payloadBytes) ?: return
        if (isFrameStale(frame)) return
        synchronized(frameQueue) {
            frameQueue.add(frame)
            if (frameQueue.size > 30) frameQueue.poll()
        }
    }

    fun stop() {
        isRunning = false
    }

    private suspend fun playbackLoop() {
        while (isRunning) {
            val frameToRender = dequeueReadyFrame()
            frameToRender?.let {
                _renderFlow.value = AudioBands(it.bass, it.mid, it.treble, it.visualMode)
            }
            delay(4)
        }
    }

    private fun dequeueReadyFrame(): SyncedFftFrame? {
        val now = System.currentTimeMillis()
        synchronized(frameQueue) {
            while (frameQueue.isNotEmpty()) {
                val head = frameQueue.peek() ?: break
                if (now >= clockSyncEngine.hostTimeToLocalTime(head.targetTimestamp)) {
                    return frameQueue.poll()
                } else {
                    break
                }
            }
        }
        return null
    }

    private fun isFrameStale(frame: SyncedFftFrame): Boolean {
        val timeDiff = System.currentTimeMillis() - clockSyncEngine.hostTimeToLocalTime(frame.targetTimestamp)
        return timeDiff > 20
    }
}
