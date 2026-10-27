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

Then use `0.1.0-SNAPSHOT` as the version.

## Quick start

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}

val audio = elevenLabs.textToSpeech.generate(
    TextToSpeechRequest(
        voiceId = "JBFqnCBsd6RMkjVDRZzb",
        text = "Hello from Kotlin Multiplatform.",
    ),
)
// audio.bytes is MP3 by default.
```

Don't put an API key in an Android or iOS app. Have your backend issue single-use tokens instead,
as described in the [authentication guide](https://yveskalume.github.io/elevenlabs-kmp/authentication/).

## Features

- Text to speech: complete files, HTTP streaming, and realtime WebSocket sessions that accept text as
  it's generated, including multi-context sessions that host several streams on one connection
- Text to dialogue: realtime multi-speaker conversations with Eleven v3
- Speech to text: file transcription with diarization, and live transcription from raw PCM
- Voice and model discovery, with pagination
- API keys, rotating key providers, and single-use tokens
- Typed errors with a `kind` and `isRetryable` on every failure

## Documentation

The guides are at [yveskalume.github.io/elevenlabs-kmp](https://yveskalume.github.io/elevenlabs-kmp):

- [Getting started](https://yveskalume.github.io/elevenlabs-kmp/getting-started/)
- [Text to speech](https://yveskalume.github.io/elevenlabs-kmp/text-to-speech/)
- [Text to dialogue](https://yveskalume.github.io/elevenlabs-kmp/text-to-dialogue/)
- [Speech to text](https://yveskalume.github.io/elevenlabs-kmp/speech-to-text/)
- [Voices and models](https://yveskalume.github.io/elevenlabs-kmp/voices-and-models/)
- [Realtime connections](https://yveskalume.github.io/elevenlabs-kmp/realtime-connections/)
- [Error handling](https://yveskalume.github.io/elevenlabs-kmp/error-handling/)
- [API reference](https://yveskalume.github.io/elevenlabs-kmp/api/)

## Running the samples

The Android and iOS sample apps share one Compose Multiplatform UI, in `sharedUI`, with screens for
text to speech and live transcription. The samples build your development key into the app, so never
ship a build made this way.

For Android, add the following to the ignored `local.properties` file:

```properties
ELEVENLABS_API_KEY=your-development-key
```

For iOS, create the ignored secrets configuration and replace its placeholder value:

```shell
cp iosApp/Configuration/Secrets.xcconfig.example \
  iosApp/Configuration/Secrets.xcconfig
```
