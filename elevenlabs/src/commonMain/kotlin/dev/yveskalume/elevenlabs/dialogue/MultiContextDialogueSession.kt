package dev.yveskalume.elevenlabs.dialogue

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.realtime.Alignment
import dev.yveskalume.elevenlabs.tts.AudioChunk
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * One ElevenLabs WebSocket that hosts several independent dialogue contexts.
 *
 * Each context has its own speakers, settings, and audio stream. At most
 * [MultiContextDialogueSession.MAX_CONTEXTS] contexts may be open at the same time.
 *
 * ElevenLabs closes the whole connection on any protocol error, such as a line for a voice that
 * was not registered. The SDK validates what it can before sending; any remaining server error
 * fails the session and every open context's [MultiContextDialogueContext.events].
 */
public interface MultiContextDialogueSession {
    /**
     * Opens a new dialogue context whose lines can be spoken by [voiceIds].
     *
     * `eleven_v3` supports up to [MultiContextDialogueSession.MAX_VOICES_PER_CONTEXT] voices per
     * context. The default model, `eleven_v3_conversational`, and the Eleven v4 models support one.
     *
     * @throws IllegalArgumentException if [voiceIds] is empty, has blank or duplicate IDs, or has
     * more voices than the model allows, or if [contextId] is blank or was already used.
     * @throws IllegalStateException if [MultiContextDialogueSession.MAX_CONTEXTS] contexts are already open, or if you
     * already called [finish] or [close].
     * @throws ElevenLabsException with the original failure if the session has failed.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun openContext(
        voiceIds: List<String>,
        options: MultiContextDialogueContextOptions = MultiContextDialogueContextOptions(),
        contextId: String? = null,
    ): MultiContextDialogueContext

    /**
     * Speaks [lines] in a new context for [voiceIds] and emits its audio.
     *
     * The returned flow is cold. Each collection opens a context, sends every line, finishes the
     * context when [lines] completes, and completes after the final audio chunk. Cancelling
     * collection closes the context without affecting the session or other contexts.
     */
    public fun stream(
        voiceIds: List<String>,
        lines: Flow<DialogueLine>,
        options: MultiContextDialogueContextOptions = MultiContextDialogueContextOptions(),
    ): Flow<AudioChunk>

    /**
     * Asks ElevenLabs to complete every open context and close the connection, then suspends until
     * the connection is closed. Calling it on a session that is finishing or closed does nothing.
     *
     * @throws dev.yveskalume.elevenlabs.error.ElevenLabsException.Timeout if the connection does not close within
     * [MultiContextDialogueTimeouts.finishTimeoutMillis]; the session is closed in that case.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun finish()

    /** Immediately closes the connection and every context. Calling it more than once is safe. */
    public suspend fun close()

    public companion object {
        /** Maximum number of contexts that may be open at the same time on one connection. */
        public const val MAX_CONTEXTS: Int = 5

        /** Maximum number of voices one context can register with `eleven_v3`. Other models allow one. */
        public const val MAX_VOICES_PER_CONTEXT: Int = 10
    }
}

/** One independent dialogue inside a [MultiContextDialogueSession]. */
public interface MultiContextDialogueContext {
    /** Identifier of this context, unique within its session. */
    public val id: String

    /** Voices registered for this context. Every [DialogueLine.voiceId] must be one of them. */
    public val voiceIds: List<String>

    /**
     * A buffered stream of this context's audio and turn boundaries, for one collector at a time.
     * A second concurrent collector fails with [IllegalStateException].
     *
     * The flow completes after [DialogueEvent.Finished], or without it once [close] is called. It
     * terminates with the session's failure if the connection fails.
     */
    public val events: Flow<DialogueEvent>

    /**
     * Appends [line] to this context. Text is buffered by ElevenLabs until it has enough context or
     * until [flush] or [finish] is called.
     *
     * @throws IllegalArgumentException if the line's voice is not registered for this context.
     * @throws IllegalStateException if the context or session is finishing or closed.
     * @throws ElevenLabsException with the original failure if the context or session has failed.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun send(line: DialogueLine)

    /** Appends several [lines] in one message, in order. */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun send(lines: List<DialogueLine>)

    /** Generates the text buffered so far without closing the context. */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun flush()

    /**
     * Resets the 20-second inactivity timeout of this context. Only needed when
     * [MultiContextDialogueOptions.keepAlive] is disabled.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun keepAlive()

    /**
     * Generates the remaining text and closes the context. Completion is reported by
     * [DialogueEvent.Finished] on [events]. Calling it again does nothing.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun finish()

    /**
     * Stops delivering this context's audio immediately and closes it, for example when the
     * listener skips a scene. The slot is released right away. Calling it more than once is safe.
     *
     * The protocol has no way to cancel generation, so ElevenLabs may still generate the remaining
     * text; the SDK discards that audio.
     */
    public suspend fun close()
}

/** One line of dialogue. */
public class DialogueLine private constructor(
    /** Voice that speaks this line. It must be registered for the context. */
    public val voiceId: String,
    /** Non-empty text of the line. */
    public val text: String,
    /**
     * Whether this line starts a new speaker turn. ElevenLabs then finalizes the previous turn's
     * delivery before this line, and reports its end with [DialogueEvent.TurnFinished].
     */
    public val newTurn: Boolean,
) {
    init {
        require(voiceId.isNotBlank()) { "voiceId cannot be blank." }
        require(text.isNotEmpty()) { "Dialogue text cannot be empty." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder(voiceId = voiceId, text = text).also {
        it.newTurn = newTurn
    }

    /**
     * Builds a [DialogueLine]. Required values are constructor parameters; the others start at
     * their defaults.
     */
    public class Builder(
        /** Voice that speaks this line. It must be registered for the context. */
        public var voiceId: String,
        /** Non-empty text of the line. */
        public var text: String,
    ) {
        /**
         * Whether this line starts a new speaker turn. ElevenLabs then finalizes the previous
         * turn's
         * delivery before this line, and reports its end with [DialogueEvent.TurnFinished].
         */
        public var newTurn: Boolean = false

        /**
         * Creates the [DialogueLine].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): DialogueLine = DialogueLine(voiceId, text, newTurn)
    }

    override fun equals(other: Any?): Boolean =
        other is DialogueLine &&
            voiceId == other.voiceId &&
            text == other.text &&
            newTurn == other.newTurn

    override fun hashCode(): Int {
        var result = voiceId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + newTurn.hashCode()
        return result
    }

    override fun toString(): String = "DialogueLine(voiceId=$voiceId, text=$text, newTurn=$newTurn)"
}

/**
 * Creates a [DialogueLine]. Set optional values in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun DialogueLine(
    voiceId: String,
    text: String,
    configure: DialogueLine.Builder.() -> Unit = {},
): DialogueLine = DialogueLine.Builder(voiceId, text).apply(configure).build()

/**
 * Event emitted by a [MultiContextDialogueContext].
 *
 * Later SDK versions can add event types, so give a `when` over these events an `else` branch.
 */
public sealed interface DialogueEvent {
    /** A generated audio chunk. */
    public class Audio internal constructor(
        /** Generated audio. The array belongs to the caller; the SDK never reads or changes it again. */
        public val bytes: ByteArray,
        /** Character timing for this chunk, when [MultiContextDialogueOptions.syncAlignment] is enabled. */
        public val alignment: Alignment? = null,
    ) : DialogueEvent {
        override fun equals(other: Any?): Boolean =
            other is Audio && bytes.contentEquals(other.bytes) && alignment == other.alignment

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + (alignment?.hashCode() ?: 0)

        override fun toString(): String = "Audio(bytes=${bytes.size}, alignment=$alignment)"
    }

    /** All audio of the current speaker turn has been delivered. */
    public data object TurnFinished : DialogueEvent

    /** The context is complete and no more events will be emitted. */
    public data object Finished : DialogueEvent

    /** A forward-compatible server message for this context not recognized by this SDK version. */
    public data class Unknown(
        public val rawMessage: String,
    ) : DialogueEvent
}
