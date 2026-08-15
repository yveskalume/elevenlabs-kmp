# Text to speech

Choose an API based on how the input text becomes available.

| API | Use it when |
| --- | --- |
| `generate()` | You need one complete audio result. |
| `stream()` | All text is available, but audio should arrive incrementally over HTTP. |
| `realtime()` | Text arrives incrementally, such as tokens from a language model. |
| `openRealtimeSession()` | You need explicit send, flush, finish, or alignment control. |

## Complete generation

```kotlin
val audio = elevenLabs.textToSpeech.generate(
    TextToSpeechRequest(
        voiceId = voiceId,
        text = "A complete passage to synthesize.",
        outputFormat = OutputFormat.Mp3_44100_128,
    ),
)
```

## HTTP streaming

```kotlin
elevenLabs.textToSpeech.stream(
    TextToSpeechRequest(
        voiceId = voiceId,
        text = "A longer passage to synthesize.",
    ),
).collect { chunk ->
    audioPipeline.write(chunk.bytes)
}
```

An `AudioChunk` is an arbitrary transport chunk. It is not guaranteed to contain a complete codec
frame or audio container.

## Realtime text input

```kotlin
val llmText: Flow<String> = languageModel.responses()

elevenLabs.textToSpeech.realtime(
    voiceId = voiceId,
    text = llmText,
    options = RealtimeTtsOptions(
        modelId = "eleven_flash_v2_5",
        outputFormat = OutputFormat.Pcm_24000,
    ),
).collect { chunk ->
    audioPipeline.write(chunk.bytes)
}
```

The returned flow is cold. Every collection opens a new session, resolves fresh credentials, sends
each non-empty input value, and closes the session on completion, cancellation, or failure.

## Controllable realtime session

```kotlin
val session = elevenLabs.textToSpeech.openRealtimeSession(
    voiceId = voiceId,
    options = RealtimeTtsOptions(syncAlignment = true),
)

coroutineScope {
    launch {
        session.events.collect { event ->
            when (event) {
                is RealtimeTtsEvent.Audio -> {
                    audioPipeline.write(event.bytes)
                    updateCaptions(event.normalizedAlignment)
                }
                RealtimeTtsEvent.Finished -> Unit
            }
        }
    }

    session.sendText("Hello ")
    session.sendText("from realtime TTS.", flush = true)
    session.finish()
}
```

Use `finish()` for graceful completion. Observe completion through `events`, or call `close()` to
cancel immediately.
