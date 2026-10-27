# Error handling

Everything the SDK throws on purpose is an `ElevenLabsException`. Two other kinds of exception can
reach you, and both mean a bug in the calling code rather than a failure:

- `IllegalArgumentException` for a value the SDK rejects before sending, such as a blank voice ID or
  a `pageSize` of 500.
- `IllegalStateException` for calling a realtime session in the wrong state, such as `sendText()`
  after `finish()`.

Coroutine cancellation passes through untouched.

## Handling failures by kind

Most apps don't need a branch per exception class. Every `ElevenLabsException` has a `kind`, and
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

Use `kind` to pick what the user sees. `Authentication` means the key or token was rejected, `Quota`
means the account is out of credits, and `Validation` means the request had a bad field.

## Handling specific exceptions

When you need details, catch the concrete types:

```kotlin
try {
    elevenLabs.textToSpeech.generate(request)
} catch (error: ApiException.RateLimitExceeded) {
    println("Retry after ${error.retryAfterSeconds} seconds")
} catch (error: ApiException) {
    if (error.validationErrors.isNotEmpty()) {
        error.validationErrors.forEach { issue ->
            println("${issue.location.joinToString(".")}: ${issue.message} (${issue.type})")
        }
    } else {
        println("HTTP ${error.statusCode}: ${error.message}; request=${error.requestId}")
    }
} catch (error: TimeoutError) {
    // The request exceeded a transport timeout.
} catch (error: NetworkError) {
    // No HTTP response was received.
} catch (error: SerializationError) {
    // ElevenLabs returned a success response the SDK couldn't decode.
} catch (error: UnknownError) {
    // Anything else. Log error.cause.
}
```

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

Every `ApiException` has `statusCode`, `message`, `errorCode`, `requestId`, and `validationErrors`.
Include `requestId` when you contact ElevenLabs support.

`responseBody` holds the raw body, cut off after 16,384 characters. It can contain sensitive data,
so keep it out of UI and out of logs that many people can read.

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

When a realtime WebSocket fails at the protocol level, the SDK throws `RealtimeServerError`. Its
`errorCode` is the code ElevenLabs sent, such as `quota_exceeded` or `auth_error`, if there was one.
`closeCode` and `responseBody` help with debugging. To react to specific codes, branch on
`errorCode`:

```kotlin
session.events
    .catch { failure ->
        when ((failure as? RealtimeServerError)?.errorCode) {
            "quota_exceeded" -> showUpgradePrompt()
            "auth_error" -> refreshCredentials()
            else -> throw failure
        }
    }
    .collect(::render)
```

ElevenLabs defines these codes and can change them, so always keep a fallback branch.

The SDK maps the codes it knows to a `kind`, so realtime failures can share handling with HTTP
failures: `auth_error` is `Authentication`, `quota_exceeded` is `Quota`, `rate_limited` is
`RateLimit`, `transcriber_error` is `Server`, and so on. `isRetryable` follows from `kind`. An error
with no code, or with a code the SDK doesn't know, keeps the `Realtime` kind.

A transport failure, such as a dropped connection, throws `NetworkError`. A timeout throws
`TimeoutError`, and a message the SDK can't decode throws `SerializationError`.

If a broad `catch` in your code can catch `CancellationException`, rethrow it.
