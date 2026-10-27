# Realtime connections

Realtime text to speech and speech to text both run over a WebSocket. This page covers what the
guides skip: who owns the connection, how long the SDK waits, and what happens when the network
drops.

## Flows close themselves, sessions don't

`textToSpeech.realtime()` returns a cold flow. Collecting it opens a connection, and the connection
closes when collection ends, whether it completes, fails, or is cancelled. You never touch the
socket.

`openRealtimeSession()` hands the connection to you. On both `textToSpeech` and `speechToText`, you
must call `close()` when you're done, including on error paths, so put it in a `finally` block:

```kotlin
val session = elevenLabs.speechToText.openRealtimeSession()
try {
    // send audio, collect events
} finally {
    session.close()
}
```

`close()` is safe to call more than once.

## One collector per session

A session's `events` flow is single-consumer. Collect it from one coroutine. If two parts of your
app need the events, collect once and fan out yourself, for example into a `SharedFlow`.

Start collecting before you send anything. Events go through a small buffer, and once it's full the
SDK stops reading from the socket until someone collects. A session nobody collects from stalls.

## Timeouts

Realtime text-to-speech sessions take their limits from `RealtimeTtsOptions.timeouts`:

| Setting | Default | What it limits |
| --- | --- | --- |
| `connectTimeoutMillis` | 10 s | Opening the WebSocket. |
| `sendTimeoutMillis` | 10 s | Each `sendText()`, `flush()`, or `finish()` call. |
| `finishTimeoutMillis` | 30 s | How long `finish()` waits for ElevenLabs to deliver the last audio. |
| `inactivityTimeoutSeconds` | 60 s | Sent to ElevenLabs. The server closes the connection after this much silence. Accepts 1 to 180. |

A local timeout fails the session with `TimeoutError`.

```kotlin
val options = RealtimeTtsOptions(
    timeouts = RealtimeTtsTimeouts(
        connectTimeoutMillis = 5_000,
        inactivityTimeoutSeconds = 180,
    ),
)
```

## Keepalive

Text from a language model often arrives in bursts with long pauses between them. To stop
ElevenLabs closing the connection during a pause, the SDK sends a keepalive message every 15 seconds
while the session is idle. Any `sendText()` call resets the timer.

The interval must be shorter than `inactivityTimeoutSeconds`, otherwise the options constructor
throws `IllegalArgumentException`. Turn it off if you'd rather let idle sessions close:

```kotlin
RealtimeTtsOptions(keepAlive = RealtimeTtsKeepAlive(enabled = false))
```

## Reconnecting

By default a dropped connection fails the session. You can opt into automatic reconnects, with one
restriction: the SDK only reconnects before the first audio chunk has arrived.

```kotlin
RealtimeTtsOptions(
    reconnectPolicy = RealtimeTtsReconnectPolicy.BeforeFirstAudio(
        maxAttempts = 3,
        initialDelayMillis = 500,
        maxDelayMillis = 5_000,
    ),
)
```

The restriction exists because a reconnect replays every message you've sent on the new connection.
Before any audio has reached you, that replay is invisible. After audio starts, replaying would make
ElevenLabs speak the same text twice, so the SDK fails the session instead and leaves the decision to
you.

A reconnect is attempted for network errors, timeouts, and server closes with the "going away",
"internal error", or "service restart" close codes. Decoding failures and errors that came with a
server response body are not retried, because a new connection would fail the same way. Delays grow
exponentially from `initialDelayMillis` up to `maxDelayMillis`, with `jitterFactor` (default 0.2)
spreading out retries from many clients.

Each attempt opens a new connection with the same `authorization`. If you use a `TokenProvider`,
it's asked for a fresh token every time.

Realtime speech to text has no reconnect policy. If the connection fails, `events` ends with the
error and you open a new session.
