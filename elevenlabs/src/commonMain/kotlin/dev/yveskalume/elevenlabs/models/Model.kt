package dev.yveskalume.elevenlabs.models

/** Describes an ElevenLabs model and its supported capabilities. */
public data class Model(
    val id: String,
    val name: String,
    val description: String? = null,
    /** Whether the model supports text-to-speech generation. */
    val canDoTextToSpeech: Boolean,
    /** Whether the model supports voice conversion. */
    val canDoVoiceConversion: Boolean,
    /** Whether the model accepts the style voice setting. */
    val canUseStyle: Boolean,
    /** Whether the model accepts the speaker-boost voice setting. */
    val canUseSpeakerBoost: Boolean,
    /** Maximum input length accepted per request, when reported by the API. */
    val maximumTextLengthPerRequest: Int? = null,
    /** Languages supported by this model. */
    val languages: List<ModelLanguage> = emptyList(),
)

/** A language supported by an ElevenLabs model. */
public data class ModelLanguage(
    val id: String,
    val name: String,
)
