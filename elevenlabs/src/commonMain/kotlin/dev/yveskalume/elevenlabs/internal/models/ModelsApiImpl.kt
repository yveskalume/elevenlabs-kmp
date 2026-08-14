package dev.yveskalume.elevenlabs.internal.models

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.models.Model
import dev.yveskalume.elevenlabs.models.ModelsApi
import io.ktor.client.call.body
import io.ktor.client.request.get

internal class ModelsApiImpl(
    private val http: ElevenLabsHttpClient,
) : ModelsApi {

    override suspend fun list(): List<Model> {
        val response = http.client.get("${http.baseUrl}/v1/models") {
            http.run { authenticate() }
        }
        http.validate(response)
        return response.body<List<ModelDto>>().map(ModelDto::toPublic)
    }
}