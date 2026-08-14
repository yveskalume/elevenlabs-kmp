package dev.yveskalume.elevenlabs.models

interface ModelsApi {
    suspend fun list(): List<Model>
}