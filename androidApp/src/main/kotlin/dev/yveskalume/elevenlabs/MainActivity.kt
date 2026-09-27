package dev.yveskalume.elevenlabs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yveskalume.elevenlabs.audio.AndroidAudioPlayerImpl
import dev.yveskalume.elevenlabs.audio.AndroidMicrophoneRecorderImpl

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            var hasMicrophonePermission by remember {
                mutableStateOf(
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED,
                )
            }

            var hasRequestedPermission by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasMicrophonePermission = granted
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasMicrophonePermission =
                            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                    }
                }

                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            App(
                sampleViewModel = viewModel {
                    SampleViewModel(
                        ElevenLabs { apiKey(BuildConfig.ELEVENLABS_API_KEY) },
                        AndroidAudioPlayerImpl(context.applicationContext),
                        AndroidMicrophoneRecorderImpl()
                    )
                },
                hasMicrophonePermission = hasMicrophonePermission,
                onRequestMicrophonePermission = {
                    if (hasRequestedPermission && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            ),
                        )
                    } else {
                        hasRequestedPermission = true
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        }
    }
}
