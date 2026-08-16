# Speech to text

The SDK supports complete-file transcription and live WebSocket sessions.

## Complete file

```kotlin
val transcript = elevenLabs.speechToText.transcribe(
    SpeechToTextRequest(
        audio = recordedAudio,
        fileName = "recording.m4a",
        contentType = "audio/mp4",
        modelId = "scribe_v2",
        diarize = true,
    ),
)

println(transcript.text)
transcript.words.forEach { word ->
    println("${word.startSeconds}: ${word.text} (${word.speakerId})")
}
```

The request retains the input `ByteArray` to avoid duplicating large media. Do not mutate that array
until `transcribe()` returns.

## Realtime transcription

Declare the format that your microphone or audio source produces:

```kotlin
val session = elevenLabs.speechToText.openRealtimeSession(
    options = RealtimeSttOptions(
        audioFormat = RealtimeSttAudioFormat.Pcm16000,
        commitStrategy = RealtimeSttCommitStrategy.VoiceActivityDetection,
        includeTimestamps = true,
    ),
)
```

Collect events while sending audio:

```kotlin
coroutineScope {
    launch {
        session.events.collect { event ->
            when (event) {
                is RealtimeSttEvent.PartialTranscript -> showLiveText(event.text)
                is RealtimeSttEvent.CommittedTranscript -> saveText(event.text)
                else -> Unit
            }
        }
    }

    microphone.pcm16MonoChunks(sampleRate = 16_000).collect { chunk ->
        session.sendAudio(chunk)
    }
    session.commit()
    session.close()
}
```

With manual commits, call `commit()` at each desired transcript boundary. With voice activity
detection, ElevenLabs determines the boundaries, though an explicit final commit can still be sent.
