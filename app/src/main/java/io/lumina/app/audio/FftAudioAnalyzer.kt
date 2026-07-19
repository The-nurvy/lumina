package io.lumina.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

object SimpleFFT {
    fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tx = x[i]; x[i] = x[j]; x[j] = tx
                val ty = y[i]; y[i] = y[j]; y[j] = ty
            }
            var m = n / 2
            while (m <= j) { j -= m; m /= 2 }
            j += m
        }
        var l = 1
        while (l < n) {
            val l2 = l * 2
            val arg = -Math.PI / l
            val c = Math.cos(arg).toFloat()
            val s = Math.sin(arg).toFloat()
            for (j2 in 0 until l) {
                for (i in j2 until n step l2) {
                    val i1 = i + l
                    val t1 = c * x[i1] - s * y[i1]
                    val t2 = c * y[i1] + s * x[i1]
                    x[i1] = x[i] - t1
                    y[i1] = y[i] - t2
                    x[i] += t1
                    y[i] += t2
                }
            }
            l = l2
        }
    }
}

class FftAudioAnalyzer {
    private val _bandsFlow = MutableStateFlow(AudioBands(0f, 0f, 0f))
    val bandsFlow: StateFlow<AudioBands> = _bandsFlow
    private var isRecording = false
    private val sampleRate = 44100
    private val fftSize = 1024

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (isRecording) return
        isRecording = true
        Thread {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize.coerceAtLeast(fftSize * 2))
            val shortBuffer = ShortArray(fftSize)
            val real = FloatArray(fftSize)
            val imag = FloatArray(fftSize)
            audioRecord.startRecording()
            
            while (isRecording) {
                if (audioRecord.read(shortBuffer, 0, fftSize) == fftSize) {
                    for (i in 0 until fftSize) {
                        real[i] = (shortBuffer[i] / 32768f) * (0.5f * (1f - Math.cos(2.0 * Math.PI * i / (fftSize - 1)).toFloat()))
                        imag[i] = 0f
                    }
                    SimpleFFT.fft(real, imag)
                    var bassEnergy = 0f; var midEnergy = 0f; var trebleEnergy = 0f
                    for (i in 0 until fftSize / 2) {
                        val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
                        val freq = (i * sampleRate) / fftSize.toFloat()
                        when {
                            freq in 20f..250f -> bassEnergy += mag
                            freq in 250f..4000f -> midEnergy += mag
                            freq in 4000f..16000f -> trebleEnergy += mag
                        }
                    }
                    _bandsFlow.value = AudioBands(
                        (bassEnergy / 10f).coerceIn(0f, 1f),
                        (midEnergy / 30f).coerceIn(0f, 1f),
                        (trebleEnergy / 30f).coerceIn(0f, 1f)
                    )
                }
            }
            audioRecord.release()
        }.start()
    }
    fun stopListening() { isRecording = false }
}
