# Text to speech

Choose an API based on how the input text becomes available.

| API | Use it when |
| --- | --- |
| `generate()` | You need one complete audio result. |
| `stream()` | All text is available, but audio should arrive incrementally over HTTP. |
| `realtime()` | Text arrives incrementally, such as tokens from a language model. |
| `openRealtimeSession()` | You need explicit send, flush, finish, or alignment control. |
| `openMultiContextSession()` | One connection must host several independent streams, such as an agent that can be interrupted. |

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

## Multi-context sessions

A multi-context session keeps one WebSocket open and hosts up to five independent generation
contexts on it (`MultiContextTtsSession.MAX_CONTEXTS`). Each context has its own text buffer,
voice settings, and audio stream. Use it when speech must be interrupted and restarted quickly, when
the next utterance should be prepared while the current one plays, or when several streams share a
voice but need different settings.

```kotlin
val session = elevenLabs.textToSpeech.openMultiContextSession(
    voiceId = voiceId,
    options = MultiContextTtsOptions(
        modelId = "eleven_flash_v2_5",
        outputFormat = OutputFormat.Pcm_24000,
    ),
)
```

Connection-level settings such as the model and output format are fixed when the session opens.
Per-context settings go in `MultiContextTtsContextOptions`.

### Interruptible agent speech

`stream()` speaks a `Flow<String>` in a fresh context and emits its audio. Cancelling the collection
closes only that context, so interrupting the agent is a job cancellation:

```kotlin
var speaking: Job? = null

fun onAgentReply(reply: Flow<String>) {
    speaking?.cancel() // stops the previous reply; late audio for it is discarded
    speaking = scope.launch {
        session.stream(reply).collect { chunk -> player.write(chunk.bytes) }
    }
}
```

### Explicit contexts

For alignment data or finer control, open contexts yourself. A context works like a
`RealtimeTtsSession`: `events` emits `RealtimeTtsEvent`s for that context only, `finish()` completes it
gracefully, and `close()` stops it immediately.

```kotlin
val narrator = session.openContext(
    MultiContextTtsContextOptions(voiceSettings = VoiceSettings(stability = 0.7)),
)
val aside = session.openContext(
    MultiContextTtsContextOptions(voiceSettings = VoiceSettings(stability = 0.2, speed = 1.1)),
)

coroutineScope {
    launch { narrator.events.collect { event -> if (event is RealtimeTtsEvent.Audio) mainTrack.write(event.bytes) } }
    launch { aside.events.collect { event -> if (event is RealtimeTtsEvent.Audio) sideTrack.write(event.bytes) } }

    narrator.sendText("It was a quiet night. ", flush = true)
    aside.sendText("Or so they thought.", flush = true)
    narrator.finish()
    aside.finish()
}

session.finish()
```

### Lifecycle

| Call | Effect |
| --- | --- |
| `context.finish()` | Generates buffered text, then closes the context. `events` ends with `Finished`. |
| `context.close()` | Stops the context now. Buffered and late audio is discarded and the slot is released immediately. |
| `session.finish()` | Lets ElevenLabs complete every context, then suspends until the connection closes. |
| `session.close()` | Closes the connection now. Open context flows complete without an error. |

Failures are scoped to what they affect. An error ElevenLabs reports for one context, or a context
that does not finish within `RealtimeTtsTimeouts.finishTimeoutMillis`, fails only that context's
`events`. A connection failure fails every open context with the same error, and later calls throw
`IllegalStateException` with that error as the cause.

Each context's events are buffered without limit so that a slow consumer never stalls the others
sharing the connection. Collect every context you open, or close it when its audio is no longer
needed.

Automatic keepalive (`MultiContextTtsOptions.keepAlive`) keeps idle contexts from hitting the
ElevenLabs inactivity timeout. A context that the server closes anyway completes with `Finished`,
and further `sendText()` calls throw `IllegalStateException`.

Multi-context sessions do not reconnect automatically. Replaying text on a new connection could
duplicate audio across several contexts, so reconnecting is left to the application.
