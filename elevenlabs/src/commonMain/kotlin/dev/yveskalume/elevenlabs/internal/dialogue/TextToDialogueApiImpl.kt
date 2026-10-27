package dev.yveskalume.elevenlabs.internal.dialogue

import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueOptions
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueSession
import dev.yveskalume.elevenlabs.dialogue.TextToDialogueApi
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.error.toRealtimeFailure
import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsConnection
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsConnectionImpl
import dev.yveskalume.elevenlabs.internal.tts.wireValue
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAuthorization
import dev.yveskalume.elevenlabs.tts.resolveCredentials
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import kotlinx.coroutines.CancellationException

internal fun interface MultiContextDialogueConnectionFactory {
    suspend fun open(
        options: MultiContextDialogueOptions,
        authorization: RealtimeTtsAuthorization,
    ): RealtimeTtsConnection
}

internal class MultiContextDialogueConnectionFactoryImpl(
    private val http: ElevenLabsHttpClient,
) : MultiContextDialogueConnectionFactory {
    override suspend fun open(
        options: MultiContextDialogueOptions,
        authorization: RealtimeTtsAuthorization,
    ): RealtimeTtsConnection {
        val credentials = authorization.resolveCredentials(http)
        val endpoint = buildMultiContextDialogueUrl(
            baseUrl = http.baseUrl,
            options = options,
            singleUseToken = credentials.singleUseToken,
        )
        val session = http.client.webSocketSession {
            method = HttpMethod.Get
            url(endpoint)
            credentials.apiKey?.let { header(ElevenLabsHttpClient.API_KEY_HEADER, it) }
        }
        return RealtimeTtsConnectionImpl(session)
    }
}

internal class TextToDialogueApiImpl(
    private val http: ElevenLabsHttpClient,
    private val connectionFactory: MultiContextDialogueConnectionFactory =
        MultiContextDialogueConnectionFactoryImpl(http),
) : TextToDialogueApi {

    override suspend fun openMultiContextSession(
        options: MultiContextDialogueOptions,
        authorization: RealtimeTtsAuthorization,
    ): MultiContextDialogueSession = try {
        MultiContextDialogueSessionImpl.open(
            openConnection = { connectionFactory.open(options, authorization) },
            options = options,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: ElevenLabsException) {
        throw exception
    } catch (throwable: Throwable) {
        throw throwable.toRealtimeFailure()
    }
}

internal fun buildMultiContextDialogueUrl(
    baseUrl: String,
    options: MultiContextDialogueOptions,
    singleUseToken: String?,
): String = URLBuilder(baseUrl).apply {
    appendPathSegments("v1", "text-to-dialogue", "multi-stream-input")
    protocol = when (protocol) {
        URLProtocol.HTTP -> URLProtocol.WS
        URLProtocol.HTTPS -> URLProtocol.WSS
        else -> protocol
    }
    parameters.append("output_format", options.outputFormat.value)
    parameters.append("enable_logging", options.enableLogging.toString())
    parameters.append("sync_alignment", options.syncAlignment.toString())
    options.modelId?.let { parameters.append("model_id", it) }
    options.languageCode?.let { parameters.append("language_code", it) }
    options.textNormalization?.let { parameters.append("apply_text_normalization", it.wireValue) }
    options.seed?.let { parameters.append("seed", it.toString()) }
    singleUseToken?.let { parameters.append("single_use_token", it) }
}.buildString()
