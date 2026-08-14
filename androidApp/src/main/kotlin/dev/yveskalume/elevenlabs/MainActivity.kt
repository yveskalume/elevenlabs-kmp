package dev.yveskalume.elevenlabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import dev.yveskalume.elevenlabs.audio.AndroidAudioPlayer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val audioPlayer = remember(context) { AndroidAudioPlayer(context) }
            DisposableEffect(audioPlayer) {
                onDispose(audioPlayer::close)
            }

            val coroutineScope = rememberCoroutineScope()
            App(
                apiKey = BuildConfig.ELEVENLABS_API_KEY,
                onPlayAudio = {
                    coroutineScope.launch { audioPlayer.play(it) }
                },
                onStopAudio = audioPlayer::stop,
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(apiKey = "preview-api-key")
}
