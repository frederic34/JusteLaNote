# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**Juste La Note** is a single-screen Android app for vocal pitch training: the user picks a target musical note, plays a reference tone, sings it back, and the app measures how many cents sharp/flat they were. UI text is in French (intentionally written without accents in source strings).

## Commands

```bash
./gradlew assembleDebug                                      # Build debug APK
./gradlew installDebug                                       # Build + install on connected device/emulator
./gradlew test                                               # Run JVM unit tests (app/src/test)
./gradlew test --tests "com.justelanote.app.ExampleUnitTest" # Run a single unit test class
./gradlew connectedAndroidTest                               # Run instrumented tests (needs a device/emulator)
./gradlew lint                                               # Android lint
```

The SDK path lives in `local.properties` (`sdk.dir`), which is machine-specific and untracked.

## Architecture

Everything is in the single package `com.justelanote.app` (`app/src/main/java/com/justelanote/app/`). There is no DI framework, no repository layer, no ViewModel — UI state is held directly in the `PitchTrainerScreen` Composable with `remember`/`mutableStateOf`.

The audio pipeline is pure Kotlin DSP with **no third-party audio libraries** — all synthesis, recording, and pitch detection use raw `android.media` APIs (`AudioTrack`, `AudioRecord`) on 44100 Hz / mono / PCM 16-bit `ShortArray` buffers.

Five files, each one responsibility:

- **`MainActivity.kt`** — the only Activity and the only screen. Owns the `RECORD_AUDIO` runtime permission flow (`registerForActivityResult` + a stored callback) and the `PitchTrainerScreen` Composable. The record→detect→verdict logic lives in `startRecordingAndAnalyze()` inside the Composable, launched on a coroutine scope.
- **`AudioRecorderHelper.kt`** — `suspend fun record()` captures mic audio on `Dispatchers.IO` and returns a `ShortArray`. Assumes the permission is already granted (annotated `@SuppressLint("MissingPermission")`); the caller in `MainActivity` is responsible for checking/requesting it first.
- **`PitchDetector.kt`** — stateless `object` implementing the **YIN** fundamental-frequency algorithm (CMNDF + parabolic interpolation). Runs over sliding windows and returns the **median** detection for robustness, or `null` if nothing reliable is found. Frequency search is bounded to 60–1100 Hz.
- **`NotePlayer.kt`** — synthesizes a sine-wave tone for a given frequency into an `AudioTrack` (`MODE_STATIC`), with a short fade in/out to avoid clicks. `release()` is called from `MainActivity.onDestroy()`.
- **`NoteLibrary.kt`** — generates the note table from MIDI 36–84 (Do2–Do6) using equal temperament (A4 = 440 Hz). Provides `closestNote()` and `centsOff()` (the cents formula `1200 * log2(f/target)`); note names use the French solfège convention (Do, Re, Mi…). Default note is La4.

Data flow for one round: `NotePlayer.playNote(freq)` for the reference → `AudioRecorderHelper.record()` → `PitchDetector.detectPitch(buffer, sampleRate)` → `NoteLibrary.centsOff(detected, selectedNote)` → verdict string ("Juste !" when within ±15 cents).

## Toolchain notes

- Modern, leading-edge versions pinned in `gradle/libs.versions.toml`: AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01, `compileSdk` 36.1, `minSdk` 24, Java 11. Material3 + Jetpack Compose only (no Views/XML layouts).
- Gradle **configuration cache is enabled** (`gradle.properties`).
- The `release` build type explicitly **disables** optimization/minification — there is no signing or ProGuard setup; this is a debug-oriented project.
