package org.pcbu.libreclient.service

import org.pcbu.libreclient.analytics.DashboardAnalytics

import org.pcbu.libreclient.ui.language.AppLanguage
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.pcbu.libreclient.biometric.BiometricAuthResult
import org.pcbu.libreclient.biometric.UnlockBiometricAuthenticator
import org.pcbu.libreclient.storage.SecretsStore
import org.pcbu.libreclient.unlock.AuthProgram

/**
 * Transparent trampoline: turns the screen on over the lock screen, shows
 * the biometric prompt for one pending unlock challenge, reports the result
 * back to UnlockForegroundService via [UnlockChallengeBridge], then finishes.
 */
class UnlockChallengeActivity : FragmentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }


    private var requestId: String? = null
    private var deviceId: String? = null
    private var deviceName: String = "PC"
    private var user: String = ""
    private var program = AuthProgram.parse("")
    private var authRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DashBoard analytics (installs, sessions, retention) — see DashBoard/integration/README.md
        DashboardAnalytics.init(application, "pk_aa5fac8b6ac5440e68ac54c52b48ca00")

        // setShowWhenLocked/setTurnScreenOn (API 27+) are what the manifest's showWhenLocked/
        // turnScreenOn attributes also declare; OEM skins (Samsung in particular) have been
        // seen not reliably honoring the older FLAG_SHOW_WHEN_LOCKED/FLAG_TURN_SCREEN_ON window
        // flags on recent Android versions, so both are set here as a code-path fallback for the
        // rare API 26 device where the manifest attributes are simply ignored.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "PC"
        user = intent.getStringExtra(EXTRA_USER) ?: ""
        program = AuthProgram.parse(intent.getStringExtra(EXTRA_PROGRAM) ?: "")

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    Text(
                        program.elevation?.let { "Autoriser $it sur $deviceName ?\n$user" }
                            ?: "Déverrouiller $deviceName ?\n$user",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        if (requestId == null) {
            finish()
        }
    }

    /**
     * BiometricPrompt.authenticate() must not be called until the window has
     * actually gained focus: launched as we are via a full-screen intent over
     * a locked/off screen (setShowWhenLocked/setTurnScreenOn), calling it from
     * onCreate()/onResume() races the keyguard handoff — the vendor biometric
     * service (confirmed on Samsung: "Evicting client due to top activity is
     * not <package>") still sees the keyguard as top and evicts the request
     * within a fraction of a second, so the prompt flashes and vanishes.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val id = requestId ?: return
        if (!hasFocus || authRequested) return
        authRequested = true

        lifecycleScope.launch {
            // Biometric-gated passwordKey: the prompt itself decrypts it. Gated but no usable
            // cipher (key invalidated by a new fingerprint) can never succeed, so deny outright.
            val secrets = SecretsStore(this@UnlockChallengeActivity)
            val gated = deviceId?.let { secrets.isPasswordKeyBiometricGated(it) } == true
            val decryption = deviceId?.let { secrets.preparePasswordKeyDecryption(it) }
            val resolution = if (gated && decryption == null) {
                ChallengeResolution.Denied
            } else {
                val result = UnlockBiometricAuthenticator(this@UnlockChallengeActivity).authenticate(
                    title = program.elevation?.let { "Autoriser $it sur $deviceName" } ?: "Déverrouiller $deviceName",
                    subtitle = user,
                    negativeButtonText = "Annuler",
                    cryptoCipher = decryption?.cipher,
                )
                when (result) {
                    is BiometricAuthResult.Success -> {
                        val passwordKey = decryption?.let { d -> runCatching { d.finish(result.cipher ?: d.cipher) }.getOrNull() }
                        if (decryption != null && passwordKey == null) ChallengeResolution.Denied
                        else ChallengeResolution.Approved(passwordKey)
                    }
                    BiometricAuthResult.Failure -> ChallengeResolution.Denied
                }
            }
            UnlockChallengeBridge.complete(id, resolution)
            finish()
        }
    }

    companion object {
        private const val EXTRA_REQUEST_ID = "requestId"
        private const val EXTRA_DEVICE_ID = "deviceId"
        private const val EXTRA_DEVICE_NAME = "deviceName"
        private const val EXTRA_USER = "user"
        private const val EXTRA_PROGRAM = "program"

        fun intentFor(context: Context, requestId: String, deviceId: String, deviceName: String, user: String, program: String): Intent =
            Intent(context, UnlockChallengeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_USER, user)
                putExtra(EXTRA_PROGRAM, program)
            }
    }
}
