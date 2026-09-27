package dev.yveskalume.elevenlabs.error

/** Stable, transport-independent category for an SDK failure. */
public enum class ElevenLabsErrorKind {
    /** The API rejected the request with an HTTP response. */
    Api,
    /** Authentication credentials were rejected or missing. */
    Authentication,
    /** Request fields failed server-side validation. */
    Validation,
    /** The account lacks the required quota or credits. */
    Quota,
    /** The caller is not allowed to use the requested resource or feature. */
    Permission,
    /** The requested resource does not exist. */
    NotFound,
    /** The caller exceeded a server rate limit. */
    RateLimit,
    /** The server returned an internal failure. */
    Server,
    /** The request could not reach or communicate with the server. */
    Network,
    /** A request or realtime operation exceeded its timeout. */
    Timeout,
    /** A response or realtime message could not be decoded. */
    Serialization,
    /** A realtime WebSocket protocol or close failure. */
    Realtime,
    /** An error that does not fit another category. */
    Unknown,
}