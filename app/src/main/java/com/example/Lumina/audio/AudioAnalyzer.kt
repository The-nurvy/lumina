package com.example.Lumina.audio

import android.annotation.SuppressLint
import com.example.Lumina.models.AudioBands
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.util.fft.FFT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioAnalyzer {
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

                    val bands = extractFrequencyBands(amplitudes)
                    _bandsFlow.value = bands.copy(visualMode = currentVisualMode)
                    return true
                }

                override fun processingFinished() {}
            })
            Thread(dispatcher, "Audio-FFT-Thread").start()
        } catch (e: Exception) {
            // Handle emulator/no-mic crash silently
        }
    }

    fun stopListening() {
        dispatcher?.stop()
        dispatcher = null
    }

    private fun extractFrequencyBands(amplitudes: FloatArray): AudioBands {
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

        return AudioBands(
            bass = (bassEnergy / 10f).coerceIn(0f, 1f),
            mid = (midEnergy / 30f).coerceIn(0f, 1f),
            treble = (trebleEnergy / 30f).coerceIn(0f, 1f),
            visualMode = 0
        )
    }
}
