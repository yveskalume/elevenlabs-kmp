# Authentication

The client takes one API key, and every API on it (voices, models, text to speech, speech to text)
sends that key:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(System.getenv("ELEVENLABS_API_KEY"))
}
```

## Rotating keys

If your key lives in a secret store and can change, pass an `ApiKeyProvider` instead. The SDK calls it
before every authenticated request and never caches the result, so a rotated key takes effect on the
next call:

```kotlin
val elevenLabs = ElevenLabs {
    apiKey(
        ApiKeyProvider {
            credentialsStore.currentElevenLabsKey()
        },
    )
}
```

The provider is a `suspend` function, so it can read from disk or the network. Keep it fast: it runs
on every request.

## Mobile apps

Don't ship an API key inside an Android or iOS app. Anyone can extract it from the binary and spend
your credits with it.

Instead, have your backend create a single-use token and hand it to the app. Tokens are scoped to one
kind of endpoint and expire after 15 minutes. Your backend creates them with its own API key:

```http
POST https://api.elevenlabs.io/v1/single-use-token/{token_type}
xi-api-key: <your key>
```

| Token type | Use it for |
| --- | --- |
| `tts_websocket` | `textToSpeech.realtime()` and `textToSpeech.openRealtimeSession()` |
| `realtime_scribe` | `speechToText.openRealtimeSession()` |
| `batch_scribe` | `speechToText.transcribe()` |

See the [ElevenLabs token reference](https://elevenlabs.io/docs/api-reference/tokens/create) for the
response format.

On the app side, pass a token provider as the `authorization` argument. The SDK calls it for every
new connection or request, which matters because a token only works once:

```kotlin
val authorization = RealtimeTtsAuthorization.TokenProvider(
    RealtimeTokenProvider {
        backend.fetchElevenLabsToken(type = "tts_websocket")
    },
)

elevenLabs.textToSpeech.realtime(
    voiceId = voiceId,
    text = textParts,
    authorization = authorization,
).collect { chunk ->
    player.write(chunk.bytes)
}
```

`backend.fetchElevenLabsToken` stands for your own call to your server. The SDK doesn't talk to your
backend.

Speech to text works the same way with `SpeechToTextAuthorization.TokenProvider`. Both also accept a
`SingleUseToken` if you've already fetched one, but it can only be used for one connection.

An app that only uses tokens can create the client without calling `apiKey()`:

```kotlin
val elevenLabs = ElevenLabs {}
```

Calls that need a key, which are `generate()`, `stream()`, voices, and models, then fail with a "No
ElevenLabs API key is configured" error. Route those through your backend.
