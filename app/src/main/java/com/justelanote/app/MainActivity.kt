// Copyright (C) 2026 Frédéric France
// SPDX-License-Identifier: GPL-3.0-or-later

package com.justelanote.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.justelanote.app.ui.theme.JusteLaNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var notePlayer: NotePlayer
    private val recorder = AudioRecorderHelper()
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onPermissionResult?.invoke(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notePlayer = NotePlayer(this)

        setContent {
            // null = suit le reglage systeme ; sinon choix manuel via la bascule.
            var darkOverride by remember { mutableStateOf<Boolean?>(null) }
            var showTuner by remember { mutableStateOf(false) }
            val isDark = darkOverride ?: isSystemInDarkTheme()
            val requestMic: (callback: (Boolean) -> Unit) -> Unit = { callback ->
                onPermissionResult = callback
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            JusteLaNoteTheme(darkTheme = isDark, dynamicColor = false) {
                if (showTuner) {
                    BackHandler { showTuner = false }
                    TunerScreen(
                        hasPermission = { hasRecordPermission() },
                        requestPermission = requestMic,
                        onBack = { showTuner = false }
                    )
                } else {
                    PitchTrainerScreen(
                        notePlayer = notePlayer,
                        recorder = recorder,
                        hasPermission = { hasRecordPermission() },
                        requestPermission = requestMic,
                        isDarkTheme = isDark,
                        onToggleTheme = { darkOverride = !isDark },
                        onOpenTuner = { showTuner = true }
                    )
                }
            }
        }
    }

    private fun hasRecordPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        notePlayer.release()
    }
}

@Composable
fun PitchTrainerScreen(
    notePlayer: NotePlayer,
    recorder: AudioRecorderHelper,
    hasPermission: () -> Boolean,
    requestPermission: (callback: (Boolean) -> Unit) -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenTuner: () -> Unit
) {
    var selectedNote by remember { mutableStateOf(NoteLibrary.defaultNote) }
    var selectedInstrument by remember { mutableStateOf(Instruments.default) }
    // Notes en cours de lecture (polyphonie) ; comptees pour gerer les chevauchements.
    val playingCounts = remember { mutableStateMapOf<String, Int>() }
    var isRecording by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var melodyPlaying by remember { mutableStateOf(false) }
    var melodyJob by remember { mutableStateOf<Job?>(null) }
    var scrollTarget by remember { mutableStateOf<MusicalNote?>(null) }

    fun startRecordingAndAnalyze() {
        isRecording = true
        resultText = "Enregistrement en cours..."
        scope.launch {
            val buffer = recorder.record(2500)
            val detected = PitchDetector.detectPitch(buffer, recorder.sampleRate())
            isRecording = false
            resultText = if (detected == null) {
                "Aucune note detectee. Chantez plus fort, plus pres du micro, ou tenez la note plus longtemps."
            } else {
                val cents = NoteLibrary.centsOffIgnoringOctave(detected, selectedNote)
                val detectedClosest = NoteLibrary.closestNote(detected)
                val octaves = NoteLibrary.octaveShift(detected, selectedNote)
                val verdict = when {
                    kotlin.math.abs(cents) < 15 -> "Juste !"
                    cents > 0 -> "Trop haut de ${"%.0f".format(cents)} cents"
                    else -> "Trop bas de ${"%.0f".format(-cents)} cents"
                }
                val octaveNote = when {
                    octaves > 0 -> "\n(${octaves} octave${if (octaves > 1) "s" else ""} plus haut)"
                    octaves < 0 -> "\n(${-octaves} octave${if (-octaves > 1) "s" else ""} plus bas)"
                    else -> ""
                }
                // Conseil technique vocal selon le sens de l'ecart.
                val tip = when {
                    cents <= -15 -> "\nSoulevez le palais"
                    cents >= 15 -> "\nAbaissez le larynx"
                    else -> ""
                }
                "Detecte : ${detectedClosest.name} (${detected.toInt()} Hz)\n$verdict$octaveNote$tip"
            }
        }
    }

    // Joue une note (polyphonie) : elle devient la cible unique et est surlignee
    // tant qu'elle sonne ; plusieurs notes peuvent etre surlignees en meme temps.
    fun play(note: MusicalNote, instrument: Instrument) {
        selectedNote = note
        notePlayer.playNote(note, instrument)
        playingCounts[note.name] = (playingCounts[note.name] ?: 0) + 1
        scope.launch {
            delay(1500)
            val remaining = (playingCounts[note.name] ?: 1) - 1
            if (remaining <= 0) playingCounts.remove(note.name) else playingCounts[note.name] = remaining
        }
    }

    // Joue la melodie prechargee (MusicXML) avec l'instrument courant, en
    // surlignant chaque touche le temps de sa note. Re-appui = arret.
    fun toggleMelody() {
        val current = melodyJob
        if (current != null) {
            current.cancel()
            return
        }
        melodyPlaying = true
        melodyJob = scope.launch {
            try {
                val melody = withContext(Dispatchers.IO) {
                    context.assets.open("au_clair_de_la_lune.musicxml").use { MusicXmlParser.parse(it) }
                }
                // Defile le clavier vers la melodie pour que le surlignage soit visible.
                melody.firstOrNull { it.midi != null }?.midi?.let { scrollTarget = NoteLibrary.noteForMidi(it) }
                for (n in melody) {
                    val mn = n.midi?.let { NoteLibrary.noteForMidi(it) }
                    if (mn != null) {
                        notePlayer.playNote(mn, selectedInstrument, n.durationMs)
                        playingCounts[mn.name] = (playingCounts[mn.name] ?: 0) + 1
                    }
                    delay(n.durationMs.toLong())
                    if (mn != null) {
                        val c = (playingCounts[mn.name] ?: 1) - 1
                        if (c <= 0) playingCounts.remove(mn.name) else playingCounts[mn.name] = c
                    }
                }
            } finally {
                melodyPlaying = false
                melodyJob = null
                playingCounts.clear()
            }
        }
    }

    val noteLabel: @Composable () -> Unit = {
        Text(
            "Note cible : ${selectedNote.name} (${selectedNote.frequency.toInt()} Hz)",
            style = MaterialTheme.typography.titleMedium
        )
    }

    val keyboard: @Composable () -> Unit = {
        PianoKeyboard(
            notes = NoteLibrary.keyboardNotes,
            highlightedNotes = playingCounts.keys.toSet(),
            centerNote = selectedNote,
            onNoteSelected = { note -> play(note, selectedInstrument) },
            modifier = Modifier.fillMaxWidth(),
            scrollTarget = scrollTarget
        )
    }

    // Bouton-icone d'instrument (icone seule, sans texte). Le nom reste expose
    // via contentDescription pour l'accessibilite.
    val instrumentChip: @Composable (Instrument) -> Unit = { instrument ->
        FilledIconToggleButton(
            checked = instrument == selectedInstrument,
            onCheckedChange = {
                selectedInstrument = instrument
                play(selectedNote, instrument)
            }
        ) {
            Icon(
                painter = painterResource(instrument.icon),
                contentDescription = instrument.name
            )
        }
    }

    // Portrait : rangee horizontale defilante.
    val instrumentSelector: @Composable () -> Unit = {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Instruments.all.forEach { instrumentChip(it) }
        }
    }

    val recordButton: @Composable (Modifier) -> Unit = { buttonModifier ->
        Button(
            modifier = buttonModifier,
            enabled = !isRecording,
            onClick = {
                if (!hasPermission()) {
                    requestPermission { granted ->
                        if (granted) startRecordingAndAnalyze()
                        else resultText = "Permission micro refusee."
                    }
                } else {
                    startRecordingAndAnalyze()
                }
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(if (isRecording) "Enregistrement..." else "Enregistrer (3s)")
        }
    }

    val resultDisplay: @Composable () -> Unit = {
        Text(
            resultText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }

    val melodyButton: @Composable () -> Unit = {
        OutlinedButton(onClick = { toggleMelody() }) {
            Icon(
                painter = painterResource(if (melodyPlaying) R.drawable.ic_stop else R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Au clair de la lune")
        }
    }

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize()) {
    if (isLandscape) {
        // Paysage : clavier a gauche, controles a droite, pour tout afficher sans scroller.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(2f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Juste La Note", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                noteLabel()
                Spacer(Modifier.height(8.dp))
                keyboard()
                Spacer(Modifier.height(12.dp))
                melodyButton()
            }
            Spacer(Modifier.width(24.dp))
            // Instruments empiles verticalement (defilants), bouton epingle en bas.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Instruments.all.forEach { instrumentChip(it) }
                }
                Spacer(Modifier.height(12.dp))
                recordButton(Modifier)
                Spacer(Modifier.height(8.dp))
                resultDisplay()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Juste La Note", style = MaterialTheme.typography.headlineMedium)
            Text("Entrainement a la justesse", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            noteLabel()
            Spacer(Modifier.height(8.dp))
            keyboard()
            Spacer(Modifier.height(12.dp))
            melodyButton()
            Spacer(Modifier.height(16.dp))
            instrumentSelector()
            Spacer(Modifier.height(16.dp))
            recordButton(Modifier)
            Spacer(Modifier.height(24.dp))
            resultDisplay()
        }
    }

        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isDarkTheme) R.drawable.ic_light_mode else R.drawable.ic_dark_mode
                ),
                contentDescription = if (isDarkTheme) "Mode clair" else "Mode sombre"
            )
        }

        IconButton(
            onClick = onOpenTuner,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_diapason),
                contentDescription = "Accordeur"
            )
        }
    }
    }
}
