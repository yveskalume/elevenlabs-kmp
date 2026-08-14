# ElevenLabs KMP

A Kotlin-first Multiplatform SDK for the ElevenLabs API.

The project currently targets Android, iOS (`iosArm64` and `iosSimulatorArm64`), and JVM. Its public API uses coroutines and `Flow`.

## Current scope

- API-key authentication with a suspendable provider
- Paginated voice listing and voice retrieval
- Model listing
- Complete text-to-speech generation
- HTTP audio streaming

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

Release resources when the client is no longer needed:

```kotlin
elevenLabs.close()
```

## Mobile security

Never ship an ElevenLabs API key inside an Android or iOS application. It can be extracted from the app. Production mobile applications should call a trusted backend or use an endpoint-specific short-lived credential supported by ElevenLabs.
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
