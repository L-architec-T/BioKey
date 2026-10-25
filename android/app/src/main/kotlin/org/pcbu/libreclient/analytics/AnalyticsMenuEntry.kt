package org.pcbu.libreclient.analytics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import org.pcbu.libreclient.ui.components.MenuEntry

/**
 * Menu row to refuse / allow the anonymous usage statistics (right to object, GDPR art. 21 —
 * required for the CNIL audience-measurement exemption). See DashBoard/integration/README.md.
 */
fun analyticsMenuEntry(): MenuEntry = MenuEntry(
    icon = Icons.Filled.QueryStats,
    title = "Statistiques anonymes",
    subtitle = if (DashboardAnalytics.enabled) "Activées — toucher pour les refuser" else "Refusées — toucher pour les autoriser",
    onClick = { DashboardAnalytics.setEnabled(!DashboardAnalytics.enabled) },
)
