// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

import java.io.ByteArrayOutputStream

/**
 * Construit en memoire un fichier MIDI minimal (Standard MIDI File, format 0)
 * jouant une seule note avec un instrument General MIDI. Ce fichier est destine
 * au synthetiseur integre d'Android (Sonivox), via MediaPlayer.
 */
object MidiBuilder {

    private const val DIVISION = 480 // ticks par noire (a 120 BPM : 1 noire = 0,5 s)
    private const val CHANNEL = 0
    private const val VELOCITY = 100

    /**
     * @param midiNote numero de note MIDI (60 = Do4)
     * @param program  programme General MIDI (0-127), ex. 0 = piano acoustique
     * @param soundTicks duree pendant laquelle la note sonne (defaut 1440 = 1,5 s)
     * @param tailTicks  silence apres le note-off, pour laisser respirer la chute
     */
    fun singleNote(
        midiNote: Int,
        program: Int,
        soundTicks: Int = 1440,
        tailTicks: Int = 480
    ): ByteArray {
        val track = ByteArrayOutputStream()
        // Choix de l'instrument
        writeVarLen(track, 0); track.write(0xC0 or CHANNEL); track.write(program and 0x7F)
        // Volume du canal au maximum
        writeVarLen(track, 0); track.write(0xB0 or CHANNEL); track.write(7); track.write(127)
        // Note ON
        writeVarLen(track, 0); track.write(0x90 or CHANNEL); track.write(midiNote and 0x7F); track.write(VELOCITY)
        // Note OFF apres la duree de jeu
        writeVarLen(track, soundTicks); track.write(0x80 or CHANNEL); track.write(midiNote and 0x7F); track.write(0)
        // Fin de piste, apres un court silence
        writeVarLen(track, tailTicks); track.write(0xFF); track.write(0x2F); track.write(0)
        val trackBytes = track.toByteArray()

        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt32(out, 6)
        writeInt16(out, 0) // format 0
        writeInt16(out, 1) // une seule piste
        writeInt16(out, DIVISION)
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeInt32(out, trackBytes.size)
        out.write(trackBytes)
        return out.toByteArray()
    }

    private fun writeInt16(o: ByteArrayOutputStream, v: Int) {
        o.write((v ushr 8) and 0xFF); o.write(v and 0xFF)
    }

    private fun writeInt32(o: ByteArrayOutputStream, v: Int) {
        o.write((v ushr 24) and 0xFF); o.write((v ushr 16) and 0xFF)
        o.write((v ushr 8) and 0xFF); o.write(v and 0xFF)
    }

    /** Delta-time MIDI : entier code en quantite a longueur variable (7 bits/octet). */
    private fun writeVarLen(o: ByteArrayOutputStream, value: Int) {
        val stack = ArrayDeque<Int>()
        stack.addLast(value and 0x7F) // octet de poids faible, sans bit de continuation
        var v = value ushr 7
        while (v > 0) {
            stack.addLast((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        while (stack.isNotEmpty()) o.write(stack.removeLast())
    }
}
