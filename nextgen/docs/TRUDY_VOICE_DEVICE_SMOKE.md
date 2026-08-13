# Trudy Voice real-device smoke checklist

Run this on a physical Android device before merging `trudy/integration-7` into `11.2`.

## Install / first use

1. Open Trudy and confirm the chat screen appears without loading Kokoro.
2. Tap Speak on an assistant message with Kokoro not installed.
3. Confirm the UI offers an explicit one-time local voice-model install.
4. Start install and confirm progress is visible, app remains responsive, and text chat still works.
5. Kill/restart the app during download once; retry and confirm partial state is not marked installed.
6. Complete installation and confirm READY state.

## Synthesis / playback

1. Speak a short answer and confirm audible output.
2. Speak a long multi-sentence answer and confirm chunked playback remains intelligible with no overlaps.
3. Tap Stop during synthesis and during playback; both should stop promptly.
4. Start one answer, then tap Speak on a different answer; only the newest answer should continue.
5. Submit a new Trudy prompt during playback; speech should stop and chat submission should proceed normally.
6. Change voice and speed; confirm the next utterance uses the new settings.
7. Enable auto-speak, receive one assistant reply, and confirm it speaks once only. Disable auto-speak afterward.

## Lifecycle / resilience

1. Navigate away from Trudy during playback; speech should stop.
2. Background the app during playback; speech should stop.
3. Return to Trudy; text conversation remains usable.
4. Toggle voice off; text Trudy must continue normally.
5. With voice unavailable or synthesis failing, confirm a successful text answer is never converted into a failed chat message.

## Performance observations

Record on the target device:

- cold app startup with Kokoro already installed but unused;
- first voice model load time;
- short-answer synthesis latency;
- long-answer synthesis latency / real-time factor if diagnostics expose it;
- visible frame drops while downloading, loading and speaking;
- memory before first speech, during speech and after leaving Trudy.

## Pass criteria

- no crash or ANR;
- no model load during ordinary app startup;
- no automatic model download;
- text Trudy works regardless of voice state;
- one utterance at a time;
- stop/navigation cancellation is prompt;
- model install survives interruption safely;
- voice output is generated locally and intelligible;
- no unacceptable sustained UI jank or memory pressure on the target device.
