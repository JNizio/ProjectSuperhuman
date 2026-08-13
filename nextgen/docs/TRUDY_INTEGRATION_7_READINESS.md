# Trudy integration-7 readiness

Current integration branch: `trudy/integration-7`.

## Completed

- Trudy text architecture, typed tools and evidence binding
- personal evidence / association / experiment intelligence foundation
- model training/evaluation and promotion-gate foundation
- local-first Kokoro voice runtime
- explicit model installer and app-private storage
- Trudy voice UI, settings, speak/stop and lifecycle handling
- voice contract reconciliation between runtime and UI branches
- signing-secret cleanup
- Data Vault boundary hardening

## Verification still required before merge to 11.2

1. Full integrated Gradle/unit-test/build run.
2. Data-boundary guard execution on the integrated head.
3. Physical-device Kokoro smoke test using `TRUDY_VOICE_DEVICE_SMOKE.md`.

The GitHub Actions validation workflow was triggered on the integration branch, but the job completed as a failure before any workflow steps were exposed/executed. Do not treat the integrated build as verified until an executable CI/local run succeeds.

## Merge policy

Do not merge `trudy/integration-7` into `11.2` until the integrated build/test gate succeeds and the physical-device Kokoro smoke test is acceptable.
