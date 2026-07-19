package io.lumina.app.sync

import io.lumina.app.audio.AudioBands
import io.lumina.app.network.ClockSyncEngine
import io.lumina.app.network.FftPacketSerializer
import io.lumina.app.network.SyncedFftFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.PriorityQueue

class SynchronizedJitterBuffer(private val clockSyncEngine: ClockSyncEngine) {
    private val _renderFlow = MutableStateFlow(AudioBands(0f, 0f, 0f))
    val renderFlow: StateFlow<AudioBands> = _renderFlow
    private val frameQueue = PriorityQueue<SyncedFftFrame>(compareBy { it.targetTimestamp })
    private var isRunning = false

    fun startPlaybackLoop(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        scope.launch(Dispatchers.Default) {
            while (isRunning) {
                val now = System.currentTimeMillis()
                var frameToRender: SyncedFftFrame? = null
                synchronized(frameQueue) {
                    while (frameQueue.isNotEmpty()) {
                        val head = frameQueue.peek() ?: break
                        if (now >= clockSyncEngine.hostTimeToLocalTime(head.targetTimestamp)) {
                            frameToRender = frameQueue.poll()
                        } else break
                    }
                }
                frameToRender?.let { _renderFlow.value = AudioBands(it.bass, it.mid, it.treble) }
                delay(4)
            }
        }
    }

    fun onPacketReceived(payloadBytes: ByteArray) {
        val frame = FftPacketSerializer.decode(payloadBytes) ?: return
        if (System.currentTimeMillis() - clockSyncEngine.hostTimeToLocalTime(frame.targetTimestamp) > 20) return
        synchronized(frameQueue) {
            frameQueue.add(frame)
            if (frameQueue.size > 30) frameQueue.poll()
        }
    }
    fun stop() { isRunning = false }
}
