package com.example.Lumina.ui

import androidx.compose.animation.core.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.example.Lumina.models.AudioBands
import com.example.Lumina.models.Particle
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

@Composable
fun VisualizerScreen(fftBandsFlow: StateFlow<AudioBands>) {
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

                if (bands.bass > 0.85f) strobeAlpha = 1f

                if (bands.treble > 0.5f) {
                    generateParticles(bands, particleList)
                }

                strobeAlpha = (strobeAlpha - (deltaTimeSec * 2.0f)).coerceAtLeast(0f)
                updateParticles(particleList, deltaTimeSec, elapsedFrameMs)
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        when (bands.visualMode) {
            0 -> drawClassicMode(bands, strobeAlpha, particleList)
            1 -> drawGeoBurstMode(bands, strobeAlpha, particleList, globalRotation)
            2 -> drawRetroWaveMode(bands, strobeAlpha, particleList)
        }
    }
}

private fun generateParticles(bands: AudioBands, particleList: MutableList<Particle>) {
    val count = if (bands.visualMode == 2) 2 else 5
    repeat(count) {
        val isSquare = bands.visualMode == 1
        val vx = if (bands.visualMode == 2) 0f else Random.nextInt(-150, 150).toFloat()
        val vy = if (bands.visualMode == 2) Random.nextInt(400, 800).toFloat() else Random.nextInt(50, 300).toFloat()

        particleList.add(
            Particle(
                position = Offset(
                    Random.nextInt(0, 1080).toFloat(),
                    if (bands.visualMode == 2) 0f else Random.nextInt(0, 1920).toFloat()
                ),
                velocity = Offset(vx, vy),
                initialAlpha = Random.nextInt(5, 10) / 10f,
                alpha = 1f,
                lifeTimeMs = Random.nextInt(700, 1500),
                isSquare = isSquare
            )
        )
    }
}

private fun updateParticles(
    particleList: MutableList<Particle>,
    deltaTimeSec: Float,
    elapsedFrameMs: Int
) {
    val iterator = particleList.iterator()
    while (iterator.hasNext()) {
        val p = iterator.next()
        p.position += p.velocity * deltaTimeSec
        p.elapsedMs += elapsedFrameMs
        p.alpha = (p.initialAlpha * (1f - (p.elapsedMs.toFloat() / p.lifeTimeMs))).coerceIn(0f, 1f)
        if (p.elapsedMs >= p.lifeTimeMs || p.alpha <= 0.01f) {
            iterator.remove()
        }
    }
}
