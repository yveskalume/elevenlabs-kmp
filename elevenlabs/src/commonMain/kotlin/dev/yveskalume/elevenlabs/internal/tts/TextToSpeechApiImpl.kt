package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.tts.dtos.toBody
import dev.yveskalume.elevenlabs.tts.Audio
import dev.yveskalume.elevenlabs.tts.AudioChunk
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAuthorization
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsSession
import dev.yveskalume.elevenlabs.tts.TextToSpeechApi
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class TextToSpeechApiImpl(
    private val http: ElevenLabsHttpClient,
    private val realtimeConnectionFactory: RealtimeTtsConnectionFactory = RealtimeTtsConnectionFactoryImpl(
        http
    ),
) : TextToSpeechApi {

    override suspend fun generate(request: TextToSpeechRequest): Audio {
        val response = http.client.preparePost(http.url("v1", "text-to-speech", request.voiceId)) {
            http.run { authenticate() }
            parameter("output_format", request.outputFormat.value)
            parameter("enable_logging", request.enableLogging)
            contentType(ContentType.Application.Json)
            setBody(request.toBody())
        }.execute()
        http.validate(response)
        return Audio(
            bytes = response.body(),
            contentType = response.contentType()?.toString(),
            requestId = response.headers[ElevenLabsHttpClient.REQUEST_ID_HEADER],
        )
    }

    override fun stream(request: TextToSpeechRequest): Flow<AudioChunk> = channelFlow {
        http.client.preparePost(http.url("v1", "text-to-speech", request.voiceId, "stream")) {
            http.run { authenticate() }
            parameter("output_format", request.outputFormat.value)
            parameter("enable_logging", request.enableLogging)
            contentType(ContentType.Application.Json)
            setBody(request.toBody())
        }.execute { response ->
            http.validate(response)
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            while (!channel.isClosedForRead) {
                val count = channel.readAvailable(buffer)
                if (count > 0) send(AudioChunk(buffer.copyOf(count)))
            }
        }

    }

    override fun realtime(
        voiceId: String,
        text: Flow<String>,
        options: RealtimeTtsOptions,
        authorization: RealtimeTtsAuthorization,
    ): Flow<AudioChunk> = flow {
        coroutineScope {
            val session = openRealtimeSession(voiceId, options, authorization)
            val sender = launch {
                text.collect { part ->
                    if (part.isNotEmpty()) session.sendText(part)
                }
                session.finish()
            }

            try {
                session.events.collect { event ->
                    if (event is RealtimeTtsEvent.Audio) {
                        emit(AudioChunk(event.bytes))
                    }
                }
                sender.join()
            } finally {
                sender.cancel()
                withContext(NonCancellable) { session.close() }
            }
        }
    }

    override suspend fun openRealtimeSession(
        voiceId: String,
        options: RealtimeTtsOptions,
        authorization: RealtimeTtsAuthorization,
    ): RealtimeTtsSession {
        require(voiceId.isNotBlank()) { "voiceId cannot be blank." }
        return try {
            val connection = realtimeConnectionFactory.open(voiceId, options, authorization)
            RealtimeTtsSessionImpl.open(connection, options)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: ElevenLabsException) {
            throw exception
        } catch (throwable: Throwable) {
            throw ElevenLabsException.Realtime(
                message = throwable.message ?: "Could not open the realtime TTS connection.",
                cause = throwable,
            )
        }
    }

    private companion object {
        const val STREAM_BUFFER_SIZE = 8 * 1024
    }
}
