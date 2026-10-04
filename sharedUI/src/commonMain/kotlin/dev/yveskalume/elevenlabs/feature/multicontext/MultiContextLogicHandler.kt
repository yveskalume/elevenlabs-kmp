package dev.yveskalume.elevenlabs.feature.multicontext

import dev.yveskalume.elevenlabs.AudioPlayer
import dev.yveskalume.elevenlabs.ElevenLabs
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.feature.LogicHandler
import dev.yveskalume.elevenlabs.feature.userMessage
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContext
import dev.yveskalume.elevenlabs.tts.MultiContextTtsOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsSession
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsTimeouts
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * An audiobook you can jump around in, read over a single multi-context connection.
 *
 * Each paragraph is generated in its own context. While one plays, the next
 * [MultiContextUiState.lookAhead] paragraphs are generated in theirs, one after the other, so
 * moving on to them is instant. Jumping to a line closes the contexts that are no longer useful
 * and, unless the line starts a paragraph that is already generated, opens a context from that
 * line. The connection stays open throughout.
 *
 * The line being read is found with character alignment: each audio chunk says which characters it
 * speaks and when, which places every line in the audio stream. Comparing that with the player's
 * position tells which line is heard.
 */
internal class MultiContextLogicHandler(
    private val client: ElevenLabs,
    private val audioPlayer: AudioPlayer,
    private val scope: CoroutineScope,
) : LogicHandler<MultiContextUiState, MultiContextAction> {

    private val _uiState = MutableStateFlow(MultiContextUiState())

    override val uiState: StateFlow<MultiContextUiState> = _uiState
        .onStart {
            if (_uiState.value.voices !is LoadingState.Idle) onAction(MultiContextAction.LoadVoices)
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = _uiState.value,
        )

    private val book: Book
        get() = _uiState.value.book

    private var voicesJob: Job? = null
    private var readingJob: Job? = null

    // Reading state. Only touched from [scope]'s main-thread coroutines, so no locking.
    private var reading: CoroutineScope? = null
    private var session: MultiContextTtsSession? = null
    private var playbackJob: Job? = null

    /**
     * Completed when the book is read to the end. Reading waits for it rather than for its
     * children: between the jobs of two jumps there can be none left running.
     */
    private var bookRead: CompletableDeferred<Unit>? = null
    private var playingParagraph = -1
    private var contextCount = 0

    /** Paragraphs with an open (or finished but unplayed) context, by paragraph index. */
    private val prepared = mutableMapOf<Int, Prepared>()

    /** Serializes changes to [prepared], so a paragraph is never generated twice. */
    private val preparing = Mutex()

    // Where each line starts in the audio written since the player's stream started.
    private val marks = ArrayList<Mark>()
    private var writtenBytes = 0L
    private var streamStarted = false

    private class Mark(val atMillis: Long, val line: Int)

    /** A paragraph generated in its own context. Its audio waits in [audio] until it is played. */
    private class Prepared(
        val paragraph: Int,
        val fromLine: Int,
        val lines: List<BookLine>,
        val context: MultiContextTtsContext,
    ) {
        val audio = Channel<RealtimeTtsEvent.Audio>(Channel.UNLIMITED)
        var collector: Job? = null
        var status = ContextStatus.Generating
        var audioBytes = 0L

        /** Whether ElevenLabs finished generating, which can happen after playing started. */
        var generated = false

        val text: String = lines.joinToString(" ") { it.spoken }

        /** Character offset of each line in [text]. */
        private val lineStarts: List<Int> =
            lines.runningFold(0) { offset, line -> offset + line.spoken.length + 1 }.dropLast(1)

        fun lineAt(offset: Int): Int = lines[lineStarts.indexOfLast { it <= offset }.coerceAtLeast(0)].index
    }

    override fun onAction(action: MultiContextAction) {
        when (action) {
            MultiContextAction.LoadVoices -> loadVoices()
            is MultiContextAction.SelectVoice -> {
                // The voice is part of the connection URL, so a new voice needs a new connection.
                stop()
                _uiState.update { it.copy(selectedVoiceId = action.voiceId) }
            }

            MultiContextAction.Play -> play()
            MultiContextAction.Stop -> stop()
            is MultiContextAction.JumpTo -> jumpTo(action.line)
            MultiContextAction.Previous -> jumpTo(_uiState.value.currentLine - 1)
            MultiContextAction.Next -> jumpTo(_uiState.value.currentLine + 1)
            is MultiContextAction.SetLookAhead -> setLookAhead(action.paragraphs)
            MultiContextAction.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    // region Controls

    private fun play() {
        if (readingJob?.isActive == true) return
        val state = _uiState.value
        val voice = state.selectedVoice ?: return
        _uiState.update {
            it.copy(
                playback = Playback.Connecting,
                currentLine = if (state.ended) 0 else state.currentLine,
                ended = false,
                lastJump = null,
                error = null,
            )
        }
        val previous = readingJob
        readingJob = scope.launch {
            previous?.join()
            read(voice)
        }
    }

    private fun stop() {
        readingJob?.cancel()
        readingJob = null
    }

    private fun jumpTo(line: Int) {
        val target = line.coerceIn(0, book.lines.lastIndex)
        if (reading != null && session != null) {
            seek(target, report = true)
        } else {
            // Not reading yet, or still connecting: reading starts from the current line.
            _uiState.update { it.copy(currentLine = target, ended = false) }
            play()
        }
    }

    private fun setLookAhead(paragraphs: Int) {
        val lookAhead = paragraphs.coerceIn(0, MAX_LOOK_AHEAD)
        _uiState.update { it.copy(lookAhead = lookAhead) }
        val reading = reading ?: return
        reading.launch {
            preparing.withLock {
                prepared.values.filter { it.paragraph > playingParagraph + lookAhead }.forEach { discard(it) }
            }
            prepareAhead()
        }
    }

    // endregion

    // region Reading

    /** Opens the connection and reads until the end of the book, a failure, or [stop]. */
    private suspend fun read(voice: Voice) {
        // Set again: a previous reading that just stopped resets it to Stopped.
        _uiState.update { it.copy(playback = Playback.Connecting) }
        try {
            coroutineScope {
                reading = this
                val finished = CompletableDeferred<Unit>().also { bookRead = it }
                contextCount = 0
                session = client.textToSpeech.openMultiContextSession(voice.id, options = TTS_OPTIONS)
                _uiState.update { it.copy(playback = Playback.Reading) }
                seek(_uiState.value.currentLine, report = false)
                finished.await()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            _uiState.update { it.copy(error = throwable.userMessage("Reading stopped.")) }
        } finally {
            reading = null
            bookRead = null
            playbackJob = null
            playingParagraph = -1
            audioPlayer.stop()
            streamStarted = false
            marks.clear()
            prepared.clear()
            // Closing the connection closes every context on it.
            session?.let { withContext(NonCancellable) { runCatching { it.close() } } }
            session = null
            _uiState.update { it.copy(playback = Playback.Stopped, contexts = emptyList(), bufferedParagraph = -1) }
        }
    }

    /**
     * Reads from [line]: stops what is playing, closes the contexts this position doesn't need,
     * and plays paragraph after paragraph, generating the next ones ahead.
     */
    private fun seek(line: Int, report: Boolean) {
        val reading = reading ?: return
        val jumpedAt = TimeSource.Monotonic.markNow()
        val previous = playbackJob
        previous?.cancel()
        audioPlayer.stop() // silence now, rather than when the previous playback is torn down
        _uiState.update { it.copy(currentLine = line, ended = false, bufferedParagraph = -1) }

        playbackJob = reading.launch {
            previous?.join()
            streamStarted = false
            marks.clear()
            writtenBytes = 0
            val paragraph = book.lines[line].paragraph
            playingParagraph = paragraph

            val fromLookAhead = preparing.withLock {
                val lookAhead = _uiState.value.lookAhead
                // Keep the line's paragraph if it was generated ahead, and the look-ahead
                // paragraphs that are complete. One still generating is closed so that the paragraph
                // to play now is the only one generating; it is generated again later.
                prepared.values
                    .filter {
                        val target = it.paragraph == paragraph && it.fromLine == line
                        it.status == ContextStatus.Playing ||
                            it.paragraph !in paragraph..paragraph + lookAhead ||
                            (it.paragraph == paragraph && !target) ||
                            (!target && !it.generated)
                    }
                    .forEach { discard(it) }
                paragraph in prepared
            }
            publish()

            var onFirstAudio: (() -> Unit)? = if (report) {
                {
                    val millis = jumpedAt.elapsedNow().inWholeMilliseconds
                    _uiState.update { it.copy(lastJump = JumpReport(fromLookAhead, millis)) }
                }
            } else {
                null
            }

            val follower = launch { followAlong() }
            var next: Int? = line
            while (next != null) {
                val current = book.lines[next].paragraph
                playingParagraph = current
                val item = preparing.withLock { prepared[current] ?: prepare(current, next) }
                item.status = ContextStatus.Playing
                publish()
                prepareAhead()

                play(item) {
                    onFirstAudio?.invoke()
                    onFirstAudio = null
                }

                preparing.withLock { prepared.remove(current) }
                publish()
                next = if (current < book.lastParagraph) book.firstLineOf(current + 1) else null
            }

            // Let the end of the book play out, then end the reading.
            while (audioPlayer.streamPositionMillis() < writtenMillis()) delay(FOLLOW_INTERVAL)
            follower.cancel()
            _uiState.update { it.copy(ended = true) }
            bookRead?.complete(Unit)
        }
    }

    /**
     * Starts generating the next paragraph that should be ready ahead of the playing one.
     *
     * Contexts are generated one at a time, so the paragraph being played is never waiting behind
     * look-ahead ones. Whether parallel contexts would slow each other down hasn't been measured.
     * Called again whenever a context finishes generating.
     */
    private suspend fun prepareAhead() {
        preparing.withLock {
            if (playingParagraph < 0 || prepared.values.any { !it.generated }) return@withLock
            val last = minOf(playingParagraph + _uiState.value.lookAhead, book.lastParagraph)
            val next = (playingParagraph + 1..last).firstOrNull { it !in prepared } ?: return@withLock
            prepare(next, book.firstLineOf(next))
        }
    }

    /**
     * Opens a context for [paragraph] from [fromLine] and sends its text. Generation starts right
     * away and the audio is kept until the paragraph is played. Must hold [preparing].
     */
    private suspend fun prepare(paragraph: Int, fromLine: Int): Prepared {
        val reading = checkNotNull(reading)
        val context = checkNotNull(session).openContext(contextId = "ctx-${++contextCount}")
        val item = Prepared(paragraph, fromLine, book.linesOf(paragraph, fromLine), context)
        prepared[paragraph] = item
        item.collector = reading.launch {
            context.events.collect { event ->
                when (event) {
                    is RealtimeTtsEvent.Audio -> {
                        item.audio.send(event)
                        item.audioBytes += event.bytes.size
                    }

                    RealtimeTtsEvent.Finished -> {
                        item.generated = true
                        if (item.status == ContextStatus.Generating) item.status = ContextStatus.Ready
                    }
                }
                publish()
            }
            item.audio.close()
            prepareAhead()
        }
        context.sendText(item.text + " ")
        context.finish() // generate everything, then close the context once its audio is sent
        publish()
        return item
    }

    /** Closes [item]'s context and drops its audio. Must hold [preparing]. */
    private suspend fun discard(item: Prepared) {
        prepared.remove(item.paragraph)
        item.collector?.cancel()
        item.audio.cancel()
        runCatching { item.context.close() }
    }

    /** Plays [item]'s audio and records where each of its lines starts in the stream. */
    private suspend fun play(item: Prepared, onFirstAudio: () -> Unit) {
        marks += Mark(writtenMillis(), item.fromLine)
        var offset = 0
        var received = false
        for (event in item.audio) {
            received = true
            if (!streamStarted) {
                audioPlayer.startStream(SAMPLE_RATE)
                streamStarted = true
            }
            if (_uiState.value.bufferedParagraph != item.paragraph) {
                _uiState.update { it.copy(bufferedParagraph = item.paragraph) }
            }
            val chunkStart = writtenMillis()
            event.alignment?.let { alignment ->
                // Times are taken relative to the chunk's first character, which works whether
                // ElevenLabs reports them per chunk or from the start of the context.
                val origin = alignment.characterStartTimesMs.firstOrNull() ?: 0
                alignment.characters.forEachIndexed { index, character ->
                    val line = item.lineAt(offset)
                    if (line != marks.last().line) {
                        val start = alignment.characterStartTimesMs.getOrElse(index) { origin }
                        marks += Mark(chunkStart + start - origin, line)
                    }
                    offset += character.length
                }
            }
            writtenBytes += event.bytes.size
            onFirstAudio()
            try {
                audioPlayer.writeStream(event.bytes)
            } catch (throwable: Throwable) {
                // A jump stops the player under this write. That is expected.
                currentCoroutineContext().ensureActive()
                throw throwable
            }
        }
        // ElevenLabs finishes a context without audio, and without an error, when the account
        // has no characters left. Say so rather than silently skipping the paragraph.
        check(received) {
            "No audio was generated for paragraph ${item.paragraph + 1}. " +
                "Check that your ElevenLabs account has characters left."
        }
    }

    /** Highlights the line being heard. */
    private suspend fun followAlong() {
        while (true) {
            val position = audioPlayer.streamPositionMillis()
            val line = marks.lastOrNull { it.atMillis <= position }?.line
            if (line != null && line != _uiState.value.currentLine) {
                _uiState.update { it.copy(currentLine = line) }
            }
            delay(FOLLOW_INTERVAL)
        }
    }

    private fun publish() {
        val slots = prepared.values.sortedBy { it.paragraph }.map {
            ContextSlot(
                contextId = it.context.id,
                paragraph = it.paragraph,
                fromLine = it.fromLine,
                status = it.status,
                audioMillis = millisOf(it.audioBytes),
            )
        }
        _uiState.update { it.copy(contexts = slots) }
    }

    private fun writtenMillis(): Long = millisOf(writtenBytes)

    private fun millisOf(bytes: Long): Long = bytes * 1_000 / (SAMPLE_RATE * BYTES_PER_SAMPLE)

    // endregion

    private fun loadVoices() {
        _uiState.update { it.copy(voices = LoadingState.Loading) }
        voicesJob?.cancel()
        voicesJob = scope.launch {
            val result = try {
                LoadingState.Idle(client.voices.list(ListVoicesRequest(pageSize = 100)).voices)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                LoadingState.Error(throwable.userMessage("Could not load voices."))
            }
            _uiState.update { it.copy(voices = result) }
        }
    }

    companion object {
        /** The playing paragraph takes one context; the others can generate ahead. */
        const val MAX_LOOK_AHEAD = MultiContextTtsSession.MAX_CONTEXTS - 1
        const val MODEL_ID = "eleven_flash_v2_5"

        private const val SAMPLE_RATE = 24_000
        private const val BYTES_PER_SAMPLE = 2
        private val FOLLOW_INTERVAL = 50.milliseconds

        private val TTS_OPTIONS = MultiContextTtsOptions(
            modelId = MODEL_ID,
            outputFormat = OutputFormat.Pcm_24000,
            // Character timings, to follow along with the audio.
            syncAlignment = true,
            // Whole sentences are sent, so generation can start without waiting for more text.
            autoMode = true,
            // A long paragraph can take a while to generate when the service is busy.
            timeouts = RealtimeTtsTimeouts(finishTimeoutMillis = 60_000),
        )
    }
}
