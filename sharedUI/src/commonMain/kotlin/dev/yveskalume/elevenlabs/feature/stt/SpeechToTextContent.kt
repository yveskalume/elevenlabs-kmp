package dev.yveskalume.elevenlabs.feature.stt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.ui.ErrorScreen
import dev.yveskalume.elevenlabs.ui.LoadingScreen
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SpeechToTextContent(
    logicHandler: STTLogicHandler,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val state by logicHandler.uiState.collectAsStateWithLifecycle()

    Button(
        onClick = {
            if (hasPermission) {
                logicHandler.toggleListening()
            } else {
                onRequestPermission()
            }
        },
        enabled = !state.isStopping,
        modifier = Modifier.fillMaxWidth(),
        colors = if (state.isListening) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else ButtonDefaults.buttonColors(),
    ) {
        Text(
            text = when {
                !hasPermission -> "Allow microphone"
                state.isStopping -> "Finishing transcription…"
                state.isListening -> "Stop listening"
                else -> "Start listening"
            },
        )
    }
    when (val processingState = state.processingState) {
        is LoadingState.Error -> ErrorScreen(
            message = processingState.message,
            onAction = logicHandler::clearError,
        )

        is LoadingState.Idle -> Unit
        LoadingState.Loading -> LoadingScreen()
    }
    Text("Live transcript", style = MaterialTheme.typography.titleMedium)
    AnimatedTranscript(state.transcript, Modifier.fillMaxWidth().heightIn(min = 120.dp))
}

private data class AnimatedTranscriptWord(val id: Long, val text: String, val visible: Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatedTranscript(text: String, modifier: Modifier = Modifier) {
    val words = rememberAnimatedWords(text)
    if (words.isEmpty()) {
        Text(
            "Your speech will appear here",
            modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        FlowRow(modifier) {
            words.forEach { word ->
                key(word.id) {
                    AnimatedVisibility(
                        word.visible,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                    ) { Text("${word.text} ", style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

@Composable
private fun rememberAnimatedWords(text: String): List<AnimatedTranscriptWord> {
    val words: MutableList<AnimatedTranscriptWord> = remember { mutableStateListOf() }
    var nextId by remember { mutableStateOf(0L) }
    LaunchedEffect(text) {
        words.indices.forEach { index ->
            if (!words[index].visible) words[index] = words[index].copy(visible = true)
        }
        val incoming = TRANSCRIPT_WORD.findAll(text).map { it.value }.toList()
        repeat(minOf(words.size, incoming.size)) { index ->
            if (words[index].text != incoming[index]) {
                words[index] = words[index].copy(text = incoming[index], visible = true)
            }
        }
        while (words.size > incoming.size) words.removeAt(words.lastIndex)
        val addedIds = incoming.drop(words.size).map { word ->
            val id = nextId++
            words += AnimatedTranscriptWord(id, word, false)
            id
        }
        addedIds.forEach { id ->
            delay(WORD_REVEAL_DELAY_MS.milliseconds)
            val index = words.indexOfFirst { it.id == id }
            if (index >= 0) words[index] = words[index].copy(visible = true)
        }
    }
    return words
}

private const val WORD_REVEAL_DELAY_MS = 45L
private val TRANSCRIPT_WORD = Regex("\\S+")
