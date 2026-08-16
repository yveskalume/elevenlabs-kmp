package dev.yveskalume.elevenlabs.stt

/** Input for transcribing one complete audio or video file. */
public data class SpeechToTextRequest(
    /**
     * Non-empty contents of the file to upload.
     *
     * The request retains this array to avoid copying potentially large media. Do not mutate it
     * while [SpeechToTextApi.transcribe] is running. Because it participates in equality and hash
     * calculation, do not mutate it while this request is stored in a hashed collection.
     */
    val audio: ByteArray,
    /** File name included in the multipart upload. */
    val fileName: String = "audio.mp3",
    /** MIME type included in the multipart upload. */
    val contentType: String = "audio/mpeg",
    /** Speech-to-text model identifier. */
    val modelId: String = "scribe_v2",
    /** Expected language code, or `null` for automatic detection. */
    val languageCode: String? = null,
    /** Whether identifiable non-speech audio events should be tagged in the transcript. */
    val tagAudioEvents: Boolean = true,
    /** Expected speaker count from 1 through 32, or `null` when unknown. */
    val numberOfSpeakers: Int? = null,
    /** Requested timestamp detail. */
    val timestampsGranularity: TimestampsGranularity = TimestampsGranularity.Word,
    /** Whether to identify speakers in the transcription. */
    val diarize: Boolean = false,
    /** Hint describing the uploaded file's raw or container format. */
    val fileFormat: SpeechToTextFileFormat = SpeechToTextFileFormat.Other,
    /** Whether request logging is enabled. */
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
