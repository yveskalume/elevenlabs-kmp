package dev.yveskalume.elevenlabs.internal.voices

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import dev.yveskalume.elevenlabs.voices.Voice
import dev.yveskalume.elevenlabs.voices.VoicePage
import dev.yveskalume.elevenlabs.voices.VoicesApi
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal class VoicesApiImpl(
    private val http: ElevenLabsHttpClient,
) : VoicesApi {
    override suspend fun list(request: ListVoicesRequest): VoicePage {
        val response = http.client.get("${http.baseUrl}/v2/voices") {
            http.run { authenticate() }
            parameter("page_size", request.pageSize)
            parameter("next_page_token", request.nextPageToken)
            parameter("search", request.search)
            parameter("voice_type", request.voiceType)
            parameter("category", request.category)
            parameter("include_total_count", request.includeTotalCount)
        }
        http.validate(response)
        return response.body<VoicePageDto>().let {
            VoicePage(
                voices = it.voices.map(VoiceDto::toPublic),
                hasMore = it.hasMore,
                nextPageToken = it.nextPageToken,
                totalCount = it.totalCount,
            )
        }
    }

    override suspend fun get(voiceId: String): Voice {
        require(voiceId.isNotBlank()) { "voiceId cannot be blank." }
        val response = http.client.get(http.url("v1", "voices", voiceId)) {
            http.run { authenticate() }
        }
        http.validate(response)
        return response.body<VoiceDto>().toPublic()
    }
}