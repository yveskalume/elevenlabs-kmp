# Error handling

Operational SDK failures derive from `ElevenLabsException`. Invalid values supplied by the caller
throw `IllegalArgumentException`, invalid realtime session state throws `IllegalStateException`,
and coroutine cancellation is propagated unchanged.

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
    // No HTTP response was obtained.
} catch (error: SerializationError) {
    // A successful response did not match the documented schema.
} catch (error: UnknownError) {
    // An unclassified SDK/engine failure; log the cause and fail safely.
}
```

## HTTP errors

Non-successful HTTP responses are mapped by status:

| Status | Exception |
| --- | --- |
| 400 | `ApiException.BadRequest` |
| 401 | `ApiException.Unauthorized` |
| 402 | `ApiException.PaymentRequired` |
| 403 | `ApiException.Forbidden` |
| 404 | `ApiException.NotFound` |
| 422 | `ApiException.UnprocessableEntity` |
| 429 | `ApiException.RateLimitExceeded` |
| 500–599 | `ApiException.ServerError` |
| Other non-success status | `ApiException.UnknownHttpError` |

Every `ApiException` exposes `details`, `statusCode`, `message`, `errorCode`, `requestId`, and
`validationErrors`. `responseBody` is also available for diagnostics, but it may contain sensitive
information and should not be shown to users or included in unrestricted logs.
Diagnostic response bodies are capped by the SDK to avoid retaining unexpectedly large payloads.

Every `ElevenLabsException` also exposes a stable `kind` classification. Prefer `kind` for broad
application behavior and concrete subtypes when you need details such as `statusCode` or
`retryAfterSeconds`. The `isRetryable` property covers network, timeout, rate-limit, and server
failures.

ElevenLabs validation responses in this form are normalized into `validationErrors`:

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

The common object form, such as
`{"detail":{"status":"quota_exceeded","message":"Quota exceeded"}}`, is also supported.

## Flows and realtime sessions

HTTP and realtime flows are cold, so failures occur while collecting rather than when the flow is
created. Realtime WebSocket protocol failures use `RealtimeServerError`; its `closeCode` and
`responseBody` can provide additional diagnostics. Realtime transport, timeout, and decoding
failures use `NetworkError`, `TimeoutError`, and `SerializationError` respectively. Always rethrow
`CancellationException` if it is caught by a broad application-level catch block.
