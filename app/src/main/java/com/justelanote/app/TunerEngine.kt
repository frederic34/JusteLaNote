package com.justelanote.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    suspend fun run(onPitch: (Double?) -> Unit) = withContext(Dispatchers.IO) {
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
                    withContext(Dispatchers.Main) { onPitch(freq) }
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }
}
