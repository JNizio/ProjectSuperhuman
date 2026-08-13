# Trudy Voice Architecture

## Goal

Trudy voice is local-first and optional. Text conversation must remain fully functional if speech is disabled, the model is not installed, or local inference cannot initialize.

The initial target is Kokoro-82M v1.0 using a local inference backend. No hosted TTS service is required and no API key is part of the voice path.

## Runtime shape

`Trudy answer -> TrudyVoiceController -> TrudyVoiceRuntimeSource -> TrudyVoiceRuntimeFactory -> TrudyVoiceService -> TrudySpeechEngine -> KokoroTrudySpeechEngine -> KokoroInferenceBackend -> PCM -> TrudyAudioSink -> Android AudioTrack`

The inference backend is intentionally replaceable. Android/Compose code must not know about ONNX tensors, tokenizer internals, phonemizers, model files or PCM conversion.

## Current foundation

The Android app now contains:

- `TrudyVoiceConfig` and local/off modes.
- provider-neutral speech/audio contracts.
- `KokoroTrudySpeechEngine` with lazy, one-time inference initialization.
- `KokoroModelStore` so model installation/storage is independent of synthesis.
- `KokoroInferenceBackend` as the single runtime-specific seam.
- `AndroidTrudyAudioSink` for 16-bit mono streaming playback.
- `TrudyVoiceService` for synthesis/playback lifecycle.
- `TrudyVoiceRuntimeFactory` that safely leaves Trudy in text mode when Kokoro is unavailable.
- `TrudyVoiceController` / `TrudyVoiceUiState` for presentation-neutral voice lifecycle state.
- `TrudyVoiceRuntimeSource` as the narrow bridge from UI lifecycle into the existing runtime factory and optional installer metadata.
- Android preference storage for enabled, selected voice, speed and auto-speak only.
- deterministic fake voice runtime support for previews and unit tests.

No Kokoro model weights or large runtime libraries are committed at this stage.

## Model/loading policy

Kokoro must not be loaded during normal app startup or simply because the Trudy screen is opened. Model installation is explicit user action. The model should be installed to app-private storage and inference initialization should occur only when the user first asks Trudy to speak (or after an opted-in auto-speak event).

`TrudySpeechEngine.prepare()` is a backward-compatible optional lifecycle hook. The default implementation is a no-op; Kokoro maps it to its existing lazy `ensureInitialized()` path. This lets UI distinguish first-use model preparation from synthesis without exposing inference implementation details or changing existing engine implementations.

This protects startup time, memory pressure and UI frame stability. A concrete model installer should support resumable/on-demand installation and verify file integrity before marking a model installed. Installation progress may expose a trustworthy approximate/total byte count, but UI must not invent one.

The configured model identity is currently `onnx-community/Kokoro-82M-v1.0-ONNX`, with `af_heart` as the default voice and 24 kHz mono PCM as the expected output shape. Runtime code does not depend on that repository layout.

## Voice UX lifecycle

Compose receives only presentation concepts such as unavailable, model-not-installed, downloading, ready, loading, synthesizing, speaking, stopped and error. It does not receive Kokoro model paths, backend sessions, tensor/phoneme structures or raw audio.

Only completed assistant answer text is speakable. Evidence IDs, tool details, diagnostics and detached warning cards are not included in the default spoken payload. Starting another utterance, submitting a new user prompt, leaving Trudy, or stopping/backgrounding the activity cancels active speech. There is no background media-service behavior in v1.

Auto-speak is off by default. Voice preferences are lightweight and independent from conversation/health content. Voice-level failures never mutate a successful Trudy chat message into a conversation error.

## Installation seam

`TrudyVoiceModelInstaller` is an optional composition seam for Instance A's concrete app-private model installer. It exposes only installation state, progress, safe model/version metadata, available voice choices and remove/reinstall capability. It does not expose internal asset filenames to Compose.

Until a concrete installer/model store/inference backend is registered, the production controller reports voice as unavailable while text conversation remains fully functional. The deterministic fake runtime exercises installed, not-installed, installing, speaking and failure states without a real model.

## Boundaries

Voice code must not:

- read Data Vault or Module Parity directly;
- alter Trudy evidence or answer text;
- send health or conversation content to a hosted service;
- keep the Kokoro model resident when voice has never been used;
- silently download model assets without an explicit model-install action;
- log spoken personal content by default;
- persist current spoken text, conversation text, health content or inference internals.

## Failure behavior

Any model-install, initialization, synthesis or playback failure must leave the text conversation usable. Voice failure is a feature-level degradation, never an app-level failure.
