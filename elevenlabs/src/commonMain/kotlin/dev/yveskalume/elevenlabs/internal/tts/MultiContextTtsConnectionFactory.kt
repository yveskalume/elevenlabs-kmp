package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.tts.MultiContextTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAuthorization
import dev.yveskalume.elevenlabs.tts.resolveCredentials
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments

internal fun interface MultiContextTtsConnectionFactory {
    suspend fun open(
        voiceId: String,
        options: MultiContextTtsOptions,
        authorization: RealtimeTtsAuthorization,
    ): RealtimeTtsConnection
}

internal class MultiContextTtsConnectionFactoryImpl(
    private val http: ElevenLabsHttpClient,
) : MultiContextTtsConnectionFactory {
    override suspend fun open(
        voiceId: String,
        options: MultiContextTtsOptions,
        authorization: RealtimeTtsAuthorization,
    ): RealtimeTtsConnection {
        val credentials = authorization.resolveCredentials(http)
        val session = http.client.webSocketSession {
            method = HttpMethod.Get
            url(
                buildMultiContextTtsUrl(
                    baseUrl = http.baseUrl,
                    voiceId = voiceId,
                    options = options,
                    singleUseToken = credentials.singleUseToken,
                ),
            )
            credentials.apiKey?.let {
                header(ElevenLabsHttpClient.API_KEY_HEADER, it)
            }
        }
        return RealtimeTtsConnectionImpl(session)
    }
}

internal fun buildMultiContextTtsUrl(
    baseUrl: String,
    voiceId: String,
    options: MultiContextTtsOptions,
    singleUseToken: String?,
): String = URLBuilder(baseUrl).apply {
    appendPathSegments("v1", "text-to-speech", voiceId, "multi-stream-input")
    protocol = when (protocol) {
        URLProtocol.HTTP -> URLProtocol.WS
        URLProtocol.HTTPS -> URLProtocol.WSS
        else -> protocol
    }
    parameters.append("output_format", options.outputFormat.value)
    parameters.append("enable_logging", options.enableLogging.toString())
    parameters.append("sync_alignment", options.syncAlignment.toString())
    parameters.append("auto_mode", options.autoMode.toString())
    parameters.append("apply_text_normalization", options.applyTextNormalization.value)
    parameters.append("enable_ssml_parsing", options.enableSsmlParsing.toString())
    parameters.append("inactivity_timeout", options.timeouts.inactivityTimeoutSeconds.toString())
    options.modelId?.let { parameters.append("model_id", it) }
    options.languageCode?.let { parameters.append("language_code", it) }
    options.seed?.let { parameters.append("seed", it.toString()) }
    singleUseToken?.let { parameters.append("single_use_token", it) }
}.buildString()
