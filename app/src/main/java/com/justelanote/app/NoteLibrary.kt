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

    private fun noteAt(midi: Int): MusicalNote {
        val freq = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val octave = midi / 12 - 1
        return MusicalNote("${noteNames[midi % 12]}$octave", freq, midi)
    }

    /**
     * Plage complete (MIDI 23 a 96, Si0 ~31 Hz a Do7) utilisee pour la detection
     * et l'accordeur : permet d'identifier aussi les cordes graves des instruments.
     */
    val notes: List<MusicalNote> by lazy { (23..96).map { noteAt(it) } }

    /**
     * Sous-ensemble affiche sur le clavier de piano : Do2 (~65 Hz) a Do6 (~1047 Hz),
     * qui couvre confortablement les tessitures vocales.
     */
    val keyboardNotes: List<MusicalNote> by lazy { (36..84).map { noteAt(it) } }

    val defaultNote: MusicalNote
        get() = keyboardNotes.first { it.name == "La4" }

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
