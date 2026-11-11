# Error handling

Everything the SDK throws on purpose is an `ElevenLabsException`. Two other kinds of exception can
reach you, and both mean a bug in the calling code rather than a failure:

- `IllegalArgumentException` for a value the SDK rejects before sending, such as a blank voice ID or
  a `pageSize` of 500.
- `IllegalStateException` for calling a realtime session in the wrong state, such as `sendText()`
  after `finish()` or `close()`.

Coroutine cancellation passes through untouched.

On iOS, Swift sees all three as errors you can catch with `do`/`catch`. Constructors that validate
their arguments, such as `VoiceSettings`, are throwing initializers, so they need `try` too.

!!! warning "Two cases still crash a Swift app"

    Kotlin/Native can only turn an exception into a Swift error when the function declares it, and
    these two functions can't:

    - **Collecting a flow directly from Swift.** `Flow.collect` belongs to kotlinx.coroutines, so a
      flow that fails, such as `events` or `stream()`, terminates the app. Collect flows in Kotlin
      code and expose the results to Swift from there.
    - **`doCopy` with an invalid value.** Call the throwing initializer instead.

## Handling failures by kind

`kind` is the main way to handle errors. Every `ElevenLabsException` has one, and it means the same
thing however the failure reached you. A rejected key is `Authentication` on an HTTP request and on
a WebSocket, while `catch (error: ApiException.Unauthorized)` only sees the HTTP case:

```kotlin
try {
    elevenLabs.textToSpeech.generate(request)
} catch (error: ElevenLabsException) {
    when (error.kind) {
        ElevenLabsErrorKind.Authentication -> promptForCredentials()
        ElevenLabsErrorKind.Quota -> showUpgradePrompt()
        ElevenLabsErrorKind.RateLimit -> showTryAgainLater()
        else -> showError(error.message)
    }
}
```

`Authentication` means credentials were rejected or couldn't be obtained, `Quota` means the account
is out of credits, and `Validation` means the request had a bad field. Keep an `else` branch, because
later SDK versions can add kinds.

## Retrying

`isRetryable` is true for network errors, timeouts, rate limits, and server errors. That's enough for
a retry loop:

```kotlin
suspend fun <T> withRetries(attempts: Int = 3, block: suspend () -> T): T {
    repeat(attempts - 1) { attempt ->
        try {
            return block()
        } catch (error: ElevenLabsException) {
            if (!error.isRetryable) throw error
            val waitSeconds = (error as? ApiException.RateLimitExceeded)?.retryAfterSeconds
            delay(waitSeconds?.seconds ?: (attempt + 1).seconds)
        }
    }
    return block()
}

val audio = withRetries { elevenLabs.textToSpeech.generate(request) }
```

The SDK doesn't retry HTTP requests on its own, so a helper like this is up to you.

!!! warning "Retries can use credits twice"

    `isRetryable` means a retry may succeed, not that it's free. A `generate()` that timed out may
    already have run on the server, so retrying it can bill the same text again. Decide whether
    that's acceptable before you retry generation on `Timeout`.

## Error details

Every `ElevenLabsException` has the same optional details, whichever subtype it is:

| Property | Set when |
| --- | --- |
| `statusCode` | The failure came from an HTTP response. |
| `requestId` | ElevenLabs sent a request ID. Include it when you contact support. |
| `errorCode` | ElevenLabs sent a machine-readable code, such as `quota_exceeded`. |
| `responseBody` | The server sent a body. Cut off after 16,384 characters. |

`responseBody` can contain sensitive data, so keep it out of UI and out of logs that many people can
read.

```kotlin
catch (error: ElevenLabsException) {
    log.warn("ElevenLabs ${error.kind}: request=${error.requestId} code=${error.errorCode}")
}
```

## Handling specific exceptions

The subtypes say where a failure happened, so they're a convenience on top of `kind`. Reach for one
when you need something only that subtype has, such as `retryAfterSeconds` or `validationErrors`:

```kotlin
try {
    elevenLabs.textToSpeech.generate(request)
} catch (error: ElevenLabsException) {
    when (error.kind) {
        ElevenLabsErrorKind.RateLimit -> {
            val waitSeconds = (error as? ApiException.RateLimitExceeded)?.retryAfterSeconds
            println("Retry after $waitSeconds seconds")
        }
        ElevenLabsErrorKind.Validation -> {
            (error as? ApiException)?.validationErrors?.forEach { issue ->
                println("${issue.location.joinToString(".")}: ${issue.message} (${issue.type})")
            }
        }
        else -> println("${error.kind}: ${error.message}; request=${error.requestId}")
    }
}
```

| Subtype | Where it failed |
| --- | --- |
| `ApiException` | ElevenLabs returned a non-success HTTP status. |
| `ElevenLabsException.RealtimeServer` | A realtime WebSocket reported an error or closed unexpectedly. |
| `ElevenLabsException.MissingCredentials` | The SDK had no usable credentials, so nothing was sent. |
| `ElevenLabsException.Timeout` | A request or realtime operation exceeded a timeout. |
| `ElevenLabsException.Network` | No response was received, or the connection was lost. |
| `ElevenLabsException.Serialization` | A response or message couldn't be decoded. |
| `ElevenLabsException.Unknown` | Anything else. Log `error.cause`. |

## Credential failures

`ElevenLabsException.MissingCredentials` means nothing was sent, because the SDK had no usable
credentials. No key is configured, or an `ApiKeyProvider` or token provider returned a blank value
or threw. Its `kind` is `Authentication`, and `cause` holds whatever the provider threw.

If your provider throws an `ElevenLabsException` of its own, the SDK reports that one as is.

## HTTP errors

The SDK turns each non-success status into its own `ApiException` subclass:

| Status | Exception | `kind` |
| --- | --- | --- |
| 400 | `ApiException.BadRequest` | `Validation` if field errors came back, otherwise `Api` |
| 401 | `ApiException.Unauthorized` | `Authentication` |
| 402 | `ApiException.PaymentRequired` | `Quota` |
| 403 | `ApiException.Forbidden` | `Permission` |
| 404 | `ApiException.NotFound` | `NotFound` |
| 422 | `ApiException.UnprocessableEntity` | `Validation` |
| 429 | `ApiException.RateLimitExceeded` | `RateLimit` |
| 500–599 | `ApiException.ServerError` | `Server` |
| Any other non-success status | `ApiException.UnknownHttpError` | `Unknown` |

The SDK parses ElevenLabs validation responses in this form into `validationErrors`:

```json
{
  "detail": [
    {
      "loc": ["body", "text"],
      "msg": "must not be blank",
      "type": "value_error"
    }
  ]
}
```

It also reads the common object form, such as
`{"detail":{"status":"quota_exceeded","message":"Quota exceeded"}}`, and fills in `message` and
`errorCode` from it.

## Flows and realtime sessions

HTTP and realtime flows are cold, so they fail while you collect them, not when you create them.

When a realtime WebSocket fails at the protocol level, the SDK throws
`ElevenLabsException.RealtimeServer`. The SDK maps the codes it knows to a `kind`, so realtime
failures share handling with HTTP failures: `auth_error` is `Authentication`, `quota_exceeded` is
`Quota`, `rate_limited` is `RateLimit`, `transcriber_error` is `Server`, and so on. `isRetryable`
follows from `kind`. An error with no code, or with a code the SDK doesn't know, keeps the `Realtime`
kind.

```kotlin
session.events
    .catch { failure ->
        when ((failure as? ElevenLabsException)?.kind) {
            ElevenLabsErrorKind.Quota -> showUpgradePrompt()
            ElevenLabsErrorKind.Authentication -> refreshCredentials()
            else -> throw failure
        }
    }
    .collect(::render)
```

For a code the SDK doesn't map, branch on `errorCode`. ElevenLabs defines these codes and can change
them, so always keep a fallback branch. `closeCode` and `responseBody` help with debugging.

A transport failure, such as a dropped connection, throws `ElevenLabsException.Network`. A timeout
throws `ElevenLabsException.Timeout`, and a message the SDK can't decode throws
`ElevenLabsException.Serialization`.

Once the server or the network has ended a session, later sends such as `sendText()` or `sendAudio()`
throw that same failure, so you can handle them the same way as `events`. A failed send also ends the
session. `IllegalStateException` is only for a session you already finished or closed yourself.
Calling `finish()` or `close()` on a session that already ended does nothing.

If a broad `catch` in your code can catch `CancellationException`, rethrow it.
