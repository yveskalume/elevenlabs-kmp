package dev.yveskalume.elevenlabs.voices

data class Voice(
    val id: String,
    val name: String,
    val category: String? = null,
    val description: String? = null,
    val previewUrl: String? = null,
    val labels: Map<String, String> = emptyMap(),
)

