package dev.yveskalume.elevenlabs.stt

data class RealtimeSttOptions(
    val modelId: String = "scribe_v2_realtime",
    val audioFormat: RealtimeSttAudioFormat = RealtimeSttAudioFormat.Pcm16000,
    val languageCode: String? = null,
    val secondaryLanguages: List<String> = emptyList(),
    val commitStrategy: RealtimeSttCommitStrategy = RealtimeSttCommitStrategy.Manual,
    val includeTimestamps: Boolean = false,
    val includeLanguageDetection: Boolean = false,
    val keyterms: List<String> = emptyList(),
    val noVerbatim: Boolean = false,
    val enableLogging: Boolean = true,
) {
    init {
        require(modelId.isNotBlank()) { "modelId cannot be blank." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(secondaryLanguages.all(String::isNotBlank)) { "secondaryLanguages cannot contain blanks." }
        require(keyterms.size <= 50) { "Realtime STT supports at most 50 keyterms." }
        require(keyterms.all(String::isNotBlank)) { "keyterms cannot contain blanks." }
    }
}