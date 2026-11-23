# ElevenLabs for Kotlin Multiplatform

ElevenLabs KMP is a Kotlin client for the ElevenLabs API. You call it from common code, and it runs on
Android, iOS, and the JVM.

[Get started](getting-started.md){ .md-button .md-button--primary }
[API reference](https://yveskalume.github.io/elevenlabs-kmp/api/){ .md-button }

!!! note

    This is an unofficial community SDK. It isn't affiliated with or endorsed by ElevenLabs.

## What it covers

| You want to | Use | Guide |
| --- | --- | --- |
| Turn text into an audio file | `textToSpeech.generate()` | [Text to speech](text-to-speech.md) |
| Start playback before the whole file is ready | `textToSpeech.stream()` | [Text to speech](text-to-speech.md) |
| Speak an LLM reply while it's still being written | `textToSpeech.streamRealtime()` | [Text to speech](text-to-speech.md) |
| Run several interruptible streams on one connection | `textToSpeech.openMultiContextSession()` | [Text to speech](text-to-speech.md) |
| Voice a conversation between several speakers | `textToDialogue.openMultiContextSession()` | [Text to dialogue](text-to-dialogue.md) |
| Transcribe a recording | `speechToText.transcribe()` | [Speech to text](speech-to-text.md) |
| Show live captions from the microphone | `speechToText.openRealtimeSession()` | [Speech to text](speech-to-text.md) |
| Let users pick a voice | `voices.list()` | [Voices and models](voices-and-models.md) |

## How the API is shaped

One-shot calls are `suspend` functions. Streaming calls are named `stream…()` and return cold
`Flow`s. Nothing is sent until you collect, and cancelling the collector cancels the request.

Requests and options take a trailing lambda where you set only what you need:
`RealtimeTtsOptions { syncAlignment = true }`. Required values, like the voice ID, go in the
parentheses. Call `toBuilder()` to make a changed copy.

Later SDK versions can add new events and error kinds, so give every `when` over events or
`ElevenLabsErrorKind` an `else` branch.

To control a WebSocket yourself, for example to flush text at sentence boundaries or commit audio
at the end of an utterance, open a session. You have to close sessions yourself. See
[Realtime connections](realtime-connections.md).

## Supported targets

| Target | Ktor engine |
| --- | --- |
| Android (API 24+) | OkHttp |
| iOS devices (`iosArm64`) | Darwin |
| iOS simulator on Apple silicon (`iosSimulatorArm64`) | Darwin |
| JVM (11+) | CIO |

There is no `iosX64` target, so the library doesn't run in the iOS simulator on Intel Macs.
