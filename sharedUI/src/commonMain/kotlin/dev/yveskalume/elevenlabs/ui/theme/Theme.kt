package dev.yveskalume.elevenlabs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable


@Composable
fun ElevenLabsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = (if (dynamicColor) dynamicColorScheme(darkTheme) else null)
        ?: if (darkTheme) DarkColors else LightColors

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
internal expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
