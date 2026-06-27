package com.justelanote.app

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class MusicalNote(val name: String, val frequency: Double, val midi: Int)

object NoteLibrary {

    private val noteNames = listOf(
        "Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"
    )

    /**
     * Genere les notes de Do2 (~65 Hz) a Do6 (~1047 Hz), soit MIDI 36 a 84.
     * Couvre confortablement la quasi-totalite des tessitures vocales.
     */
    val notes: List<MusicalNote> by lazy {
        (36..84).map { midi ->
            val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
            val octave = midi / 12 - 1
            val name = "${noteNames[midi % 12]}$octave"
            MusicalNote(name, freq, midi)
        }
    }

    val defaultNote: MusicalNote
        get() = notes.first { it.name == "La4" }

    fun closestNote(frequency: Double): MusicalNote =
        notes.minByOrNull { abs(it.frequency - frequency) }!!

    /** Ecart en cents entre une frequence detectee et une note cible (positif = trop haut). */
    fun centsOff(frequency: Double, target: MusicalNote): Double =
        1200.0 * ln(frequency / target.frequency) / ln(2.0)

    /**
     * Ecart en cents par rapport a la note cible, en ignorant l'octave : le
     * resultat est ramene dans [-600, +600]. Chanter le bon nom de note dans
     * n'importe quelle octave (voix grave ou aigue) est ainsi compte juste.
     */
    fun centsOffIgnoringOctave(frequency: Double, target: MusicalNote): Double {
        val raw = centsOff(frequency, target)
        return raw - 1200.0 * (raw / 1200.0).roundToInt()
    }

    /**
     * Nombre d'octaves d'ecart entre la frequence detectee et la cible
     * (positif = plus haut, negatif = plus bas, 0 = meme octave).
     */
    fun octaveShift(frequency: Double, target: MusicalNote): Int =
        (centsOff(frequency, target) / 1200.0).roundToInt()
}
