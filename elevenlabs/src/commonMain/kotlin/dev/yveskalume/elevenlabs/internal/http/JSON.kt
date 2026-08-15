package dev.yveskalume.elevenlabs.internal.http

import kotlinx.serialization.json.Json

val JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}