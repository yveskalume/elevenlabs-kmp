package dev.yveskalume.elevenlabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.delay

@Composable
fun App(
    apiKey: String,
    onAudioCommand: suspend (AudioCommand) -> Unit = {},
    microphoneAudio: Flow<ByteArray> = emptyFlow(),
    onMicrophoneCommand: suspend (MicrophoneCommand) -> Unit = {},
    hasMicrophonePermission: Boolean = true,
    onRequestMicrophonePermission: () -> Unit = {},
) {
    val sampleViewModel = viewModel { SampleViewModel(apiKey, microphoneAudio) }
    val state by sampleViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sampleViewModel, onAudioCommand) {
        sampleViewModel.audioCommands.collect(onAudioCommand)
    }
    LaunchedEffect(sampleViewModel, onMicrophoneCommand) {
        sampleViewModel.microphoneCommands.collect(onMicrophoneCommand)
    }

    MaterialTheme {
        SampleScreen(
            state = state,
            onLoadVoices = sampleViewModel::loadVoices,
            onVoiceSelected = sampleViewModel::selectVoice,
            onTextChanged = sampleViewModel::updateText,
            onFeatureSelected = sampleViewModel::selectFeature,
            onModeSelected = sampleViewModel::selectMode,
            onSynthesize = sampleViewModel::synthesize,
            onClearError = sampleViewModel::clearError,
            onStopPlayback = sampleViewModel::stopPlayback,
            onToggleTranscription = sampleViewModel::toggleTranscription,
            hasMicrophonePermission = hasMicrophonePermission,
            onRequestMicrophonePermission = onRequestMicrophonePermission,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleScreen(
    state: SampleUiState,
    onLoadVoices: () -> Unit,
    onVoiceSelected: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onFeatureSelected: (SampleFeature) -> Unit,
    onModeSelected: (SynthesisMode) -> Unit,
    onSynthesize: () -> Unit,
    onClearError: () -> Unit,
    onStopPlayback: () -> Unit,
    onToggleTranscription: () -> Unit,
    hasMicrophonePermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
) {

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ElevenLabs Sample", style = MaterialTheme.typography.headlineMedium)

            FeatuewSelector(
                state = state,
                onFeatureSelected = onFeatureSelected,
                isLoading = state.isLoading
            )

            if (state.feature == SampleFeature.TextToSpeech) {
                TextToSpeechScreenContent(
                    state = state,
                    onTextChanged = onTextChanged,
                    isLoading = state.isLoading,
                    onVoiceSelected = onVoiceSelected,
                    onModeSelected = onModeSelected,
                    isRealtimeActive = state.isRealtimeActive,
                    onStopPlayback = onStopPlayback,
                    onSynthesize = onSynthesize
                )
            } else {
                SpeechToTextScreenContent(
                    hasMicrophonePermission = hasMicrophonePermission,
                    onToggleTranscription = onToggleTranscription,
                    onRequestMicrophonePermission = onRequestMicrophonePermission,
                    state = state,
                    transcriptText = state.transcriptText
                )
            }

            when (val requestState = state.requestState) {
                RequestState.Loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            if (state.feature == SampleFeature.SpeechToText) {
                                if (state.isStoppingTranscription) {
                                    "Finalizing transcript…"
                                } else {
                                    "Listening to microphone…"
                                }
                            } else if (state.mode == SynthesisMode.Realtime) {
                                "Receiving realtime audio…"
                            } else {
                                "Creating speech…"
                            },
                        )
                    }
                }

                is RequestState.Error -> {
                    Text(
                        text = requestState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (state.feature == SampleFeature.TextToSpeech && state.voices.isEmpty()) {
                        TextButton(onClick = onLoadVoices) {
                            Text("Retry loading voices")
                        }
                    } else {
                        TextButton(onClick = onClearError) {
                            Text("Dismiss")
                        }
                    }
                }

                else -> {}
            }
            if (
                state.feature == SampleFeature.TextToSpeech &&
                state.requestState == RequestState.Idle &&
                state.voices.isEmpty()
            ) {
                TextButton(onClick = onLoadVoices) {
                    Text("Retry loading voices")
                }
            }
        }
    }
}

@Composable
private fun FeatuewSelector(
    state: SampleUiState,
    onFeatureSelected: (SampleFeature) -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChip(
            selected = state.feature == SampleFeature.TextToSpeech,
            onClick = { onFeatureSelected(SampleFeature.TextToSpeech) },
            enabled = !isLoading,
            label = { Text("Text to speech") },
        )
        FilterChip(
            selected = state.feature == SampleFeature.SpeechToText,
            onClick = { onFeatureSelected(SampleFeature.SpeechToText) },
            enabled = !isLoading,
            label = { Text("Speech to text") },
        )
    }
}

@Composable
private fun SpeechToTextScreenContent(
    hasMicrophonePermission: Boolean,
    onToggleTranscription: () -> Unit,
    onRequestMicrophonePermission: () -> Unit,
    state: SampleUiState,
    transcriptText: String
) {
    Button(
        onClick = if (hasMicrophonePermission) {
            onToggleTranscription
        } else {
            onRequestMicrophonePermission
        },
        enabled = !state.isStoppingTranscription,
        modifier = Modifier.fillMaxWidth(),
        colors = if (state.isTranscribing) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            when {
                !hasMicrophonePermission -> "Allow microphone"
                state.isStoppingTranscription -> "Finishing transcription…"
                state.isTranscribing -> "Stop and transcribe"
                else -> "Start listening"
            },
        )
    }

    Text("Live transcript", style = MaterialTheme.typography.titleMedium)
    AnimatedTranscript(
        text = transcriptText,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TextToSpeechScreenContent(
    state: SampleUiState,
    onTextChanged: (String) -> Unit,
    isLoading: Boolean,
    onVoiceSelected: (String) -> Unit,
    onModeSelected: (SynthesisMode) -> Unit,
    isRealtimeActive: Boolean,
    onStopPlayback: () -> Unit,
    onSynthesize: () -> Unit
) {
    var voiceListExpanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        label = { Text("Text") },
        minLines = 4,
        maxLines = 8,
    )

    if (state.voices.isNotEmpty()) {
        ExposedDropdownMenuBox(
            expanded = voiceListExpanded,
            onExpandedChange = { voiceListExpanded = !voiceListExpanded },
        ) {
            OutlinedTextField(
                value = state.selectedVoice?.name.orEmpty(),
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = !isLoading,
                    )
                    .fillMaxWidth(),
                enabled = !isLoading,
                readOnly = true,
                label = { Text("Voice") },
                placeholder = { Text("Select a voice") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = voiceListExpanded,
                    )
                },
            )
            ExposedDropdownMenu(
                expanded = voiceListExpanded,
                onDismissRequest = { voiceListExpanded = false },
            ) {
                state.voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.name) },
                        onClick = {
                            onVoiceSelected(voice.id)
                            voiceListExpanded = false
                        },
                        enabled = !isLoading,
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        Text("Mode", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = state.mode == SynthesisMode.Generate,
                onClick = { onModeSelected(SynthesisMode.Generate) },
                enabled = !isLoading,
                label = { Text("Generate") },
            )
            FilterChip(
                selected = state.mode == SynthesisMode.Stream,
                onClick = { onModeSelected(SynthesisMode.Stream) },
                enabled = !isLoading,
                label = { Text("Stream") },
            )
            FilterChip(
                selected = state.mode == SynthesisMode.Realtime,
                onClick = { onModeSelected(SynthesisMode.Realtime) },
                enabled = !isLoading,
                label = { Text("Realtime") },
            )
        }

        Button(
            onClick = if (isRealtimeActive) onStopPlayback else onSynthesize,
            enabled = state.canPlay || isRealtimeActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isRealtimeActive) "Stop realtime" else "Create speech")
        }

        Button(
            onClick = onStopPlayback,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text("Stop playback")
        }
    }
}

private data class AnimatedTranscriptWord(
    val id: Long,
    val text: String,
    val visible: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatedTranscript(
    text: String,
    modifier: Modifier = Modifier,
) {
    val words = rememberAnimatedWords(text)

    if (words.isEmpty()) {
        Text(
            text = "Your speech will appear here",
            modifier = modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    } else {
        FlowRow(modifier = modifier) {
            words.forEach { word ->
                key(word.id) {
                    AnimatedVisibility(
                        visible = word.visible,
                        enter = fadeIn(tween(180)) +
                            slideInVertically(tween(180)) { it / 3 },
                    ) {
                        Text(
                            text = "${word.text} ",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberAnimatedWords(text: String): SnapshotStateList<AnimatedTranscriptWord> {
    val words = remember { mutableStateListOf<AnimatedTranscriptWord>() }
    var nextId by remember { mutableStateOf(0L) }

    LaunchedEffect(text) {
        words.indices.forEach { index ->
            if (!words[index].visible) words[index] = words[index].copy(visible = true)
        }

        val incoming = TRANSCRIPT_WORD.findAll(text).map { it.value }.toList()
        val sharedWordCount = minOf(words.size, incoming.size)
        repeat(sharedWordCount) { index ->
            if (words[index].text != incoming[index]) {
                words[index] = words[index].copy(
                    text = incoming[index],
                    visible = true,
                )
            }
        }

        while (words.size > incoming.size) words.removeAt(words.lastIndex)
        val addedIds = incoming.drop(words.size).map { word ->
            val id = nextId++
            words += AnimatedTranscriptWord(id = id, text = word, visible = false)
            id
        }
        addedIds.forEach { id ->
            delay(WORD_REVEAL_DELAY_MS)
            val index = words.indexOfFirst { it.id == id }
            if (index >= 0) words[index] = words[index].copy(visible = true)
        }
    }
    return words
}

private const val WORD_REVEAL_DELAY_MS = 45L
private val TRANSCRIPT_WORD = Regex("\\S+")

@Preview
@Composable
private fun SampleScreenPreview() {
    MaterialTheme {
        Surface {
            SampleScreen(
                state = SampleUiState(
                    apiKey = "preview-api-key",
                    voices = listOf(Voice(id = "voice-1", name = "Rachel", category = "premade")),
                    selectedVoiceId = "voice-1",
                ),
                onLoadVoices = {},
                onVoiceSelected = {},
                onTextChanged = {},
                onFeatureSelected = {},
                onModeSelected = {},
                onSynthesize = {},
                onClearError = {},
                onStopPlayback = {},
                onToggleTranscription = {},
                hasMicrophonePermission = true,
                onRequestMicrophonePermission = {},
            )
        }
    }
}
