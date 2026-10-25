package org.pcbu.libreclient.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory handoff between UnlockForegroundService (owns the open TCP
 * socket and awaits a result) and UnlockChallengeActivity (the only thing
 * that can host a BiometricPrompt). Same-process singleton — no need for
 * Binder/Messenger IPC since both always run in this app's process.
 */
object UnlockChallengeBridge {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ChallengeResolution>>()

    fun register(requestId: String): CompletableDeferred<ChallengeResolution> {
        val deferred = CompletableDeferred<ChallengeResolution>()
        pending[requestId] = deferred
        return deferred
    }

    fun complete(requestId: String, resolution: ChallengeResolution) {
        pending.remove(requestId)?.complete(resolution)
    }
}

sealed class ChallengeResolution {
    /** [passwordKeyOverride]: the passwordKey decrypted through the prompt, for a biometric-gated device. */
    data class Approved(val passwordKeyOverride: String? = null) : ChallengeResolution()
    data object Denied : ChallengeResolution()
}
