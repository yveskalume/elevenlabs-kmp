package dev.yveskalume.elevenlabs.tts

class AudioChunk internal constructor(bytes: ByteArray) {
    val bytes: ByteArray = bytes.copyOf()
}

