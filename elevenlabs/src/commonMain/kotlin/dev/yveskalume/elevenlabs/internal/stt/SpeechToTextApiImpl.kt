package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.error.toRealtimeFailure
import dev.yveskalume.elevenlabs.internal.stt.dtos.SpeechToTextResponseDto
import dev.yveskalume.elevenlabs.stt.RealtimeSttOptions
import dev.yveskalume.elevenlabs.stt.RealtimeSttSession
import dev.yveskalume.elevenlabs.stt.SpeechToTextApi
import dev.yveskalume.elevenlabs.stt.SpeechToTextAuthorization
import dev.yveskalume.elevenlabs.stt.SpeechToTextRequest
import dev.yveskalume.elevenlabs.stt.Transcription
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

internal class SpeechToTextApiImpl(
    private val http: ElevenLabsHttpClient,
    private val realtimeConnectionFactory: RealtimeSttConnectionFactory =
        RealtimeSttConnectionFactoryImpl(http),
) : SpeechToTextApi {

    override suspend fun transcribe(
        request: SpeechToTextRequest,
        authorization: SpeechToTextAuthorization,
    ): Transcription {
        val credentials = authorization.resolveCredentials(http)
        val response = http.client.post(http.url("v1", "speech-to-text")) {
            credentials.apiKey?.let { header(ElevenLabsHttpClient.API_KEY_HEADER, it) }
            credentials.token?.let { parameter("token", it) }
            parameter("enable_logging", request.enableLogging)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = request.audio,
                            headers = Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"${request.fileName}\"",
                                )
                                append(HttpHeaders.ContentType, request.contentType)
                            },
                        )
                        append("model_id", request.modelId)
                        request.languageCode?.let { append("language_code", it) }
                        append("tag_audio_events", request.tagAudioEvents.toString())
                        request.numberOfSpeakers?.let { append("num_speakers", it.toString()) }
                        append("timestamps_granularity", request.timestampsGranularity.value)
                        append("diarize", request.diarize.toString())
                        append("file_format", request.fileFormat.value)
                    },
                ),
            )
        }
        return response.body<SpeechToTextResponseDto>().toPublic()
    }

    override suspend fun openRealtimeSession(
        options: RealtimeSttOptions,
        authorization: SpeechToTextAuthorization,
    ): RealtimeSttSession = try {
        RealtimeSttSessionImpl(
            realtimeConnectionFactory.open(options, authorization),
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: ElevenLabsException) {
        throw exception
    } catch (throwable: Throwable) {
        throw throwable.toRealtimeFailure()
    }
}
