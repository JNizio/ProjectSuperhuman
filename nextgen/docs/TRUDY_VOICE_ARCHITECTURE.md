# Trudy Voice Architecture

## Goal

Trudy voice is local-first and optional. Text conversation must remain fully functional if speech is disabled, the model is not installed, or local inference cannot initialize.

The initial target is Kokoro-82M v1.0 using a local inference backend. No hosted TTS service is required and no API key is part of the voice path.

## Runtime shape

`Trudy answer -> TrudyVoiceService -> TrudySpeechEngine -> KokoroTrudySpeechEngine -> KokoroInferenceBackend -> PCM -> TrudyAudioSink -> Android AudioTrack`

The inference backend is intentionally replaceable. Android/Compose code must not know about ONNX tensors, tokenizer internals, phonemizers, or model files.

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
- tests for lazy initialization, missing runtime fallback, voice selection and speech text cleanup.

No Kokoro model weights or large runtime libraries are committed at this stage.

## Model/loading policy

Kokoro must not be loaded during normal app startup. The model should be installed to app-private storage and inference initialization should occur only when the user first asks Trudy to speak.

This protects startup time, memory pressure and UI frame stability. A future model installer should support resumable/on-demand installation and verify file integrity before marking a model installed.

The configured model identity is currently `onnx-community/Kokoro-82M-v1.0-ONNX`, with `af_heart` as the default voice and 24 kHz mono PCM as the expected output shape. Runtime code does not depend on that repository layout.

## Next Kokoro implementation step

Implement exactly two missing pieces:

1. A concrete `KokoroModelStore` that installs/resolves the selected model and voice assets in app-private storage.
2. A concrete `KokoroInferenceBackend`, most likely using an Android-compatible ONNX Runtime package after validating the exact Kokoro v1.0 graph and phonemization/tokenization requirements.

Then wire `TrudyVoiceRuntimeFactory` into the Trudy screen with manual speak/stop controls first. Auto-speak should remain an explicit user option rather than the default.

## Boundaries

Voice code must not:

- read Data Vault or Module Parity directly;
- alter Trudy evidence or answer text;
- send health or conversation content to a hosted service;
- keep the Kokoro model resident when voice has never been used;
- silently download model assets without an explicit model-install action;
- log spoken personal content by default.

## Failure behavior

Any model-install, initialization, synthesis or playback failure must leave the text conversation usable. Voice failure is a feature-level degradation, never an app-level failure.
