package org.pcbu.libreclient.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.pcbu.libreclient.ui.theme.Accent
import org.pcbu.libreclient.ui.theme.AccentDim
import org.pcbu.libreclient.ui.theme.BgPrimary
import org.pcbu.libreclient.ui.theme.GlassCard
import org.pcbu.libreclient.ui.theme.ScreenWallpaperBackdrop
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.TextSecondary
import org.pcbu.libreclient.ui.theme.wallpaperBackdropActive

// BioKey's thin layer over the foundation design system: the pieces its secondary screens share.

/**
 * A pushed screen (device detail, macro editor, pairing): shared wallpaper recording, see-through
 * Scaffold, and a header with a back arrow and the title. [fab] goes bottom-end over the content.
 */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    fab: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    ScreenWallpaperBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = if (wallpaperBackdropActive) Color.Transparent else BgPrimary,
            contentColor = TextPrimary,
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1)
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                content(padding)
                Box(Modifier.align(Alignment.BottomEnd).padding(padding)) { fab() }
            }
        }
    }
}

/** Tonal-outline pill (design-system rule): [tone] = Accent (neutral), Success (go), Red (destructive). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Accent,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (tone == Accent) AccentDim else tone.copy(alpha = 0.10f),
            contentColor = tone,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextSecondary,
        ),
        border = BorderStroke(1.dp, if (enabled) tone else TextSecondary.copy(alpha = 0.4f)),
    ) { Text(text) }
}

/** A titled [GlassCard] section. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            content()
        }
    }
}

@Composable
fun AccentSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = AccentDim, checkedBorderColor = Accent),
    )
}
