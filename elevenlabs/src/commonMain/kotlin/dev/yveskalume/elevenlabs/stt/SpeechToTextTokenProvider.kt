package dev.yveskalume.elevenlabs.stt

/** Supplies single-use credentials for speech-to-text operations. */
public fun interface SpeechToTextTokenProvider {
    /** Returns a fresh endpoint-appropriate single-use token. */
    public suspend fun getToken(): String
}
