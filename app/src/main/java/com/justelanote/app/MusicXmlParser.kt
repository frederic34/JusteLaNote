// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/** Une note de melodie : [midi] (null = silence) et sa duree en millisecondes. */
data class MelodyNote(val midi: Int?, val durationMs: Int)

/**
 * Lecteur MusicXML minimal pour partitions monophoniques simples. Gere
 * <divisions>, <sound tempo>, les <note> avec <pitch> (step/alter/octave) ou
 * <rest>, et <duration>. Renvoie la sequence de notes prete a etre jouee.
 */
object MusicXmlParser {

    private val stepSemitone = mapOf(
        "C" to 0, "D" to 2, "E" to 4, "F" to 5, "G" to 7, "A" to 9, "B" to 11
    )

    fun parse(input: InputStream): List<MelodyNote> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var divisions = 1
        var tempo = 120.0
        val raw = mutableListOf<Pair<Int?, Int>>() // (midi ou null, duree en divisions)

        var inNote = false
        var isRest = false
        var step: String? = null
        var alter = 0
        var octave = 4
        var duration = 0
        var text = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "note" -> {
                            inNote = true; isRest = false
                            step = null; alter = 0; octave = 4; duration = 0
                        }
                        "rest" -> if (inNote) isRest = true
                        "sound" -> parser.getAttributeValue(null, "tempo")?.toDoubleOrNull()?.let { tempo = it }
                    }
                    text = ""
                }
                XmlPullParser.TEXT -> text += parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "divisions" -> text.trim().toIntOrNull()?.let { divisions = it }
                    "step" -> if (inNote) step = text.trim()
                    "alter" -> if (inNote) alter = text.trim().toIntOrNull() ?: 0
                    "octave" -> if (inNote) octave = text.trim().toIntOrNull() ?: 4
                    "duration" -> if (inNote) duration = text.trim().toIntOrNull() ?: 0
                    "note" -> {
                        val midi = if (isRest || step == null) null
                        else 12 * (octave + 1) + (stepSemitone[step] ?: 0) + alter
                        raw.add(midi to duration)
                        inNote = false
                    }
                }
            }
            event = parser.next()
        }

        val safeDivisions = if (divisions <= 0) 1 else divisions
        val msPerQuarter = 60000.0 / tempo
        return raw.map { (midi, dur) ->
            MelodyNote(midi, ((dur.toDouble() / safeDivisions) * msPerQuarter).toInt())
        }
    }
}
