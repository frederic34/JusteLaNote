package com.justelanote.app

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

class NotePlayer {

    private var audioTrack: AudioTrack? = null

    fun playNote(frequencyHz: Double, durationMs: Int = 1500) {
        audioTrack?.release()

        val sampleRate = 44100
        val numSamples = (durationMs / 1000.0 * sampleRate).toInt()
        val samples = DoubleArray(numSamples)
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            samples[i] = sin(2.0 * PI * i * frequencyHz / sampleRate)
        }

        // Fondu d'entree/sortie pour eviter les "clics" audio
        val fadeLen = (sampleRate * 0.015).toInt().coerceAtLeast(1)
        for (i in 0 until fadeLen) {
            val factor = i.toDouble() / fadeLen
            samples[i] *= factor
            samples[numSamples - 1 - i] *= factor
        }

        for (i in 0 until numSamples) {
            buffer[i] = (samples[i] * Short.MAX_VALUE * 0.8).toInt().toShort()
        }

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        audioTrack?.write(buffer, 0, buffer.size)
        audioTrack?.play()
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
    }
}
