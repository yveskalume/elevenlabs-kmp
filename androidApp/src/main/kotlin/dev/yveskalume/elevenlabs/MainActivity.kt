package dev.yveskalume.elevenlabs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import dev.yveskalume.elevenlabs.audio.AndroidAudioPlayer
import dev.yveskalume.elevenlabs.audio.AndroidMicrophoneRecorder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val audioPlayer = remember(context) { AndroidAudioPlayer(context) }
            val microphone = remember { AndroidMicrophoneRecorder() }
            var hasMicrophonePermission by remember {
                mutableStateOf(
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasMicrophonePermission = granted
            }
            DisposableEffect(audioPlayer, microphone) {
                onDispose {
                    audioPlayer.close()
                    microphone.close()
                }
            }

            App(
                apiKey = BuildConfig.ELEVENLABS_API_KEY,
                onAudioCommand = { command ->
                    when (command) {
                        is AudioCommand.PlayComplete -> audioPlayer.play(command.bytes)
                        is AudioCommand.StartStream -> audioPlayer.startStream(command.sampleRate)
                        is AudioCommand.StreamChunk -> audioPlayer.writeStream(command.bytes)
                        AudioCommand.FinishStream -> audioPlayer.finishStream()
                        AudioCommand.Stop -> audioPlayer.stop()
                    }
                },
                microphoneAudio = microphone.audio,
                onMicrophoneCommand = { command ->
                    when (command) {
                        is MicrophoneCommand.Start -> microphone.start(command.sampleRate)
                        MicrophoneCommand.Stop -> microphone.stop()
                    }
                },
                hasMicrophonePermission = hasMicrophonePermission,
                onRequestMicrophonePermission = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(apiKey = "preview-api-key", hasMicrophonePermission = true)
}
