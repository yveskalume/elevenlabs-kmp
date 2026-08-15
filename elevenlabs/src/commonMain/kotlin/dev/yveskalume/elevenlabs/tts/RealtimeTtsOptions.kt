package dev.yveskalume.elevenlabs.tts

data class RealtimeTtsOptions(
    val modelId: String? = null,
    val languageCode: String? = null,
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    val voiceSettings: VoiceSettings? = null,
    val generationConfig: RealtimeTtsGenerationConfig? = null,
    val enableLogging: Boolean = true,
    val syncAlignment: Boolean = false,
    val enableSsmlParsing: Boolean = false,
) {
    init {
        require(modelId == null || modelId.isNotBlank()) { "modelId cannot be blank." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
    }
}