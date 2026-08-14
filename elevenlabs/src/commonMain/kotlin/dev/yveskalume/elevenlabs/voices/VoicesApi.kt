package dev.yveskalume.elevenlabs.voices

interface VoicesApi {
    suspend fun list(request: ListVoicesRequest = ListVoicesRequest()): VoicePage
    suspend fun get(voiceId: String): Voice
}