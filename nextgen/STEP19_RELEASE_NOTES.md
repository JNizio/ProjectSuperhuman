# Project Superhuman — Step 19 Release Candidate

Version: `nextgen-step19-rc1` (`versionCode 319`)

## What is included

- Native-first Home dashboard with live shared health data.
- Native Clinical OCR import, editable review, reference ranges, status classification, and remembered ranges.
- Native Nutrition search/diary with bundled food catalogue plus Open Food Facts product lookup; existing scanner handoff remains for camera scanning.
- Native Sleep with Health Connect import, stages, duration, and sleep scoring.
- Native Body & Progress logging with weight/body-fat/waist/goal tracking and trend presentation.
- Native Exercise library, set logging, workout sessions, volume and PR context.
- Native Mindfulness sessions with breathing/meditation/body scan and stress check-ins.
- Native Settings Data Vault with JSON backup, merge restore, and protected reset.
- Blood Pressure remains intentionally deferred and keeps its compatibility route.

## Step 19 safety decisions

- The native RC keeps the `com.projectsuperhuman.next` application id. It therefore does **not** overwrite the mature legacy installation while native parity is still being validated.
- R8/minification and resource shrinking remain disabled for RC1. This is deliberate to reduce regression risk around Compose, ML Kit, barcode libraries, OpenCV and compatibility code.
- The CI artifact remains an installable debug-signed APK for device testing. Production signing/package takeover should only happen after on-device parity and data-migration validation.

## Safe performance work

- SQLDelight reads and writes used by native screens now execute on `Dispatchers.IO` instead of blocking the Compose/UI thread.
- Data Vault export/import/database operations are moved off the UI thread.
- Gradle build cache, parallel execution and incremental Kotlin compilation are enabled for faster CI/local iteration without changing runtime behaviour.

## RC1 test priorities

1. Open every native module from Home and return without crashes.
2. Add Nutrition, Body, Exercise and Mindfulness data and confirm Home refreshes correctly.
3. Sync Sleep through Health Connect and verify score/stages/history.
4. Import several Clinical screenshots and verify OCR review, units and ranges before saving.
5. Export a backup, add new data, restore the backup, and confirm merge behaviour.
6. Confirm the existing mature app remains installed and untouched alongside the RC.
7. Exercise the remaining Nutrition scanner and Blood Pressure compatibility routes.

## Not yet treated as complete

- Blood Pressure native parity.
- Removal of the final Nutrition camera-scanner compatibility handoff.
- Production package/signing takeover of the mature app.
- Aggressive APK shrinking/minification; this should only be enabled after RC regression testing.
