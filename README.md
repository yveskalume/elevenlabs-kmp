# ElevenLabs KMP

A Kotlin-first Multiplatform SDK for the ElevenLabs API.

The project currently targets Android, iOS (`iosArm64` and `iosSimulatorArm64`), and JVM. Its public API uses coroutines and `Flow`.

## Current scope

- API-key authentication with a suspendable provider
- Paginated voice listing and voice retrieval
- Model listing
- Complete text-to-speech generation
- HTTP audio streaming
- Realtime text-input streaming over WebSockets
- Batch speech-to-text transcription
- Realtime speech-to-text over WebSockets

## Usage

Create a client in a trusted environment:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

Credentials can be resolved immediately before each request:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(
        ApiKeyProvider {
            credentialsStore.currentElevenLabsKey()
        },
    )
}
```

List voices:

```kotlin
val page = elevenLabs.voices.list(
    ListVoicesRequest(
        pageSize = 25,
        search = "narrator",
    ),
)
```

Generate complete audio:

```kotlin
val audio = elevenLabs.textToSpeech.generate(
    TextToSpeechRequest(
        voiceId = "JBFqnCBsd6RMkjVDRZzb",
        text = "Hello from Kotlin Multiplatform.",
        modelId = "eleven_multilingual_v2",
    ),
)

saveAudio(audio.bytes)
```

Stream audio over HTTP when the complete input text is already available:

```kotlin
elevenLabs.textToSpeech.stream(
    TextToSpeechRequest(
        voiceId = "JBFqnCBsd6RMkjVDRZzb",
        text = "A longer passage to synthesize.",
    ),
).collect { chunk ->
    audioPipeline.write(chunk.bytes)
}
```

`AudioChunk` represents an arbitrary transport chunk. Its bytes are not guaranteed to contain a complete MP3, PCM, or other codec frame.

Use realtime TTS when text arrives incrementally, such as tokens produced by an LLM:

```kotlin
val llmText: Flow<String> = languageModel.responses()

elevenLabs.textToSpeech.realtime(
    voiceId = "JBFqnCBsd6RMkjVDRZzb",
    text = llmText,
    options = RealtimeTtsOptions(
        modelId = "eleven_flash_v2_5",
        outputFormat = OutputFormat.Pcm_24000,
    ),
).collect { chunk ->
    audioPipeline.write(chunk.bytes)
}
```

The convenience flow opens a WebSocket when collected, sends each non-empty text value in order, finishes after the input flow completes, and closes on cancellation or failure.

Use a session when you need explicit flush control or character alignment:

```kotlin
val session = elevenLabs.textToSpeech.openRealtimeSession(
    voiceId = "JBFqnCBsd6RMkjVDRZzb",
    options = RealtimeTtsOptions(
        modelId = "eleven_flash_v2_5",
        outputFormat = OutputFormat.Pcm_24000,
        syncAlignment = true,
    ),
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

`finish()` gracefully signals that no more text is coming; the events flow completes after ElevenLabs sends its final response. `close()` cancels immediately. A session's `events` flow supports one collector.

When all input text is already available, prefer HTTP `stream()`. Realtime TTS is intended for partial input and can add buffering overhead for complete text.

Transcribe a complete audio or video file with Scribe:

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

For live microphone transcription, open a realtime session and send PCM chunks in the format declared by the options:

```kotlin
val session = elevenLabs.speechToText.openRealtimeSession(
    options = RealtimeSttOptions(
        audioFormat = RealtimeSttAudioFormat.Pcm16000,
        commitStrategy = RealtimeSttCommitStrategy.VoiceActivityDetection,
        includeTimestamps = true,
    ),
)

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
}
```

`RealtimeSttSession` supports manual commits and VAD-based commits. Always call `close()` when microphone capture ends or the screen owning the session is disposed.

The included Android and iOS sample apps expose a **Speech to text** mode. They request microphone permission, capture 16 kHz mono PCM16 audio, show partial transcripts while recording, and commit the final transcript when **Stop and transcribe** is pressed.

Release resources when the client is no longer needed:

```kotlin
elevenLabs.close()
```

## API KEY

Never ship an ElevenLabs API key inside an Android or iOS application. It can be extracted from the app. Production mobile applications should call a trusted backend or use an endpoint-specific short-lived credential supported by ElevenLabs.

Realtime TTS accepts a single-use token obtained from a trusted backend, allowing the SDK client itself to be created without an embedded API key:

```kotlin
val elevenLabs = ElevenLabs { }
val authorization = RealtimeTtsAuthorization.TokenProvider(
    RealtimeTokenProvider {
        // Your authenticated backend obtains the token from ElevenLabs.
        backend.fetchRealtimeTtsToken()
    },
)

elevenLabs.textToSpeech.realtime(
    voiceId = voiceId,
    text = llmText,
    authorization = authorization,
).collect { chunk ->
    audioPipeline.write(chunk.bytes)
}
```

The provider is called once immediately before each WebSocket connection opens. This includes every collection of the cold `realtime()` flow, so a single-use token is never reused or cached by the SDK. If your application has already fetched a token, pass it directly with `RealtimeTtsAuthorization.SingleUseToken(token)`.

Single-use tokens are sent only through the WebSocket query parameter required by ElevenLabs. Configured API keys are sent only through the `xi-api-key` header. Fetch tokens through your own authenticated backend; never put the ElevenLabs API key in the provider or mobile application.

Speech-to-text supports the same mobile-safe pattern for both batch and realtime requests:

```kotlin
val sttAuthorization = SpeechToTextAuthorization.TokenProvider(
    SpeechToTextTokenProvider {
        backend.fetchSpeechToTextToken()
    },
)

val session = elevenLabs.speechToText.openRealtimeSession(
    authorization = sttAuthorization,
)
```

The STT provider is evaluated once per batch request or realtime connection. The backend must return the correct endpoint-specific single-use token; the SDK never caches it.

The Android sample requires an ElevenLabs API key at build time. Add it to the
ignored `local.properties` file:

```properties
ELEVENLABS_API_KEY=your-development-key
```

Build the iOS sample for the simulator with:

```shell
cp iosApp/Configuration/Secrets.xcconfig.example \
  iosApp/Configuration/Secrets.xcconfig
```

Then replace the placeholder in the ignored `Secrets.xcconfig` with a
development API key and build:

Like Android's `local.properties` setup, this embeds the development key in the
sample app. It is not appropriate for a production application.
