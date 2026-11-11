package dev.yveskalume.elevenlabs.models

/**
 * Identifier of an ElevenLabs model.
 *
 * The companion constants cover the models this SDK version knows about. Construct this type with
 * any other ID the service supports, such as one returned by [ModelsApi.list], without waiting for
 * an SDK release.
 */
public data class ModelId @Throws(IllegalArgumentException::class) constructor(public val value: String) {
    init {
        require(value.isNotBlank()) { "The model ID cannot be blank." }
    }

    public companion object {
        // Text to speech and text to dialogue.

        /** Eleven v4. */
        public val ElevenV4: ModelId = ModelId("eleven_v4")
        /** Eleven v4 Turbo. */
        public val ElevenV4Turbo: ModelId = ModelId("eleven_v4_turbo")
        /** Eleven v3. */
        public val ElevenV3: ModelId = ModelId("eleven_v3")
        /** Eleven v3 Conversational, the default text-to-dialogue model. */
        public val ElevenV3Conversational: ModelId = ModelId("eleven_v3_conversational")
        /** Eleven Multilingual v2. */
        public val ElevenMultilingualV2: ModelId = ModelId("eleven_multilingual_v2")
        /** Eleven Flash v2.5. */
        public val ElevenFlashV2_5: ModelId = ModelId("eleven_flash_v2_5")
        /** Eleven Flash v2. */
        public val ElevenFlashV2: ModelId = ModelId("eleven_flash_v2")

        // Speech to text.

        /** Scribe v3, for batch transcription. */
        public val ScribeV3: ModelId = ModelId("scribe_v3")
        /** Scribe v2, for batch transcription. */
        public val ScribeV2: ModelId = ModelId("scribe_v2")
        /** Scribe v3 Live, for realtime transcription. */
        public val ScribeV3Live: ModelId = ModelId("scribe_v3_live")
        /** Scribe v2 Realtime, for realtime transcription. */
        public val ScribeV2Realtime: ModelId = ModelId("scribe_v2_realtime")
    }
}
