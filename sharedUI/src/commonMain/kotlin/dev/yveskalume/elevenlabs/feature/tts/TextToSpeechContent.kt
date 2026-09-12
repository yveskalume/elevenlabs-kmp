package dev.yveskalume.elevenlabs.feature.tts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.ui.ErrorScreen
import dev.yveskalume.elevenlabs.ui.LoadingScreen
import dev.yveskalume.elevenlabs.voices.Voice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextToSpeechContent(
    logicHandler: TTSLogicHandler,
) {
    val state by logicHandler.uiState.collectAsStateWithLifecycle()

    when (val voiceState = state.voices) {
        is LoadingState.Error -> ErrorScreen(
            message = voiceState.message,
            onAction = logicHandler::loadVoices,
            actionLabel = "Retry",
        )

        is LoadingState.Idle -> SuccessTextToSpeechContent(
            logicHandler = logicHandler,
            voices = voiceState.data,
            selectedVoice = state.selectedVoice,
            text = state.text,
            isInputEnabled = !state.isProcessing,
            selectedMode = state.mode,
            canCreateSpeech = state.canCreateSpeech,
            isCreatingSpeech = state.isProcessing,
            processingError = (state.processingState as? LoadingState.Error<*>)?.message,
            onClearError = logicHandler::clearError,
        )

        LoadingState.Loading -> LoadingScreen()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SuccessTextToSpeechContent(
    voices: List<Voice>,
    selectedVoice: Voice?,
    text: String,
    isInputEnabled: Boolean,
    selectedMode: TTSMode,
    canCreateSpeech: Boolean,
    isCreatingSpeech: Boolean,
    processingError: String?,
    onClearError: () -> Unit,
    logicHandler: TTSLogicHandler,
    modifier: Modifier = Modifier
) {
    var voiceListExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(
                rememberScrollState()
            )
    ) {

        OutlinedTextField(
            value = text,
            onValueChange = logicHandler::updateText,
            modifier = Modifier.fillMaxWidth(),
            enabled = isInputEnabled,
            label = { Text("Text") },
            minLines = 4,
            maxLines = 8,
        )

        ExposedDropdownMenuBox(
            expanded = voiceListExpanded,
            onExpandedChange = { if (isInputEnabled) voiceListExpanded = !voiceListExpanded },
        ) {

            OutlinedTextField(
                value = selectedVoice?.name.orEmpty(),
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, isInputEnabled)
                    .fillMaxWidth(),
                enabled = isInputEnabled,
                readOnly = true,
                label = { Text("Voice") },
                placeholder = { Text("Select a voice") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceListExpanded) },
            )

            ExposedDropdownMenu(
                expanded = voiceListExpanded,
                onDismissRequest = { voiceListExpanded = false }) {
                voices.forEach { voice ->
                    DropdownMenuItem(
                        text = { Text(voice.name) },
                        onClick = {
                            logicHandler.selectVoice(voice.id)
                            voiceListExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }

        Text("Mode", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TTSMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { logicHandler.selectMode(mode) },
                    enabled = isInputEnabled,
                    label = { Text(mode.name) },
                )
            }
        }

        Button(
            logicHandler::createSpeech,
            Modifier.fillMaxWidth(),
            enabled = canCreateSpeech
        ) {
            Text("Create speech")
        }

        Button(
            onClick = logicHandler::stopPlayback,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Stop playback")
        }

        if (isCreatingSpeech) {
            LoadingScreen(modifier = Modifier.wrapContentHeight())
        }

        if (processingError != null) {
            ErrorScreen(message = processingError, onAction = onClearError)
        }
    }
}
