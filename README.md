# Karakalpak Voice AI

A native Android app for having a spoken conversation with an AI in the **Karakalpak
(Qaraqalpaq)** language. It calls the Google Gemini API directly — no backend.

## Features

- **Hands-free voice mode** — auto-mic with energy-based VAD; speak a question, it is
  detected, answered, and spoken back, then it returns to listening. Animated robot orb
  reacts to each state (listening / capturing / thinking / speaking).
- **Text chat mode** — typed input, with replayable spoken answers.
- **One combined call** for speech-to-text + chat (Gemini returns
  `{transcript, answer}` JSON), plus a separate text-to-speech call — 2 requests per turn.
- **Half-duplex**: the mic is closed while the AI speaks, so it never transcribes its own
  voice.
- **Conversation persistence** with Room (restored on launch).
- Professional dark Material 3 UI; Karakalpak (Latin) interface strings.

## Stack

- Kotlin, Jetpack Compose, Material 3, MVVM (ViewModel + StateFlow + Coroutines)
- Retrofit + OkHttp + kotlinx.serialization (raw Gemini REST)
- Room (persistence), Navigation Compose
- `AudioRecord` (VAD capture) + `MediaPlayer` (playback)
- minSdk 26, target/compileSdk 36

## Setup

1. Get a Gemini API key from <https://aistudio.google.com/apikey>.
2. Copy the template and add your key:
   ```bash
   cp local.properties.example local.properties
   ```
   Then set `GEMINI_API_KEY=...` (and `sdk.dir=...`) in `local.properties`.
   **`local.properties` is git-ignored — never commit your key.**
3. Build/run:
   ```bash
   ./gradlew assembleDebug      # or open in Android Studio
   ```

The key is read from `local.properties` into `BuildConfig.GEMINI_API_KEY`.

> **Security / TODO:** For a public release, do **not** ship the API key in the app —
> proxy Gemini calls through a backend that holds the key server-side. Shipping the key in
> `BuildConfig` is acceptable only for local/dev builds restricted by package name + SHA-1
> in the Google Cloud Console.

## Models

- STT + chat: `gemini-2.5-flash-lite` (audio in → `{transcript, answer}` JSON, thinking
  disabled).
- TTS: `gemini-3.1-flash-tts-preview` (primary) with `gemini-2.5-flash-preview-tts`
  fallback on 429 (each model has its own free-tier daily quota). Output is raw PCM
  24 kHz / 16-bit / mono, wrapped in a WAV header before playback.

Model ids change — re-verify at <https://ai.google.dev/gemini-api/docs/models>.

## Language note

Karakalpak is not officially supported by Gemini TTS, so pronunciation is best-effort.
The `TtsProvider` interface lets you swap in another engine (e.g. Azure `kk-KZ`) later
without touching the rest of the app.
