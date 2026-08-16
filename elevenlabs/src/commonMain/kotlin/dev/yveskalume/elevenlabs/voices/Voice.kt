package dev.yveskalume.elevenlabs.voices

/** A voice available for speech generation. */
public data class Voice(
    /** Stable voice identifier accepted by text-to-speech requests. */
    val id: String,
    /** Display name of the voice. */
    val name: String,
    /** Voice category reported by ElevenLabs. */
    val category: String? = null,
    /** Human-readable description, when provided. */
    val description: String? = null,
    /** URL of a preview recording, when provided. */
    val previewUrl: String? = null,
    /** Descriptive metadata attached to the voice. */
    val labels: Map<String, String> = emptyMap(),
)
