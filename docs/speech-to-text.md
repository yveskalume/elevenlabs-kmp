# Speech to text

There are two ways in. `transcribe()` uploads a finished recording and returns the whole transcript.
A realtime session takes audio as you capture it and sends text back while the person is still
talking.

## Transcribing a file

```kotlin
val transcript = elevenLabs.speechToText.transcribe(
    SpeechToTextRequest(
        audio = recording,
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

`fileName` and `contentType` are sent with the upload, so set them to match the file. Most audio and
video containers work as-is. If you're uploading raw 16 kHz 16-bit PCM with no header, set
`fileFormat = SpeechToTextFileFormat.PcmS16Le16` so ElevenLabs doesn't try to detect a container.

`words` holds one entry per word by default. Set `timestampsGranularity` to `Character` for
per-character timing or `None` if you only need the text. With `diarize = true`, each word also has a
`speakerId`. If you know how many people are talking, `numberOfSpeakers` helps the model split them.

The request keeps a reference to your `ByteArray` instead of copying it, because recordings can be
large. Don't modify the array until `transcribe()` returns.

## Live transcription

Open a session and declare the audio format you'll send. Realtime sessions take raw PCM or µ-law, not
compressed audio, so the format has to match what your microphone code produces:

```kotlin
val session = elevenLabs.speechToText.openRealtimeSession(
    options = RealtimeSttOptions(
        audioFormat = RealtimeSttAudioFormat.Pcm16000,
        commitStrategy = RealtimeSttCommitStrategy.VoiceActivityDetection,
        includeTimestamps = true,
    ),
)
```

Then collect events in one coroutine while you send audio from another:

```kotlin
try {
    coroutineScope {
        launch {
            session.events.collect { event ->
                when (event) {
                    is RealtimeSttEvent.SessionStarted -> Unit
                    is RealtimeSttEvent.PartialTranscript -> showLiveText(event.text)
                    is RealtimeSttEvent.FinalTranscript -> Unit
                    is RealtimeSttEvent.CommittedTranscript -> appendToTranscript(event.text)
                    is RealtimeSttEvent.Unknown -> Unit
                }
            }
        }

        microphone.pcm16MonoChunks(sampleRate = 16_000).collect { chunk ->
            session.sendAudio(chunk)
        }
        session.commit()
    }
} finally {
    session.close()
}
```

`showLiveText`, `appendToTranscript`, and `microphone` stand for your own code. The sample app has
working recorders that capture mono 16-bit PCM:
[Android](https://github.com/yveskalume/elevenlabs-kmp/blob/main/androidApp/src/main/kotlin/dev/yveskalume/elevenlabs/audio/AndroidMicrophoneRecorderImpl.kt)
and [iOS](https://github.com/yveskalume/elevenlabs-kmp/blob/main/sharedUI/src/iosMain/kotlin/dev/yveskalume/elevenlabs/audio/IOSMicrophoneRecorder.kt).

### Which events to show

A `PartialTranscript` is a guess that changes as more audio arrives. Show it as the in-progress line
and replace it with each new partial.

A `CommittedTranscript` is final text for a segment of audio, sent after a commit. Append it to the
transcript you keep.

`FinalTranscript` is a finalized segment that hasn't necessarily been committed yet. Most apps can
ignore it and wait for the commit. `Unknown` carries server messages this version of the SDK doesn't
recognize, so a newer server never breaks an older app.

### When audio gets committed

With `RealtimeSttCommitStrategy.Manual`, the default, nothing is committed until you call `commit()`
or pass `commit = true` to `sendAudio()`. Use it when you know where utterances end, for example a
push-to-talk button.

With `VoiceActivityDetection`, ElevenLabs commits when it hears a pause. You can still call
`commit()` yourself, which is useful at the end of the recording so the last words aren't left
waiting for a pause that never comes.

### Improving accuracy

If you know the language, set `languageCode`. Otherwise it's detected automatically, and
`secondaryLanguages` lists others that may come up. For names and jargon the model is likely to get
wrong, pass up to 50 `keyterms`.
