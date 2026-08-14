package dev.yveskalume.elevenlabs

/** Supplies an ElevenLabs API key immediately before each request. */
fun interface ApiKeyProvider {
    suspend fun getApiKey(): String
}

