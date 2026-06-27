package com.justelanote.app

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

class NotePlayer {

    private var audioTrack: AudioTrack? = null

    fun playNote(
        frequencyHz: Double,
        instrument: Instrument = Instruments.default,
        durationMs: Int = 1500
    ) {
        audioTrack?.release()

        val sampleRate = 44100
        val numSamples = (durationMs / 1000.0 * sampleRate).toInt()
        val samples = DoubleArray(numSamples)

        // Synthese additive : on ne garde que les partiels sous la frequence de
        // Nyquist pour eviter le repliement (aliasing) sur les notes aigues.
        val nyquist = sampleRate / 2.0
        val partials = instrument.harmonics
            .mapIndexed { index, amp -> (index + 1) to amp }
            .filter { (harmonic, amp) -> amp != 0.0 && harmonic * frequencyHz < nyquist }
        val ampSum = partials.sumOf { abs(it.second) }.coerceAtLeast(1e-9)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var value = 0.0
            for ((harmonic, amp) in partials) {
                value += amp * sin(2.0 * PI * harmonic * frequencyHz * t)
            }
            samples[i] = value / ampSum // normalise pour eviter la saturation
        }

        applyEnvelope(samples, sampleRate, instrument)

        val buffer = ShortArray(numSamples) {
            (samples[it] * Short.MAX_VALUE * 0.85).toInt().toShort()
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

    /**
     * Applique une attaque (montee progressive), une eventuelle decroissance
     * exponentielle, et un court fondu de sortie pour eviter les "clics".
     */
    private fun applyEnvelope(samples: DoubleArray, sampleRate: Int, instrument: Instrument) {
        val n = samples.size
        if (n == 0) return
        val attack = (sampleRate * instrument.attackMs / 1000.0).toInt().coerceIn(1, n)
        val release = (sampleRate * 0.02).toInt().coerceIn(1, n)
        val tau = n / 3.5 // constante de temps de la decroissance
        for (i in 0 until n) {
            var gain = 1.0
            if (i < attack) gain *= i.toDouble() / attack
            if (instrument.decay) gain *= exp(-i.toDouble() / tau)
            val toEnd = n - 1 - i
            if (toEnd < release) gain *= toEnd.toDouble() / release
            samples[i] *= gain
        }
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
    }
}
