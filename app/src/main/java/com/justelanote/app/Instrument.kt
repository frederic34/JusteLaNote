package com.justelanote.app

/**
 * Timbre d'un instrument pour la synthese additive : amplitudes relatives des
 * partiels (harmoniques) plus une enveloppe d'amplitude simple. La fondamentale
 * reste a la frequence demandee : la justesse de la note de reference est donc
 * conservee quel que soit l'instrument.
 */
data class Instrument(
    val name: String,
    val harmonics: List<Double>, // [0] = fondamentale, [1] = 2e harmonique, ...
    val attackMs: Int,
    val decay: Boolean           // true = decroissance exponentielle (son percussif type piano)
)

object Instruments {
    val sine = Instrument("Sinus", listOf(1.0), attackMs = 15, decay = false)
    val organ = Instrument("Orgue", listOf(1.0, 0.8, 0.6, 0.45, 0.3, 0.25), attackMs = 12, decay = false)
    val flute = Instrument("Flute", listOf(1.0, 0.3, 0.1), attackMs = 30, decay = false)
    val piano = Instrument("Piano", listOf(1.0, 0.7, 0.5, 0.35, 0.25, 0.18, 0.12), attackMs = 5, decay = true)
    val strings = Instrument("Cordes", listOf(1.0, 0.5, 0.33, 0.25, 0.2, 0.16, 0.13, 0.11), attackMs = 70, decay = false)

    val all = listOf(sine, organ, flute, piano, strings)
    val default = sine
}
