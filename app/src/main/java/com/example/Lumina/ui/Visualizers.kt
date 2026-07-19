package com.example.Lumina.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.Lumina.models.AudioBands
import com.example.Lumina.models.Particle

fun DrawScope.drawClassicMode(bands: AudioBands, strobeAlpha: Float, particles: List<Particle>) {
    drawCircle(
        color = Color.Green.copy(alpha = bands.mid * 0.3f),
        radius = (size.width * 0.4f) * bands.mid,
        center = center,
        style = Stroke(width = 40f * bands.mid)
    )
    drawCircle(
        color = Color.Red.copy(alpha = bands.bass * 0.5f),
        radius = (size.width * 0.8f) * bands.bass,
        center = center,
        style = Stroke(width = 80f * bands.bass)
    )

    if (strobeAlpha > 0f) {
        drawRect(color = Color.Magenta.copy(alpha = strobeAlpha * 0.4f), size = size)
        if (strobeAlpha > 0.8f) {
            drawRect(color = Color.White.copy(alpha = strobeAlpha * 0.5f), size = size)
        }
    }

    particles.forEach { p ->
        if (p.alpha > 0f) {
            drawCircle(color = Color.Cyan.copy(alpha = p.alpha), radius = 12f, center = p.position)
            drawCircle(color = Color.White.copy(alpha = p.alpha * 0.2f), radius = 24f, center = p.position)
        }
    }
}

fun DrawScope.drawGeoBurstMode(bands: AudioBands, strobeAlpha: Float, particles: List<Particle>, rotation: Float) {
    val trianglePath = Path().apply {
        val w = size.width * 0.5f
        moveTo(center.x, center.y - w)
        lineTo(center.x + w * 0.866f, center.y + w * 0.5f)
        lineTo(center.x - w * 0.866f, center.y + w * 0.5f)
        close()
    }

    rotate(degrees = rotation, pivot = center) {
        drawPath(
            path = trianglePath,
            color = Color.Yellow.copy(alpha = bands.mid * 0.4f),
            style = Stroke(width = 20f + (60f * bands.bass))
        )
    }
    rotate(degrees = -rotation * 1.5f, pivot = center) {
        drawPath(
            path = trianglePath,
            color = Color.Red.copy(alpha = bands.bass * 0.6f),
            style = Stroke(width = 10f)
        )
    }

    if (strobeAlpha > 0f) {
        drawRect(color = Color.Yellow.copy(alpha = strobeAlpha * 0.3f), size = size)
    }

    particles.forEach { p ->
        if (p.alpha > 0f) {
            drawRect(
                color = Color(0xFF00FFCC).copy(alpha = p.alpha),
                topLeft = androidx.compose.ui.geometry.Offset(p.position.x - 10f, p.position.y - 10f),
                size = androidx.compose.ui.geometry.Size(20f, 20f)
            )
        }
    }
}

fun DrawScope.drawRetroWaveMode(bands: AudioBands, strobeAlpha: Float, particles: List<Particle>) {
    val horizonY = size.height * 0.6f

    for (i in 0..10) {
        val yOffset = horizonY + (i * i * 15f) + (bands.bass * i * 20f)
        if (yOffset < size.height) {
            drawLine(
                color = Color(0xFFFF00FF).copy(alpha = 1f - (i / 10f)),
                start = androidx.compose.ui.geometry.Offset(0f, yOffset),
                end = androidx.compose.ui.geometry.Offset(size.width, yOffset),
                strokeWidth = 5f
            )
        }
    }

    val centerLine = size.width / 2
    for (i in -5..5) {
        val xOffset = centerLine + (i * size.width * 0.2f) + (bands.treble * i * 30f)
        drawLine(
            color = Color(0xFFFF00FF).copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(centerLine, horizonY),
            end = androidx.compose.ui.geometry.Offset(xOffset, size.height),
            strokeWidth = 5f
        )
    }

    drawCircle(
        color = Color(0xFFFF5500),
        radius = 200f + (bands.mid * 100f),
        center = androidx.compose.ui.geometry.Offset(centerLine, horizonY)
    )

    if (strobeAlpha > 0f) {
        drawRect(color = Color(0xFFFF00FF).copy(alpha = strobeAlpha * 0.2f), size = size)
    }

    particles.forEach { p ->
        if (p.alpha > 0f) {
            drawLine(
                color = Color.Cyan.copy(alpha = p.alpha),
                start = p.position,
                end = androidx.compose.ui.geometry.Offset(p.position.x, p.position.y + 80f),
                strokeWidth = 10f
            )
        }
    }
}
