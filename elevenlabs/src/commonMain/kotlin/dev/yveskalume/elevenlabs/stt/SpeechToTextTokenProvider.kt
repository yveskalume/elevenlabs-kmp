package dev.yveskalume.elevenlabs.stt

fun interface SpeechToTextTokenProvider {
    /** Returns a fresh endpoint-appropriate single-use token. */
    suspend fun getToken(): String
}