package com.example.Lumina

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.util.fft.FFT
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer
import java.util.PriorityQueue
import kotlin.random.Random

// ==========================================
// 1. DATA STRUCTURES & PROTOCOLS
// ==========================================
data class AudioBands(val bass: Float, val mid: Float, val treble: Float, val visualMode: Int = 0)

data class SyncedFftFrame(
    val targetTimestamp: Long, val bass: Float, val mid: Float, val treble: Float, val visualMode: Int
)

object FftPacketSerializer {
    private const val HEADER_FFT: Byte = 0x02
    fun encode(targetTimestamp: Long, bass: Float, mid: Float, treble: Float, visualMode: Int): ByteArray {
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
}

// ==========================================
// 2. AUDIO FFT ENGINE (TarsosDSP)
// ==========================================
class FftAudioAnalyzer {
    private val _bandsFlow = MutableStateFlow(AudioBands(0f, 0f, 0f, 0))
    val bandsFlow: StateFlow<AudioBands> = _bandsFlow

    private var dispatcher: AudioDispatcher? = null
    private val sampleRate = 44100
    private val bufferSize = 1024
    
    var currentVisualMode = 0 

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (dispatcher != null) return
        
        try {
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, 0)
            val fft = FFT(bufferSize)

            dispatcher?.addAudioProcessor(object : be.tarsos.dsp.AudioProcessor {
                override fun process(audioEvent: AudioEvent): Boolean {
                    val audioFloatBuffer = audioEvent.floatBuffer
                    val amplitudes = FloatArray(bufferSize / 2)

                    fft.forwardTransform(audioFloatBuffer)
                    fft.modulus(audioFloatBuffer, amplitudes)

                    var bassEnergy = 0f
                    var midEnergy = 0f
                    var trebleEnergy = 0f

                    for (i in amplitudes.indices) {
                        val frequency = (i * sampleRate) / bufferSize.toFloat()
                        val magnitude = amplitudes[i]

                        when {
                            frequency in 20f..250f -> bassEnergy += magnitude
                            frequency in 250f..4000f -> midEnergy += magnitude
                            frequency in 4000f..16000f -> trebleEnergy += magnitude
                        }
                    }

                    _bandsFlow.value = AudioBands(
                        bass = (bassEnergy / 10f).coerceIn(0f, 1f),
                        mid = (midEnergy / 30f).coerceIn(0f, 1f),
                        treble = (trebleEnergy / 30f).coerceIn(0f, 1f),
                        visualMode = currentVisualMode
                    )
                    return true
                }

                override fun processingFinished() {}
            })
            Thread(dispatcher, "Audio-FFT-Thread").start()
        } catch (e: Exception) {
             // Handle emulator/no-mic crash
        }
    }

    fun stopListening() {
        dispatcher?.stop()
        dispatcher = null
    }
}

// ==========================================
// 3. NTP & JITTER BUFFER (NETWORK SYNC)
// ==========================================
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
        val message = String(payloadBytes)
        val parts = message.split(":")
        when (parts[0]) {
            "REQ" -> {
                val t1 = System.currentTimeMillis()
                val t0 = parts[1].toLongOrNull() ?: return
                val t2 = System.currentTimeMillis()
                sendPayload(senderId, "RESP:$t0:$t1:$t2".toByteArray())
            }
            "RESP" -> {
                val t3 = System.currentTimeMillis()
                val t0 = parts[1].toLongOrNull() ?: return
                val t1 = parts[2].toLongOrNull() ?: return
                val t2 = parts[3].toLongOrNull() ?: return
                
                val roundTripTime = (t3 - t0) - (t2 - t1)
                if (roundTripTime < 100) {
                    synchronized(rttSamples) {
                        val offset = ((t1 - t0) + (t2 - t3)) / 2
                        rttSamples.add(Pair(roundTripTime, offset))
                    }
                }
            }
        }
    }
    fun hostTimeToLocalTime(hostTimestamp: Long) = hostTimestamp - currentOffset
}

class SynchronizedJitterBuffer(private val clockSyncEngine: ClockSyncEngine) {
    private val _renderFlow = MutableStateFlow(AudioBands(0f, 0f, 0f, 0))
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
                frameToRender?.let { 
                    _renderFlow.value = AudioBands(it.bass, it.mid, it.treble, it.visualMode) 
                }
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

class NearbySyncManager(context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val strategy = Strategy.P2P_STAR
    
    @SuppressLint("MissingPermission")
    fun startHosting(onClient: (String) -> Unit, onMsg: (String, ByteArray) -> Unit) {
        client.startAdvertising("Host", "com.example.musicpulse", object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                client.acceptConnection(id, buildPayloadCallback(onMsg))
            }
            override fun onConnectionResult(id: String, res: ConnectionResolution) { if (res.status.isSuccess) onClient(id) }
            override fun onDisconnected(id: String) {}
        }, AdvertisingOptions.Builder().setStrategy(strategy).build())
    }

    @SuppressLint("MissingPermission")
    fun startDiscovering(onHost: (String) -> Unit, onMsg: (String, ByteArray) -> Unit) {
        client.startDiscovery("com.example.Lumina", object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                client.requestConnection("Client", id, object : ConnectionLifecycleCallback() {
                    override fun onConnectionInitiated(id: String, info: ConnectionInfo) { client.acceptConnection(id, buildPayloadCallback(onMsg)) }
                    override fun onConnectionResult(id: String, res: ConnectionResolution) { if (res.status.isSuccess) onHost(id) }
                    override fun onDisconnected(id: String) {}
                })
            }
            override fun onEndpointLost(id: String) {}
        }, DiscoveryOptions.Builder().setStrategy(strategy).build())
    }

    private fun buildPayloadCallback(onMsg: (String, ByteArray) -> Unit) = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) { payload.asBytes()?.let { onMsg(id, it) } }
        override fun onPayloadTransferUpdate(id: String, update: PayloadTransferUpdate) {}
    }

    fun sendPayload(ids: List<String>, bytes: ByteArray) { client.sendPayload(ids, Payload.fromBytes(bytes)) }
}

// ==========================================
// 4. VIEW MODEL
// ==========================================
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
                val packet = FftPacketSerializer.encode(System.currentTimeMillis() + 80L, bands.bass, bands.mid, bands.treble, bands.visualMode)
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
    
    fun cycleVisualMode() {
        fftAnalyzer.currentVisualMode = (fftAnalyzer.currentVisualMode + 1) % 3
    }
}

// ==========================================
// 5. JETPACK COMPOSE UI & RENDERERS
// ==========================================
data class Particle(var position: Offset, val velocity: Offset, val initialAlpha: Float, var alpha: Float, val lifeTimeMs: Int, var elapsedMs: Int = 0, val isSquare: Boolean = false)

@Composable
fun AdvancedVisualizerScreen(fftBandsFlow: StateFlow<AudioBands>) {
    val bands by fftBandsFlow.collectAsState(AudioBands(0f, 0f, 0f, 0))
    var strobeAlpha by remember { mutableStateOf(0f) }
    val particleList = remember { mutableStateListOf<Particle>() }
    var lastFrameNanos by remember { mutableStateOf(System.nanoTime()) }
    var globalRotation by remember { mutableStateOf(0f) }

    LaunchedEffect(key1 = true) {
        while (true) {
            withFrameNanos { currentFrameNanos ->
                val deltaTimeNanos = currentFrameNanos - lastFrameNanos
                val deltaTimeSec = deltaTimeNanos / 1_000_000_000f
                val elapsedFrameMs = (deltaTimeNanos / 1_000_000f).toInt()
                lastFrameNanos = currentFrameNanos

                globalRotation += 45f * deltaTimeSec * (1f + bands.mid)

                // Mode-specific particle triggers
                if (bands.bass > 0.85f) strobeAlpha = 1f
                
                if (bands.treble > 0.5f) {
                    val count = if (bands.visualMode == 2) 2 else 5
                    repeat(count) {
                        val isSquare = bands.visualMode == 1
                        val vx = if (bands.visualMode == 2) 0f else Random.nextInt(-150, 150).toFloat()
                        val vy = if (bands.visualMode == 2) Random.nextInt(400, 800).toFloat() else Random.nextInt(50, 300).toFloat()
                        
                        particleList.add(Particle(
                            position = Offset(Random.nextInt(0, 1080).toFloat(), if (bands.visualMode == 2) 0f else Random.nextInt(0, 1920).toFloat()),
                            velocity = Offset(vx, vy),
                            initialAlpha = (Random.nextInt(5, 10) / 10f), alpha = 1f, lifeTimeMs = Random.nextInt(700, 1500),
                            isSquare = isSquare
                        ))
                    }
                }
                
                strobeAlpha = (strobeAlpha - (deltaTimeSec * 2.0f)).coerceAtLeast(0f)
                val iterator = particleList.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.position += p.velocity * deltaTimeSec
                    p.elapsedMs += elapsedFrameMs
                    p.alpha = (p.initialAlpha * (1f - (p.elapsedMs.toFloat() / p.lifeTimeMs))).coerceIn(0f, 1f)
                    if (p.elapsedMs >= p.lifeTimeMs || p.alpha <= 0.01f) iterator.remove()
                }
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        when (bands.visualMode) {
            0 -> drawClassicMode(bands, strobeAlpha, particleList)
            1 -> drawGeoBurstMode(bands, strobeAlpha, particleList, globalRotation)
            2 -> drawRetroWaveMode(bands, strobeAlpha, particleList)
        }
    }
}

// --- RENDER MODE 0: Classic Rings ---
private fun DrawScope.drawClassicMode(bands: AudioBands, strobeAlpha: Float, particles: List<Particle>) {
    drawCircle(color = Color.Green.copy(alpha = bands.mid * 0.3f), radius = (size.width * 0.4f) * bands.mid, center = center, style = Stroke(width = 40f * bands.mid))
    drawCircle(color = Color.Red.copy(alpha = bands.bass * 0.5f), radius = (size.width * 0.8f) * bands.bass, center = center, style = Stroke(width = 80f * bands.bass))
    
    if (strobeAlpha > 0f) {
        drawRect(color = Color.Magenta.copy(alpha = strobeAlpha * 0.4f), size = size)
        if (strobeAlpha > 0.8f) drawRect(color = Color.White.copy(alpha = strobeAlpha * 0.5f), size = size)
    }
    
    particles.forEach { p ->
        if (p.alpha > 0f) {
            drawCircle(color = Color.Cyan.copy(alpha = p.alpha), radius = 12f, center = p.position)
            drawCircle(color = Color.White.copy(alpha = p.alpha * 0.2f), radius = 24f, center = p.position)
        }
    }
}

// --- RENDER MODE 1: Geo Burst ---
private fun DrawScope.drawGeoBurstMode(bands: AudioBands, strobeAlpha: Float, particles: List<Particle>, rotation: Float) {
    val trianglePath = Path().apply {
        val w = size.width * 0.5f
        moveTo(center.x, center.y - w)
        lineTo(center.x + w * 0.866f, center.y + w * 0.5f)
        lineTo(center.x - w * 0.866f, center.y + w * 0.5f)
        close()
    }
    
    rotate(degrees = rotation, pivot = center) {
        drawPath(path = trianglePath, color = Color.Yellow.copy(alpha = bands.mid * 0.4f), style = Stroke(width = 20f + (60f * bands.bass)))
    }
    rotate(degrees = -rotation * 1.5f, pivot = center) {
        drawPath(path = trianglePath, color = Color.Red.copy(alpha = bands.bass * 0.6f), style = Stroke(width = 10f))
    }

    if (strobeAlpha > 0f) {
        drawRect(color = Color.Yellow.copy(alpha = strobeAlpha * 0.3f), size = size)
    }

    particles.forEach { p ->
        if (p.alpha > 0f) {
            drawRect(color = Color(0xFF00FFCC).copy(alpha = p.alpha), topLeft = Offset(p.position.x - 10f, p.position.y - 10f), size = Size(20f, 20f))
        }
    }
}

// --- RENDER MODE 2: Retro Wave ---
private fun DrawScope.drawRetroWaveMode(bands: AudioBands, strobeAlpha: Float, particles: List<Particle>) {
    val horizonY = size.height * 0.6f
    
    // Grid horizontal lines (wave with bass)
    for (i in 0..10) {
        val yOffset = horizonY + (i * i * 15f) + (bands.bass * i * 20f)
        if (yOffset < size.height) {
            drawLine(color = Color(0xFFFF00FF).copy(alpha = 1f - (i/10f)), start = Offset(0f, yOffset), end = Offset(size.width, yOffset), strokeWidth = 5f)
        }
    }
    // Grid vertical lines
    val centerLine = size.width / 2
    for (i in -5..5) {
        val xOffset = centerLine + (i * size.width * 0.2f) + (bands.treble * i * 30f)
        drawLine(color = Color(0xFFFF00FF).copy(alpha = 0.5f), start = Offset(centerLine, horizonY), end = Offset(xOffset, size.height), strokeWidth = 5f)
    }
    
    // Sun
    drawCircle(color = Color(0xFFFF5500), radius = 200f + (bands.mid * 100f), center = Offset(centerLine, horizonY))
    
    if (strobeAlpha > 0f) {
        drawRect(color = Color(0xFFFF00FF).copy(alpha = strobeAlpha * 0.2f), size = size)
    }

    // Laser particles
    particles.forEach { p ->
        if (p.alpha > 0f) {
            drawLine(color = Color.Cyan.copy(alpha = p.alpha), start = p.position, end = Offset(p.position.x, p.position.y + 80f), strokeWidth = 10f)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val viewModel: VisualizerViewModel by viewModels()
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.addAll(listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES))
        }
        if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(perms.toTypedArray())
        }

        setContent {
            MaterialTheme {
                Surface(color = Color.Black) {
                    val uiMode by viewModel.uiMode.collectAsState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        AdvancedVisualizerScreen(fftBandsFlow = viewModel.renderBandsFlow)
                        
                        if (uiMode == "IDLE") {
                            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { viewModel.startHosting() }, modifier = Modifier.padding(8.dp)) { Text("HOST (Listen to Mic)") }
                                Button(onClick = { viewModel.startJoining() }, modifier = Modifier.padding(8.dp)) { Text("JOIN (Sync to Host)") }
                            }
                        } else {
                            Text("Status: $uiMode", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(32.dp))
                            
                            // Only Host can change visual modes
                            if (uiMode == "HOSTING") {
                                IconButton(
                                    onClick = { viewModel.cycleVisualMode() },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp).size(64.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Change Visuals", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
