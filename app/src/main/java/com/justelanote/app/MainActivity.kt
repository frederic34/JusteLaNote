package com.justelanote.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var notePlayer: NotePlayer
    private val recorder = AudioRecorderHelper()
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onPermissionResult?.invoke(granted) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notePlayer = NotePlayer()

        setContent {
            MaterialTheme {
                PitchTrainerScreen(
                    notePlayer = notePlayer,
                    recorder = recorder,
                    hasPermission = { hasRecordPermission() },
                    requestPermission = { callback ->
                        onPermissionResult = callback
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
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
    requestPermission: (callback: (Boolean) -> Unit) -> Unit
) {
    var selectedNote by remember { mutableStateOf(NoteLibrary.defaultNote) }
    var selectedInstrument by remember { mutableStateOf(Instruments.default) }
    var isRecording by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                val cents = NoteLibrary.centsOff(detected, selectedNote)
                val detectedClosest = NoteLibrary.closestNote(detected)
                val verdict = when {
                    kotlin.math.abs(cents) < 15 -> "Juste !"
                    cents > 0 -> "Trop haut de ${"%.0f".format(cents)} cents"
                    else -> "Trop bas de ${"%.0f".format(-cents)} cents"
                }
                "Detecte : ${detectedClosest.name} (${detected.toInt()} Hz)\n$verdict"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Juste La Note", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Entrainement a la justesse",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        Text(
            "Note cible : ${selectedNote.name} (${selectedNote.frequency.toInt()} Hz)",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        PianoKeyboard(
            notes = NoteLibrary.notes,
            selectedNote = selectedNote,
            onNoteSelected = { note ->
                selectedNote = note
                notePlayer.playNote(note.frequency, selectedInstrument)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Text("Instrument", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Instruments.all.forEach { instrument ->
                FilterChip(
                    selected = instrument == selectedInstrument,
                    onClick = {
                        selectedInstrument = instrument
                        notePlayer.playNote(selectedNote.frequency, instrument)
                    },
                    label = { Text(instrument.name) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
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
            Text(if (isRecording) "Enregistrement..." else "Enregistrer (3s)")
        }

        Spacer(Modifier.height(24.dp))
        Text(
            resultText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}
