package org.pcbu.libreclient.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// android-foundation/legal — source of truth in the foundation; reinstall instead of editing.

/** Menu label, identical in every app. */
const val LEGAL_MENU_TITLE = "Confidentialité et mentions légales"
const val LEGAL_MENU_SUBTITLE = "Vos données, vos droits"

/**
 * Full-screen privacy policy + legal notice, readable offline, with the analytics opt-out switch
 * when the app measures its audience. Show it from the app menu:
 * `if (showLegal) LegalScreen(onClose = { showLegal = false })`.
 */
@Composable
fun LegalScreen(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var analytics by remember { mutableStateOf(LegalContent.analyticsEnabled()) }
        val uri = LocalUriHandler.current
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    LegalContent.APP_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("Fermer") }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Confidentialité") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Mentions légales") })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Dernière mise à jour : ${LegalContent.UPDATED}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (tab == 0 && LegalContent.HAS_ANALYTICS) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceVariant)
                                .clickable {
                                    analytics = !analytics
                                    LegalContent.setAnalyticsEnabled(analytics)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Statistiques anonymes",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.onSurface,
                                )
                                Text(
                                    if (analytics) "Activées — touchez pour les refuser" else "Refusées",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = analytics,
                                onCheckedChange = {
                                    analytics = it
                                    LegalContent.setAnalyticsEnabled(it)
                                },
                                // Design system: checked thumb = accent, track = dimmed accent.
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.primary,
                                    checkedTrackColor = colors.primary.copy(alpha = 0.3f),
                                    checkedBorderColor = colors.primary,
                                ),
                            )
                        }
                    }
                }
                items(if (tab == 0) LegalContent.privacy else LegalContent.notice) { section ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .padding(16.dp),
                    ) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(section.body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = { runCatching { uri.openUri(LegalContent.WEB_URL) } }) {
                            Text("Version en ligne")
                        }
                        TextButton(onClick = { runCatching { uri.openUri("mailto:${LegalContent.CONTACT_EMAIL}") } }) {
                            Text(LegalContent.CONTACT_EMAIL)
                        }
                    }
                }
            }
        }
    }
}
