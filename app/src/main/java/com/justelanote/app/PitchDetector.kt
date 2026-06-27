package com.justelanote.app

import kotlin.math.abs

/**
 * Detection de hauteur vocale par algorithme YIN, applique sur des fenetres
 * glissantes pour plus de robustesse (on prend la mediane des detections).
 *
 * Reference : De Cheveigne & Kawahara, "YIN, a fundamental frequency
 * estimator for speech and music", 2002.
 */
object PitchDetector {

    private const val MIN_FREQ = 60.0    // Hz - couvre les voix les plus graves
    private const val MAX_FREQ = 1100.0  // Hz - couvre les voix les plus aigues
    private const val WINDOW_SIZE = 2048
    private const val HOP_SIZE = 2048    // pas de chevauchement = calcul rapide
    private const val THRESHOLD = 0.15

    fun detectPitch(audioBuffer: ShortArray, sampleRate: Int): Double? {
        val detections = mutableListOf<Double>()
        var start = 0
        while (start + WINDOW_SIZE <= audioBuffer.size) {
            val window = DoubleArray(WINDOW_SIZE) { audioBuffer[start + it] / 32768.0 }
            yin(window, sampleRate)?.let { detections.add(it) }
            start += HOP_SIZE
        }
        if (detections.isEmpty()) return null
        val sorted = detections.sorted()
        return sorted[sorted.size / 2] // mediane : plus robuste qu'une moyenne
    }

    private fun yin(buffer: DoubleArray, sampleRate: Int): Double? {
        val maxTau = (sampleRate / MIN_FREQ).toInt().coerceAtMost(buffer.size - 1)
        val minTau = (sampleRate / MAX_FREQ).toInt().coerceAtLeast(2)
        if (maxTau <= minTau) return null

        // Fonction de difference cumulee
        val diff = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            val limit = buffer.size - tau
            for (i in 0 until limit) {
                val delta = buffer[i] - buffer[i + tau]
                sum += delta * delta
            }
            diff[tau] = sum
        }

        // Fonction de difference moyenne normalisee cumulee (CMNDF)
        val cmndf = DoubleArray(maxTau + 1)
        cmndf[0] = 1.0
        var runningSum = 0.0
        for (tau in 1..maxTau) {
            runningSum += diff[tau]
            cmndf[tau] = if (runningSum == 0.0) 1.0 else diff[tau] * tau / runningSum
        }

        // Recherche du premier minimum local sous le seuil, dans la plage utile
        var tauEstimate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (cmndf[tau] < THRESHOLD) {
                var local = tau
                while (local + 1 <= maxTau && cmndf[local + 1] < cmndf[local]) local++
                tauEstimate = local
                break
            }
            tau++
        }
        if (tauEstimate == -1) return null

        // Interpolation parabolique pour affiner l'estimation de tau
        val betterTau = if (tauEstimate > minTau && tauEstimate < maxTau) {
            val s0 = cmndf[tauEstimate - 1]
            val s1 = cmndf[tauEstimate]
            val s2 = cmndf[tauEstimate + 1]
            val denom = 2 * s1 - s2 - s0
            if (abs(denom) < 1e-9) tauEstimate.toDouble()
            else tauEstimate + (s2 - s0) / (2 * denom)
        } else {
            tauEstimate.toDouble()
        }

        val freq = sampleRate / betterTau
        return if (freq in MIN_FREQ..MAX_FREQ) freq else null
    }
}
