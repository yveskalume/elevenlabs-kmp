package dev.yveskalume.elevenlabs.feature.multicontext

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.tts.MultiContextTtsSession
import dev.yveskalume.elevenlabs.ui.AppIcons
import dev.yveskalume.elevenlabs.ui.ErrorBanner
import dev.yveskalume.elevenlabs.ui.ErrorScreen
import dev.yveskalume.elevenlabs.ui.LoadingScreen
import dev.yveskalume.elevenlabs.voices.Voice

@Composable
internal fun MultiContextContent(
    state: MultiContextUiState,
    onAction: (MultiContextAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val voiceState = state.voices) {
        is LoadingState.Error -> ErrorScreen(
            title = "Couldn't load voices",
            message = voiceState.message,
            onAction = { onAction(MultiContextAction.LoadVoices) },
            modifier = modifier,
        )

        LoadingState.Loading -> LoadingScreen(modifier = modifier, message = "Loading voices…")

        is LoadingState.Idle -> Reader(
            voices = voiceState.data,
            state = state,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun Reader(
    voices: List<Voice>,
    state: MultiContextUiState,
    onAction: (MultiContextAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var following by remember { mutableStateOf(true) }
    var showContexts by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Header(
            book = state.book,
            voices = voices,
            selected = state.selectedVoice,
            onSelectVoice = { onAction(MultiContextAction.SelectVoice(it)) },
            onShowContexts = { showContexts = true },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Lyrics(
                state = state,
                following = following,
                onStopFollowing = { following = false },
                onJump = { line ->
                    following = true
                    onAction(MultiContextAction.JumpTo(line))
                },
            )
            // Qualified: the Column scope's overload would be picked otherwise.
            androidx.compose.animation.AnimatedVisibility(
                visible = !following,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            ) {
                FilledTonalButton(onClick = { following = true }) { Text("Back to current") }
            }
        }
        ErrorBanner(
            message = state.error,
            onDismiss = { onAction(MultiContextAction.ClearError) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PlayerBar(
            state = state,
            onAction = { action ->
                if (action != MultiContextAction.Stop) following = true
                onAction(action)
            },
        )
    }

    if (showContexts) {
        ContextsSheet(state = state, onAction = onAction, onDismiss = { showContexts = false })
    }
}

@Composable
private fun Header(
    book: Book,
    voices: List<Voice>,
    selected: Voice?,
    onSelectVoice: (String) -> Unit,
    onShowContexts: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VoiceMenu(voices = voices, selected = selected, onSelect = onSelectVoice)
        IconButton(onClick = onShowContexts) {
            Icon(AppIcons.Tune, contentDescription = "Contexts and look-ahead")
        }
    }
}

/**
 * The book, one sentence per line, scrolled so the line being read sits a third of the way down.
 * Lines already read, and lines whose audio is already generated, are brighter than the rest.
 */
@Composable
private fun Lyrics(
    state: MultiContextUiState,
    following: Boolean,
    onStopFollowing: () -> Unit,
    onJump: (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val anchor = maxHeight * 0.3f
        val listState = rememberLazyListState()
        val dragged by listState.interactionSource.collectIsDraggedAsState()
        LaunchedEffect(dragged) { if (dragged) onStopFollowing() }
        LaunchedEffect(state.currentLine, following) {
            if (following) listState.animateScrollToItem(state.currentLine)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().fadingEdges(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = anchor, bottom = maxHeight - anchor),
        ) {
            items(state.book.lines, key = { it.index }) { line ->
                val firstOfParagraph = line.index == state.book.firstLineOf(line.paragraph)
                LyricLine(
                    line = line,
                    color = lineColor(state, line.index),
                    topSpacing = when {
                        line.index == 0 -> 0.dp
                        line.isHeading -> 40.dp
                        firstOfParagraph -> 24.dp
                        else -> 8.dp
                    },
                    onClick = { onJump(line.index) },
                )
            }
        }
    }
}

/** Fades the text out at the top and bottom instead of cutting it off. */
private fun Modifier.fadingEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.06f to Color.Black,
                    0.88f to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }

@Composable
private fun LyricLine(line: BookLine, color: Color, topSpacing: Dp, onClick: () -> Unit) {
    val animated by animateColorAsState(color, animationSpec = tween(durationMillis = 300))
    Text(
        text = if (line.isHeading) line.text.uppercase() else line.text,
        style = if (line.isHeading) {
            MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
        } else {
            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        },
        color = animated,
        modifier = Modifier
            .padding(top = topSpacing)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun lineColor(state: MultiContextUiState, line: Int): Color {
    val text = MaterialTheme.colorScheme.onSurface
    return when {
        line == state.currentLine -> text
        line < state.currentLine -> text.copy(alpha = 0.5f)
        state.isPrepared(line) -> text.copy(alpha = 0.32f)
        else -> text.copy(alpha = 0.18f)
    }
}

@Composable
private fun PlayerBar(
    state: MultiContextUiState,
    onAction: (MultiContextAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { (state.currentLine + 1f) / state.book.lines.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onAction(MultiContextAction.Previous) }) {
                Icon(AppIcons.SkipPrevious, contentDescription = "Previous sentence")
            }
            FilledIconButton(
                onClick = {
                    onAction(
                        if (state.playback == Playback.Stopped) MultiContextAction.Play else MultiContextAction.Stop,
                    )
                },
                modifier = Modifier.size(64.dp),
            ) {
                when (state.playback) {
                    Playback.Stopped -> Icon(AppIcons.PlayArrow, contentDescription = "Play")
                    Playback.Connecting -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )

                    Playback.Reading -> Icon(AppIcons.Stop, contentDescription = "Stop")
                }
            }
            IconButton(onClick = { onAction(MultiContextAction.Next) }) {
                Icon(AppIcons.SkipNext, contentDescription = "Next sentence")
            }
        }
    }
}

/** The contexts in use on the connection, how far ahead they generate, and the last jump. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextsSheet(
    state: MultiContextUiState,
    onAction: (MultiContextAction) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Contexts", style = MaterialTheme.typography.titleLarge)
            Text(
                text = when (state.playback) {
                    Playback.Stopped -> "Each paragraph is read in its own context, all on one connection. " +
                        "The connection opens when you press play."
                    Playback.Connecting -> "Opening the connection…"
                    Playback.Reading -> "Each paragraph is read in its own context, all on one connection " +
                        "to ${MultiContextLogicHandler.MODEL_ID}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            repeat(MultiContextTtsSession.MAX_CONTEXTS) { index ->
                ContextRow(slot = state.contexts.getOrNull(index), book = state.book)
            }
            state.lastJump?.let { jump ->
                Text(
                    text = if (jump.fromLookAhead) {
                        "Last jump: already generated, audio started after ${jump.firstAudioMillis} ms."
                    } else {
                        "Last jump: new context, audio started after ${jump.firstAudioMillis} ms."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionTitle("Look-ahead")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = 0..MultiContextLogicHandler.MAX_LOOK_AHEAD
                options.forEach { paragraphs ->
                    SegmentedButton(
                        selected = state.lookAhead == paragraphs,
                        onClick = { onAction(MultiContextAction.SetLookAhead(paragraphs)) },
                        shape = SegmentedButtonDefaults.itemShape(index = paragraphs, count = options.count()),
                    ) { Text("$paragraphs") }
                }
            }
            Text(
                text = "How many paragraphs to generate before they're needed. Moving on to them is " +
                    "instant, but audio you skip is still generated and billed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextRow(slot: ContextSlot?, book: Book) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (slot == null) {
            Box(Modifier.size(10.dp).border(1.dp, colors.outlineVariant, CircleShape))
        } else {
            StatusDot(
                when (slot.status) {
                    ContextStatus.Playing -> colors.tertiary
                    ContextStatus.Generating -> colors.outline
                    ContextStatus.Ready -> colors.primary
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (slot == null) {
                Text("Free", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                val sentence = slot.fromLine - book.firstLineOf(slot.paragraph) + 1
                Text(
                    text = "Paragraph ${slot.paragraph + 1}" + if (sentence > 1) ", from line $sentence" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${slot.contextId}, ${slot.status.label}, ${seconds(slot.audioMillis)} of audio",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun VoiceMenu(
    voices: List<Voice>,
    selected: Voice?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            // Voice names often carry a description, as in "Bella - Professional, Bright, Warm".
            text = (selected?.name?.substringBefore(" - ") ?: "Voice") + " ▾",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { expanded = true }
                .padding(8.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.name) },
                    onClick = {
                        expanded = false
                        onSelect(voice.id)
                    },
                )
            }
        }
    }
}

private val ContextStatus.label: String
    get() = when (this) {
        ContextStatus.Generating -> "generating"
        ContextStatus.Ready -> "ready"
        ContextStatus.Playing -> "playing"
    }

private fun seconds(millis: Long): String = "${millis / 1_000}.${millis % 1_000 / 100} s"
