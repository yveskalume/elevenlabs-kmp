package dev.yveskalume.elevenlabs.dialogue

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAuthorization
import kotlin.coroutines.cancellation.CancellationException

/** Generates multi-speaker dialogue with Eleven v3 models. */
public interface TextToDialogueApi {
    /**
     * Opens one realtime WebSocket that can host up to [MultiContextDialogueSession.MAX_CONTEXTS]
     * independent dialogue contexts, such as scenes that are generated ahead while another plays.
     *
     * Unlike text-to-speech, voices are chosen per context: each context registers the voices of its
     * speakers when it is opened, and every line names the voice that says it.
     *
     * The caller owns the returned session and must call [MultiContextDialogueSession.finish] or
     * [MultiContextDialogueSession.close].
     *
     * @throws dev.yveskalume.elevenlabs.error.RealtimeServerError for a realtime protocol failure.
     * @throws dev.yveskalume.elevenlabs.error.NetworkError for a transport failure.
     * @throws dev.yveskalume.elevenlabs.error.TimeoutError when opening times out.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun openMultiContextSession(
        options: MultiContextDialogueOptions = MultiContextDialogueOptions(),
        authorization: RealtimeTtsAuthorization = RealtimeTtsAuthorization.ConfiguredApiKey,
    ): MultiContextDialogueSession
}
