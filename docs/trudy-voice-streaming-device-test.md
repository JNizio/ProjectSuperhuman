# Trudy local voice streaming — device test

Use a physical Android device with the local Kitten/Kokoro model installed. Keep the selected voice,
speed, and quality settings identical between builds. Enable Developer mode, clear diagnostics, and
run each build after one short warm-up utterance so model loading does not distort synthesis timing.

## 500+ character response

Ask Trudy to speak the following text exactly (or use an answer of comparable length):

> Your recent sleep pattern shows a steadier bedtime and fewer interruptions than earlier this week.
> Total sleep duration is moving in the right direction, although the final hour was lighter than your
> usual baseline. Resting heart rate remained stable overnight, and the available recovery signals do
> not show an unusual change. Hydration appears slightly below your recent pattern, so drink steadily
> through the afternoon instead of trying to catch up all at once. Your environment was generally
> supportive, with temperature staying close to your preferred range. If your energy feels normal,
> keep today's exercise easy to moderate and use how you feel as the deciding signal. This is a trend
> summary rather than a diagnosis; seek medical help if you have concerning or persistent symptoms.

Test Stop during the third chunk, leave Trudy during another run, then start a replacement response
while a long response is speaking. In every case, confirm that current audio, queued audio, and stale
synthesis stop and that the replacement never contains a chunk from the prior answer.

## Diagnostics to capture

Export the full diagnostic log and compare these events in order:

| Event | Compare |
|---|---|
| `voice.stream.pipeline_started` | input characters, lookahead (`1` or `2`) |
| `voice.stream.plan_created` | total chunk count, first/second/max chunk characters |
| `voice.stream.first_chunk_synthesis_start` | timestamp for first native generation |
| `voice.stream.first_chunk_ready` | first synthesis and audio durations |
| `voice.stream.first_playback_start` | time-to-first-audio (`latencyMs`) |
| `voice.stream.chunk_synthesis_start` | next synthesis begins while playback is active |
| `voice.stream.chunk_ready` | per-chunk characters, synthesis time, and audio duration |
| `voice.stream.lookahead_reserved` | no more than two synthesized/in-flight future chunks |
| `voice.stream.queue_depth` | ordered enqueue/dequeue and depth never above capacity |
| `voice.stream.playback_starvation` | exact device-buffer exhaustion while synthesis is unfinished |
| `voice.stream.synthesis_complete` | total synthesis, audio duration, and RTF |
| `voice.stream.speech_complete` | wall time, played chunk count, and starvation count |
| `voice.stream.cancel_requested` / `voice.stream.cancelled` | prompt cancellation and no later playback |

The key before/after result is fewer and much shorter starvation intervals. There should be no
per-chunk `AudioTrack` teardown, chunk indexes must remain ordered with no duplicates, queue depth
must remain bounded, and the selected voice must sound unchanged.

## Device-dependent limit

When native RTF is at or above 1.0, synthesis cannot permanently outrun playback on that device.
The bounded pipeline and smaller chunks reduce long waits and make ready audio continuous, but a
slow device may still report brief starvation. Record the count and timestamps rather than hiding it
with faster speech or lower quality.
