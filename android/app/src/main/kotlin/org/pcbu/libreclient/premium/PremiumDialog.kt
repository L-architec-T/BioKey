package org.pcbu.libreclient.premium

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pcbu.libreclient.ui.appearance.GlassAlertDialog
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.Red
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary

/** What sent the user here, so the dialog can say why before listing everything Premium adds. */
enum class PremiumFeature(val reason: String) {
    MULTIPLE_PCS("La version gratuite permet d'appairer un seul PC."),
    PROXIMITY_LOCK("Le verrouillage de proximité fait partie de Premium."),
    MACROS("Les macros après déverrouillage font partie de Premium."),
    NONE(""),
}

/** Same look as the update dialog. [onPurchased] runs once the purchase went through. */
@Composable
fun PremiumDialog(
    feature: PremiumFeature,
    onDismiss: () -> Unit,
    onPurchased: () -> Unit = {},
) {
    val context = LocalContext.current
    val premium = remember { PremiumAccess.get(context) }
    val scope = rememberCoroutineScope()
    var purchasing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = { if (!purchasing) onDismiss() },
        title = {
            Column {
                Text("BIOKEY PREMIUM", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("${PremiumAccess.PRICE_LABEL} — ACHAT UNIQUE, À VIE", style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        },
        text = {
            Column {
                if (feature.reason.isNotEmpty()) {
                    Text(feature.reason, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Spacer(Modifier.height(14.dp))
                }
                PremiumPerk(Icons.Outlined.Computer, "Plusieurs PC", "Fixe, portable, PC du travail…")
                PremiumPerk(Icons.Outlined.LockClock, "Verrouillage de proximité", "Le PC se verrouille quand vous vous éloignez (Wi-Fi ou Bluetooth).")
                PremiumPerk(Icons.Outlined.AutoFixHigh, "Macros après déverrouillage", "Touches et texte tapés automatiquement une fois le PC ouvert.")
                if (premium.isSimulated) {
                    Spacer(Modifier.height(6.dp))
                    Text("Version de test : l'achat est simulé, rien n'est payé.", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Red)
                }
                if (purchasing) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !purchasing,
                onClick = {
                    val activity = context as? Activity ?: return@TextButton
                    purchasing = true
                    error = null
                    scope.launch {
                        when (val result = premium.purchase(activity)) {
                            PurchaseResult.Purchased -> { onPurchased(); onDismiss() }
                            PurchaseResult.Cancelled -> Unit
                            is PurchaseResult.Failed -> error = result.message
                        }
                        purchasing = false
                    }
                },
            ) {
                Text("PASSER À PREMIUM", style = MaterialTheme.typography.labelSmall, color = if (purchasing) TextSecondary else Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !purchasing) {
                Text("PLUS TARD", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        },
    )
}

@Composable
private fun PremiumPerk(icon: ImageVector, title: String, subtitle: String) {
    Row(modifier = Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
