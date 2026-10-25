package org.pcbu.libreclient.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class BiometricAuthResult {
    /** [cipher]: the CryptoObject's cipher, now authenticated, when one was passed in. */
    data class Success(val cipher: Cipher? = null) : BiometricAuthResult()
    data object Failure : BiometricAuthResult()
}

/**
 * Wraps BiometricPrompt as a single suspend call: resumes [BiometricAuthResult.Success]
 * on success, [BiometricAuthResult.Failure] on any error/cancellation (a single failed attempt, e.g. an
 * unrecognized finger, does NOT resolve — BiometricPrompt keeps the sheet
 * open for the user to retry, matching platform UX conventions). A [cryptoCipher]
 * restricts the prompt to strong biometrics, the only class that can unlock a
 * Keystore key.
 */
class UnlockBiometricAuthenticator(private val activity: FragmentActivity) {

    suspend fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String,
        cryptoCipher: Cipher? = null,
    ): BiometricAuthResult =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resumeWith(Result.success(BiometricAuthResult.Success(result.cryptoObject?.cipher)))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resumeWith(Result.success(BiometricAuthResult.Failure))
                }

                override fun onAuthenticationFailed() {
                    // Wrong finger/face: let the prompt keep retrying, don't resolve yet.
                }
            }

            val allowedAuthenticators = if (cryptoCipher != null) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            cont.invokeOnCancellation { prompt.cancelAuthentication() }
            if (cryptoCipher != null) {
                prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoCipher))
            } else {
                prompt.authenticate(promptInfo)
            }
        }
}
