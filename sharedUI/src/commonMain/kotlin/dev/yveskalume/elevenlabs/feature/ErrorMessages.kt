package dev.yveskalume.elevenlabs.feature


internal fun Throwable.userMessage(fallback: String): String =
    message?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
