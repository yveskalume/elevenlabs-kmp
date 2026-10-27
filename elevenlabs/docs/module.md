# Module ElevenLabs KMP

A coroutine-based Kotlin Multiplatform client for the ElevenLabs API.

Create an [ElevenLabs][dev.yveskalume.elevenlabs.ElevenLabs] client, configure authentication,
and use its voices, models, text-to-speech, text-to-dialogue, and speech-to-text APIs. The client owns network
resources and must be closed when it is no longer needed.

# Package dev.yveskalume.elevenlabs

Client configuration, authentication, and common error types.

# Package dev.yveskalume.elevenlabs.models

Model discovery APIs and response types.

# Package dev.yveskalume.elevenlabs.voices

Voice discovery APIs, pagination, and response types.

# Package dev.yveskalume.elevenlabs.tts

Batch, HTTP-streamed, and realtime text-to-speech APIs.

# Package dev.yveskalume.elevenlabs.dialogue

Realtime multi-speaker dialogue with Eleven v3 models.

# Package dev.yveskalume.elevenlabs.stt

Batch and realtime speech-to-text APIs.
