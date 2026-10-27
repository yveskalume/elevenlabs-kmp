# Getting started

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
            implementation("io.github.yveskalume:elevenlabs-kmp:0.1.0")
        }
    }
}
```

### Snapshots

Development snapshots go to the Central Portal snapshots repository. Add it before Maven Central and
use `0.1.0-SNAPSHOT` as the version:

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

## Create a client

You'll need an API key from your
[ElevenLabs account settings](https://elevenlabs.io/app/settings/api-keys).

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

The examples on this page use JVM APIs such as `System.getenv` and `File` to stay short, so they fit
a server or a desktop tool. Mobile apps shouldn't hold an API key at all;
[Authentication](authentication.md) covers what to do instead.

Create one client and share it. It wraps a single HTTP client, and it's safe to use
from several coroutines at once. Close it when your app or service shuts down:

```kotlin
elevenLabs.close()
```

## Generate speech

```kotlin
val audio = elevenLabs.textToSpeech.generate(
    TextToSpeechRequest(
        voiceId = "JBFqnCBsd6RMkjVDRZzb",
        text = "Hello from Kotlin Multiplatform.",
        modelId = "eleven_multilingual_v2",
    ),
)

// MP3 by default. Write audio.bytes to a file or pass it to a player.
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
        diarize = true,
    ),
)

println(transcript.text)
```

`diarize = true` labels each word with a speaker ID, which helps when more than one person is talking.

## Next steps

- [Text to speech](text-to-speech.md) covers streaming and speaking text as it's generated.
- [Speech to text](speech-to-text.md) covers live transcription from a microphone.
- [Error handling](error-handling.md) lists what each call can throw.
