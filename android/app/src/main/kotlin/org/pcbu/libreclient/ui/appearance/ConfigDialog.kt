package org.pcbu.libreclient.ui.appearance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.pcbu.libreclient.data.prefs.AppearancePreferences
import org.pcbu.libreclient.data.prefs.DEFAULT_ACCENT_ARGB
import org.pcbu.libreclient.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// Ported from Donova's ui/ConfigDialog.kt (../SecurityGuardian/donova) at the user's request —
// same "Apparence" panel: theme toggle, accent color, wallpaper, custom theme, liquid glass.
// SettingsManager (SharedPreferences) is replaced with AppearancePreferences (DataStore), per
// this project's architecture — every composable and interaction below is otherwise unchanged.
val ACCENT_PALETTE = listOf(
    Color(0xFFF5C400), Color(0xFF00D084), Color(0xFF0078D4), Color(0xFF9B59B6), Color(0xFFE91E63),
    Color(0xFF00BCD4), Color(0xFFF44336), Color(0xFFFF8C00), Color(0xFF4CAF50), Color(0xFFFF6B6B),
    Color(0xFFCCFF00), Color(0xFFFF00E5), Color(0xFF6200FF), Color(0xFF000000), Color(0xFFFFFFFF),
)

// Discrete 0..100-by-5 steps every HorizontalWheelPicker below rides on. Blur is the one glass
// property where a 5%-wide step is coarse enough to see — every-1% steps so the wheel tracks the
// actual visual falloff instead of jumping between 21 sample points.
val PERCENT_STEPS = (0..100 step 5).toList()
val BLUR_PERCENT_STEPS = (0..100).toList()
private fun Float.toPercentStep(): Int =
    (((this * 100f) / 5f).roundToInt() * 5).coerceIn(0, 100)
private fun Float.toBlurPercentStep(): Int = (this * 100f).roundToInt().coerceIn(0, 100)

// Dialog stand-in for anything holding a horizontal-drag control (HorizontalWheelPicker's
// snapping LazyRow) — Material3's AlertDialog wraps `text` in its own scrollable container that
// steals a held drag mid-gesture (confirmed on-device). A hand-rolled Dialog+Card has no such
// hidden wrapper.
@Composable
internal fun SliderSafeDialog(
    onDismissRequest: () -> Unit,
    title: String,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        GlassCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Spacer(Modifier.height(16.dp))
                // Takes what's left once the title and buttons are placed, so a tall body scrolls
                // (when it asks to) instead of pushing the buttons off the screen.
                Column(Modifier.weight(1f, fill = false), content = content)
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, content = buttons)
            }
        }
    }
}

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    fallbackColor: Color = BgCard,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        GlassCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, fallbackColor = fallbackColor) {
            Column(modifier = Modifier.padding(24.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                Spacer(Modifier.height(16.dp))
                // Same as SliderSafeDialog: a tall body (long release notes) scrolls within what's
                // left instead of pushing the buttons off the screen.
                Box(Modifier.weight(1f, fill = false)) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                }
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun ConfigDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { AppearancePreferences(context) }
    val scope   = rememberCoroutineScope()

    var lightTheme          by remember { mutableStateOf(!AppTheme.isDark) }
    var showColorPicker     by remember { mutableStateOf(false) }
    var showWallpaperPicker by remember { mutableStateOf(false) }
    var showCustomTheme     by remember { mutableStateOf(false) }
    var showLiquidGlass     by remember { mutableStateOf(false) }
    var showResetConfirm    by remember { mutableStateOf(false) }

    SliderSafeDialog(
        onDismissRequest = onDismiss,
        title = "Apparence",
        buttons = {
            TextButton(onClick = { showResetConfirm = true }) { Text("RÉINITIALISER", color = TextSecondary) }
            TextButton(onClick = onDismiss) { Text("FERMER", color = Accent) }
        },
    ) {
            Column {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Thème clair", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            if (lightTheme) "Mode jour activé" else "Mode nuit activé",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = lightTheme,
                        onCheckedChange = { v ->
                            lightTheme = v
                            AppTheme.isDark = !v
                            scope.launch(Dispatchers.IO) { prefs.setLightTheme(v) }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = AccentDim),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().clickable { showColorPicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Couleur d'accent", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(22.dp).background(AppTheme.accentColor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth().clickable { showWallpaperPicker = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Fond d'écran", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    val current = AppWallpapers.find(AppTheme.wallpaperKey)
                    Text(current?.label ?: "Aucun", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }

                Spacer(Modifier.height(12.dp))

                // No separate on/off switch — picking a color in CustomThemeDialog enables it
                // (see that dialog's persist()); RÉINITIALISER below is how it gets turned back off.
                Row(
                    modifier          = Modifier.fillMaxWidth().clickable { showCustomTheme = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Personnaliser les couleurs", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier          = Modifier.fillMaxWidth().clickable { showLiquidGlass = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Effet verre", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            if (AppTheme.liquidGlassEnabled) "Activé" else "Désactivé",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                }
            }
    }

    if (showResetConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Réinitialiser l'apparence ?", color = TextPrimary) },
            text = { Text("Thème, couleur d'accent, fond d'écran, thème personnalisé et effet verre reviennent à leurs valeurs par défaut.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    lightTheme         = false
                    AppTheme.isDark              = true
                    AppTheme.accentColor         = Color(DEFAULT_ACCENT_ARGB)
                    AppTheme.wallpaperKey        = ""
                    AppTheme.wallpaperIntensity  = 1f
                    AppTheme.customThemeEnabled  = false
                    AppTheme.customBgPrimary     = Color(0xFF0A0A0A)
                    AppTheme.customBgSurface     = Color(0xFF121212)
                    AppTheme.customBgCard        = Color(0xFF17171A)
                    AppTheme.customBgElevated    = Color(0xFF1E1E22)
                    AppTheme.customTextPrimary   = Color(0xFFF5F5F0)
                    AppTheme.customTextSecondary = Color(0xFF9A9A94)
                    AppTheme.customBorder        = Color(0xFF2C2C30)
                    AppTheme.liquidGlassEnabled  = false
                    AppTheme.glassBlur           = GLASS_DEFAULT_BLUR
                    AppTheme.glassDistortion     = GLASS_DEFAULT_DISTORTION
                    AppTheme.glassReflection     = GLASS_DEFAULT_REFLECTION
                    AppTheme.glassGlow           = GLASS_DEFAULT_GLOW
                    AppTheme.glassCorner         = GLASS_DEFAULT_CORNER
                    AppTheme.glassTint           = GLASS_DEFAULT_TINT
                    AppTheme.glassBorder         = GLASS_DEFAULT_BORDER
                    AppTheme.glassShapeRadius    = GLASS_DEFAULT_SHAPE_RADIUS
                    scope.launch(Dispatchers.IO) {
                        prefs.setLightTheme(false)
                        prefs.setAccentColor(DEFAULT_ACCENT_ARGB)
                        prefs.setWallpaperKey("")
                        prefs.setWallpaperIntensity(1f)
                        prefs.setCustomThemeEnabled(false)
                        prefs.setCustomBgPrimary(0xFF0A0A0A.toInt())
                        prefs.setCustomBgSurface(0xFF121212.toInt())
                        prefs.setCustomBgCard(0xFF17171A.toInt())
                        prefs.setCustomBgElevated(0xFF1E1E22.toInt())
                        prefs.setCustomTextPrimary(0xFFF5F5F0.toInt())
                        prefs.setCustomTextSecondary(0xFF9A9A94.toInt())
                        prefs.setCustomBorder(0xFF2C2C30.toInt())
                        prefs.setLiquidGlassEnabled(false)
                        prefs.setGlassBlur(GLASS_DEFAULT_BLUR)
                        prefs.setGlassDistortion(GLASS_DEFAULT_DISTORTION)
                        prefs.setGlassReflection(GLASS_DEFAULT_REFLECTION)
                        prefs.setGlassGlow(GLASS_DEFAULT_GLOW)
                        prefs.setGlassCorner(GLASS_DEFAULT_CORNER)
                        prefs.setGlassTint(GLASS_DEFAULT_TINT)
                        prefs.setGlassBorder(GLASS_DEFAULT_BORDER)
                        prefs.setGlassShapeRadius(GLASS_DEFAULT_SHAPE_RADIUS)
                    }
                    showResetConfirm = false
                }) { Text("RÉINITIALISER", color = Red) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("ANNULER", color = TextSecondary) } },
        )
    }

    if (showColorPicker) {
        AccentColorPickerDialog(
            current  = AppTheme.accentColor,
            onSelect = { color ->
                AppTheme.accentColor = color
                scope.launch(Dispatchers.IO) { prefs.setAccentColor(color.toArgb()) }
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showWallpaperPicker) {
        WallpaperPickerDialog(
            current  = AppTheme.wallpaperKey,
            onSelect = { key ->
                AppTheme.wallpaperKey = key
                scope.launch(Dispatchers.IO) { prefs.setWallpaperKey(key) }
                showWallpaperPicker = false
            },
            onDismiss = { showWallpaperPicker = false },
        )
    }

    if (showCustomTheme) {
        CustomThemeDialog(onDismiss = { showCustomTheme = false })
    }

    if (showLiquidGlass) {
        LiquidGlassDialog(onDismiss = { showLiquidGlass = false })
    }
}

@Composable
private fun LiquidGlassDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { AppearancePreferences(context) }
    val scope   = rememberCoroutineScope()

    var enabled     by remember { mutableStateOf(AppTheme.liquidGlassEnabled) }
    var blur        by remember { mutableStateOf(AppTheme.glassBlur) }
    val distortion  = AppTheme.glassDistortion
    val reflection  = AppTheme.glassReflection
    val glow        = AppTheme.glassGlow
    val corner      = AppTheme.glassCorner
    val tint        = AppTheme.glassTint
    val border      = AppTheme.glassBorder
    val shapeRadius = AppTheme.glassShapeRadius
    var previewWallpaperKey by remember { mutableStateOf("") }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    var previewBackdropSize by remember { mutableStateOf(Size.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp) {
          Column {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Effet verre", style = MaterialTheme.typography.titleLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = TextPrimary)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            HorizontalDivider(color = Border)
              Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Activer", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { v ->
                            enabled = v
                            AppTheme.liquidGlassEnabled = v
                            scope.launch(Dispatchers.IO) { prefs.setLiquidGlassEnabled(v) }
                            if (!v) onDismiss()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = AccentDim),
                    )
                }
                if (enabled) {
                    Spacer(Modifier.height(16.dp))
                    Text("Aperçu", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PreviewBackdropChip(label = "Grille", selected = previewWallpaperKey.isBlank()) { previewWallpaperKey = "" }
                        AppWallpapers.ALL.forEach { wp ->
                            PreviewBackdropChip(
                                label      = wp.label,
                                selected   = previewWallpaperKey == wp.key,
                                thumbRes   = wp.thumbRes,
                                onClick    = { previewWallpaperKey = wp.key },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val previewWallpaper = AppWallpapers.find(previewWallpaperKey)
                    val previewPainter = previewWallpaper?.let { painterResource(it.fullRes) } ?: rememberGridPreviewPainter()
                    val density = LocalDensity.current
                    val cardWidthDp  = 220.dp
                    val cardHeightDp = 58.dp
                    val cardWidthPx  = with(density) { cardWidthDp.toPx() }
                    val cardHeightPx = with(density) { cardHeightDp.toPx() }
                    val previewBackdrop = rememberLayerBackdrop { drawRect(BgElevated); drawContent() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .onSizeChanged { newSize ->
                                val size = Size(newSize.width.toFloat(), newSize.height.toFloat())
                                if (previewBackdropSize != size) {
                                    previewBackdropSize = size
                                    previewOffset = Offset(
                                        ((size.width - cardWidthPx) / 2f).coerceAtLeast(0f),
                                        ((size.height - cardHeightPx) / 2f).coerceAtLeast(0f),
                                    )
                                }
                            },
                    ) {
                        Image(
                            painter            = previewPainter,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).layerBackdrop(previewBackdrop),
                        )
                        Box(
                            modifier = Modifier
                                .size(cardWidthDp, cardHeightDp)
                                .offset { IntOffset(previewOffset.x.roundToInt(), previewOffset.y.roundToInt()) }
                                .pointerInput(previewBackdropSize) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val maxX = (previewBackdropSize.width - cardWidthPx).coerceAtLeast(0f)
                                        val maxY = (previewBackdropSize.height - cardHeightPx).coerceAtLeast(0f)
                                        previewOffset = Offset(
                                            (previewOffset.x + dragAmount.x).coerceIn(0f, maxX),
                                            (previewOffset.y + dragAmount.y).coerceIn(0f, maxY),
                                        )
                                    }
                                }
                                .liquidGlassCard(
                                    backdrop            = previewBackdrop,
                                    blurOverride        = blur,
                                    distortionOverride  = distortion,
                                    reflectionOverride  = reflection,
                                    glowOverride        = glow,
                                    cornerOverride      = corner,
                                    tintOverride        = tint,
                                    borderOverride      = border,
                                    shapeRadiusOverride = shapeRadius,
                                ),
                        ) {
                            Row(
                                modifier          = Modifier.matchParentSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Exemple de carte", style = MaterialTheme.typography.bodySmall, color = TextPrimary, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Flou", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    HorizontalWheelPicker(
                        value         = blur.toBlurPercentStep(),
                        values        = BLUR_PERCENT_STEPS,
                        displayStr    = { "$it" },
                        onValueChange = { p -> blur = p / 100f },
                        onRelease = {
                            AppTheme.glassBlur = blur
                            scope.launch(Dispatchers.IO) { prefs.setGlassBlur(blur) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
              }
          }
        }
    }
}

@Composable
private fun PreviewBackdropChip(label: String, selected: Boolean, thumbRes: Int? = null, onClick: () -> Unit) {
    Column(
        modifier            = Modifier.width(56.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Border, RoundedCornerShape(8.dp)),
        ) {
            Image(
                painter            = thumbRes?.let { painterResource(it) } ?: rememberGridPreviewPainter(),
                contentDescription = label,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CustomThemeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs   = remember { AppearancePreferences(context) }
    val scope   = rememberCoroutineScope()

    fun persist(write: suspend AppearancePreferences.(Int) -> Unit, argb: Int) {
        AppTheme.customThemeEnabled = true
        scope.launch(Dispatchers.IO) {
            prefs.write(argb)
            prefs.setCustomThemeEnabled(true)
        }
    }

    fun applyPreset(preset: ThemePreset) {
        AppTheme.customBgPrimary     = preset.bgPrimary;     persist(AppearancePreferences::setCustomBgPrimary, preset.bgPrimary.toArgb())
        AppTheme.customBgSurface     = preset.bgSurface;     persist(AppearancePreferences::setCustomBgSurface, preset.bgSurface.toArgb())
        AppTheme.customBgCard        = preset.bgCard;        persist(AppearancePreferences::setCustomBgCard, preset.bgCard.toArgb())
        AppTheme.customBgElevated    = preset.bgElevated;    persist(AppearancePreferences::setCustomBgElevated, preset.bgElevated.toArgb())
        AppTheme.customTextPrimary   = preset.textPrimary;   persist(AppearancePreferences::setCustomTextPrimary, preset.textPrimary.toArgb())
        AppTheme.customTextSecondary = preset.textSecondary; persist(AppearancePreferences::setCustomTextSecondary, preset.textSecondary.toArgb())
        AppTheme.customBorder        = preset.border;        persist(AppearancePreferences::setCustomBorder, preset.border.toArgb())
    }

    // "Aucun": back to the default palette (BgPrimary/BgCard/... default dark/light values).
    fun clearCustomTheme() {
        AppTheme.customThemeEnabled = false
        scope.launch(Dispatchers.IO) { prefs.setCustomThemeEnabled(false) }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleurs personnalisées", color = TextPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text("Thèmes prédéfinis", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val noneActive = !AppTheme.customThemeEnabled
                    Column(
                        modifier            = Modifier.width(64.dp).clickable { clearCustomTheme() },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(if (noneActive) 2.dp else 1.dp, if (noneActive) Accent else Border, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Block, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Aucun", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    ThemePresets.forEach { preset ->
                        val isActive = AppTheme.customThemeEnabled &&
                            AppTheme.customBgPrimary == preset.bgPrimary && AppTheme.customBgCard == preset.bgCard
                        Column(
                            modifier            = Modifier.width(64.dp).clickable { applyPreset(preset) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(preset.bgPrimary)
                                    .border(if (isActive) 2.dp else 1.dp, if (isActive) Accent else Border, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(preset.bgCard).border(1.dp, preset.border, CircleShape))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(preset.label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("FERMER", color = Accent) } },
    )
}

private suspend fun saveCustomWallpaper(ctx: android.content.Context, prefs: AppearancePreferences, uri: Uri): String? {
    return try {
        val original = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
        val maxDim = 1600
        val longest = maxOf(original.width, original.height)
        val scale = (maxDim.toFloat() / longest).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
        } else original
        val dir = java.io.File(ctx.filesDir, "wallpapers").apply { mkdirs() }
        prefs.getCustomWallpaperPath()?.let { java.io.File(it).delete() }
        val file = java.io.File(dir, "custom_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out -> scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out) }
        file.absolutePath
    } catch (e: Exception) { null }
}

@Composable
private fun WallpaperPickerDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx   = LocalContext.current
    val prefs = remember { AppearancePreferences(ctx) }
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch(Dispatchers.IO) {
            val path = saveCustomWallpaper(ctx, prefs, uri)
            if (path != null) prefs.setCustomWallpaperPath(path)
            withContext(Dispatchers.Main) {
                uploading = false
                if (path != null) {
                    AppTheme.customWallpaperPath = path
                    onSelect(AppWallpapers.CUSTOM_KEY)
                }
            }
        }
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fond d'écran", color = TextPrimary) },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(560.dp)) {
                item {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(width = 84.dp, height = 150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgElevated)
                            .border(
                                if (current.isBlank()) 2.dp else 1.dp,
                                if (current.isBlank()) Color.White else Border,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect("") },
                        contentAlignment = Alignment.Center,
                    ) { Text("AUCUN", style = MaterialTheme.typography.labelSmall, color = TextSecondary) }
                }
                val custom = AppWallpapers.find(AppWallpapers.CUSTOM_KEY)
                if (custom != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .size(width = 84.dp, height = 150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (current == custom.key) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                                    else Modifier.border(1.dp, Border, RoundedCornerShape(10.dp)),
                                )
                                .clickable { onSelect(custom.key) },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Image(
                                painter            = rememberWallpaperPainter(custom, thumb = true),
                                contentDescription = custom.label,
                                contentScale       = ContentScale.Crop,
                                modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)),
                            )
                            if (current == custom.key) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
                items(AppWallpapers.ALL) { wp ->
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(width = 84.dp, height = 150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (current == wp.key) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                                else Modifier.border(1.dp, Border, RoundedCornerShape(10.dp)),
                            )
                            .clickable { onSelect(wp.key) },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Image(
                            painter            = painterResource(wp.thumbRes),
                            contentDescription = wp.label,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)),
                        )
                        if (current == wp.key) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(width = 84.dp, height = 150.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgElevated)
                            .border(1.dp, Border, RoundedCornerShape(10.dp))
                            .clickable(enabled = !uploading) { pickImage.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(color = Accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Uploader un fond d'écran", tint = TextSecondary, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("UPLOADER", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("FERMER", color = TextSecondary) } },
    )
}

@Composable
private fun AccentColorPickerDialog(current: Color, onSelect: (Color) -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Couleur d'accent", color = TextPrimary) },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(5)) {
                items(ACCENT_PALETTE) { color ->
                    val indicatorColor = if (color.luminance() > 0.5f) Color.Black else Color.White
                    Box(
                        modifier         = Modifier
                            .padding(6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(if (color == current) 2.dp else 1.dp, if (color == current) indicatorColor else Border, CircleShape)
                            .clickable { onSelect(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (color == current) Icon(Icons.Filled.Check, contentDescription = null, tint = indicatorColor, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("FERMER", color = TextSecondary) } },
    )
}
