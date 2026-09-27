package dev.yveskalume.elevenlabs.feature.tts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.ui.AppIcons
import dev.yveskalume.elevenlabs.ui.ErrorBanner
import dev.yveskalume.elevenlabs.ui.ErrorScreen
import dev.yveskalume.elevenlabs.ui.LoadingScreen
import dev.yveskalume.elevenlabs.voices.Voice

@Composable
internal fun TextToSpeechContent(
    state: TTSUiState,
    onAction: (TTSAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val voiceState = state.voices) {
        is LoadingState.Error -> ErrorScreen(
            title = "Couldn't load voices",
            message = voiceState.message,
            onAction = { onAction(TTSAction.LoadVoices) },
            modifier = modifier,
        )

        LoadingState.Loading -> LoadingScreen(modifier = modifier, message = "Loading voices…")

        is LoadingState.Idle -> TextToSpeechForm(
            voices = voiceState.data,
            state = state,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun TextToSpeechForm(
    voices: List<Voice>,
    state: TTSUiState,
    onAction: (TTSAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInputEnabled = !state.isProcessing

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ModeSelector(
            selected = state.mode,
            enabled = isInputEnabled,
            onSelect = { onAction(TTSAction.SelectMode(it)) },
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = { onAction(TTSAction.UpdateText(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = isInputEnabled,
            label = { Text("Text") },
            placeholder = { Text("What should the voice say?") },
            minLines = 5,
            maxLines = 10,
            shape = MaterialTheme.shapes.medium,
        )

        VoicePicker(
            voices = voices,
            selectedVoice = state.selectedVoice,
            enabled = isInputEnabled,
            onSelect = { onAction(TTSAction.SelectVoice(it)) },
        )

        ErrorBanner(
            message = (state.processingState as? LoadingState.Error<*>)?.message,
            onDismiss = { onAction(TTSAction.ClearError) },
        )

        PlaybackControls(
            canCreateSpeech = state.canCreateSpeech,
            isProcessing = state.isProcessing,
            onCreate = { onAction(TTSAction.CreateSpeech) },
            onStop = { onAction(TTSAction.StopPlayback) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(
    voices: List<Voice>,
    selectedVoice: Voice?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedVoice?.name.orEmpty(),
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
            enabled = enabled,
            readOnly = true,
            singleLine = true,
            label = { Text("Voice") },
            placeholder = { Text("Select a voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = MaterialTheme.shapes.medium,
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(voice.name, style = MaterialTheme.typography.bodyLarge)
                            voice.category?.let {
                                Text(
                                    text = it.replaceFirstChar(Char::uppercase),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(voice.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    selected: TTSMode,
    enabled: Boolean,
    onSelect: (TTSMode) -> Unit,
) {
    val modes = TTSMode.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = { Text(mode.name) },
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    canCreateSpeech: Boolean,
    isProcessing: Boolean,
    onCreate: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onCreate,
            enabled = canCreateSpeech,
            modifier = Modifier.weight(1f).height(52.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    AppIcons.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(if (isProcessing) "Creating speech…" else "Create speech")
        }

        // Always available: on iOS playback continues after the request finishes.
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.height(52.dp),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(
                imageVector = AppIcons.Stop,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Stop")
        }
    }
}
