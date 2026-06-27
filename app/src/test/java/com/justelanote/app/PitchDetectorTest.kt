package com.justelanote.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchDetectorTest {

    private val sampleRate = 44100

    private fun sine(frequency: Double, durationMs: Int = 500): ShortArray {
        val n = sampleRate * durationMs / 1000
        return ShortArray(n) { i ->
            (sin(2.0 * PI * frequency * i / sampleRate) * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun assertDetects(frequency: Double) {
        val detected = PitchDetector.detectPitch(sine(frequency), sampleRate)
        assertNotNull("Aucune frequence detectee pour $frequency Hz", detected)
        // Tolerance 2 % (les graves sont les plus exigeants).
        assertEquals(frequency, detected!!, frequency * 0.02)
    }

    @Test
    fun detecteMi1_basse_5_cordes() = assertDetects(41.2)   // Mi1, mi grave d'une basse

    @Test
    fun detecteSi0_basse_5_cordes() = assertDetects(30.87)  // Si0, corde la plus grave d'une basse 5 cordes

    @Test
    fun detecteMi2_guitare() = assertDetects(82.41)         // Mi2, mi grave d'une guitare

    @Test
    fun detecteLa2() = assertDetects(110.0)

    @Test
    fun detecteLa4() = assertDetects(440.0)
}
