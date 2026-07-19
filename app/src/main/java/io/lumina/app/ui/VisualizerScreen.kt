package io.lumina.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import io.lumina.app.audio.AudioBands
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

data class Particle(var position: Offset, val velocity: Offset, val initialAlpha: Float, var alpha: Float, val lifeTimeMs: Int, var elapsedMs: Int = 0)

@Composable
fun AdvancedVisualizerScreen(fftBandsFlow: StateFlow<AudioBands>) {
    val bands by fftBandsFlow.collectAsState(AudioBands(0f, 0f, 0f))
    var strobeAlpha by remember { mutableStateOf(0f) }
    val particleList = remember { mutableStateListOf<Particle>() }
    var lastFrameNanos by remember { mutableStateOf(System.nanoTime()) }

    LaunchedEffect(key1 = true) {
        while (true) {
            withFrameNanos { currentFrameNanos ->
                val deltaTimeSec = (currentFrameNanos - lastFrameNanos) / 1_000_000_000f
                val elapsedFrameMs = ((currentFrameNanos - lastFrameNanos) / 1_000_000f).toInt()
                lastFrameNanos = currentFrameNanos

                if (bands.bass > 0.85f) strobeAlpha = 1f
                if (bands.treble > 0.5f) {
                    repeat(5) {
                        particleList.add(Particle(
                            position = Offset(Random.nextInt(0, 1080).toFloat(), Random.nextInt(0, 1920).toFloat()),
                            velocity = Offset(Random.nextInt(-150, 150).toFloat(), Random.nextInt(50, 300).toFloat()),
                            initialAlpha = (Random.nextInt(5, 10) / 10f), alpha = 1f, lifeTimeMs = Random.nextInt(700, 1500)
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
        drawCircle(color = Color.Green.copy(alpha = bands.mid * 0.3f), radius = (size.width * 0.4f) * bands.mid, center = center, style = Stroke(width = 40f * bands.mid))
        if (strobeAlpha > 0f) {
            drawRect(color = Color.Magenta.copy(alpha = strobeAlpha * 0.4f), size = size)
            if (strobeAlpha > 0.8f) drawRect(color = Color.White.copy(alpha = strobeAlpha * 0.5f), size = size)
        }
        particleList.forEach { p ->
            if (p.alpha > 0f) {
                drawCircle(color = Color.Cyan.copy(alpha = p.alpha), radius = 12f, center = p.position)
                drawCircle(color = Color.White.copy(alpha = p.alpha * 0.2f), radius = 24f, center = p.position)
            }
        }
    }
}
