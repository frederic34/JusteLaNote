// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private val whiteKeyWidth = 44.dp
private val blackKeyWidth = 28.dp
private val keyboardHeight = 180.dp

// Noms de notes (solfege francais) suivis d'un diese sur le clavier.
private val pitchesWithSharp = setOf("Do", "Re", "Fa", "Sol", "La")

/**
 * Clavier de piano scrollable pour selectionner une note. Les touches blanches
 * sont disposees en ligne ; les touches noires sont superposees aux limites des
 * touches blanches concernees. Une pression appelle [onNoteSelected].
 */
@Composable
fun PianoKeyboard(
    notes: List<MusicalNote>,
    highlightedNotes: Set<String>,
    centerNote: MusicalNote,
    onNoteSelected: (MusicalNote) -> Unit,
    modifier: Modifier = Modifier,
    scrollTarget: MusicalNote? = null
) {
    val whiteKeys = notes.filter { !it.name.contains("#") }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    fun scrollPxFor(note: MusicalNote): Int {
        val index = whiteKeys.indexOfFirst { it.name == note.name }
            .let { if (it >= 0) it else whiteKeys.indexOfLast { w -> w.frequency <= note.frequency } }
            .coerceAtLeast(0)
        return with(density) { (whiteKeyWidth * (index - 2)).toPx() }.toInt().coerceAtLeast(0)
    }

    // Centre le clavier sur la note de reference au premier affichage.
    LaunchedEffect(Unit) {
        val target = scrollPxFor(centerNote)
        // Attend que le contenu soit mesure pour que scrollTo ne soit pas tronque a 0.
        snapshotFlow { scrollState.maxValue }.filter { it > 0 }.first()
        scrollState.scrollTo(target)
    }

    // Suit une note cible (ex. au lancement d'une melodie) en defilant vers elle.
    LaunchedEffect(scrollTarget) {
        if (scrollTarget != null) scrollState.animateScrollTo(scrollPxFor(scrollTarget))
    }

    Box(
        modifier = modifier
            .horizontalScroll(scrollState)
            .height(keyboardHeight)
    ) {
        Row {
            whiteKeys.forEach { note ->
                WhiteKey(
                    note = note,
                    selected = note.name in highlightedNotes,
                    onClick = { onNoteSelected(note) }
                )
            }
        }
        // Touches noires par-dessus : dessinees apres la rangee blanche, donc au-dessus.
        whiteKeys.forEachIndexed { index, white ->
            val sharp = sharpFor(white, notes) ?: return@forEachIndexed
            BlackKey(
                selected = sharp.name in highlightedNotes,
                onClick = { onNoteSelected(sharp) },
                modifier = Modifier.offset(x = whiteKeyWidth * (index + 1) - blackKeyWidth / 2)
            )
        }
    }
}

@Composable
private fun WhiteKey(note: MusicalNote, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(whiteKeyWidth)
            .fillMaxHeight()
            .border(0.5.dp, Color(0xFF999999))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Repere d'octave : seules les touches Do sont etiquetees.
        if (note.name.startsWith("Do")) {
            Text(
                text = note.name,
                fontSize = 10.sp,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Color(0xFF666666),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun BlackKey(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(blackKeyWidth)
            .height(keyboardHeight * 0.62f)
            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFF1C1C1C))
            .clickable(onClick = onClick)
    )
}

/** Renvoie la note diese qui suit une touche blanche, ou null (Mi et Si n'en ont pas). */
private fun sharpFor(white: MusicalNote, notes: List<MusicalNote>): MusicalNote? {
    val pitch = white.name.takeWhile { !it.isDigit() }
    if (pitch !in pitchesWithSharp) return null
    val octave = white.name.dropWhile { !it.isDigit() }
    return notes.find { it.name == "$pitch#$octave" }
}
