package dev.yveskalume.elevenlabs.tts

/** Configuration applied when opening a realtime text-to-speech connection. */
public data class RealtimeTtsOptions(
    /** Model identifier, or `null` to use the service default. */
    val modelId: String? = null,
    /** Language code, or `null` to let the selected model infer it. */
    val languageCode: String? = null,
    /** Encoding and sample rate of emitted audio. */
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    /** Optional voice tuning parameters. */
    val voiceSettings: VoiceSettings? = null,
    /** Optional server-side text chunking configuration. */
    val generationConfig: RealtimeTtsGenerationConfig? = null,
    /** Whether request logging is enabled on ElevenLabs. */
    val enableLogging: Boolean = true,
    /** Whether audio events include character alignment information. */
    val syncAlignment: Boolean = false,
    /** Whether the service should parse SSML tags in submitted text. */
    val enableSsmlParsing: Boolean = false,
    /** Client and server timeout configuration. */
    val timeouts: RealtimeTtsTimeouts = RealtimeTtsTimeouts(),
    /** Keepalive behavior for otherwise idle connections. */
    val keepAlive: RealtimeTtsKeepAlive = RealtimeTtsKeepAlive(),
    /** Reconnection behavior after a transient connection failure. */
    val reconnectPolicy: RealtimeTtsReconnectPolicy = RealtimeTtsReconnectPolicy.Never,
) {
    init {
        require(modelId == null || modelId.isNotBlank()) { "modelId cannot be blank." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(
            !keepAlive.enabled ||
                keepAlive.intervalMillis < timeouts.inactivityTimeoutSeconds * 1_000L,
        ) {
            "The keepalive interval must be shorter than the ElevenLabs inactivity timeout."
        }
    }
}
