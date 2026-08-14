package dev.yveskalume.elevenlabs.internal.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal expect fun createPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient

