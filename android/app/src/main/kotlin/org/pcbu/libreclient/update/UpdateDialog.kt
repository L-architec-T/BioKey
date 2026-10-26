package org.pcbu.libreclient.update

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.padding
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
import org.pcbu.libreclient.ui.appearance.GlassAlertDialog
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
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
                Text("BIOKEY", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("MISE À JOUR DISPONIBLE", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Version ${info.versionName}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ReleaseNotes(info.releaseNotes)
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

/**
 * GitHub release notes are Markdown: renders the subset RELEASE_NOTES.md uses (headings,
 * "-" bullets, nested by two spaces, **bold**) instead of showing the raw syntax. The leading
 * "## BioKey x.y.z" heading is dropped, the dialog already shows the version.
 */
@Composable
private fun ReleaseNotes(markdown: String) {
    val lines = markdown.lines().dropWhile { it.isBlank() }.let { all ->
        if (all.firstOrNull()?.startsWith("## ") == true) all.drop(1) else all
    }
    Column {
        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> Text(
                    line.trimStart('#').trim(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val depth = (line.length - line.trimStart().length) / 2
                    Row(modifier = Modifier.padding(start = (depth * 14).dp, bottom = 3.dp)) {
                        Text("•", style = MaterialTheme.typography.bodySmall, color = Accent)
                        Spacer(Modifier.width(8.dp))
                        Text(inlineMarkdown(line.trimStart().drop(2)), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

/** **bold** and `code` spans; anything else stays as written. */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i -> {
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextPrimary)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            text[i] == '`' && text.indexOf('`', i + 1) > i -> {
                val end = text.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            else -> { append(text[i]); i++ }
        }
    }
}
