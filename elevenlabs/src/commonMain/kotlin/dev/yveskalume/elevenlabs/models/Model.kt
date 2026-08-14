package dev.yveskalume.elevenlabs.models

data class Model(
    val id: String,
    val name: String,
    val description: String? = null,
    val canDoTextToSpeech: Boolean,
    val canDoVoiceConversion: Boolean,
    val canUseStyle: Boolean,
    val canUseSpeakerBoost: Boolean,
    val maximumTextLengthPerRequest: Int? = null,
    val languages: List<ModelLanguage> = emptyList(),
)

data class ModelLanguage(
    val id: String,
    val name: String,
)

