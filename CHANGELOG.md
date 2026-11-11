# Changelog

All notable changes to this project will be documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

## Unreleased

### Added

- Multi-context realtime text-to-speech with `TextToSpeechApi.openMultiContextSession()`: up to five independent contexts share one WebSocket, each with its own voice settings, generation config, pronunciation dictionaries, and audio stream.
- `MultiContextTtsSession.stream()` speaks a `Flow<String>` in a new context; cancelling the collection interrupts only that context.
- `MultiContextTtsContext` extends `RealtimeTtsSession`, so code written for a single realtime session also works with one context.
- Realtime text-to-dialogue with `ElevenLabs.textToDialogue.openMultiContextSession()`: multi-speaker Eleven v3 dialogue over one WebSocket, with voices registered per context, `DialogueLine` inputs with speaker turns, and `DialogueEvent.TurnFinished` boundaries. It shares its engine with multi-context text-to-speech.
- `ElevenLabsException.errorCode` exposes the machine-readable code ElevenLabs reports for realtime TTS, multi-context TTS, and realtime STT errors, such as `quota_exceeded` or `auth_error`.

- `ElevenLabsException.MissingCredentials`, with kind `Authentication`, for a request with no usable credentials. `cause` holds what a failing provider threw.
- `statusCode`, `requestId`, `errorCode`, and `responseBody` on every `ElevenLabsException`, so there is one place to read them.

### Changed

- `ElevenLabsException.RealtimeServer.kind` now reflects a recognized `errorCode` (for example `Authentication`, `Quota`, `RateLimit`, `Server`) instead of always being `Realtime`. As a result, `isRetryable` is `true` for realtime rate-limit and server errors. Errors without a recognized code are unchanged.
- **Breaking:** Error types are nested under `ElevenLabsException`. `UnknownError` no longer clashes with `java.lang.UnknownError`, and the top-level `*Error` types are gone.
- **Breaking:** A missing or blank key, a blank token, and an exception thrown by `ApiKeyProvider`, `RealtimeTokenProvider`, or `SpeechToTextTokenProvider` now throw `ElevenLabsException.MissingCredentials`. These were `IllegalStateException`, `IllegalArgumentException`, `UnknownError`, `RealtimeServerError`, or the provider's own exception, depending on the call. Cancellation and an `ElevenLabsException` from a provider pass through unchanged.
- **Breaking:** Sending on a realtime session or context that the server or transport ended now throws the original `ElevenLabsException`, or `ElevenLabsException.RealtimeServer` if the server closed it without an error, instead of `IllegalStateException`. `IllegalStateException` is now only for a session or context you finished or closed yourself. `finish()` and `close()` on a session that already ended still do nothing.
- **Breaking:** A failed send on a realtime TTS or STT session now fails the session, as it already did for multi-context sessions: `events` terminates with that failure and later sends rethrow it.
- **Breaking (Swift):** Constructors that validate their arguments and `ElevenLabsConfiguration.apiKey(String)` now need `try`.
- A realtime TTS or STT transport cancelled underneath the session now fails with `ElevenLabsException.Network`, as multi-context sessions already did, instead of a `CancellationException` reaching callers that were not cancelled.

### Fixed

- On iOS, `IllegalArgumentException` and `IllegalStateException` no longer crash the app. Public suspend functions and validating constructors now report them as Swift errors, and Swift implementations of `ApiKeyProvider` and the token providers can throw. Two cases still crash because the functions can't declare the exception: collecting a failing `Flow` directly from Swift, and calling `doCopy` with an invalid value.

### Removed

- **Breaking:** `ElevenLabsException.UnexpectedResponse` and `ElevenLabsException.Realtime`, deprecated since 0.1.1, and the unused old `ElevenLabsException.Serialization`.
- **Breaking:** `ElevenLabsErrorDetails`, along with `ApiException.details` and `SerializationError.details`. Read the same values from the exception.

### Migration

| Before | After |
| --- | --- |
| `NetworkError` | `ElevenLabsException.Network` |
| `TimeoutError` | `ElevenLabsException.Timeout` |
| `SerializationError` | `ElevenLabsException.Serialization` |
| `RealtimeServerError` | `ElevenLabsException.RealtimeServer` |
| `UnknownError` | `ElevenLabsException.Unknown` |
| `ElevenLabsException.UnexpectedResponse` | `ApiException` |
| `ElevenLabsException.Realtime` | `ElevenLabsException.RealtimeServer` |
| `error.details.requestId` (also `message`, `errorCode`, `responseBody`, `validationErrors`) | `error.requestId` (and so on) |
| `error.details.statusCode`, `error.statusCode` as `Int` | `error.statusCode`, now `Int?` |
| `catch (e: IllegalStateException)` around a missing key | `catch (e: ElevenLabsException.MissingCredentials)` |
| `catch (e: IllegalStateException)` around a send, then reading `e.cause` | `catch (e: ElevenLabsException)` |
| Swift: `VoiceSettings(...)` | Swift: `try VoiceSettings(...)` |

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
