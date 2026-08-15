package dev.yveskalume.elevenlabs.tts

/** Supplies a fresh single-use token immediately before a realtime connection is opened. */
fun interface RealtimeTokenProvider {
    suspend fun getToken(): String
}