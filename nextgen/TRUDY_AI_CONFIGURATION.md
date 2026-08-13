# Trudy AI runtime

Trudy now has a direct hosted language-model path that does not require a Project Superhuman server.

## Default behavior

- If `TRUDY_GEMINI_API_KEY` is present at build time, Android selects `HOSTED` mode automatically.
- The default hosted provider is `gemini`.
- The default model is `gemini-3.5-flash`.
- The direct transport calls the official Gemini Developer API over HTTPS.
- If the key is absent or the hosted request fails, Trudy falls back to the deterministic offline model instead of breaking chat.
- Personal health retrieval, calculations, associations and experiment logic remain in Trudy's deterministic tool layer. The hosted model only turns bounded context/tool output into conversational language.
- Hosted model output cannot add arbitrary second-stage tool calls. Evidence references are rebound from actual successful tool results before they leave the model boundary.

## Supplying the key

Do not commit an API key to the repository.

Supply `TRUDY_GEMINI_API_KEY` as either:

- a Gradle property, for example in the developer machine's user-level `~/.gradle/gradle.properties`, or
- an environment variable available to the Gradle build.

Example property name only:

```properties
TRUDY_GEMINI_API_KEY=<your key>
```

The resulting personal APK contains the key in generated `BuildConfig`, so this direct-client mode is intended for personal/development builds. A broadly distributed production app should move hosted credentials behind a service or replace the hosted runtime with Trudy's local-model implementation.

## Overrides

The existing provider-neutral settings remain available:

```properties
TRUDY_RUNTIME_MODE=HOSTED
TRUDY_HOSTED_PROVIDER_ID=gemini
TRUDY_HOSTED_MODEL_ID=gemini-3.5-flash
TRUDY_HOSTED_ENDPOINT=https://generativelanguage.googleapis.com/v1beta/models
```

`DETERMINISTIC` remains a valid explicit runtime mode, and `LOCAL` remains reserved for the on-device Trudy language-model engine.
