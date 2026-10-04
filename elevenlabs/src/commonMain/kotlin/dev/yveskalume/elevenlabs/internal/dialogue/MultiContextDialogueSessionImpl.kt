package dev.yveskalume.elevenlabs.internal.dialogue

import dev.yveskalume.elevenlabs.dialogue.DialogueEvent
import dev.yveskalume.elevenlabs.dialogue.DialogueLine
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueContext
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueContextOptions
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueOptions
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueSession
import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextEngine
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextEngineConfig
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsConnection
import dev.yveskalume.elevenlabs.tts.AudioChunk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

internal class MultiContextDialogueSessionImpl private constructor(
    private val engine: MultiContextEngine<DialogueEvent>,
    private val maxVoicesPerContext: Int,
) : MultiContextDialogueSession {

    override suspend fun openContext(
        voiceIds: List<String>,
        options: MultiContextDialogueContextOptions,
        contextId: String?,
    ): MultiContextDialogueContext {
        // ElevenLabs answers an invalid registration by closing the whole connection, so validate
        // here and fail only this call.
        require(voiceIds.isNotEmpty()) { "A dialogue context needs at least one voice." }
        require(voiceIds.all { it.isNotBlank() }) { "Voice IDs cannot be blank." }
        require(voiceIds.toSet().size == voiceIds.size) { "Voice IDs must be distinct." }
        require(voiceIds.size <= maxVoicesPerContext) {
            if (maxVoicesPerContext == 1) {
                "eleven_v3_conversational supports one voice per context. Use another Eleven v3 model " +
                    "in MultiContextDialogueOptions.modelId for multi-speaker contexts."
            } else {
                "A dialogue context supports at most $maxVoicesPerContext voices."
            }
        }
        val voices = voiceIds.toList()
        val (id, events) = engine.openContext(contextId) { newId ->
            MultiContextDialogueProtocol.register(newId, voices, options)
        }
        return Context(id, voices, events)
    }

    override fun stream(
        voiceIds: List<String>,
        lines: Flow<DialogueLine>,
        options: MultiContextDialogueContextOptions,
    ): Flow<AudioChunk> = flow {
        val context = openContext(voiceIds, options)
        try {
            coroutineScope {
                val sender = launch {
                    lines.collect { context.send(it) }
                    context.finish()
                }
                context.events.collect { event ->
                    if (event is DialogueEvent.Audio) emit(AudioChunk(event.bytes))
                }
                sender.cancel()
            }
        } finally {
            withContext(NonCancellable) { context.close() }
        }
    }

    override suspend fun finish() {
        engine.finish()
    }

    override suspend fun close() {
        engine.close()
    }

    private inner class Context(
        override val id: String,
        override val voiceIds: List<String>,
        override val events: Flow<DialogueEvent>,
    ) : MultiContextDialogueContext {

        override suspend fun send(line: DialogueLine) {
            send(listOf(line))
        }

        override suspend fun send(lines: List<DialogueLine>) {
            if (lines.isEmpty()) return
            lines.firstOrNull { it.voiceId !in voiceIds }?.let { line ->
                throw IllegalArgumentException(
                    "Voice '${line.voiceId}' is not registered for dialogue context '$id'. " +
                        "Registered voices: $voiceIds.",
                )
            }
            engine.send(id, MultiContextDialogueProtocol.lines(id, lines))
        }

        override suspend fun flush() {
            engine.send(id, MultiContextDialogueProtocol.flush(id))
        }

        override suspend fun keepAlive() {
            engine.send(id, MultiContextDialogueProtocol.keepAlive(id))
        }

        // close_context flushes in this protocol, so no extra message is needed.
        override suspend fun finish() {
            engine.finishContext(id, emptyList())
        }

        override suspend fun close() {
            engine.closeContext(id)
        }

        override fun toString(): String = "MultiContextDialogueContext(id=$id, voices=$voiceIds)"
    }

    internal companion object {
        suspend fun open(
            openConnection: suspend () -> RealtimeTtsConnection,
            options: MultiContextDialogueOptions,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): MultiContextDialogueSessionImpl {
            val connection = try {
                withTimeout(options.timeouts.connectTimeoutMillis.milliseconds) { openConnection() }
            } catch (timeout: TimeoutCancellationException) {
                throw TimeoutError(
                    message = "Multi-context dialogue connection timed out after " +
                        "${options.timeouts.connectTimeoutMillis} ms.",
                    cause = timeout,
                )
            }
            val engine = MultiContextEngine(
                connection = connection,
                protocol = MultiContextDialogueProtocol,
                config = MultiContextEngineConfig(
                    sendTimeoutMillis = options.timeouts.sendTimeoutMillis,
                    finishTimeoutMillis = options.timeouts.finishTimeoutMillis,
                    keepAliveEnabled = options.keepAlive.enabled,
                    keepAliveIntervalMillis = options.keepAlive.intervalMillis,
                    maxContexts = MultiContextDialogueSession.MAX_CONTEXTS,
                    label = "Multi-context dialogue",
                ),
                dispatcher = dispatcher,
            )
            engine.start()
            return MultiContextDialogueSessionImpl(engine, options.maxVoicesPerContext)
        }
    }
}
