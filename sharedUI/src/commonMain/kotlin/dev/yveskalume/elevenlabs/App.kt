package dev.yveskalume.elevenlabs

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yveskalume.elevenlabs.feature.stt.STTAction
import dev.yveskalume.elevenlabs.feature.stt.STTUiState
import dev.yveskalume.elevenlabs.feature.stt.SpeechToTextContent
import dev.yveskalume.elevenlabs.feature.tts.TTSAction
import dev.yveskalume.elevenlabs.feature.tts.TTSUiState
import dev.yveskalume.elevenlabs.feature.tts.TextToSpeechContent
import dev.yveskalume.elevenlabs.ui.theme.ElevenLabsTheme

@Composable
fun App(
    hasMicrophonePermission: Boolean = true,
    onRequestMicrophonePermission: () -> Unit = {},
    sampleViewModel: SampleViewModel
) {

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, sampleViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) sampleViewModel.stopActiveAudio()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val feature by sampleViewModel.selectedFeature.collectAsStateWithLifecycle()

    val ttsState by sampleViewModel.ttsState.collectAsStateWithLifecycle()
    val sttState by sampleViewModel.sttState.collectAsStateWithLifecycle()

    ElevenLabsTheme {
        SampleScreen(
            feature = feature,
            onFeatureSelected = sampleViewModel::selectFeature,
            ttsState = ttsState,
            sttState = sttState,
            onTtsAction = sampleViewModel::onTtsAction,
            onSttAction = sampleViewModel::onSttAction,
            hasMicrophonePermission = hasMicrophonePermission,
            onRequestMicrophonePermission = onRequestMicrophonePermission,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleScreen(
    feature: SampleFeature,
    onFeatureSelected: (SampleFeature) -> Unit,
    ttsState: TTSUiState,
    sttState: STTUiState,
    onTtsAction: (TTSAction) -> Unit,
    onSttAction: (STTAction) -> Unit,
    hasMicrophonePermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("ElevenLabs-KMP Playground", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                FeatureSelector(
                    feature = feature,
                    onSelect = onFeatureSelected,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Crossfade(targetState = feature, label = "feature") { current ->
                    when (current) {
                        SampleFeature.TextToSpeech -> TextToSpeechContent(
                            state = ttsState,
                            onAction = onTtsAction,
                        )

                        SampleFeature.SpeechToText -> SpeechToTextContent(
                            state = sttState,
                            onAction = onSttAction,
                            hasPermission = hasMicrophonePermission,
                            onRequestPermission = onRequestMicrophonePermission,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureSelector(
    feature: SampleFeature,
    onSelect: (SampleFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val features = SampleFeature.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        features.forEachIndexed { index, item ->
            SegmentedButton(
                selected = item == feature,
                onClick = { onSelect(item) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = features.size),
                label = {
                    Text(
                        when (item) {
                            SampleFeature.TextToSpeech -> "Text to speech"
                            SampleFeature.SpeechToText -> "Speech to text"
                        },
                    )
                },
            )
        }
    }
}

private val MAX_CONTENT_WIDTH = 640.dp
