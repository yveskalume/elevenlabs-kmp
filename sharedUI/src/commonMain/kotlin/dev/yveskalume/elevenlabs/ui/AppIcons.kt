package dev.yveskalume.elevenlabs.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few Material icons the sample needs, defined inline so the app doesn't
 * depend on the (no longer updated) material-icons artifact.
 * Tint them with Icon(tint = …); the fill colour here is ignored.
 */
internal object AppIcons {
    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
                "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28" +
                "c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
        )
    }
    val MicOff: ImageVector by lazy {
        icon(
            "MicOff",
            "M19,11h-1.7c0,0.74 -0.16,1.43 -0.43,2.05l1.23,1.23c0.56,-0.98 0.9,-2.09 0.9,-3.28z" +
                "M14.98,11.17c0,-0.06 0.02,-0.11 0.02,-0.17L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v0.18l5.98,5.99z" +
                "M4.27,3L3,4.27l6.01,6.01L9.01,11c0,1.66 1.33,3 2.99,3 0.22,0 0.44,-0.03 0.65,-0.08l1.66,1.66" +
                "c-0.71,0.33 -1.5,0.52 -2.31,0.52 -2.76,0 -5.3,-2.1 -5.3,-5.1L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28" +
                "c0.91,-0.13 1.77,-0.45 2.54,-0.9L19.73,21 21,19.73 4.27,3z",
        )
    }
    val Stop: ImageVector by lazy { icon("Stop", "M6,6h12v12H6z") }
    val PlayArrow: ImageVector by lazy { icon("PlayArrow", "M8,5v14l11,-7z") }
    val SkipPrevious: ImageVector by lazy { icon("SkipPrevious", "M6,6h2v12L6,18zM9.5,12l8.5,6L18,6z") }
    val Tune: ImageVector by lazy {
        icon(
            "Tune",
            "M3,17v2h6v-2L3,17zM3,5v2h10L13,5L3,5zM13,21v-2h8v-2h-8v-2h-2v6h2zM7,9v2L3,11v2h4v2h2L9,9L7,9z" +
                "M21,13v-2L11,11v2h10zM15,9h2L17,7h4L21,5h-4L17,3h-2v6z",
        )
    }
    val SkipNext: ImageVector by lazy { icon("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2L18,6h-2z") }
    val Close: ImageVector by lazy {
        icon(
            "Close",
            "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z",
        )
    }

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
}
