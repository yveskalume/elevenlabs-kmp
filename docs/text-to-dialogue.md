# Text to dialogue

Text-to-dialogue generates conversations between several speakers with Eleven v3 models, with
delivery that follows the flow of the conversation. The realtime API hosts up to five independent
dialogue contexts on one WebSocket (`MultiContextDialogueSession.MAX_CONTEXTS`), for example to
prepare the next scene while the current one plays.

```kotlin
val session = elevenLabs.textToDialogue.openMultiContextSession(
    options = MultiContextDialogueOptions(
        modelId = "eleven_v3",
        outputFormat = OutputFormat.Pcm_24000,
    ),
)
```

Connection-level settings, such as the model and output format, are fixed when the session opens.
Unlike text-to-speech, the voices are chosen per context.

## Voices per context

Each context registers the voices of its speakers when it opens, and every line names the voice that
says it. `eleven_v3_conversational`, the default model, allows one voice per context; other Eleven
v3 models allow up to ten (`MultiContextDialogueSession.MAX_VOICES_PER_CONTEXT`).

```kotlin
val scene = session.openContext(
    voiceIds = listOf(aliceVoiceId, bobVoiceId),
    options = MultiContextDialogueContextOptions(stability = 0.4),
)

coroutineScope {
    launch {
        scene.events.collect { event ->
            when (event) {
                is DialogueEvent.Audio -> player.write(event.bytes)
                DialogueEvent.TurnFinished -> markTurnBoundary()
                DialogueEvent.Finished -> Unit
            }
        }
    }

    scene.send(DialogueLine(aliceVoiceId, "Did you hear that?", newTurn = true))
    scene.send(DialogueLine(bobVoiceId, "Hear what? I didn't hear anything.", newTurn = true))
    scene.finish()
}

session.finish()
```

`newTurn = true` tells ElevenLabs that a new speaker turn starts, so it finalizes the previous
turn's delivery. The end of each turn's audio is reported by `DialogueEvent.TurnFinished`.

`stability` is the only voice setting Eleven v3 dialogue supports. Lower values give a broader
emotional range.

## Streaming lines

`stream()` speaks a `Flow<DialogueLine>` in a new context and emits its audio. Cancelling the
collection closes only that context.

```kotlin
session.stream(
    voiceIds = listOf(aliceVoiceId, bobVoiceId),
    lines = scriptWriter.lines(), // Flow<DialogueLine>, e.g. from a language model
).collect { chunk -> player.write(chunk.bytes) }
```

## Lifecycle and errors

| Call | Effect |
| --- | --- |
| `context.finish()` | Generates the remaining text and closes the context. `events` ends with `Finished`. |
| `context.close()` | Stops delivering the context's audio now and frees its slot. ElevenLabs may still generate its remaining text; that audio is discarded. |
| `session.finish()` | Lets ElevenLabs complete every context, then suspends until the connection closes. |
| `session.close()` | Closes the connection now. |

ElevenLabs closes the whole connection on any protocol error, such as a line for a voice that was
not registered on its context. The SDK checks voices and lines before sending, so these mistakes
throw `IllegalArgumentException` and the session stays open. Any remaining server error fails the
session and every open context with a `RealtimeServerError`, whose `errorCode` (for example
`authentication_required`) and `kind` identify the cause.

Contexts are closed by ElevenLabs after 20 seconds without input. Automatic keepalive
(`MultiContextDialogueOptions.keepAlive`) prevents that while a context is open.
