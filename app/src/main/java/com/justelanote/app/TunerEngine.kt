package com.justelanote.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Analyse de hauteur en temps reel pour l'accordeur : lit le micro en continu
 * et estime la frequence fondamentale (YIN, via [PitchDetector]) sur des
 * fenetres glissantes. Necessite la permission RECORD_AUDIO deja accordee.
 *
 * [run] boucle jusqu'a l'annulation de la coroutine appelante (qui declenche
 * l'arret et la liberation du micro).
 */
class TunerEngine {

    private val sampleRate = 44100
    private val readSize = 4096 // ~93 ms : ~10 mises a jour par seconde

    @SuppressLint("MissingPermission")
    suspend fun run(onResult: (frequency: Double?, level: Float) -> Unit) = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, readSize * 2)
        )
        val buffer = ShortArray(readSize)
        try {
            record.startRecording()
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val window = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val freq = PitchDetector.detectPitch(window, sampleRate)
                    val level = levelOf(window)
                    withContext(Dispatchers.Main) { onResult(freq, level) }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    /** Niveau d'entree pour le vu-metre : RMS converti en echelle dB normalisee
     *  (-60 dBFS -> 0, 0 dBFS -> 1). */
    private fun levelOf(buffer: ShortArray): Float {
        if (buffer.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in buffer) {
            val normalized = sample / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / buffer.size)
        if (rms <= 0.0) return 0f
        val db = 20.0 * log10(rms)
        return ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    }
}
