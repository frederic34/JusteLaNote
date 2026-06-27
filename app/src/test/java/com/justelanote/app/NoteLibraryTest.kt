package com.justelanote.app

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class NoteLibraryTest {

    private val la4 = NoteLibrary.notes.first { it.name == "La4" } // 440 Hz

    @Test
    fun memeNote_memeOctave_estJuste() {
        assertEquals(0.0, NoteLibrary.centsOffIgnoringOctave(440.0, la4), 1.0)
    }

    @Test
    fun memeNote_uneOctavePlusBas_estJuste() {
        // La3 = 220 Hz : une voix grave chantant le bon "La" doit etre comptee juste.
        assertEquals(0.0, NoteLibrary.centsOffIgnoringOctave(220.0, la4), 1.0)
    }

    @Test
    fun memeNote_deuxOctavesPlusBas_estJuste() {
        // La2 = 110 Hz (voix de basse).
        assertEquals(0.0, NoteLibrary.centsOffIgnoringOctave(110.0, la4), 1.0)
    }

    @Test
    fun unDemiTonTropHaut_ignoreLOctave() {
        // La#3 = La3 * 2^(1/12) : un demi-ton (100 cents) trop haut, peu importe l'octave.
        val laDiese3 = 220.0 * 2.0.pow(1.0 / 12.0)
        assertEquals(100.0, NoteLibrary.centsOffIgnoringOctave(laDiese3, la4), 1.0)
    }

    @Test
    fun unDemiTonTropBas_ignoreLOctave() {
        // Sol#3 = La3 / 2^(1/12) : un demi-ton trop bas.
        val solDiese3 = 220.0 / 2.0.pow(1.0 / 12.0)
        assertEquals(-100.0, NoteLibrary.centsOffIgnoringOctave(solDiese3, la4), 1.0)
    }

    @Test
    fun decalageDOctave() {
        assertEquals(0, NoteLibrary.octaveShift(440.0, la4))   // La4
        assertEquals(-1, NoteLibrary.octaveShift(220.0, la4))  // La3
        assertEquals(-2, NoteLibrary.octaveShift(110.0, la4))  // La2 (basse)
        assertEquals(1, NoteLibrary.octaveShift(880.0, la4))   // La5
    }
}
