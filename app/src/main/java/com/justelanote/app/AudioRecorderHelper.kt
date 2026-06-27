package com.justelanote.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRecorderHelper {

    private val sampleRate = 44100

    fun sampleRate() = sampleRate

    /**
     * Enregistre durationMs millisecondes depuis le micro.
     * Necessite la permission RECORD_AUDIO deja accordee avant l'appel.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(durationMs: Int = 2500): ShortArray = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, sampleRate) * 2
        val numSamples = (durationMs / 1000.0 * sampleRate).toInt()
        val output = ShortArray(numSamples)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        try {
            audioRecord.startRecording()
            var totalRead = 0
            while (totalRead < numSamples) {
                val read = audioRecord.read(output, totalRead, numSamples - totalRead)
                if (read > 0) totalRead += read else break
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        output
    }
}
