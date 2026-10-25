package org.pcbu.libreclient.ui.language

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.pcbu.libreclient.ui.appearance.GlassAlertDialog
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary

/** Title and button stay bilingual: whoever opens this may not read the current language. */
@Composable
fun LanguageDialog(onDismiss: () -> Unit) {
    val activity = LocalContext.current as Activity
    val current = AppLanguage.current(activity)
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Langue / Language", style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppLanguage.Choice.entries.forEach { choice ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                onDismiss()
                                if (choice != current) AppLanguage.set(activity, choice)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = choice == current,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = TextSecondary),
                        )
                        Text(choice.label, color = TextPrimary, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("FERMER / CLOSE", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        },
    )
}
