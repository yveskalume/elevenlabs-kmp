package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.stt.RealtimeSttOptions
import dev.yveskalume.elevenlabs.stt.SpeechToTextAuthorization
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments

internal fun interface RealtimeSttConnectionFactory {
    suspend fun open(
        options: RealtimeSttOptions,
        authorization: SpeechToTextAuthorization,
    ): RealtimeSttConnection
}

internal class RealtimeSttConnectionFactoryImpl(
    private val http: ElevenLabsHttpClient,
) : RealtimeSttConnectionFactory {
    override suspend fun open(
        options: RealtimeSttOptions,
        authorization: SpeechToTextAuthorization,
    ): RealtimeSttConnection {
        val credentials = authorization.resolveCredentials(http)
        val endpoint = buildRealtimeSttUrl(http.baseUrl, options, credentials.token)
        val session = http.client.webSocketSession {
            method = HttpMethod.Get
            url(endpoint)
            credentials.apiKey?.let { header(ElevenLabsHttpClient.API_KEY_HEADER, it) }
        }
        return RealtimeSttConnectionImpl(session)
    }

    private fun buildRealtimeSttUrl(
        baseUrl: String,
        options: RealtimeSttOptions,
        token: String?,
    ): String = URLBuilder(baseUrl).apply {
        appendPathSegments("v1", "speech-to-text", "realtime")
        protocol = when (protocol) {
            URLProtocol.HTTP -> URLProtocol.WS
            URLProtocol.HTTPS -> URLProtocol.WSS
            else -> protocol
        }
        parameters.append("model_id", options.modelId)
        parameters.append("audio_format", options.audioFormat.value)
        parameters.append("commit_strategy", options.commitStrategy.value)
        parameters.append("include_timestamps", options.includeTimestamps.toString())
        parameters.append("include_language_detection", options.includeLanguageDetection.toString())
        parameters.append("no_verbatim", options.noVerbatim.toString())
        parameters.append("enable_logging", options.enableLogging.toString())
        options.languageCode?.let { parameters.append("language_code", it) }
        options.secondaryLanguages.forEach { parameters.append("secondary_languages", it) }
        options.keyterms.forEach { parameters.append("keyterms", it) }
        token?.let { parameters.append("token", it) }
    }.buildString()

}