package dev.yveskalume.elevenlabs.stt

data class SpeechToTextRequest(
    val audio: ByteArray,
    val fileName: String = "audio.mp3",
    val contentType: String = "audio/mpeg",
    val modelId: String = "scribe_v2",
    val languageCode: String? = null,
    val tagAudioEvents: Boolean = true,
    val numberOfSpeakers: Int? = null,
    val timestampsGranularity: TimestampsGranularity = TimestampsGranularity.Word,
    val diarize: Boolean = false,
    val fileFormat: SpeechToTextFileFormat = SpeechToTextFileFormat.Other,
    val enableLogging: Boolean = true,
) {
    init {
        require(audio.isNotEmpty()) { "audio cannot be empty." }
        require(fileName.isNotBlank()) { "fileName cannot be blank." }
        require(contentType.isNotBlank()) { "contentType cannot be blank." }
        require(modelId.isNotBlank()) { "modelId cannot be blank." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(numberOfSpeakers == null || numberOfSpeakers in 1..32) {
            "numberOfSpeakers must be between 1 and 32."
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SpeechToTextRequest

        if (tagAudioEvents != other.tagAudioEvents) return false
        if (numberOfSpeakers != other.numberOfSpeakers) return false
        if (diarize != other.diarize) return false
        if (enableLogging != other.enableLogging) return false
        if (!audio.contentEquals(other.audio)) return false
        if (fileName != other.fileName) return false
        if (contentType != other.contentType) return false
        if (modelId != other.modelId) return false
        if (languageCode != other.languageCode) return false
        if (timestampsGranularity != other.timestampsGranularity) return false
        if (fileFormat != other.fileFormat) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tagAudioEvents.hashCode()
        result = 31 * result + (numberOfSpeakers ?: 0)
        result = 31 * result + diarize.hashCode()
        result = 31 * result + enableLogging.hashCode()
        result = 31 * result + audio.contentHashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + (languageCode?.hashCode() ?: 0)
        result = 31 * result + timestampsGranularity.hashCode()
        result = 31 * result + fileFormat.hashCode()
        return result
    }
}