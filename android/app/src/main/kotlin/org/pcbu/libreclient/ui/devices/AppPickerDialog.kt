package org.pcbu.libreclient.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.pcbu.libreclient.R
import org.pcbu.libreclient.ui.components.PillButton
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.AccentDim
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.ui.theme.verticalEdgeFade
import org.pcbu.libreclient.ui.util.toUserMessage
import org.pcbu.libreclient.unlock.PcApp

/** Full-screen list of the PC's applications, alphabetical, with a search field. [loadApps]
 * is the editor's cached download, so reopening the picker doesn't fetch the list again. */
@Composable
fun AppPickerDialog(
    loadApps: suspend (forceRefresh: Boolean) -> List<PcApp>,
    onDismiss: () -> Unit,
    onPick: (PcApp) -> Unit,
) {
    var apps by remember { mutableStateOf<List<PcApp>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(attempt) {
        apps = null
        error = null
        try {
            apps = loadApps(attempt > 0)
        } catch (e: Exception) {
            error = e.toUserMessage()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel), tint = TextPrimary)
                    }
                    Text(stringResource(R.string.app_picker_title), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
                val currentApps = apps
                val currentError = error
                when {
                    currentError != null -> Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_picker_error, currentError), color = TextSecondary)
                        PillButton(
                            onClick = { attempt++ },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            text = stringResource(R.string.app_picker_retry),
                        )
                    }
                    currentApps == null -> Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Accent)
                        Text(
                            stringResource(R.string.app_picker_loading),
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    else -> {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            placeholder = { Text(stringResource(R.string.app_picker_search, currentApps.size)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        val filtered = remember(currentApps, query) {
                            val q = query.trim()
                            if (q.isEmpty()) currentApps else currentApps.filter { it.name.contains(q, ignoreCase = true) }
                        }
                        if (filtered.isEmpty()) {
                            Text(stringResource(R.string.app_picker_empty), color = TextSecondary, modifier = Modifier.padding(16.dp))
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f).verticalEdgeFade(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(filtered, key = { it.id }) { app -> AppRow(app, onClick = { onPick(app) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: PcApp, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        AppIcon(app, Modifier.size(40.dp))
        Text(app.name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.padding(start = 16.dp))
    }
}

/** The PC's PNG icon, or a generic app icon when the PC has none (or only an SVG one). */
@Composable
fun AppIcon(app: PcApp, modifier: Modifier = Modifier) {
    val icon = app.icon
    if (icon != null) {
        val bitmap = remember(icon) { icon.asImageBitmap() }
        Image(bitmap, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.clip(MaterialTheme.shapes.small).background(AccentDim), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Apps, contentDescription = null, tint = Accent, modifier = Modifier.fillMaxSize(0.6f))
        }
    }
}
