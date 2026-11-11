package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.models.ModelId
import dev.yveskalume.elevenlabs.realtime.KeepAlive

/** Configuration applied when opening a realtime text-to-speech connection. */
public class RealtimeTtsOptions private constructor(
    /** Model identifier, or `null` to use the service default. */
    public val modelId: ModelId?,
    /** Language code, or `null` to let the selected model infer it. */
    public val languageCode: String?,
    /** Encoding and sample rate of emitted audio. */
    public val outputFormat: OutputFormat,
    /** Optional voice tuning parameters. */
    public val voiceSettings: VoiceSettings?,
    /** Optional server-side text chunking configuration. */
    public val generationConfig: RealtimeTtsGenerationConfig?,
    /** Whether request logging is enabled on ElevenLabs. */
    public val enableLogging: Boolean,
    /** Whether audio events include character alignment information. */
    public val syncAlignment: Boolean,
    /** Whether the service should parse SSML tags in submitted text. */
    public val enableSsmlParsing: Boolean,
    /** Client and server timeout configuration. */
    public val timeouts: RealtimeTtsTimeouts,
    /** Keepalive behavior for otherwise idle connections. */
    public val keepAlive: KeepAlive,
    /** Reconnection behavior after a transient connection failure. */
    public val reconnectPolicy: RealtimeTtsReconnectPolicy,
) {
    init {
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(
            !keepAlive.enabled ||
                keepAlive.intervalMillis < timeouts.inactivityTimeoutSeconds * 1_000L,
        ) {
            "The keepalive interval must be shorter than the ElevenLabs inactivity timeout."
        }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.modelId = modelId
        it.languageCode = languageCode
        it.outputFormat = outputFormat
        it.voiceSettings = voiceSettings
        it.generationConfig = generationConfig
        it.enableLogging = enableLogging
        it.syncAlignment = syncAlignment
        it.enableSsmlParsing = enableSsmlParsing
        it.timeouts = timeouts
        it.keepAlive = keepAlive
        it.reconnectPolicy = reconnectPolicy
    }

    /** Builds a [RealtimeTtsOptions]. Every value starts at its default. */
    public class Builder {
        /** Model identifier, or `null` to use the service default. */
        public var modelId: ModelId? = null
        /** Language code, or `null` to let the selected model infer it. */
        public var languageCode: String? = null
        /** Encoding and sample rate of emitted audio. */
        public var outputFormat: OutputFormat = OutputFormat.Mp3_44100_128
        /** Optional voice tuning parameters. */
        public var voiceSettings: VoiceSettings? = null
        /** Optional server-side text chunking configuration. */
        public var generationConfig: RealtimeTtsGenerationConfig? = null
        /** Whether request logging is enabled on ElevenLabs. */
        public var enableLogging: Boolean = true
        /** Whether audio events include character alignment information. */
        public var syncAlignment: Boolean = false
        /** Whether the service should parse SSML tags in submitted text. */
        public var enableSsmlParsing: Boolean = false
        /** Client and server timeout configuration. */
        public var timeouts: RealtimeTtsTimeouts = RealtimeTtsTimeouts()
        /** Keepalive behavior for otherwise idle connections. */
        public var keepAlive: KeepAlive = KeepAlive()
        /** Reconnection behavior after a transient connection failure. */
        public var reconnectPolicy: RealtimeTtsReconnectPolicy = RealtimeTtsReconnectPolicy.Never

        /**
         * Creates the [RealtimeTtsOptions].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): RealtimeTtsOptions = RealtimeTtsOptions(
            modelId,
            languageCode,
            outputFormat,
            voiceSettings,
            generationConfig,
            enableLogging,
            syncAlignment,
            enableSsmlParsing,
            timeouts,
            keepAlive,
            reconnectPolicy,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is RealtimeTtsOptions &&
            modelId == other.modelId &&
            languageCode == other.languageCode &&
            outputFormat == other.outputFormat &&
            voiceSettings == other.voiceSettings &&
            generationConfig == other.generationConfig &&
            enableLogging == other.enableLogging &&
            syncAlignment == other.syncAlignment &&
            enableSsmlParsing == other.enableSsmlParsing &&
            timeouts == other.timeouts &&
            keepAlive == other.keepAlive &&
            reconnectPolicy == other.reconnectPolicy

    override fun hashCode(): Int {
        var result = modelId.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + outputFormat.hashCode()
        result = 31 * result + voiceSettings.hashCode()
        result = 31 * result + generationConfig.hashCode()
        result = 31 * result + enableLogging.hashCode()
        result = 31 * result + syncAlignment.hashCode()
        result = 31 * result + enableSsmlParsing.hashCode()
        result = 31 * result + timeouts.hashCode()
        result = 31 * result + keepAlive.hashCode()
        result = 31 * result + reconnectPolicy.hashCode()
        return result
    }

    override fun toString(): String =
        "RealtimeTtsOptions(modelId=$modelId, languageCode=$languageCode, " +
            "outputFormat=$outputFormat, voiceSettings=$voiceSettings, " +
            "generationConfig=$generationConfig, enableLogging=$enableLogging, " +
            "syncAlignment=$syncAlignment, enableSsmlParsing=$enableSsmlParsing, " +
            "timeouts=$timeouts, keepAlive=$keepAlive, reconnectPolicy=$reconnectPolicy)"
}

/**
 * Creates a [RealtimeTtsOptions]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun RealtimeTtsOptions(
    configure: RealtimeTtsOptions.Builder.() -> Unit = {},
): RealtimeTtsOptions = RealtimeTtsOptions.Builder().apply(configure).build()
