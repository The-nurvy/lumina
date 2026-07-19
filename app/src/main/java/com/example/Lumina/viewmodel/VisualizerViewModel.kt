package com.example.Lumina.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.Lumina.audio.AudioAnalyzer
import com.example.Lumina.models.UIMode
import com.example.Lumina.network.FftPacketSerializer
import com.example.Lumina.network.NearbySyncManager
import com.example.Lumina.sync.ClockSyncEngine
import com.example.Lumina.sync.JitterBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VisualizerViewModel(application: Application) : AndroidViewModel(application) {
    private val audioAnalyzer = AudioAnalyzer()
    private val nearbyManager = NearbySyncManager(application)
    private val clockSyncEngine = ClockSyncEngine { id, bytes -> nearbyManager.sendPayload(listOf(id), bytes) }
    private val jitterBuffer = JitterBuffer(clockSyncEngine)

    private val _uiMode = MutableStateFlow(UIMode.IDLE)
    val uiMode: StateFlow<UIMode> = _uiMode.asStateFlow()
    val renderBandsFlow: StateFlow<com.example.Lumina.models.AudioBands> = jitterBuffer.renderFlow
    private val connectedClients = mutableListOf<String>()

    fun startHosting() {
        _uiMode.value = UIMode.HOSTING
        jitterBuffer.startPlaybackLoop(viewModelScope)
        nearbyManager.startHosting(
            onClient = { if (!connectedClients.contains(it)) connectedClients.add(it) },
            onMsg = { id, bytes -> clockSyncEngine.onMessageReceived(id, bytes) }
        )
        audioAnalyzer.startListening()
        viewModelScope.launch {
            audioAnalyzer.bandsFlow.collect { bands ->
                val packet = FftPacketSerializer.encode(
                    System.currentTimeMillis() + 80L,
                    bands.bass,
                    bands.mid,
                    bands.treble,
                    bands.visualMode
                )
                if (connectedClients.isNotEmpty()) nearbyManager.sendPayload(connectedClients, packet)
                jitterBuffer.onPacketReceived(packet)
            }
        }
    }

    fun startJoining() {
        _uiMode.value = UIMode.SEARCHING
        jitterBuffer.startPlaybackLoop(viewModelScope)
        nearbyManager.startDiscovering(
            onHost = { hostId ->
                clockSyncEngine.startSyncBurst(hostId, viewModelScope)
                _uiMode.value = UIMode.CONNECTED
            },
            onMsg = { id, bytes ->
                when {
                    FftPacketSerializer.isFFTPacket(bytes) -> jitterBuffer.onPacketReceived(bytes)
                    else -> clockSyncEngine.onMessageReceived(id, bytes)
                }
            }
        )
    }

    fun cycleVisualMode() {
        audioAnalyzer.currentVisualMode = (audioAnalyzer.currentVisualMode + 1) % 3
    }

    override fun onCleared() {
        super.onCleared()
        audioAnalyzer.stopListening()
        jitterBuffer.stop()
    }
}
