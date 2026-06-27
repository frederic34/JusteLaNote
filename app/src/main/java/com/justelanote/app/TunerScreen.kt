// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private val tuneGreen = Color(0xFF4CAF50)

// Palette du vu-metre analogique (independante du theme clair/sombre).
private val vuFace = Color(0xFFF2E2B6)
private val vuLine = Color(0xFF4A3B22)
private val vuRed = Color(0xFFB3261E)
private val vuNeedle = Color(0xFF222222)

/**
 * Accordeur d'instrument : affiche en continu la note la plus proche et l'ecart
 * en cents (jauge + texte). Contrairement a l'entrainement vocal, l'octave
 * compte ici (on accorde une corde precise).
 */
@SuppressLint("MissingPermission")
@Composable
fun TunerScreen(
    hasPermission: () -> Boolean,
    requestPermission: (callback: (Boolean) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    var granted by remember { mutableStateOf(hasPermission()) }
    var note by remember { mutableStateOf<MusicalNote?>(null) }
    var displayCents by remember { mutableStateOf(0.0) }
    var hasReading by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(0f) }
    val engine = remember { TunerEngine() }

    LaunchedEffect(Unit) {
        if (!granted) requestPermission { granted = it }
    }
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        var silence = 0
        engine.run { detected, lvl ->
            // Vu-metre : attaque rapide, retombee plus lente.
            level = maxOf(lvl, level * 0.8f)
            if (detected != null) {
                silence = 0
                val n = NoteLibrary.closestNote(detected)
                val c = NoteLibrary.centsOff(detected, n)
                // Lissage exponentiel de l'aiguille ; saut net si la note change
                // (sinon elle baverait a travers la frontiere +/-50 cents).
                displayCents = if (!hasReading || n.name != note?.name) c
                else displayCents * 0.85 + c * 0.15
                note = n
                hasReading = true
            } else if (++silence > 5) {
                hasReading = false // ~0,5 s de silence -> efface l'affichage
            }
        }
    }

    val cents = if (hasReading) displayCents else null
    val inTune = cents != null && abs(cents) < 5
    val shownHz = if (hasReading) note?.let { it.frequency * 2.0.pow(displayCents / 1200.0) } else null

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Icon(painter = painterResource(R.drawable.ic_back), contentDescription = "Retour")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Accordeur", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))

                if (!granted) {
                    Text(
                        "Permission micro requise pour l'accordeur.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = note?.name ?: "--",
                        style = MaterialTheme.typography.displayLarge,
                        color = if (inTune) tuneGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = shownHz?.let { "${it.toInt()} Hz" } ?: "Jouez une note",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(32.dp))
                    CentsGauge(cents = cents, inTune = inTune)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = when {
                            cents == null -> ""
                            abs(cents) < 5 -> "Juste"
                            cents > 0 -> "+${"%.0f".format(cents)} cents (trop haut)"
                            else -> "${"%.0f".format(cents)} cents (trop bas)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (inTune) tuneGreen else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.height(40.dp))
                    VintageVuMeter(
                        level = level,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(120.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VintageVuMeter(level: Float, modifier: Modifier = Modifier) {
    // L'aiguille interpole entre les mesures pour un balayage fluide et "physique".
    val needle by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 180),
        label = "needle"
    )
    val startAngle = 220f
    val sweep = 100f
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(vuFace)
            .border(2.dp, vuLine, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.95f
            val r = (size.height * 0.82f).coerceAtMost(size.width * 0.46f)
            val box = Size(r * 2, r * 2)
            val topLeft = Offset(cx - r, cy - r)

            // Arc principal puis zone rouge (niveaux eleves).
            drawArc(vuLine, startAngle, sweep, false, topLeft, box, style = Stroke(2.dp.toPx()))
            drawArc(vuRed, startAngle + sweep * 0.78f, sweep * 0.22f, false, topLeft, box, style = Stroke(4.dp.toPx()))

            // Graduations.
            for (i in 0..10) {
                val a = Math.toRadians((startAngle + sweep * i / 10f).toDouble())
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                val tick = if (i % 5 == 0) 12.dp.toPx() else 7.dp.toPx()
                drawLine(
                    vuLine,
                    Offset(cx + (r - tick) * ca, cy + (r - tick) * sa),
                    Offset(cx + r * ca, cy + r * sa),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Aiguille + pivot.
            val na = Math.toRadians((startAngle + sweep * needle).toDouble())
            drawLine(
                vuNeedle,
                Offset(cx, cy),
                Offset(cx + r * 0.92f * cos(na).toFloat(), cy + r * 0.92f * sin(na).toFloat()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(vuNeedle, radius = 5.dp.toPx(), center = Offset(cx, cy))
        }
        Text(
            "VU",
            style = MaterialTheme.typography.labelSmall,
            color = vuLine,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CentsGauge(cents: Double?, inTune: Boolean) {
    val markerColor = if (inTune) tuneGreen else MaterialTheme.colorScheme.primary
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val half = maxWidth / 2
        // Piste horizontale.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        // Repere central (0 cent).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(36.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        // Curseur : position proportionnelle a l'ecart, borne a +/- 50 cents.
        if (cents != null) {
            val frac = (cents / 50.0).coerceIn(-1.0, 1.0).toFloat()
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = half * frac)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(markerColor)
            )
        }
    }
}
