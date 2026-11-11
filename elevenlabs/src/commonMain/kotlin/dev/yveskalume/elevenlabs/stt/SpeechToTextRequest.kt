package dev.yveskalume.elevenlabs.stt

import dev.yveskalume.elevenlabs.models.ModelId
import kotlinx.io.Buffer
import kotlinx.io.RawSource

/**
 * Input for transcribing one complete audio or video file.
 *
 * The audio is read from [openAudio] while it is uploaded, so the file never has to fit in memory.
 * Requests are equal when their settings are equal and they use the same [openAudio] function.
 */
public class SpeechToTextRequest private constructor(
    /** File name included in the multipart upload, such as `recording.m4a`. */
    public val fileName: String,
    /** MIME type of the file, such as `audio/mp4`. */
    public val contentType: String,
    /**
     * Opens the file to upload. The SDK calls it once per upload and closes what it returns, so the
     * request can be sent again, for example on retry.
     */
    public val openAudio: () -> RawSource,
    /** Size of the file in bytes, or `null` when unknown. */
    public val audioSize: Long?,
    /** Speech-to-text model. Batch transcription requires one, so there is no service default. */
    public val modelId: ModelId,
    /** Expected language code, or `null` for automatic detection. */
    public val languageCode: String?,
    /** Whether identifiable non-speech audio events should be tagged in the transcript. */
    public val tagAudioEvents: Boolean,
    /** Expected speaker count from 1 through 32, or `null` when unknown. */
    public val numberOfSpeakers: Int?,
    /** Requested timestamp detail. */
    public val timestampsGranularity: TimestampsGranularity,
    /** Whether to identify speakers in the transcription. */
    public val diarize: Boolean,
    /** Hint describing the uploaded file's raw or container format. */
    public val fileFormat: SpeechToTextFileFormat,
    /** Whether request logging is enabled. */
    public val enableLogging: Boolean,
) {
    init {
        require(fileName.isNotBlank()) { "fileName cannot be blank." }
        require(contentType.isNotBlank()) { "contentType cannot be blank." }
        require(audioSize == null || audioSize > 0) { "audioSize must be greater than 0." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(numberOfSpeakers == null || numberOfSpeakers in 1..32) {
            "numberOfSpeakers must be between 1 and 32."
        }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder =
        Builder(fileName = fileName, contentType = contentType, openAudio = openAudio).also {
            it.audioSize = audioSize
            it.modelId = modelId
            it.languageCode = languageCode
            it.tagAudioEvents = tagAudioEvents
            it.numberOfSpeakers = numberOfSpeakers
            it.timestampsGranularity = timestampsGranularity
            it.diarize = diarize
            it.fileFormat = fileFormat
            it.enableLogging = enableLogging
        }

    /**
     * Builds a [SpeechToTextRequest]. Required values are constructor parameters; the others start
     * at their defaults.
     */
    public class Builder(
        /** File name included in the multipart upload, such as `recording.m4a`. */
        public var fileName: String,
        /** MIME type of the file, such as `audio/mp4`. */
        public var contentType: String,
        /**
         * Opens the file to upload. The SDK calls it once per upload and closes what it returns, so
         * the
         * request can be sent again, for example on retry.
         */
        public var openAudio: () -> RawSource,
    ) {
        /** Size of the file in bytes, or `null` when unknown. */
        public var audioSize: Long? = null
        /**
         * Speech-to-text model. Batch transcription requires one, so there is no service default.
         */
        public var modelId: ModelId = ModelId.ScribeV2
        /** Expected language code, or `null` for automatic detection. */
        public var languageCode: String? = null
        /** Whether identifiable non-speech audio events should be tagged in the transcript. */
        public var tagAudioEvents: Boolean = true
        /** Expected speaker count from 1 through 32, or `null` when unknown. */
        public var numberOfSpeakers: Int? = null
        /** Requested timestamp detail. */
        public var timestampsGranularity: TimestampsGranularity = TimestampsGranularity.Word
        /** Whether to identify speakers in the transcription. */
        public var diarize: Boolean = false
        /** Hint describing the uploaded file's raw or container format. */
        public var fileFormat: SpeechToTextFileFormat = SpeechToTextFileFormat.Other
        /** Whether request logging is enabled. */
        public var enableLogging: Boolean = true

        /**
         * Creates the [SpeechToTextRequest].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): SpeechToTextRequest = SpeechToTextRequest(
            fileName,
            contentType,
            openAudio,
            audioSize,
            modelId,
            languageCode,
            tagAudioEvents,
            numberOfSpeakers,
            timestampsGranularity,
            diarize,
            fileFormat,
            enableLogging,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is SpeechToTextRequest &&
            fileName == other.fileName &&
            contentType == other.contentType &&
            openAudio == other.openAudio &&
            audioSize == other.audioSize &&
            modelId == other.modelId &&
            languageCode == other.languageCode &&
            tagAudioEvents == other.tagAudioEvents &&
            numberOfSpeakers == other.numberOfSpeakers &&
            timestampsGranularity == other.timestampsGranularity &&
            diarize == other.diarize &&
            fileFormat == other.fileFormat &&
            enableLogging == other.enableLogging

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + openAudio.hashCode()
        result = 31 * result + audioSize.hashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + tagAudioEvents.hashCode()
        result = 31 * result + numberOfSpeakers.hashCode()
        result = 31 * result + timestampsGranularity.hashCode()
        result = 31 * result + diarize.hashCode()
        result = 31 * result + fileFormat.hashCode()
        result = 31 * result + enableLogging.hashCode()
        return result
    }

    override fun toString(): String =
        "SpeechToTextRequest(fileName=$fileName, contentType=$contentType, " +
            "openAudio=$openAudio, audioSize=$audioSize, modelId=$modelId, " +
            "languageCode=$languageCode, tagAudioEvents=$tagAudioEvents, " +
            "numberOfSpeakers=$numberOfSpeakers, timestampsGranularity=$timestampsGranularity, " +
            "diarize=$diarize, fileFormat=$fileFormat, enableLogging=$enableLogging)"
}

/**
 * Creates a [SpeechToTextRequest]. Set optional values in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun SpeechToTextRequest(
    fileName: String,
    contentType: String,
    openAudio: () -> RawSource,
    configure: SpeechToTextRequest.Builder.() -> Unit = {},
): SpeechToTextRequest =
    SpeechToTextRequest.Builder(fileName, contentType, openAudio).apply(configure).build()

/**
 * Creates a [SpeechToTextRequest] that uploads [audio] from memory. Set optional values in
 * [configure].
 *
 * The request uses [audio] as it is, without copying it, so don't change it while a transcription
 * is running.
 *
 * @throws IllegalArgumentException if [audio] is empty or a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun SpeechToTextRequest(
    audio: ByteArray,
    fileName: String,
    contentType: String,
    configure: SpeechToTextRequest.Builder.() -> Unit = {},
): SpeechToTextRequest {
    require(audio.isNotEmpty()) { "audio cannot be empty." }
    return SpeechToTextRequest(
        fileName = fileName,
        contentType = contentType,
        openAudio = { Buffer().apply { write(audio) } },
    ) {
        audioSize = audio.size.toLong()
        configure()
    }
}
