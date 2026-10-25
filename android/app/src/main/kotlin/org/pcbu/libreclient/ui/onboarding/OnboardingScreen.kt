package org.pcbu.libreclient.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import org.pcbu.libreclient.ui.components.appContentPadding
import org.pcbu.libreclient.R
import org.pcbu.libreclient.ui.components.PillButton
import org.pcbu.libreclient.ui.theme.BgPrimary
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.ui.util.requestIgnoreBatteryOptimizations

@Composable
fun OnboardingScreen(preferencesStore: PreferencesStore, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored: the service still runs without a visible notification on refusal */ }

    Column(modifier = Modifier.fillMaxSize().background(BgPrimary).appContentPadding().padding(24.dp)) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(
            stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.onboarding_battery_body),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 16.dp),
        )

        PillButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                requestIgnoreBatteryOptimizations(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:${context.packageName}"),
                    )
                    runCatching { context.startActivity(intent) }
                }
                scope.launch {
                    preferencesStore.setOnboardingComplete(true)
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            text = stringResource(R.string.onboarding_continue),
        )
    }
}
