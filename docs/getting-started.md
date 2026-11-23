# Getting started

The examples on this page use JVM APIs like `System.getenv` and `File` to keep them short. If you're
building an Android or iOS app, read [Authentication](authentication.md) as well: mobile apps
shouldn't hold an API key.

## Installation

The SDK is published to
[Maven Central](https://central.sonatype.com/artifact/io.github.yveskalume/elevenlabs-kmp). Make sure
Maven Central is in your repositories:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Then add the dependency to the common source set of your shared module:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.yveskalume:elevenlabs-kmp:0.2.0")
        }
    }
}
```

## Create a client

You'll need an API key from your
[ElevenLabs account settings](https://elevenlabs.io/app/settings/api-keys).

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

Create one client and share it. It wraps a single HTTP client, and it's safe to use from several
coroutines at once. Close it when your app or service shuts down:

```kotlin
elevenLabs.close()
```

## Generate speech

```kotlin
val audio = elevenLabs.textToSpeech.generate(
    TextToSpeechRequest(
        voiceId = "JBFqnCBsd6RMkjVDRZzb",
        text = "Hello from Kotlin Multiplatform.",
    ) {
        modelId = ModelId.ElevenMultilingualV2
    },
)

// MP3 by default.
File("hello.mp3").writeBytes(audio.bytes)
```

The voice ID belongs to one of the ElevenLabs premade voices. Use
[`voices.list()`](voices-and-models.md) to find others.

## Transcribe audio

```kotlin
val recording: ByteArray = File("meeting.m4a").readBytes()

val transcript = elevenLabs.speechToText.transcribe(
    SpeechToTextRequest(
        audio = recording,
        fileName = "meeting.m4a",
        contentType = "audio/mp4",
    ) {
        diarize = true
    },
)

println(transcript.text)
```

`diarize = true` labels each word with a speaker ID, which helps when more than one person is talking.

## Next steps

- [Text to speech](text-to-speech.md) covers streaming and speaking text as it's generated.
- [Speech to text](speech-to-text.md) covers live transcription from a microphone.
- [Error handling](error-handling.md) lists what each call can throw.
