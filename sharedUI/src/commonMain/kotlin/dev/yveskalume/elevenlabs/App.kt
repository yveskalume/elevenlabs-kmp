package dev.yveskalume.elevenlabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yveskalume.elevenlabs.voices.Voice

@Composable
fun App(
    apiKey: String,
    onPlayAudio: (ByteArray) -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    val sampleViewModel = viewModel { SampleViewModel(apiKey) }
    val state by sampleViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sampleViewModel, onPlayAudio) {
        sampleViewModel.audioToPlay.collect(onPlayAudio)
    }

    MaterialTheme {
        SampleScreen(
            state = state,
            onLoadVoices = sampleViewModel::loadVoices,
            onVoiceSelected = sampleViewModel::selectVoice,
            onTextChanged = sampleViewModel::updateText,
            onModeSelected = sampleViewModel::selectMode,
            onSynthesize = {
                onStopAudio()
                sampleViewModel.synthesize()
            },
            onClearError = sampleViewModel::clearError,
            onStopPlayback = {
                onStopAudio()
            },
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
    onModeSelected: (SynthesisMode) -> Unit,
    onSynthesize: () -> Unit,
    onClearError: () -> Unit,
    onStopPlayback: () -> Unit,
) {
    var voiceListExpanded by remember { mutableStateOf(false) }
    val isLoading = state.requestState == RequestState.Loading

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ElevenLabs Player", style = MaterialTheme.typography.headlineMedium)

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
                }

                Button(
                    onClick = onSynthesize,
                    enabled = state.canPlay,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create speech")
                }

                Button(
                    onClick = onStopPlayback,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Stop playback")
                }
            }


            Text("Status", style = MaterialTheme.typography.titleMedium)
            when (val requestState = state.requestState) {
                RequestState.Loading -> {
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                is RequestState.Error -> {
                    Text(
                        text = requestState.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (state.voices.isEmpty()) {
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
            if (state.requestState == RequestState.Idle && state.voices.isEmpty()) {
                TextButton(onClick = onLoadVoices) {
                    Text("Retry loading voices")
                }
            }
        }
    }
}

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
                onModeSelected = {},
                onSynthesize = {},
                onClearError = {},
                onStopPlayback = {},
            )
        }
    }
}
