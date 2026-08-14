package dev.yveskalume.elevenlabs.tts

data class TextToSpeechRequest(
    val voiceId: String,
    val text: String,
    val modelId: String? = null,
    val languageCode: String? = null,
    val voiceSettings: VoiceSettings? = null,
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    val enableLogging: Boolean = true,
) {
    init {
        require(voiceId.isNotBlank()) { "voiceId cannot be blank." }
        require(text.isNotBlank()) { "text cannot be blank." }
    }
}