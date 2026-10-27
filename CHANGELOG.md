# Changelog

All notable changes to this project will be documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

## Unreleased

### Added

- Multi-context realtime text-to-speech with `TextToSpeechApi.openMultiContextSession()`: up to five independent contexts share one WebSocket, each with its own voice settings, generation config, pronunciation dictionaries, and audio stream.
- `MultiContextTtsSession.stream()` speaks a `Flow<String>` in a new context; cancelling the collection interrupts only that context.
- `MultiContextTtsContext` extends `RealtimeTtsSession`, so code written for a single realtime session also works with one context.
- Realtime text-to-dialogue with `ElevenLabs.textToDialogue.openMultiContextSession()`: multi-speaker Eleven v3 dialogue over one WebSocket, with voices registered per context, `DialogueLine` inputs with speaker turns, and `DialogueEvent.TurnFinished` boundaries. It shares its engine with multi-context text-to-speech.
- `RealtimeServerError.errorCode` exposes the machine-readable code ElevenLabs reports for realtime TTS, multi-context TTS, and realtime STT errors, such as `quota_exceeded` or `auth_error`.

### Changed

- `RealtimeServerError.kind` now reflects a recognized `errorCode` (for example `Authentication`, `Quota`, `RateLimit`, `Server`) instead of always being `Realtime`. As a result, `isRetryable` is `true` for realtime rate-limit and server errors. Errors without a recognized code are unchanged.

## 0.1.1 - 2026-08-22

### Added

- Structured error handling with typed `ApiException` variants for HTTP failures.
- Stable `ElevenLabsErrorKind` classifications and `isRetryable` guidance for application-level recovery.
- Error details including status codes, request IDs, validation errors, response bodies, and rate-limit retry delays.
- Dedicated network, timeout, serialization, realtime server, and unknown error types.
- Error-handling documentation covering HTTP requests, flows, and realtime sessions.

### Changed

- HTTP, text-to-speech, speech-to-text, voice, model, and realtime APIs now report failures through the structured error hierarchy.
- Coroutine cancellation is propagated unchanged, and diagnostic response bodies are capped to avoid retaining unexpectedly large payloads.
- Updated Ktor from 3.5.1 to 3.5.2.

### Migration

- Update imports from `dev.yveskalume.elevenlabs.ElevenLabsException` to types in `dev.yveskalume.elevenlabs.error`.
- Replace generic `UnexpectedResponse` and `Realtime` handling with `ApiException`, `RealtimeServerError`, or the appropriate transport error type.

## 0.1.0 - 2026-08-15

Initial public preview with Android, iOS, and JVM support.

- Voice and model discovery.
- Complete and streaming text-to-speech.
- Realtime text-to-speech.
- Batch and realtime speech-to-text.
- API-key and endpoint-specific single-use token authorization.
