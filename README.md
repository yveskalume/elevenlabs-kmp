# ElevenLabs KMP

[![Maven Central](https://img.shields.io/maven-central/v/io.github.yveskalume/elevenlabs-kmp?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.yveskalume/elevenlabs-kmp/0.1.0)
[![CI](https://github.com/yveskalume/elevenlabs-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/yveskalume/elevenlabs-kmp/actions/workflows/ci.yml)
[![Documentation](https://github.com/yveskalume/elevenlabs-kmp/actions/workflows/docs.yml/badge.svg)](https://github.com/yveskalume/elevenlabs-kmp/actions/workflows/docs.yml)

A Kotlin-first Multiplatform SDK for the [ElevenLabs](https://elevenlabs.io/) API, with coroutine and `Flow`-based APIs for Android, iOS and JVM.

> Community-maintained SDK. Not affiliated with or endorsed by ElevenLabs.

## Installation

ElevenLabs KMP is available from Maven Central.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

```kotlin
// Shared module's build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yveskalume:elevenlabs-kmp:0.1.0")
        }
    }
}
```

### Snapshots

Development snapshots are also published to the Central Portal snapshots repository:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            content {
                includeModule("io.github.yveskalume", "elevenlabs-kmp")
            }
        }
        mavenCentral()
    }
}
```

Then replace `0.1.0` with `0.1.0-SNAPSHOT` in the dependency declaration.

## Quick start

Create a client in a trusted environment:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

The configured key is used automatically by every service.

List available voices:

```kotlin
val page = elevenLabs.voices.list(
    ListVoicesRequest(
        pageSize = 25,
        search = "narrator",
    ),
)
```

Generate speech:

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

Call `elevenLabs.close()` when the client is no longer needed.

## Text to speech

Choose the API based on how text becomes available:

| API | Use it when |
| --- | --- |
| `generate()` | You need the complete audio as one result. |
| `stream()` | The complete text is available, but audio should arrive incrementally over HTTP. |
| `realtime()` | Text arrives incrementally, such as tokens from an LLM. |
| `openRealtimeSession()` | You need explicit control over sending, flushing, or alignment events. |

Stream audio over HTTP:

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

An `AudioChunk` is an arbitrary transport chunk and may not contain a complete codec frame.

Stream text and audio in realtime:

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

For explicit control, open a session:

```kotlin
val session = elevenLabs.textToSpeech.openRealtimeSession(
    voiceId = "JBFqnCBsd6RMkjVDRZzb",
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

Use `finish()` to complete a session gracefully or `close()` to cancel it immediately.

## Speech to text

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
```

For live microphone transcription, open a realtime session and send audio in the format declared by its options:

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

Close the session when microphone capture ends.

## Authentication and security

For rotating credentials, configure a provider instead of a static key:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(
        ApiKeyProvider {
            credentialsStore.currentElevenLabsKey()
        },
    )
}
```

Never embed an ElevenLabs API key in a production Android or iOS application. Use short-lived
credentials issued by a trusted backend instead. See the [authentication guide](https://yveskalume.github.io/elevenlabs-kmp/authentication/).

## Supported features

- Paginated voice listing and voice retrieval
- Model listing
- Complete and HTTP-streamed text-to-speech generation
- Realtime text-to-speech over WebSockets
- Batch speech-to-text transcription
- Realtime speech-to-text over WebSockets
- API-key and endpoint-specific token authentication

## Documentation
[https://yveskalume.github.io/elevenlabs-kmp](https://yveskalume.github.io/elevenlabs-kmp)

## Running the samples

The Android and iOS sample apps demonstrate text-to-speech and live microphone transcription. Development keys configured for these samples are embedded in the resulting app and must not be used for production builds.

For Android, add the following to the ignored `local.properties` file:

```properties
ELEVENLABS_API_KEY=your-development-key
```

For iOS, create the ignored secrets configuration and replace its placeholder value:

```shell
cp iosApp/Configuration/Secrets.xcconfig.example \
  iosApp/Configuration/Secrets.xcconfig
```
