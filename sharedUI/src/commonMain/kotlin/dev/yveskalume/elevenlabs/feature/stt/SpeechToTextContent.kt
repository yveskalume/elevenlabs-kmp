package dev.yveskalume.elevenlabs.feature.stt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yveskalume.elevenlabs.ui.AppIcons
import dev.yveskalume.elevenlabs.ui.ErrorBanner
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SpeechToTextContent(
    state: STTUiState,
    onAction: (STTAction) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MicButton(
                status = status,
                hasPermission = hasPermission,
                onClick = { if (hasPermission) onAction(STTAction.ToggleListening) else onRequestPermission() },
            )
            Text(
                text = statusTitle(status, hasPermission),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = statusHint(status, hasPermission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        ErrorBanner(
            message = (status as? ListeningStatus.Error)?.message,
            onDismiss = { onAction(STTAction.ClearError) },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        AnimatedTranscript(state.transcript, Modifier.fillMaxWidth().heightIn(min = 120.dp))
    }
}

@Composable
private fun MicButton(
    status: ListeningStatus,
    hasPermission: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isLive = status == ListeningStatus.Listening
    val isBusy = status == ListeningStatus.Connecting || status == ListeningStatus.Finishing
    val container by animateColorAsState(
        when {
            isLive -> colors.tertiary
            status == ListeningStatus.Finishing -> colors.surfaceVariant
            else -> colors.primary
        },
    )
    val content by animateColorAsState(
        when {
            isLive -> colors.onTertiary
            status == ListeningStatus.Finishing -> colors.onSurfaceVariant
            else -> colors.onPrimary
        },
    )

    Box(modifier = Modifier.size(136.dp), contentAlignment = Alignment.Center) {
        if (isLive) PulseRing(color = colors.tertiary)
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(112.dp),
                color = colors.tertiary,
                strokeWidth = 3.dp,
            )
        }
        Surface(
            onClick = onClick,
            enabled = status != ListeningStatus.Finishing,
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = if (isLive) 6.dp else 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val showStop = hasPermission && (isLive || status == ListeningStatus.Connecting)
                Icon(
                    imageVector = if (showStop) AppIcons.Stop else AppIcons.Mic,
                    contentDescription = statusTitle(status, hasPermission),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun PulseRing(color: Color) {
    val transition = rememberInfiniteTransition()
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
    )
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val scale = 1f + 0.4f * progress
                scaleX = scale
                scaleY = scale
                alpha = 0.35f * (1f - progress)
            }
            .background(color, CircleShape),
    )
}

private fun statusTitle(status: ListeningStatus, hasPermission: Boolean): String = when {
    !hasPermission -> "Allow microphone"
    status == ListeningStatus.Connecting -> "Connecting…"
    status == ListeningStatus.Listening -> "Listening"
    status == ListeningStatus.Finishing -> "Finishing transcription…"
    else -> "Start listening"
}

private fun statusHint(status: ListeningStatus, hasPermission: Boolean): String = when {
    !hasPermission -> "The app needs microphone access to transcribe your speech."
    status == ListeningStatus.Connecting -> "Tap to cancel."
    status == ListeningStatus.Listening -> "Tap to stop and get the final transcript."
    status == ListeningStatus.Finishing -> "Waiting for the committed transcript."
    else -> "Tap the microphone and start speaking."
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
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        FlowRow(modifier) {
            words.forEach { word ->
                key(word.id) {
                    AnimatedVisibility(
                        word.visible,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                    ) {
                        Text(
                            "${word.text} ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
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
