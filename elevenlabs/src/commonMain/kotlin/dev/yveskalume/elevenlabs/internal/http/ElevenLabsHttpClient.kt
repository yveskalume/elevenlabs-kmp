package dev.yveskalume.elevenlabs.internal.http

import dev.yveskalume.elevenlabs.ApiKeyProvider
import dev.yveskalume.elevenlabs.ElevenLabsConfiguration
import dev.yveskalume.elevenlabs.error.ApiException
import dev.yveskalume.elevenlabs.error.ElevenLabsErrorDetails
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.error.UnknownError
import dev.yveskalume.elevenlabs.error.truncateDiagnosticBody
import dev.yveskalume.elevenlabs.internal.error.ElevenLabsErrorPayload
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

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

    fun close() {
        if (ownsClient) client.close()
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
                httpResponseValidator()
            }
            return ElevenLabsHttpClient(
                baseUrl = configuration.baseUrl,
                apiKeyProvider = configuration.apiKeyProvider,
                client = client,
                ownsClient = true
            )
        }
    }
}

internal fun HttpClientConfig<*>.httpResponseValidator() {
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status.value !in 200..299) {
                throw response.toApiException()
            }
        }

        handleResponseExceptionWithRequest { cause, _ ->
            throw cause.toHttpFailure()
        }
    }
}

/** Normalizes failures that happen both inside and after Ktor's response pipeline. */
internal fun Throwable.toHttpFailure(): Throwable = when (this) {
    is CancellationException,
    is ElevenLabsException -> this

    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException -> TimeoutError(this)

    is ContentConvertException,
    is SerializationException -> SerializationError(this)

    is IOException -> NetworkError(this)
    else -> UnknownError(this)
}

private suspend fun HttpResponse.toApiException(): ApiException {
    val body = bodyAsText()
    val payload = body.takeIf(String::isNotBlank)?.let { value ->
        runCatching { JSON.decodeFromString<ElevenLabsErrorPayload>(value) }.getOrNull()
    }
    val error = ElevenLabsErrorDetails(
        statusCode = status.value,
        message = payload?.parsedMessage ?: "ElevenLabs returned HTTP ${status.value}.",
        requestId = headers[ElevenLabsHttpClient.REQUEST_ID_HEADER],
        responseBody = body.takeIf(String::isNotBlank).truncateDiagnosticBody(),
        errorCode = payload?.parsedCode,
        validationErrors = payload?.validationErrors.orEmpty(),
    )

    return when (status.value) {
        400 -> ApiException.BadRequest(error)
        401 -> ApiException.Unauthorized(error)
        402 -> ApiException.PaymentRequired(error)
        403 -> ApiException.Forbidden(error)
        404 -> ApiException.NotFound(error)
        422 -> ApiException.UnprocessableEntity(error)
        429 -> ApiException.RateLimitExceeded(
            error = error,
            retryAfterSeconds = headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
        )
        in 500..599 -> ApiException.ServerError(error)
        else -> ApiException.UnknownHttpError(error)
    }
}
