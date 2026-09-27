package dev.yveskalume.elevenlabs.internal.http

import dev.yveskalume.elevenlabs.ApiKeyProvider
import dev.yveskalume.elevenlabs.error.ApiException
import dev.yveskalume.elevenlabs.error.ElevenLabsErrorKind
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.UnknownError
import dev.yveskalume.elevenlabs.internal.tts.TextToSpeechApiImpl
import dev.yveskalume.elevenlabs.internal.voices.VoicesApiImpl
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ElevenLabsHttpContractTest {

    @Test
    fun `voice listing resolves fresh credentials and maps the response`() = runTest {
        var keyResolutionCount = 0
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v2/voices", request.url.encodedPath)
            assertEquals("25", request.url.parameters["page_size"])
            assertEquals("next-1", request.url.parameters["next_page_token"])
            assertEquals("narrator", request.url.parameters["search"])
            assertEquals("true", request.url.parameters["include_total_count"])
            assertEquals("key-${keyResolutionCount}", request.headers[ElevenLabsHttpClient.API_KEY_HEADER])

            respond(
                content = """{
                    "voices": [{
                        "voice_id": "voice-1",
                        "name": "Narrator",
                        "category": "premade",
                        "labels": {"accent": "neutral"}
                    }],
                    "has_more": true,
                    "next_page_token": "next-2",
                    "total_count": 42
                }""".trimIndent(),
                headers = jsonHeaders,
            )
        }
        val api = VoicesApiImpl(http(engine) { "key-${++keyResolutionCount}" })

        val page = api.list(
            ListVoicesRequest(
                pageSize = 25,
                nextPageToken = "next-1",
                search = "narrator",
                includeTotalCount = true,
            ),
        )

        assertEquals(1, keyResolutionCount)
        assertEquals("voice-1", page.voices.single().id)
        assertEquals("neutral", page.voices.single().labels["accent"])
        assertTrue(page.hasMore)
        assertEquals("next-2", page.nextPageToken)
        assertEquals(42, page.totalCount)
    }

    @Test
    fun `text to speech uses the wire contract and preserves response metadata`() = runTest {
        val expectedAudio = byteArrayOf(1, 2, 3, 4)
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v1/text-to-speech/voice-1", request.url.encodedPath)
            assertEquals("pcm_24000", request.url.parameters["output_format"])
            assertEquals("false", request.url.parameters["enable_logging"])
            assertEquals("secret", request.headers[ElevenLabsHttpClient.API_KEY_HEADER])
            assertEquals(ContentType.Application.Json, request.body.contentType)

            val body = request.body.toByteArray().decodeToString()
            assertTrue(body.contains("\"text\":\"Hello\""))
            assertTrue(body.contains("\"model_id\":\"eleven_flash_v2_5\""))
            assertTrue(body.contains("\"language_code\":\"en\""))
            assertFalse(body.contains("output_format"))

            respond(
                content = expectedAudio,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("audio/pcm"),
                    ElevenLabsHttpClient.REQUEST_ID_HEADER to listOf("request-1"),
                ),
            )
        }
        val api = TextToSpeechApiImpl(http(engine) { "secret" })

        val audio = api.generate(
            TextToSpeechRequest(
                voiceId = "voice-1",
                text = "Hello",
                modelId = "eleven_flash_v2_5",
                languageCode = "en",
                outputFormat = OutputFormat.Pcm_24000,
                enableLogging = false,
            ),
        )

        assertContentEquals(expectedAudio, audio.bytes)
        assertEquals("audio/pcm", audio.contentType)
        assertEquals("request-1", audio.requestId)
    }

    @Test
    fun `failed request exposes server details without leaking the API key`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("do-not-leak", request.headers[ElevenLabsHttpClient.API_KEY_HEADER])
            respond(
                content = """{"detail":{"message":"quota exceeded"}}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    ElevenLabsHttpClient.REQUEST_ID_HEADER to listOf("request-429"),
                ),
            )
        }
        val api = VoicesApiImpl(http(engine) { "do-not-leak" })

        val exception = assertFailsWith<ApiException.RateLimitExceeded> {
            api.list()
        }

        assertEquals(429, exception.statusCode)
        assertEquals("request-429", exception.requestId)
        assertEquals("quota exceeded", exception.message)
        assertEquals(ElevenLabsErrorKind.RateLimit, exception.kind)
        assertFalse(exception.toString().contains("do-not-leak"))
    }

    @Test
    fun `validation detail array exposes every field error`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{
                    "detail": [
                        {"loc":["body","text"],"msg":"must not be blank","type":"value_error"},
                        {"loc":["query","output_format"],"msg":"unsupported format","type":"enum"}
                    ]
                }""".trimIndent(),
                status = HttpStatusCode.UnprocessableEntity,
                headers = jsonHeaders,
            )
        }
        val api = VoicesApiImpl(http(engine) { "secret" })

        val exception = assertFailsWith<ApiException.UnprocessableEntity> { api.list() }

        assertEquals("body.text: must not be blank; query.output_format: unsupported format", exception.message)
        assertEquals(listOf("body", "text"), exception.validationErrors.first().location)
        assertEquals("must not be blank", exception.validationErrors.first().message)
        assertEquals("value_error", exception.validationErrors.first().type)
        assertEquals(2, exception.validationErrors.size)
        assertEquals(ElevenLabsErrorKind.Validation, exception.kind)
    }

    @Test
    fun `standard detail object exposes code and retry delay`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"detail":{"status":"too_many_concurrent_requests","message":"Try later"}}""",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("12"),
                ),
            )
        }
        val api = VoicesApiImpl(http(engine) { "secret" })

        val exception = assertFailsWith<ApiException.RateLimitExceeded> { api.list() }

        assertEquals("too_many_concurrent_requests", exception.errorCode)
        assertEquals(12, exception.retryAfterSeconds)
    }

    @Test
    fun `invalid success payload becomes sdk serialization error`() = runTest {
        val engine = MockEngine {
            respond(content = "not json", headers = jsonHeaders)
        }
        val api = VoicesApiImpl(http(engine) { "secret" })

        val exception = assertFailsWith<SerializationError> { api.list() }

        assertIs<ContentConvertException>(exception.cause)
        assertEquals(ElevenLabsErrorKind.Serialization, exception.kind)
    }

    @Test
    fun `unclassified transport failure becomes sdk unknown error`() = runTest {
        val engine = MockEngine { error("mock engine failed") }
        val api = VoicesApiImpl(http(engine) { "secret" })

        val exception = assertFailsWith<UnknownError> { api.list() }

        assertEquals(ElevenLabsErrorKind.Unknown, exception.kind)
    }

    private fun http(
        engine: MockEngine,
        apiKey: suspend () -> String,
    ): ElevenLabsHttpClient = ElevenLabsHttpClient(
        baseUrl = "https://api.elevenlabs.io",
        apiKeyProvider = ApiKeyProvider { apiKey() },
        client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(JSON)
            }
            httpResponseValidator()
        },
        ownsClient = true,
    )

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
