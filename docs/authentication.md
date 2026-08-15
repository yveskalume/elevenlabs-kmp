# Authentication

Configure one API key for the client. Every service uses it automatically:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

## Rotating keys

Use a provider when credentials rotate. It is evaluated immediately before each authenticated
request, and returned keys are not cached:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(
        ApiKeyProvider {
            credentialsStore.currentElevenLabsKey()
        },
    )
}
```

## Mobile applications

Never embed an ElevenLabs API key in a production Android or iOS application. Mobile applications
should obtain endpoint-specific, short-lived credentials from a trusted backend.

Pass the credential as an authorization override. For example, with realtime text to speech:

```kotlin
val authorization = RealtimeTtsAuthorization.TokenProvider(
    RealtimeTokenProvider {
        backend.fetchRealtimeTtsToken()
    },
)

elevenLabs.textToSpeech.realtime(
    voiceId = voiceId,
    text = textParts,
    authorization = authorization,
).collect { chunk ->
    audioPipeline.write(chunk.bytes)
}
```

Speech to text provides the equivalent `SpeechToTextAuthorization.TokenProvider`.