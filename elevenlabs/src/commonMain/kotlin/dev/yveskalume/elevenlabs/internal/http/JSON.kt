package dev.yveskalume.elevenlabs.internal.http

import kotlinx.serialization.json.Json

internal val JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
