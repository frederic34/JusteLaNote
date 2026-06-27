package com.justelanote.app

/**
 * Instrument jouable. [gmProgram] est un numero de programme General MIDI
 * (0-127) joue par le synthetiseur integre d'Android ; null = diapason, un
 * sinus pur synthetise, ideal comme reference de justesse. [icon] est l'icone
 * (drawable) qui le represente dans l'interface.
 */
data class Instrument(val name: String, val gmProgram: Int?, val icon: Int)

object Instruments {
    val diapason = Instrument("Diapason", null, R.drawable.ic_diapason)
    val piano = Instrument("Piano", 0, R.drawable.ic_piano)        // Acoustic Grand Piano
    val orgue = Instrument("Orgue", 19, R.drawable.ic_orgue)       // Church Organ
    val flute = Instrument("Flute", 73, R.drawable.ic_flute)       // Flute
    val violon = Instrument("Violon", 40, R.drawable.ic_violon)    // Violin
    val guitare = Instrument("Guitare", 24, R.drawable.ic_guitare) // Acoustic Guitar (nylon)

    val all = listOf(diapason, piano, orgue, flute, violon, guitare)
    val default = diapason
}
