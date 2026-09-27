@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.yveskalume.elevenlabs

import androidx.compose.runtime.*
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yveskalume.elevenlabs.audio.IOSAudioPlayer
import dev.yveskalume.elevenlabs.audio.IOSMicrophoneRecorder
import kotlinx.coroutines.launch
import platform.AVFAudio.*
import platform.Foundation.NSURL
import platform.UIKit.*

fun MainViewController(apiKey: String): UIViewController = ComposeUIViewController {
    val model = viewModel { SampleViewModel(ElevenLabs { apiKey(apiKey) }, IOSAudioPlayer(), IOSMicrophoneRecorder()) }
    var granted by remember { mutableStateOf(microphoneGranted()) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = microphoneGranted()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    App(
        sampleViewModel = model,
        hasMicrophonePermission = granted,
        onRequestMicrophonePermission = {
            val session = AVAudioSession.sharedInstance()
            if (session.recordPermission == AVAudioSessionRecordPermissionDenied) {
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                    UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
                }
            } else {
                session.requestRecordPermission { allowed -> scope.launch { granted = allowed } }
            }
        },
    )
}

private fun microphoneGranted() = AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
