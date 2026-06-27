package com.justelanote.app

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

data class MusicalNote(val name: String, val frequency: Double)

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
            MusicalNote(name, freq)
        }
    }

    val defaultNote: MusicalNote
        get() = notes.first { it.name == "La4" }

    fun closestNote(frequency: Double): MusicalNote =
        notes.minByOrNull { abs(it.frequency - frequency) }!!

    /** Ecart en cents entre une frequence detectee et une note cible (positif = trop haut). */
    fun centsOff(frequency: Double, target: MusicalNote): Double =
        1200.0 * ln(frequency / target.frequency) / ln(2.0)
}
