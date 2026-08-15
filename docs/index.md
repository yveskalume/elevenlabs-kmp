# ElevenLabs for Kotlin Multiplatform

ElevenLabs KMP is a Kotlin-first client for the ElevenLabs API. It provides coroutine and
`Flow`-based APIs from common code on Android, iOS, and JVM.

[Get started](getting-started.md){ .md-button .md-button--primary }
[API reference](https://yveskalume.github.io/elevenlabs-kmp/api/){ .md-button }

## What you can build

- Generate complete audio from text.
- Stream audio over HTTP as it arrives.
- Stream partial text into realtime text-to-speech sessions.
- Transcribe complete audio and video files.
- Transcribe live microphone audio over WebSockets.
- Discover the voices and models available to your account.

## Designed for Kotlin

The SDK exposes suspend functions for one-shot operations and cold `Flow` values for streaming.
Realtime sessions are available when you need explicit control over sending, flushing, committing,
or closing a connection.

!!! important

    This is an unofficial community SDK. It is not affiliated with or endorsed by ElevenLabs.

## Supported targets

| Target | Runtime |
| --- | --- |
| Android | OkHttp |
| iOS device and Apple silicon simulator | Darwin |
| JVM | CIO |
