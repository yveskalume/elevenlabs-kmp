package dev.yveskalume.elevenlabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yveskalume.elevenlabs.feature.stt.STTLogicHandler
import dev.yveskalume.elevenlabs.feature.stt.SpeechToTextContent
import dev.yveskalume.elevenlabs.feature.tts.TTSLogicHandler
import dev.yveskalume.elevenlabs.feature.tts.TextToSpeechContent

@Composable
fun App(
    hasMicrophonePermission: Boolean = true,
    onRequestMicrophonePermission: () -> Unit = {},
    sampleViewModel: SampleViewModel
) {

    val feature by sampleViewModel.selectedFeature.collectAsStateWithLifecycle()

    MaterialTheme {
        SampleScreen(
            feature = feature,
            onFeatureSelected = sampleViewModel::selectFeature,
            ttsLogicHandler = sampleViewModel.ttsLogicHandler,
            sttLogicHandler = sampleViewModel.sttLogicHandler,
            hasMicrophonePermission = hasMicrophonePermission,
            onRequestMicrophonePermission = onRequestMicrophonePermission,
        )
    }
}

@Composable
private fun SampleScreen(
    feature: SampleFeature,
    onFeatureSelected: (SampleFeature) -> Unit,
    ttsLogicHandler: TTSLogicHandler,
    sttLogicHandler: STTLogicHandler,
    hasMicrophonePermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Header(feature = feature, onFeatureSelected = onFeatureSelected)
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            when (feature) {
                SampleFeature.TextToSpeech -> TextToSpeechContent(
                    logicHandler = ttsLogicHandler,
                )

                SampleFeature.SpeechToText -> SpeechToTextContent(
                    logicHandler = sttLogicHandler,
                    hasPermission = hasMicrophonePermission,
                    onRequestPermission = onRequestMicrophonePermission,
                )
            }
        }
    }
}

@Composable
private fun Header(
    feature: SampleFeature,
    onFeatureSelected: (SampleFeature) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text("ElevenLabs Sample", style = MaterialTheme.typography.headlineMedium)
        FeatureSelector(feature = feature, onSelect = onFeatureSelected)
    }
}

@Composable
private fun FeatureSelector(feature: SampleFeature, onSelect: (SampleFeature) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterChip(
            selected = feature == SampleFeature.TextToSpeech,
            onClick = { onSelect(SampleFeature.TextToSpeech) },
            label = { Text("Text to speech") },
        )
        FilterChip(
            selected = feature == SampleFeature.SpeechToText,
            onClick = { onSelect(SampleFeature.SpeechToText) },
            label = { Text("Speech to text") },
        )
    }
}