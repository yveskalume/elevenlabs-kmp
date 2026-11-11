# Changelog

All notable changes to this project will be documented in this file.

The project follows [Semantic Versioning](https://semver.org/).

## Unreleased

## 0.2.0 - 2026-11-11

### Added

- Multi-context realtime text-to-speech with `TextToSpeechApi.openMultiContextSession()`: up to five independent contexts share one WebSocket, each with its own voice settings, generation config, pronunciation dictionaries, and audio stream.
- `MultiContextTtsSession.stream()` speaks a `Flow<String>` in a new context; cancelling the collection interrupts only that context.
- `MultiContextTtsContext` extends `RealtimeTtsSession`, so code written for a single realtime session also works with one context.
- Realtime text-to-dialogue with `ElevenLabs.textToDialogue.openMultiContextSession()`: multi-speaker Eleven v3 dialogue over one WebSocket, with voices registered per context, `DialogueLine` inputs with speaker turns, and `DialogueEvent.TurnFinished` boundaries. It shares its engine with multi-context text-to-speech.
- `ElevenLabsException.errorCode` exposes the machine-readable code ElevenLabs reports for realtime TTS, multi-context TTS, and realtime STT errors, such as `quota_exceeded` or `auth_error`.

- `ElevenLabsException.MissingCredentials`, with kind `Authentication`, for a request with no usable credentials. `cause` holds what a failing provider threw.
- `statusCode`, `requestId`, `errorCode`, and `responseBody` on every `ElevenLabsException`, so there is one place to read them.
- `ModelId`, with constants for the models this SDK version knows about. `Model.id` is a `ModelId`, so a listed model can be passed straight to a request.
- `RealtimeSttCommitStrategy.TurnPrediction`.
- `RealtimeTtsEvent.Unknown` and `DialogueEvent.Unknown` carry server messages this SDK version doesn't recognize, like `RealtimeSttEvent.Unknown`.
- `Flow<RealtimeTtsEvent>.audioOnly()` keeps only the audio of realtime events.
- `SpeechToTextRequest` can stream a file from disk through `openAudio`, a function that returns a kotlinx-io `RawSource`, so the file no longer has to fit in memory. The SDK opens a fresh source per upload and closes it.

### Changed

- `ElevenLabsException.RealtimeServer.kind` now reflects a recognized `errorCode` (for example `Authentication`, `Quota`, `RateLimit`, `Server`) instead of always being `Realtime`. As a result, `isRetryable` is `true` for realtime rate-limit and server errors. Errors without a recognized code are unchanged.
- **Breaking:** Error types are nested under `ElevenLabsException`. `UnknownError` no longer clashes with `java.lang.UnknownError`, and the top-level `*Error` types are gone.
- **Breaking:** A missing or blank key, a blank token, and an exception thrown by `ApiKeyProvider`, `RealtimeTokenProvider`, or `SpeechToTextTokenProvider` now throw `ElevenLabsException.MissingCredentials`. These were `IllegalStateException`, `IllegalArgumentException`, `UnknownError`, `RealtimeServerError`, or the provider's own exception, depending on the call. Cancellation and an `ElevenLabsException` from a provider pass through unchanged.
- **Breaking:** Sending on a realtime session or context that the server or transport ended now throws the original `ElevenLabsException`, or `ElevenLabsException.RealtimeServer` if the server closed it without an error, instead of `IllegalStateException`. `IllegalStateException` is now only for a session or context you finished or closed yourself. `finish()` and `close()` on a session that already ended still do nothing.
- **Breaking:** A failed send on a realtime TTS or STT session now fails the session, as it already did for multi-context sessions: `events` terminates with that failure and later sends rethrow it.
- **Breaking (Swift):** Constructors that validate their arguments and `ElevenLabsConfiguration.apiKey(String)` now need `try`.
- **Breaking:** One `Authorization` type, in the root package, replaces `RealtimeTtsAuthorization` and `SpeechToTextAuthorization`, and `SingleUseTokenProvider` replaces `RealtimeTokenProvider` and `SpeechToTextTokenProvider`. `TextToSpeechApi`, `TextToDialogueApi`, and `SpeechToTextApi` all take it.
- **Breaking:** `RealtimeTtsKeepAlive` and `RealtimeTtsAlignment` are now `KeepAlive` and `Alignment` in `dev.yveskalume.elevenlabs.realtime`, since text-to-dialogue uses them too.
- **Breaking:** `RealtimeSttAudioFormat`, `SpeechToTextFileFormat`, `TimestampsGranularity`, and `RealtimeSttCommitStrategy` are open value types instead of enums, like `OutputFormat`. Their constants keep the same names, so code that uses them still compiles, but a `when` over them now needs an `else` branch, and `entries` and `name` are gone. Construct one from a string to use a value ElevenLabs added after this release.
- **Breaking:** Every `modelId` is a `ModelId` instead of a `String`. `null` means the service default everywhere it is allowed. `RealtimeSttOptions.modelId` now defaults to `null`, which ElevenLabs resolves to `scribe_v2_realtime`. `SpeechToTextRequest.modelId` still defaults to `scribe_v2`, because batch transcription requires a model.
- **Breaking:** `TextToSpeechApi.realtime()` is now `streamRealtime()`, matching the other streaming calls, and returns `Flow<RealtimeTtsEvent>` instead of `Flow<AudioChunk>`, so alignment is no longer dropped. Call `audioOnly()` on the result for the previous behavior.
- **Breaking:** A `when` over `RealtimeTtsEvent` or `DialogueEvent` needs an `Unknown` or `else` branch. Later SDK versions can add events and `ElevenLabsErrorKind` entries without a major release, so keep an `else` branch.
- **Breaking:** A second concurrent collector of a session's or context's `events` now fails with `IllegalStateException`, instead of silently splitting the events with the first. Collecting again after the first collector stopped still works.
- `Audio.bytes` and `AudioChunk.bytes` are no longer copied. Byte arrays the SDK returns belong to the caller, and the SDK never touches them again.
- **Breaking:** Request, options, and timeout types are regular classes built with a trailing lambda instead of data classes, so later SDK versions can add fields without breaking binary compatibility. Required values stay in the parentheses: `TextToSpeechRequest(voiceId, text) { modelId = ... }`. `copy()` and `componentN()` are gone; use `toBuilder()`. Each type also has a `Builder` with `build()`, for Swift. `RealtimeTtsGenerationConfig` and `PronunciationDictionaryLocator`, which only have required values, keep a public constructor.
- **Breaking:** `SpeechToTextRequest` requires `fileName` and `contentType`; the `audio.mp3` and `audio/mpeg` defaults are gone. `SpeechToTextRequest(audio, fileName, contentType)` still takes a `ByteArray`, which it no longer copies or compares by content.
- The SDK depends on kotlinx-io-core as an `api` dependency, since `SpeechToTextRequest` exposes `RawSource`.
- A realtime TTS or STT transport cancelled underneath the session now fails with `ElevenLabsException.Network`, as multi-context sessions already did, instead of a `CancellationException` reaching callers that were not cancelled.

### Fixed

- Realtime TTS sessions read the camelCase fields ElevenLabs sends (`isFinal`, `normalizedAlignment`, `charStartTimesMs`). Alignment was always `null` with `syncAlignment = true`, and the final message was ignored. Multi-context sessions were not affected.
- Text-to-dialogue accepts Eleven v4 models, and allows one voice per context with them. It previously rejected any model that didn't start with `eleven_v3`, and would have allowed ten voices for a v4 model.
- On iOS, `IllegalArgumentException` and `IllegalStateException` no longer crash the app. Public suspend functions and validating constructors now report them as Swift errors, and Swift implementations of `ApiKeyProvider` and the token providers can throw. Two cases still crash because the functions can't declare the exception: collecting a failing `Flow` directly from Swift, and calling `doCopy` with an invalid value.
- `ModelsApi.list()` reports a blank `model_id` in the response as `ElevenLabsException.Serialization` instead of an `IllegalArgumentException` from `ModelId`.
- The `SpeechToTextRequest` overload that takes a `ByteArray` always uploads the array's length. Setting `audioSize` in its `configure` block could send a wrong length.
- `Authorization.SingleUseToken.toString()` hides the token, so logging it doesn't leak a credential.
- `RealtimeTtsReconnectPolicy.BeforeFirstAudio.maxAttempts` allows that many reconnects, as documented. The first connection used to count toward it, so a session got one reconnect fewer.
- Options keep their own copies of `keyterms`, `secondaryLanguages`, `pronunciationDictionaries`, and `chunkLengthSchedule`. Changing the list you passed in can no longer bypass validation or change an existing options object.
- `RealtimeTtsEvent.Audio.toString()` shows the byte count instead of an array reference, like `DialogueEvent.Audio`.

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
| `RealtimeTtsAuthorization`, `SpeechToTextAuthorization` | `Authorization` |
| `RealtimeTokenProvider`, `SpeechToTextTokenProvider` | `SingleUseTokenProvider` |
| `dev.yveskalume.elevenlabs.tts.RealtimeTtsKeepAlive` | `dev.yveskalume.elevenlabs.realtime.KeepAlive` |
| `dev.yveskalume.elevenlabs.tts.RealtimeTtsAlignment` | `dev.yveskalume.elevenlabs.realtime.Alignment` |
| `modelId = "eleven_flash_v2_5"` | `modelId = ModelId.ElevenFlashV2_5`, or `ModelId("eleven_flash_v2_5")` |
| `when (format) { ... }` over an STT enum | Add an `else` branch |
| `textToSpeech.realtime(...)` | `textToSpeech.streamRealtime(...).audioOnly()` |
| `when (event)` over `RealtimeTtsEvent` or `DialogueEvent` | Add an `else` branch |
| `RealtimeTtsOptions(syncAlignment = true)` | `RealtimeTtsOptions { syncAlignment = true }` |
| `TextToSpeechRequest(voiceId = v, text = t, modelId = m)` | `TextToSpeechRequest(voiceId = v, text = t) { modelId = m }` |
| `request.copy(nextPageToken = token)` | `request.toBuilder().apply { nextPageToken = token }.build()` |
| `SpeechToTextRequest(audio = bytes)` | `SpeechToTextRequest(bytes, fileName = "audio.mp3", contentType = "audio/mpeg")` |

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
