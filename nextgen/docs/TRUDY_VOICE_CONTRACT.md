# Trudy Voice Contract

This document records the canonical integration contract after reconciling the Kokoro runtime and voice UI branches.

## Runtime-owned contracts

- `TrudyVoiceRuntimeState` describes engine/model runtime state.
- `TrudyVoiceStatus` is the canonical model-manager status returned by the local model store.
- `TrudyVoiceModelManager` owns install/status/available-voice operations.
- `TrudySpeechEngine` owns preparation, synthesis/streaming, cancellation and release.
- `TrudyVoiceService` owns one-at-a-time synthesis/playback lifecycle.

## UI-owned contracts

- `TrudyVoiceUiStatus`, `TrudyVoiceModelInfo`, `TrudyVoicePreferences` and `TrudyVoiceUiState` are presentation DTOs.
- `TrudyVoiceRuntimeSource` maps runtime/model-manager contracts into UI DTOs.
- Compose must not depend on Kokoro, sherpa-onnx, model paths, tensor layouts or phonemizer internals.

## Shared lightweight DTOs

`TrudyVoiceInstallProgress` and `TrudyVoiceOption` are shared between the model-manager/runtime adapter and UI layer to avoid duplicate status types. Compatibility construction is isolated in `TrudyVoiceContracts.kt`.

## Rules

- model download is explicit, never app-start behavior;
- model/session load is lazy;
- text Trudy does not depend on voice availability;
- speech errors do not mutate successful chat answers into chat failures;
- one utterance at a time;
- navigation or a new user prompt cancels active speech;
- no spoken text or health content is logged by default.
