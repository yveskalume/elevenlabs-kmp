package dev.yveskalume.elevenlabs.tts

class Audio internal constructor(
    bytes: ByteArray,
    val contentType: String?,
    val requestId: String?,
) {
    val bytes: ByteArray = bytes.copyOf()
}