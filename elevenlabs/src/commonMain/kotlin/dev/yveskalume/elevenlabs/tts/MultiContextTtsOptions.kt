package dev.yveskalume.elevenlabs.tts

data class MultiContextTtsOptions(
    val modelId: String? = null,
    val languageCode: String? = null,
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    val enableLogging: Boolean = true,
    val syncAlignment: Boolean = false,
    val autoMode: Boolean = false,
    val applyTextNormalization: TextNormalization = TextNormalization.Auto,
    val seed: Long? = null,
    val enableSsmlParsing: Boolean = false,
    val timeouts: RealtimeTtsTimeouts = RealtimeTtsTimeouts(),
    val keepAlive: RealtimeTtsKeepAlive = RealtimeTtsKeepAlive(),
) {
    init {
        require(modelId == null || modelId.isNotBlank()) { "modelId cannot be blank." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(seed == null || seed >= 0) { "seed cannot be negative." }
        require(
            !keepAlive.enabled ||
                keepAlive.intervalMillis < timeouts.inactivityTimeoutSeconds * 1_000L,
        ) {
            "The keepalive interval must be shorter than the ElevenLabs inactivity timeout."
        }
    }
}

data class MultiContextTtsContextOptions(
    val voiceSettings: VoiceSettings? = null,
    val generationConfig: RealtimeTtsGenerationConfig? = null,
)

enum class TextNormalization(val value: String) {
    Auto("auto"),
    On("on"),
    Off("off"),
}
