// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

/**
 * Instrument jouable. [gmProgram] est un numero de programme General MIDI
 * (0-127) joue par le synthetiseur integre d'Android ; null = diapason, un
 * sinus pur synthetise, ideal comme reference de justesse. [icon] est l'icone
 * (drawable) qui le represente dans l'interface.
 *
 * [sampleDir] est le dossier d'echantillons (VSCO 2) dans assets/samples/ :
 * s'il est present et fourni d'echantillons, il prime sur [gmProgram] ; sinon
 * on retombe sur le synthetiseur MIDI. Cf. [SampledNotePlayer].
 */
data class Instrument(
    val name: String,
    val gmProgram: Int?,
    val icon: Int,
    val sampleDir: String? = null
)

object Instruments {
    val diapason = Instrument("Diapason", null, R.drawable.ic_diapason)
    val piano = Instrument("Piano", 0, R.drawable.ic_piano, sampleDir = "piano")        // Acoustic Grand Piano
    val orgue = Instrument("Orgue", 19, R.drawable.ic_orgue, sampleDir = "organ")       // Church Organ
    val violon = Instrument("Violon", 40, R.drawable.ic_violon, sampleDir = "violin")   // Violin
    val violoncelle = Instrument("Violoncelle", 42, R.drawable.ic_cello, sampleDir = "cello")     // Cello
    val flute = Instrument("Flute", 73, R.drawable.ic_flute, sampleDir = "flute")       // Flute
    val clarinette = Instrument("Clarinette", 71, R.drawable.ic_clarinet, sampleDir = "clarinet") // Clarinet
    val trompette = Instrument("Trompette", 56, R.drawable.ic_trumpet, sampleDir = "trumpet")     // Trumpet
    val guitare = Instrument("Guitare", 24, R.drawable.ic_guitare, sampleDir = "guitar") // Acoustic Guitar (nylon)

    val all = listOf(diapason, piano, orgue, violon, violoncelle, flute, clarinette, trompette, guitare)
    val default = diapason
}
