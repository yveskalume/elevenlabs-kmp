package dev.yveskalume.elevenlabs.stt

import dev.yveskalume.elevenlabs.models.ModelId

/** Configuration applied when opening a realtime speech-to-text connection. */
public data class RealtimeSttOptions @Throws(IllegalArgumentException::class) constructor(
    /** Realtime speech-to-text model, or `null` to use the service default. */
    val modelId: ModelId? = null,
    /** Encoding and sample rate of submitted audio. */
    val audioFormat: RealtimeSttAudioFormat = RealtimeSttAudioFormat.Pcm16000,
    /** Expected language code, or `null` for automatic detection. */
    val languageCode: String? = null,
    /** Additional language codes that may occur in the audio. */
    val secondaryLanguages: List<String> = emptyList(),
    /** Strategy used to commit buffered audio. */
    val commitStrategy: RealtimeSttCommitStrategy = RealtimeSttCommitStrategy.Manual,
    /** Whether final transcript events should include timestamped units. */
    val includeTimestamps: Boolean = false,
    /** Whether transcript events should include detected language information. */
    val includeLanguageDetection: Boolean = false,
    /** Up to 50 non-blank terms that bias transcription toward expected vocabulary. */
    val keyterms: List<String> = emptyList(),
    /** Whether to avoid verbatim transcription behavior supported by the service. */
    val noVerbatim: Boolean = false,
    /** Whether request logging is enabled on ElevenLabs. */
    val enableLogging: Boolean = true,
) {
    init {
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(secondaryLanguages.all(String::isNotBlank)) { "secondaryLanguages cannot contain blanks." }
        require(keyterms.size <= 50) { "Realtime STT supports at most 50 keyterms." }
        require(keyterms.all(String::isNotBlank)) { "keyterms cannot contain blanks." }
    }
}
