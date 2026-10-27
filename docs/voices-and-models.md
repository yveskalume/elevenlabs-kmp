# Voices and models

Every text-to-speech call needs a voice ID, and most accept a model ID. You can hard-code both, but
listing them at runtime lets users pick a voice and keeps you from sending a model a setting it
doesn't support.

## Listing voices

`voices.list()` returns one page at a time. The default page holds 10 voices, and you can ask for up
to 100:

```kotlin
val page = elevenLabs.voices.list(
    ListVoicesRequest(
        pageSize = 25,
        search = "narrator",
    ),
)

page.voices.forEach { voice ->
    println("${voice.name} (${voice.id}) ${voice.labels}")
}
```

`search` matches free text. `voiceType` and `category` are passed to ElevenLabs as-is, so use the
values from the [ElevenLabs voices API](https://elevenlabs.io/docs/api-reference/voices/search).

To fetch the next page, pass the token from the current one back in:

```kotlin
suspend fun allVoices(): List<Voice> {
    val voices = mutableListOf<Voice>()
    var request = ListVoicesRequest(pageSize = 100)
    while (true) {
        val page = elevenLabs.voices.list(request)
        voices += page.voices
        val token = page.nextPageToken
        if (!page.hasMore || token == null) return voices
        request = request.copy(nextPageToken = token)
    }
}
```

`totalCount` is `null` unless you set `includeTotalCount = true`. Leave it off if you only page
forward.

Each `Voice` has a `previewUrl` when ElevenLabs provides one. It points at a short sample you can
play in a voice picker without spending characters on generation.

## Getting one voice

```kotlin
val voice = elevenLabs.voices.get("JBFqnCBsd6RMkjVDRZzb")
```

`JBFqnCBsd6RMkjVDRZzb` is one of the ElevenLabs premade voices, which is why the examples in these
docs use it. Any voice ID from `voices.list()` works the same way.

## Listing models

`models.list()` returns every model your account can use, in a single call:

```kotlin
val ttsModels = elevenLabs.models.list().filter { it.canDoTextToSpeech }
```

The capability flags tell you what a model accepts before you send a request:

| Property | Meaning |
| --- | --- |
| `canDoTextToSpeech` | The model can generate speech. |
| `canUseStyle` | The model accepts `VoiceSettings.style`. |
| `canUseSpeakerBoost` | The model accepts `VoiceSettings.useSpeakerBoost`. |
| `maximumTextLengthPerRequest` | The longest input one request may contain, when ElevenLabs reports it. |
| `languages` | The languages the model supports. |

The model list changes rarely, so fetching it once per app launch is usually enough.
