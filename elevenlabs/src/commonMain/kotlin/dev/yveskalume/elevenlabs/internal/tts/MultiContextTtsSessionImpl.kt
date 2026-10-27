package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextEngine
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextEngineConfig
import dev.yveskalume.elevenlabs.internal.tts.dtos.MultiContextTtsProtocol
import dev.yveskalume.elevenlabs.tts.AudioChunk
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContext
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContextOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsSession
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
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

internal class MultiContextTtsSessionImpl private constructor(
    private val engine: MultiContextEngine<RealtimeTtsEvent>,
) : MultiContextTtsSession {

    override suspend fun openContext(
        options: MultiContextTtsContextOptions,
        contextId: String?,
    ): MultiContextTtsContext {
        val (id, events) = engine.openContext(contextId) { newId ->
            MultiContextTtsProtocol.initialize(newId, options)
        }
        return Context(id, events)
    }

    override fun stream(
        text: Flow<String>,
        options: MultiContextTtsContextOptions,
    ): Flow<AudioChunk> = flow {
        val context = openContext(options)
        try {
            coroutineScope {
                val sender = launch {
                    text.collect { part -> if (part.isNotEmpty()) context.sendText(part) }
                    context.finish()
                }
                context.events.collect { event ->
                    if (event is RealtimeTtsEvent.Audio) emit(AudioChunk(event.bytes))
                }
                // Events end after Finished (the sender is done) or because the context or session
                // was closed elsewhere, in which case the remaining text is no longer wanted.
                sender.cancel()
            }
        } finally {
            // No-op when the context already finished; discards the context when the collector was
            // cancelled or the text flow failed.
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
        override val events: Flow<RealtimeTtsEvent>,
    ) : MultiContextTtsContext {
        override suspend fun sendText(text: String, flush: Boolean) {
            require(text.isNotEmpty()) {
                "Multi-context TTS text cannot be empty. Call keepAlive() or finish() instead."
            }
            engine.send(id, MultiContextTtsProtocol.text(id, text, flush))
        }

        override suspend fun flush() {
            engine.send(id, MultiContextTtsProtocol.flush(id))
        }

        override suspend fun keepAlive() {
            engine.send(id, MultiContextTtsProtocol.keepAlive(id))
        }

        override suspend fun finish() {
            engine.finishContext(id, listOf(MultiContextTtsProtocol.flush(id)))
        }

        override suspend fun close() {
            engine.closeContext(id)
        }

        override fun toString(): String = "MultiContextTtsContext(id=$id)"
    }

    internal companion object {
        suspend fun open(
            openConnection: suspend () -> RealtimeTtsConnection,
            options: MultiContextTtsOptions,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): MultiContextTtsSessionImpl {
            val connection = try {
                withTimeout(options.timeouts.connectTimeoutMillis.milliseconds) { openConnection() }
            } catch (timeout: TimeoutCancellationException) {
                throw TimeoutError(
                    message = "Multi-context TTS connection timed out after " +
                        "${options.timeouts.connectTimeoutMillis} ms.",
                    cause = timeout,
                )
            }
            val engine = MultiContextEngine(
                connection = connection,
                protocol = MultiContextTtsProtocol,
                config = MultiContextEngineConfig(
                    sendTimeoutMillis = options.timeouts.sendTimeoutMillis,
                    finishTimeoutMillis = options.timeouts.finishTimeoutMillis,
                    keepAliveEnabled = options.keepAlive.enabled,
                    keepAliveIntervalMillis = options.keepAlive.intervalMillis,
                    maxContexts = MultiContextTtsSession.MAX_CONTEXTS,
                    label = "Multi-context TTS",
                ),
                dispatcher = dispatcher,
            )
            engine.start()
            return MultiContextTtsSessionImpl(engine)
        }
    }
}
