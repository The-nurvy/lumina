package io.lumina.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.lumina.app.audio.FftAudioAnalyzer
import io.lumina.app.network.ClockSyncEngine
import io.lumina.app.network.FftPacketSerializer
import io.lumina.app.network.NearbySyncManager
import io.lumina.app.sync.SynchronizedJitterBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisualizerViewModel(application: Application) : AndroidViewModel(application) {
    private val fftAnalyzer = FftAudioAnalyzer()
    private val nearbyManager = NearbySyncManager(application)
    private val clockSyncEngine = ClockSyncEngine { id, bytes -> nearbyManager.sendPayload(listOf(id), bytes) }
    private val jitterBuffer = SynchronizedJitterBuffer(clockSyncEngine)

    private val _uiMode = MutableStateFlow("IDLE")
    val uiMode = _uiMode.asStateFlow()
    val renderBandsFlow = jitterBuffer.renderFlow
    private val connectedClients = mutableListOf<String>()

    fun startHosting() {
        _uiMode.value = "HOSTING"
        jitterBuffer.startPlaybackLoop(viewModelScope)
        nearbyManager.startHosting(
            onClient = { if (!connectedClients.contains(it)) connectedClients.add(it) },
            onMsg = { id, bytes -> clockSyncEngine.onMessageReceived(id, bytes) }
        )
        fftAnalyzer.startListening()
        viewModelScope.launch {
            fftAnalyzer.bandsFlow.collect { bands ->
                val packet = FftPacketSerializer.encode(System.currentTimeMillis() + 80L, bands.bass, bands.mid, bands.treble)
                if (connectedClients.isNotEmpty()) nearbyManager.sendPayload(connectedClients, packet)
                jitterBuffer.onPacketReceived(packet)
            }
        }
    }

    fun startJoining() {
        _uiMode.value = "SEARCHING"
        jitterBuffer.startPlaybackLoop(viewModelScope)
        nearbyManager.startDiscovering(
            onHost = { clockSyncEngine.startSyncBurst(it, viewModelScope); _uiMode.value = "CONNECTED" },
            onMsg = { id, bytes ->
                if (bytes.isNotEmpty() && bytes[0] == 0x02.toByte()) jitterBuffer.onPacketReceived(bytes)
                else clockSyncEngine.onMessageReceived(id, bytes)
            }
        )
    }
}
