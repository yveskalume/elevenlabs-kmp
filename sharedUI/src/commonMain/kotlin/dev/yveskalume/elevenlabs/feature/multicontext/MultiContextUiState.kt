package dev.yveskalume.elevenlabs.feature.multicontext

import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContext
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel

enum class Playback {
    Stopped,
    Connecting,
    Reading,
}

data class MultiContextUiState(
    val voices: LoadingState<List<Voice>> = LoadingState.Loading,
    val selectedVoiceId: String? = null,
    val book: Book = AesopsFables,
    val playback: Playback = Playback.Stopped,
    /** The line being read, or where reading resumes. */
    val currentLine: Int = 0,
    /** Whether the book was read to the end, so playing again starts over. */
    val ended: Boolean = false,
    /** Paragraphs on the connection that already have audio, mapped to the line their audio starts from. */
    val generated: Map<Int, Int> = emptyMap(),
    /** The last paragraph whose audio started going to the player, or -1. */
    val bufferedParagraph: Int = -1,
    val error: String? = null,
) {
    val selectedVoice: Voice?
        get() = voices.dataOrNull?.firstOrNull { it.id == selectedVoiceId }
            ?: voices.dataOrNull?.firstOrNull()

    /** Whether audio for [line] has already been received, so jumping there is instant. */
    fun isPrepared(line: Int): Boolean {
        val target = book.lines[line]
        if (target.paragraph <= bufferedParagraph) return true
        return generated[target.paragraph]?.let { it <= line } == true
    }
}

/** A paragraph generated in its own context. Its audio waits in [audio] until it is played. */
class Prepared(
    val paragraph: Int,
    val fromLine: Int,
    val lines: List<BookLine>,
    val context: MultiContextTtsContext,
) {
    val audio = Channel<RealtimeTtsEvent.Audio>(Channel.UNLIMITED)
    var collector: Job? = null
    var playing = false
    var hasAudio = false

    /** Whether ElevenLabs finished generating, which can happen after playing started. */
    var generated = false

    val text: String = lines.joinToString(" ") { it.spoken }

    /** Character offset of each line in [text]. */
    private val lineStarts: List<Int> =
        lines.runningFold(0) { offset, line -> offset + line.spoken.length + 1 }.dropLast(1)

    fun lineAt(offset: Int): Int = lines[lineStarts.indexOfLast { it <= offset }.coerceAtLeast(0)].index
}

class Mark(val atMillis: Long, val line: Int)