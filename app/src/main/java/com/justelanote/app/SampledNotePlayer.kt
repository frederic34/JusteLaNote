// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.pow

/**
 * Joue des notes a partir d'instruments **echantillonnes** (VSCO 2 Community
 * Edition, licence CC0 : https://versilian-studios.com/vsco-community/).
 *
 * Principe : on ne stocke pas un WAV par note, mais quelques notes de reference
 * par instrument ; pour jouer une hauteur quelconque on prend l'echantillon le
 * plus proche et on le **transpose** par reechantillonnage (interpolation
 * lineaire), exactement comme un sampler. Polyphonie, fondus anti-clic et
 * cycle de vie calques sur [NotePlayer].
 *
 * Disposition attendue des assets (un dossier par instrument, un WAV par note,
 * nomme d'apres son numero MIDI) :
 *
 *     assets/samples/violin/48.wav   (Do2)
 *     assets/samples/violin/60.wav   (Do3)
 *     assets/samples/violin/72.wav   (Do4)
 *     assets/samples/piano/...
 *
 * Plus les notes de reference sont rapprochees (ex. tous les 3 demi-tons), plus
 * le timbre reste naturel ; plus elles sont espacees, plus l'APK est leger.
 * Les WAV PCM 8/16/24 bits et float 32 bits, mono ou stereo, sont supportes ;
 * le stereo est replie en mono.
 */
class SampledNotePlayer(context: Context, private val baseDir: String = "samples") {

    private val assets = context.applicationContext.assets
    private val handler = Handler(Looper.getMainLooper())
    private val activeTracks = mutableListOf<AudioTrack>()

    /** instrument -> numeros MIDI echantillonnes disponibles, tries. */
    private val index: Map<String, List<Int>> = buildIndex()

    /** Cache des echantillons decodes, cle "instrument/midi". */
    private val cache = HashMap<String, Sample>()

    /** Echantillon decode en mono, normalise en PCM 16 bits. */
    private class Sample(val data: ShortArray, val sampleRate: Int, val midi: Int)

    /** Instruments detectes dans [baseDir]. */
    val instruments: List<String> get() = index.keys.sorted()

    /** Vrai si [name] dispose d'au moins un echantillon. */
    fun hasInstrument(name: String): Boolean = index.containsKey(name)

    private fun buildIndex(): Map<String, List<Int>> {
        val map = LinkedHashMap<String, List<Int>>()
        val dirs = runCatching { assets.list(baseDir) }.getOrNull() ?: return map
        for (dir in dirs) {
            val files = runCatching { assets.list("$baseDir/$dir") }.getOrNull() ?: continue
            val midis = files
                .filter { it.endsWith(".wav", ignoreCase = true) }
                .mapNotNull { it.dropLast(4).toIntOrNull() }
                .sorted()
            if (midis.isNotEmpty()) map[dir] = midis
        }
        return map
    }

    /**
     * Joue [note] avec l'[instrument] (nom de dossier dans [baseDir]) pour
     * [durationMs] millisecondes. Sans echantillon disponible, ne fait rien.
     */
    fun playNote(note: MusicalNote, instrument: String, durationMs: Int = 1500) {
        val midis = index[instrument] ?: return
        val nearest = midis.minByOrNull { abs(it - note.midi) } ?: return
        val sample = loadSample(instrument, nearest) ?: return
        val buffer = render(sample, note.frequency, durationMs) ?: return
        play(buffer)
    }

    // Reechantillonne l'echantillon source vers 44100 Hz a la hauteur cible.
    private fun render(sample: Sample, targetFreqHz: Double, durationMs: Int): ShortArray? {
        val sourceFreq = 440.0 * 2.0.pow((sample.midi - 69) / 12.0)
        // Pas de lecture dans la source : conversion de frequence d'echantillonnage
        // (srcRate/OUT_RATE) combinee a la transposition (target/source).
        val step = (sample.sampleRate.toDouble() / OUT_RATE) * (targetFreqHz / sourceFreq)
        if (step <= 0.0) return null

        val requested = (durationMs / 1000.0 * OUT_RATE).toInt()
        val available = ((sample.data.size - 1) / step).toInt()
        val count = minOf(requested, available)
        if (count <= 1) return null

        val out = ShortArray(count)
        val fadeIn = (OUT_RATE * 0.005).toInt().coerceAtLeast(1)   // ~5 ms, anti-clic au depart
        val fadeOut = (OUT_RATE * 0.030).toInt().coerceAtLeast(1)  // ~30 ms, coupe douce a la fin
        var pos = 0.0
        for (i in 0 until count) {
            val idx = pos.toInt()
            val frac = pos - idx
            val s0 = sample.data[idx].toDouble()
            val s1 = if (idx + 1 < sample.data.size) sample.data[idx + 1].toDouble() else s0
            val interp = s0 + (s1 - s0) * frac
            val env = when {
                i < fadeIn -> i.toDouble() / fadeIn
                i > count - 1 - fadeOut -> (count - 1 - i).toDouble() / fadeOut
                else -> 1.0
            }
            out[i] = (interp * env * 0.8).toInt().coerceIn(-32768, 32767).toShort()
            pos += step
        }
        return out
    }

    private fun play(buffer: ShortArray) {
        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            OUT_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        track.write(buffer, 0, buffer.size)
        activeTracks.add(track)
        track.play()
        val durationMs = buffer.size * 1000L / OUT_RATE
        handler.postDelayed({
            runCatching { track.release() }
            activeTracks.remove(track)
        }, durationMs + 100)
    }

    private fun loadSample(instrument: String, midi: Int): Sample? {
        val key = "$instrument/$midi"
        cache[key]?.let { return it }
        val bytes = runCatching {
            assets.open("$baseDir/$key.wav").use { it.readBytes() }
        }.getOrNull() ?: return null
        return decodeWav(bytes, midi)?.also { cache[key] = it }
    }

    // Decodeur WAV minimal : parcourt les chunks RIFF, lit 'fmt ' et 'data',
    // replie en mono et normalise en PCM 16 bits.
    private fun decodeWav(b: ByteArray, midi: Int): Sample? {
        if (b.size < 12) return null
        fun u16(o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        fun u32(o: Int) = (b[o].toLong() and 0xFF) or
            ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or
            ((b[o + 3].toLong() and 0xFF) shl 24)

        if (String(b, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(b, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) return null

        var format = 1; var channels = 1; var rate = OUT_RATE; var bits = 16
        var dataOffset = -1; var dataLen = 0
        var pos = 12
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            val size = u32(pos + 4).toInt()
            val body = pos + 8
            when (id) {
                "fmt " -> {
                    format = u16(body); channels = u16(body + 2)
                    rate = u32(body + 4).toInt(); bits = u16(body + 14)
                }
                "data" -> { dataOffset = body; dataLen = size }
            }
            pos = body + size + (size and 1) // chunks alignes sur un mot
        }
        if (dataOffset < 0 || channels < 1) return null

        val bytesPerSample = bits / 8
        if (bytesPerSample == 0) return null
        val frameSize = bytesPerSample * channels
        val frames = minOf(dataLen, b.size - dataOffset) / frameSize
        if (frames <= 0) return null

        val out = ShortArray(frames)
        for (f in 0 until frames) {
            var acc = 0.0
            for (c in 0 until channels) {
                val i = dataOffset + f * frameSize + c * bytesPerSample
                acc += when (bits) {
                    8 -> ((b[i].toInt() and 0xFF) - 128) * 256.0
                    16 -> ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toDouble()
                    24 -> {
                        var v = (b[i].toInt() and 0xFF) or
                            ((b[i + 1].toInt() and 0xFF) shl 8) or
                            ((b[i + 2].toInt() and 0xFF) shl 16)
                        if (v and 0x800000 != 0) v = v or -0x1000000 // extension de signe
                        v / 256.0 // 24 -> 16 bits
                    }
                    32 -> {
                        val raw = u32(i).toInt()
                        if (format == 3) Float.fromBits(raw) * 32767.0 // float
                        else raw / 65536.0 // PCM 32 -> 16 bits
                    }
                    else -> return null
                }
            }
            out[f] = (acc / channels).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Sample(out, rate, midi)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        activeTracks.forEach { runCatching { it.release() } }
        activeTracks.clear()
        cache.clear()
    }

    private companion object {
        const val OUT_RATE = 44100
    }
}
