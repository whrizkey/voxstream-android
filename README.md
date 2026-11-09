# VoxStream for Android

> Real-time video speech translation, dual subtitle overlay, and synchronized audio dubbing with dynamic audio ducking for Android.

VoxStream Android is the native mobile companion to [VoxStream](https://github.com/whrizkey/voxstream). It runs a continuous rolling lookahead pipeline ahead of playback, transcribing speech, translating text, and synthesizing dubbed audio clips that play in lockstep with the video master clock.

---

## Key Features

- **Continuous Rolling Lookahead Window**: Schedules ingestion 30–120 seconds ahead of the video playhead, preventing stuttering and allowing time for neural translation and TTS synthesis.
- **Dual Subtitle Overlay**: High-contrast, styled subtitle overlay rendering primary translated text and secondary source text simultaneously for language learners and dual comprehension.
- **Dynamic Audio Ducking**: Smoothly attenuates original media audio (e.g., -14 dB) whenever dubbed speech sounds, with attack/release ramps to prevent pops or clicks.
- **Offline-First & Private**:
  - **On-Device Translation**: Backed by Google ML Kit (`com.google.mlkit:translate`), downloading models on demand for offline, zero-cost, private translation.
  - **On-Device Dubbing**: Native Android `TextToSpeech` engine utilizing pre-installed high-quality system voices with real-time pitch and speed control.
- **Pluggable Cloud Providers**: Optional integration with OpenAI Whisper, Groq, DeepSeek, or local Ollama / vLLM endpoints for multi-lingual STT and LLM translation.
- **Instant Seek Recovery**: Seek actions immediately increment the generation counter (`generation++`), cancelling stale async lookahead tasks and re-aligning the pipeline at the new playhead.
- **Hard Cost Protection**: Real-time token and duration accounting enforcing a configurable maximum spend ceiling (`max_cost_usd`) before incurring surprise API charges.

---

## Architecture

```
                  ┌─────────────────────────────────────────┐
                  │          ExoPlayer (Master Clock)       │
                  └────────────────────┬────────────────────┘
                                       │ (Playhead & Seek events)
                                       ▼
                  ┌─────────────────────────────────────────┐
                  │            LookaheadScheduler           │
                  │  (30–120s buffer, generation tracking)  │
                  └──────┬───────────────────┬──────────────┘
                         │                   │
         [Stage 1: STT]  ▼                   ▼  [Stage 2: Translation]
     ┌───────────────────────┐           ┌───────────────────────┐
     │  SttProvider          │           │  TranslateProvider    │
     │  - On-Device Recognizer           │  - Google ML Kit (On) │
     │  - Whisper API (Cloud)│           │  - OpenAI / LLM API   │
     └───────────┬───────────┘           └───────────┬───────────┘
                 │                                   │
                 ▼                                   ▼
     ┌───────────────────────┐           ┌───────────────────────┐
     │     SubtitleStore     │           │  TtsProvider (Dub)    │
     │  (Sorted Binary-Search│           │  - Android System TTS │
     │   O(log N) Cues)      │           │  - Cloud Audio TTS    │
     └───────────┬───────────┘           └───────────┬───────────┘
                 │                                   │
                 ▼                                   ▼
     ┌───────────────────────┐           ┌───────────────────────┐
     │  DualSubtitleOverlay  │           │ AudioDuckingCoordinator│
     │  (Jetpack Compose M3) │           │ (ExoPlayer volume ramp│
     └───────────────────────┘           │  1.0 -> 0.20 -> 1.0)  │
                                         └───────────────────────┘
```

---

## Provider Support Matrix

| Capability | On-Device (Free & Private) | Cloud / Self-Hosted |
| :--- | :--- | :--- |
| **Speech-to-Text** | Android SpeechRecognizer / Local VAD | OpenAI Whisper (`/v1/audio/transcriptions`), Groq, LocalAI |
| **Translation** | Google ML Kit on-device models | OpenAI (`gpt-4o-mini`), DeepSeek, Ollama (`/v1/chat/completions`) |
| **Speech Synthesis**| Android `TextToSpeech` platform voices | Microsoft Edge TTS, OpenAI TTS |
| **Key Storage** | Android Keystore / `EncryptedSharedPreferences` | N/A |

---

## Project Structure

```
voxstream-android/
├── core/                        # Pure Kotlin multiplatform-ready domain logic
│   ├── src/main/kotlin/
│   │   ├── models/              # Cue, Segment, DubClip, ChunkSpec, PlaybackMode
│   │   ├── scheduler/           # LookaheadScheduler with generation tracking
│   │   ├── subtitles/           # Thread-safe binary-search SubtitleStore
│   │   ├── audio/               # DuckingCalculator & decibel-gain curves
│   │   ├── providers/           # Provider contracts, fake test doubles, cloud HTTP
│   │   └── accounting/          # UsageTracker with hard spend ceiling
│   └── src/test/kotlin/         # Comprehensive JUnit unit test suite
├── app/                         # Android application module
│   ├── src/main/kotlin/
│   │   ├── player/              # VoxStreamPlayerController, AudioDuckingCoordinator
│   │   ├── providers/           # MlKitTranslateProvider, AndroidNativeTtsProvider
│   │   ├── storage/             # SecureKeyStore (EncryptedSharedPreferences)
│   │   └── ui/                  # Jetpack Compose Material 3 screens:
│   │       ├── player/          # VideoPlayerView, DualSubtitleOverlay, TimelineBar
│   │       ├── settings/        # SettingsScreen with sliders and metrics
│   │       └── dialogs/         # StreamUrlDialog, LanguagePickerDialog
│   └── src/main/res/            # Themes, colors, strings, security configs
└── .github/workflows/           # GitHub Actions CI automation
```

---

## Building and Testing

### Prerequisites
- JDK 17 or higher
- Android SDK with Platform 34 (`platforms;android-34`) and Build Tools 34.0.0

### Run Tests
```bash
./gradlew test
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Security

- **No Hardcoded Keys**: API keys are saved exclusively in hardware-backed `EncryptedSharedPreferences` via Android Keystore.
- **Log Redaction**: Secret tokens, bearer auth headers, and raw audio payloads are never written to Logcat or serialized in exceptions.
- **Path and URL Verification**: Content URIs are restricted through the Storage Access Framework (SAF); stream endpoints enforce HTTP/HTTPS network security policies.

---

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
