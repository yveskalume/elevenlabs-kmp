package dev.yveskalume.elevenlabs.internal.http

import dev.yveskalume.elevenlabs.ApiKeyProvider
import dev.yveskalume.elevenlabs.ElevenLabsConfiguration
import dev.yveskalume.elevenlabs.ElevenLabsError
import dev.yveskalume.elevenlabs.ElevenLabsException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class ElevenLabsHttpClient(
    val baseUrl: String,
    private val apiKeyProvider: ApiKeyProvider?,
    val client: HttpClient,
    private val ownsClient: Boolean,
) {

    fun url(vararg segments: String): String = URLBuilder(baseUrl).apply {
        appendPathSegments(*segments)
    }.buildString()


    context(builder: HttpRequestBuilder)
    suspend fun authenticate() {
        builder.header(API_KEY_HEADER, resolveApiKey())
    }

    suspend fun resolveApiKey(): String {
        val provider = checkNotNull(apiKeyProvider) {
            "No ElevenLabs API key is configured. Configure one on the client or use a single-use token for realtime TTS."
        }
        val apiKey = provider.getApiKey()
        require(apiKey.isNotBlank()) { "The ElevenLabs API key provider returned a blank key." }
        return apiKey
    }

    suspend fun validate(response: HttpResponse) {
        if (response.status.value in 200..299) return

        val body = response.bodyAsText()
        val error = ElevenLabsError(
            statusCode = response.status.value,
            message = errorMessage(body) ?: "ElevenLabs returned HTTP ${response.status.value}.",
            requestId = response.headers[REQUEST_ID_HEADER],
            responseBody = body.takeIf(String::isNotBlank),
        )
        throw ElevenLabsException.UnexpectedResponse(error)
    }

    fun close() {
        if (ownsClient) client.close()
    }

    private fun errorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching { JSON.parseToJsonElement(body).findMessage() }.getOrNull()
    }

    private fun JsonElement.findMessage(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> {
            this["message"]?.findMessage()
                ?: this["detail"]?.findMessage()
                ?: this["error"]?.findMessage()
        }

        else -> null
    }

    internal companion object {
        const val API_KEY_HEADER = "xi-api-key"
        const val REQUEST_ID_HEADER = "request-id"




        fun create(configuration: ElevenLabsConfiguration): ElevenLabsHttpClient {
            val client = createPlatformHttpClient {
                install(ContentNegotiation) {
                    json(JSON)
                }
                install(WebSockets)
            }
            return ElevenLabsHttpClient(
                configuration.baseUrl,
                configuration.apiKeyProvider,
                client,
                ownsClient = true
            )
        }
    }
}
