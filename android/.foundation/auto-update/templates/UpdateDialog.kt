package com.security.__PKG__.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.security.__PKG__.ui.appearance.GlassAlertDialog
import com.security.__PKG__.ui.theme.Accent
import com.security.__PKG__.ui.theme.TextPrimary
import com.security.__PKG__.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drop-in auto-update: checks GitHub Releases once per launch and shows [UpdateDialog] when a
 * newer APK exists. Place it once inside the app theme, next to the root screen.
 * [enabled] = false skips the popup for this launch (e.g. app opened from a widget deep link).
 */
@Composable
fun AutoUpdateHost(enabled: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (enabled) pending = withTimeoutOrNull(6_000L) { UpdateManager.checkForUpdate(context) }
    }

    pending?.let { info ->
        UpdateDialog(
            info = info,
            isDownloading = downloading,
            onInstall = {
                downloading = true
                scope.launch {
                    val apk = UpdateManager.downloadApk(context, info)
                    downloading = false
                    pending = null
                    if (apk != null) UpdateManager.launchInstaller(context, apk)
                }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
fun UpdateDialog(
    info:          UpdateInfo,
    isDownloading: Boolean = false,
    onInstall:     () -> Unit,
    onDismiss:     () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Column {
                Text("__TITLE__", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("MISE À JOUR DISPONIBLE", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Version ${info.versionName}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(info.releaseNotes, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                if (isDownloading) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Téléchargement...", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onInstall, enabled = !isDownloading) {
                Text("INSTALLER", style = MaterialTheme.typography.labelSmall, color = if (isDownloading) TextSecondary else Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text("PLUS TARD", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        },
    )
}
