package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.error.NetworkError
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RealtimeSttSessionImplTest {

    @Test
    fun `outbound transport failure is normalized`() = runTest {
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) {
                    throw IOException("disconnected")
                }

                override suspend fun receive(): RealtimeSttConnectionFrame = awaitCancellation()

                override suspend fun close() = Unit
            },
        )

        assertFailsWith<NetworkError> { session.sendAudio(byteArrayOf(1)) }
        assertFailsWith<NetworkError> { session.commit() }
        session.close()
    }

    @Test
    fun `closed session preserves local state failure`() = runTest {
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) = Unit
                override suspend fun receive(): RealtimeSttConnectionFrame = awaitCancellation()
                override suspend fun close() = Unit
            },
        )
        session.close()

        assertFailsWith<IllegalStateException> { session.commit() }
    }
}
