# Trudy Voice Architecture

## Goal

Trudy voice is local-first and optional. Text conversation remains fully functional if speech is disabled, the model is not installed, installation fails, or local inference cannot initialize.

The Android runtime uses Kokoro-82M v1.0 through sherpa-onnx. No hosted TTS service, API key, user-managed server, health-data upload, or conversation upload exists in the voice path.

## Runtime shape

`Trudy answer -> TrudyVoiceService -> TrudySpeechEngine -> KokoroTrudySpeechEngine -> KokoroInferenceBackend -> PCM -> TrudyAudioSink -> Android AudioTrack`

The concrete Android inference backend is `SherpaKokoroInferenceBackend`. Sherpa/ONNX/JNI types do not cross `KokoroInferenceBackend`; Compose, Trudy conversation code, Module Parity, and health-data code do not know about model files or native inference.

## Verified Kokoro-82M v1.0 contract

The published ONNX v1.0 usage contract uses:

- `input_ids`: `int64[1,N]` Kokoro phoneme-vocabulary IDs.
- A pad token `0` at both sequence edges. The model context is 512, leaving at most 510 phoneme tokens before padding.
- `style`: `float32[1,256]`. The voice binary is reshaped as `(-1,1,256)` and the style row is selected by phoneme-token length.
- `speed`: `float32[1]`.
- The first model output is the waveform; the publisher example consumes `audio[0]` and writes it at 24,000 Hz.

The publisher example does not name the output tensor, so Project Superhuman deliberately does not invent an output name. `Kokoro82MModelContract` records only the verified names/representations and the first-output index.

The selected sherpa package uses one `model.onnx`, not a multi-ONNX graph. The Android runtime additionally requires `voices.bin`, `tokens.txt`, `espeak-ng-data/`, and an English lexicon (`lexicon-us-en.txt`). The selected package contains 53 speakers; the default `af_heart` voice is speaker ID 3.

## Android inference runtime

Project Superhuman pins sherpa-onnx Android v1.13.4. The official v1.13.4 Android AAR contains the JNI/native inference runtime and loads `sherpa-onnx-jni`; Project Superhuman does not vendor its native binaries into git.

The backend uses the CPU provider with 2-4 worker threads depending on available processors. `OfflineTts` is created only by `KokoroInferenceBackend.initialize()`, which is reached only from the first real speech request after the model has passed installation validation.

A single native session is retained while local voice is active. It is not recreated per sentence. `TrudySpeechEngine.close()` releases it, allowing a disposed voice runtime or memory-pressure integration to relinquish the model.

## English phonemization and tokenization

Kokoro requires phoneme token IDs; passing characters directly to the raw ONNX model would be incorrect. Project Superhuman therefore does not implement a fake Kotlin grapheme-to-phoneme approximation.

`SherpaKokoroInferenceBackend` uses sherpa-onnx's Kokoro frontend with the package's `espeak-ng-data`, `tokens.txt`, and `lexicon-us-en.txt`. Sherpa owns the English eSpeak-ng phonemization and Kokoro token mapping immediately before the ONNX invocation.

`KokoroTextFrontend` is the testable app-level text boundary. It performs deterministic Trudy text cleanup and bounded utterance chunking, but intentionally does not claim that those operations are phonemization. A real phoneme/token-output smoke test therefore requires the native runtime plus installed Kokoro assets.

Initial language support for Trudy local speech is English. Universal language support is out of scope.

## Chunking and PCM

Trudy answers are not sent to one giant inference call. Prepared text is split at sentence/punctuation boundaries and then at word boundaries when necessary. The current app-level ceiling is 240 characters per native generation request, deliberately conservative relative to the raw 510-phoneme-token graph limit and Kokoro's practical long-utterance quality behavior.

Chunks are synthesized and played sequentially. Normal Trudy playback therefore does not concatenate an entire long answer into one large audio buffer. The legacy `synthesize()` contract remains available and joins chunks only for callers that explicitly request a single result.

Kokoro output is required to be 24 kHz mono float PCM. Non-finite values are sanitized and finite values are clipped to `[-1,1]`. `AndroidTrudyAudioSink` converts this to signed 16-bit mono PCM for `AudioTrack`. Empty output or an unexpected sample rate is treated as inference failure rather than playable audio.

## Cancellation and concurrency

Initialization is guarded by a mutex and happens at most once per engine instance. Synthesis is serialized against the single native session.

`TrudyVoiceService.stop()` invalidates queued speech, calls `KokoroInferenceBackend.cancelCurrent()`, and stops `AudioTrack`. Sherpa generation uses its callback cancellation path, so a stop request prevents subsequent sentence chunks from continuing after the current native callback observes cancellation. Coroutine cancellation is preserved rather than converted into an ordinary error.

## Model installation and storage

Kokoro weights are not committed to Project Superhuman and are not bundled into the APK.

`AndroidKokoroModelStore` stores the model under app-private `noBackupFilesDir/trudy/kokoro`. Production construction is tied to one reviewed distribution specification:

- model identity: `onnx-community/Kokoro-82M-v1.0-ONNX`
- runtime package version: `sherpa-kokoro-multi-lang-v1_0`
- source: the fixed sherpa-onnx `tts-models` GitHub release asset over HTTPS
- integrity source: the fixed publisher `checksum.txt` release asset over HTTPS

Installation occurs only after an explicit call to `TrudyVoiceModelManager.install()`. App startup and voice-runtime construction never trigger a download.

The installer:

1. downloads to `.downloads/<archive>.part` and uses HTTP Range resume when the server supports it;
2. restarts cleanly if a partial response cannot be resumed;
3. obtains the publisher checksum for the exact fixed archive and verifies SHA-256 before extraction;
4. extracts only into `.staging`, rejecting symbolic/hard links and path traversal;
5. verifies required asset presence/minimum sizes and the eSpeak data tree;
6. writes a local manifest containing model/version identity, exact critical-file sizes, and SHA-256 hashes;
7. promotes the staged model to `active` only after validation, with a previous-install rollback directory;
8. deep-validates hashes before resolving paths to the inference backend.

Partial downloads and staging directories never count as installed. A mismatched model version, truncated required asset, modified voice asset, or invalid local manifest is rejected.

No arbitrary remote URL supplied by a model response, conversation, or UI is accepted. Model files never live in public/shared external storage.

## Runtime states and progress

The provider-neutral status surface is:

- `NOT_INSTALLED`
- `INSTALLING`
- `READY`
- `LOADING`
- `SPEAKING`
- `ERROR`

Installation progress exposes downloaded bytes, optional total bytes, and a normalized fraction when the total is known. UI code never parses HTTP responses or Kokoro filenames.

The initial voice catalog exposes English voice IDs/display labels/language tags. UI code uses logical IDs such as `af_heart`; only `SherpaKokoroInferenceBackend` maps those IDs to speaker indices.

## Diagnostics and privacy

Speech diagnostics may expose:

- engine/model/voice ID;
- model-load duration;
- synthesis duration;
- generated audio duration;
- real-time factor;
- installed model bytes on disk.

Diagnostics do not contain spoken text, conversation history, health values, PCM payloads, credentials, or remote analytics identifiers. Sherpa debug logging is disabled in production configuration.

## Failure behavior

Missing models, incomplete installs, checksum failures, unsupported ABI, native initialization errors, malformed output, cancellation, audio playback failures, and catchable model-allocation failures remain voice-level failures. They do not alter Trudy's text answer or health-data architecture.

When Kokoro is unavailable, `TrudyVoiceRuntimeFactory` returns no speech service and leaves Trudy in text-only mode. After an explicit successful model installation, the caller can rebuild the voice runtime and obtain the local speech service.

## Boundaries

Voice code must not:

- read Data Vault or Module Parity directly;
- alter Trudy evidence or answer text;
- implement cloud TTS fallback;
- send health or conversation content to a hosted service;
- keep the Kokoro model resident when voice has never been used;
- silently download model assets without an explicit install action;
- load models from public writable storage;
- log spoken personal content by default.
