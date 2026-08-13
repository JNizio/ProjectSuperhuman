# Trudy merge gate

Target: `11.2`
Source: `trudy/integration-7`

Required before merge:

- integrated Android/shared tests execute successfully;
- integrated Android debug APK assembles successfully;
- Data Vault boundary guard succeeds;
- physical-device Kokoro smoke checklist is completed acceptably.

Current CI state: validation was triggered on the integration branch, but the observed run failed before exposing any job steps. Treat build/test status as unverified until a runnable CI/local environment confirms it.
