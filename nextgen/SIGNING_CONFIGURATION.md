# Android signing configuration

Project Superhuman signing credentials are not stored in `androidApp/build.gradle.kts`.

For builds that must use the Project Superhuman signing key, provide all four values as either Gradle properties (for example in an untracked `~/.gradle/gradle.properties` or `local.properties` integration) or environment/CI secrets:

- `SUPERHUMAN_SIGNING_STORE_FILE` — path to the keystore file.
- `SUPERHUMAN_SIGNING_STORE_PASSWORD` — keystore password.
- `SUPERHUMAN_SIGNING_KEY_ALIAS` — signing key alias.
- `SUPERHUMAN_SIGNING_KEY_PASSWORD` — signing key password.

If the complete set is not present, debug builds use the normal Android debug signing behavior and release builds are left without the Project Superhuman signing configuration rather than embedding fallback credentials.

CI/release jobs that publish install-over-compatible artifacts must inject the four values explicitly. Do not commit them to Gradle files, source, documentation, or generated BuildConfig fields.

The repository still contains a legacy signing asset for compatibility with existing release processes. This hardening pass intentionally does not move or delete that asset because doing so could break unknown external release automation; its path must now be supplied through `SUPERHUMAN_SIGNING_STORE_FILE` when it is intentionally used.
