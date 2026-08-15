# Getting started

## Installation

ElevenLabs KMP is available from
[Maven Central](https://central.sonatype.com/artifact/io.github.yveskalume/elevenlabs-kmp/0.1.0).
Then add the SDK to the shared source
set of your Kotlin Multiplatform project:

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

Then use `0.1.0-SNAPSHOT` as the dependency version.

## Create a client

Create one client and reuse it across concurrent operations:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

The configured key is used automatically by every service.

Call `close()` when the client is no longer needed:

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

saveAudio(audio.bytes)
```

## Transcribe audio

```kotlin
val transcript = elevenLabs.speechToText.transcribe(
    SpeechToTextRequest(
        audio = recordedAudio,
        fileName = "recording.m4a",
        contentType = "audio/mp4",
        diarize = true,
    ),
)

println(transcript.text)
```

Continue with [authentication](authentication.md), [text to speech](text-to-speech.md), or
[speech to text](speech-to-text.md).
