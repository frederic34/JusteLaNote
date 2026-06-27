package com.justelanote.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Joue des notes en polyphonie : chaque appel lance une voix independante qui
 * sonne sa duree puis se libere, sans couper les notes deja en cours. Le
 * diapason est synthetise (sinus pur) ; les autres instruments passent par le
 * synthetiseur General MIDI integre d'Android.
 */
class NotePlayer(context: Context) {

    private val cacheDir = context.applicationContext.cacheDir
    private val handler = Handler(Looper.getMainLooper())
    private val activeTracks = mutableListOf<AudioTrack>()
    private val activePlayers = mutableListOf<MediaPlayer>()
    private var voiceCounter = 0

    fun playNote(note: MusicalNote, instrument: Instrument, durationMs: Int = 1500) {
        val program = instrument.gmProgram
        if (program == null) playSine(note.frequency, durationMs) else playMidi(note.midi, program)
    }

    private fun playMidi(midiNote: Int, program: Int) {
        val file = File(cacheDir, "voice_${voiceCounter++}.mid")
        file.writeBytes(MidiBuilder.singleNote(midiNote, program))

        val mp = MediaPlayer()
        activePlayers.add(mp)
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { it.release(); activePlayers.remove(it); file.delete() }
        mp.setOnErrorListener { m, _, _ -> m.release(); activePlayers.remove(m); file.delete(); true }
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepareAsync()
        } catch (_: Exception) {
            mp.release(); activePlayers.remove(mp); file.delete()
        }
    }

    private fun playSine(frequencyHz: Double, durationMs: Int) {
        val sampleRate = 44100
        val numSamples = (durationMs / 1000.0 * sampleRate).toInt()
        val buffer = ShortArray(numSamples)
        // Fondu d'entree/sortie pour eviter les "clics".
        val fade = (sampleRate * 0.015).toInt().coerceAtLeast(1)
        for (i in 0 until numSamples) {
            val sample = sin(2.0 * PI * i * frequencyHz / sampleRate)
            val env = when {
                i < fade -> i.toDouble() / fade
                i > numSamples - 1 - fade -> (numSamples - 1 - i).toDouble() / fade
                else -> 1.0
            }
            buffer[i] = (sample * env * Short.MAX_VALUE * 0.8).toInt().toShort()
        }

        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        track.write(buffer, 0, buffer.size)
        activeTracks.add(track)
        track.play()
        // Libere la voix une fois la note terminee.
        handler.postDelayed({
            runCatching { track.release() }
            activeTracks.remove(track)
        }, durationMs.toLong() + 100)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        activeTracks.forEach { runCatching { it.release() } }
        activeTracks.clear()
        activePlayers.forEach { runCatching { it.reset(); it.release() } }
        activePlayers.clear()
    }
}
