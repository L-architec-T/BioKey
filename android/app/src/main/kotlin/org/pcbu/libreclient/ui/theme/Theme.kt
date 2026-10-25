package org.pcbu.libreclient.ui.theme

import org.pcbu.libreclient.data.prefs.DEFAULT_ACCENT_ARGB
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow

// Ported from CallUp's copy of the android-foundation design system (itself from Donova) — same
// AppTheme/liquid-glass engine and "Apparence" panel (ui/appearance/ConfigDialog.kt), BioKey's own default accent.
object AppTheme {
    var isDark      by mutableStateOf(true)
    var accentColor by mutableStateOf(Color(DEFAULT_ACCENT_ARGB))
    // Empty string = no wallpaper (plain BgPrimary) — see AppWallpapers.ALL for the picker.
    var wallpaperKey by mutableStateOf("")
    var customWallpaperPath by mutableStateOf<String?>(null)
    // How strongly the wallpaper shows through its scrim — 0 = barely visible, 1 = fully visible.
    var wallpaperIntensity by mutableStateOf(1f)

    // Custom theme: when on, the seven tokens below replace the built-in dark/light palette —
    // isDark keeps whatever it was so turning custom off falls back to the last dark/light choice.
    var customThemeEnabled  by mutableStateOf(false)
    var customBgPrimary     by mutableStateOf(Color(0xFF0A0A0A))
    var customBgSurface     by mutableStateOf(Color(0xFF121212))
    var customBgCard        by mutableStateOf(Color(0xFF17171A))
    var customBgElevated    by mutableStateOf(Color(0xFF1E1E22))
    var customTextPrimary   by mutableStateOf(Color(0xFFF5F5F0))
    var customTextSecondary by mutableStateOf(Color(0xFF9A9A94))
    var customBorder        by mutableStateOf(Color(0xFF2C2C30))

    // Liquid glass containers — blur/distortion/corner are 0..1, mapped to actual dp values at
    // the call site (GlassCard below) rather than stored as raw dp so the slider range stays
    // stable if the mapping is retuned later.
    var liquidGlassEnabled by mutableStateOf(false)
    var glassBlur          by mutableStateOf(GLASS_DEFAULT_BLUR)
    var glassDistortion    by mutableStateOf(GLASS_DEFAULT_DISTORTION)
    // Specular highlight streak and outer accent-colored glow — see GlassCard's own comment.
    var glassReflection    by mutableStateOf(GLASS_DEFAULT_REFLECTION)
    var glassGlow          by mutableStateOf(GLASS_DEFAULT_GLOW)
    var glassCorner        by mutableStateOf(GLASS_DEFAULT_CORNER)
    var glassTint          by mutableStateOf(GLASS_DEFAULT_TINT)
    var glassBorder        by mutableStateOf(GLASS_DEFAULT_BORDER)
    var glassShapeRadius   by mutableStateOf(GLASS_DEFAULT_SHAPE_RADIUS)
}

// Canonical liquid glass preset — single source of truth for AppearancePreferences' read-fallback
// defaults and ConfigDialog's "RÉINITIALISER" target. Glow/corner are fixed at 0 — no slider for
// either exists anymore.
const val GLASS_DEFAULT_BLUR         = 0f
const val GLASS_DEFAULT_DISTORTION   = 1f
const val GLASS_DEFAULT_REFLECTION   = 0.3f
const val GLASS_DEFAULT_GLOW         = 0f
const val GLASS_DEFAULT_CORNER       = 0f
const val GLASS_DEFAULT_TINT         = 0f
const val GLASS_DEFAULT_BORDER       = 0.5f
const val GLASS_DEFAULT_SHAPE_RADIUS = 0.4f

// Full ready-made palettes (all 7 tokens at once) — the only thing CustomThemeDialog offers: no
// per-color editor, and no "Ardoise"/"Jour" (they duplicate the default dark/light themes).
class ThemePreset(
    val label: String,
    val bgPrimary: Color, val bgSurface: Color, val bgCard: Color, val bgElevated: Color,
    val textPrimary: Color, val textSecondary: Color, val border: Color,
)

val ThemePresets = listOf(
    ThemePreset("Nuit",     Color(0xFF0A0A0A), Color(0xFF121212), Color(0xFF17171A), Color(0xFF1E1E22), Color(0xFFF5F5F0), Color(0xFF9A9A94), Color(0xFF2C2C30)),
    ThemePreset("Cyberpunk",Color(0xFF0D0221), Color(0xFF150634), Color(0xFF1D0B47), Color(0xFF29125E), Color(0xFFF5F0FF), Color(0xFFB79BE8), Color(0xFF3E1D6B)),
    ThemePreset("Matrix",   Color(0xFF010A02), Color(0xFF031005), Color(0xFF061F0B), Color(0xFF0A2E11), Color(0xFFB6FFC0), Color(0xFF4FAE5F), Color(0xFF0F3D19)),
    ThemePreset("Forêt",    Color(0xFF10160F), Color(0xFF161D14), Color(0xFF1E271B), Color(0xFF283322), Color(0xFFEDF2E7), Color(0xFFA3B399), Color(0xFF32402A)),
    ThemePreset("Kawaii",   Color(0xFFFFF0F5), Color(0xFFFFFFFF), Color(0xFFFFE4EE), Color(0xFFFFD3E4), Color(0xFF3D1F2B), Color(0xFF8C5E70), Color(0xFFFFC1D9)),
    ThemePreset("Ocean",    Color(0xFF041B24), Color(0xFF072534), Color(0xFF0B3348), Color(0xFF0F415C), Color(0xFFE4F6FF), Color(0xFF8FC4DB), Color(0xFF155674)),
    ThemePreset("Sunset",   Color(0xFF1A0E10), Color(0xFF241216), Color(0xFF321A1E), Color(0xFF412228), Color(0xFFFFEFE8), Color(0xFFE0A98F), Color(0xFF5A2E2E)),
    ThemePreset("Perle",    Color(0xFFF8F4F0), Color(0xFFFFFFFF), Color(0xFFF3EEE8), Color(0xFFE8E0D8), Color(0xFF2B2620), Color(0xFF8A8078), Color(0xFFDCD2C6)),
    ThemePreset("Lavande",  Color(0xFFF3EEFC), Color(0xFFFFFFFF), Color(0xFFEBE1FA), Color(0xFFDECDF5), Color(0xFF2E2140), Color(0xFF8570A3), Color(0xFFD4C2EE)),
    ThemePreset("Menthe",   Color(0xFFEAFAF3), Color(0xFFFFFFFF), Color(0xFFDDF5E9), Color(0xFFC9EEDC), Color(0xFF16332A), Color(0xFF5E9080), Color(0xFFBEE6D3)),
    ThemePreset("Néon",     Color(0xFF120014), Color(0xFF1D0022), Color(0xFF2B0033), Color(0xFF3D0047), Color(0xFFFFE5FA), Color(0xFFE685D1), Color(0xFFFF2FD6)),
    ThemePreset("Tropical", Color(0xFF1A0F02), Color(0xFF241505), Color(0xFF33200A), Color(0xFF432B10), Color(0xFFFFF3E0), Color(0xFFFFC978), Color(0xFFFF7A00)),
    ThemePreset("Arcade",   Color(0xFF05060F), Color(0xFF0A0C1C), Color(0xFF10132C), Color(0xFF181C3E), Color(0xFFE8FBFF), Color(0xFF7DE8FF), Color(0xFF00E5FF)),
)


// One entry per bundled background choice — full (used behind HomeScreen) and thumb (used in the
// Apparence picker) are separate assets so the picker grid never has to downscale a full-
// resolution bitmap on the fly.
data class AppWallpaper(val key: String, val label: String, val fullRes: Int = 0, val thumbRes: Int = 0, val customFilePath: String? = null)

object AppWallpapers {
    const val CUSTOM_KEY = "custom"
    val ALL = listOf(
        AppWallpaper("samurai",   "Samurai",   org.pcbu.libreclient.R.drawable.wallpaper_samurai,   org.pcbu.libreclient.R.drawable.wallpaper_samurai_thumb),
        AppWallpaper("cyberpunk", "Cyberpunk", org.pcbu.libreclient.R.drawable.wallpaper_cyberpunk, org.pcbu.libreclient.R.drawable.wallpaper_cyberpunk_thumb),
        AppWallpaper("forest",    "Forêt",     org.pcbu.libreclient.R.drawable.wallpaper_forest,    org.pcbu.libreclient.R.drawable.wallpaper_forest_thumb),
        AppWallpaper("kawaii",    "Kawaii",    org.pcbu.libreclient.R.drawable.wallpaper_kawaii,    org.pcbu.libreclient.R.drawable.wallpaper_kawaii_thumb),
        AppWallpaper("matrix",    "Matrix",    org.pcbu.libreclient.R.drawable.wallpaper_matrix,    org.pcbu.libreclient.R.drawable.wallpaper_matrix_thumb),
    )
    fun find(key: String): AppWallpaper? {
        if (key == CUSTOM_KEY) return AppTheme.customWallpaperPath?.let { AppWallpaper(CUSTOM_KEY, "Personnalisé", customFilePath = it) }
        return ALL.find { it.key == key }
    }
}

@Composable
fun rememberWallpaperPainter(wallpaper: AppWallpaper, thumb: Boolean = false): Painter {
    val path = wallpaper.customFilePath
    if (path != null) {
        val bitmap = remember(path) {
            runCatching { android.graphics.BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
        return bitmap?.let { BitmapPainter(it) } ?: ColorPainter(BgElevated)
    }
    return painterResource(if (thumb) wallpaper.thumbRes else wallpaper.fullRes)
}

// Brand accent (jaune) — pas un signal "succès" (voir Success ci-dessous), juste la couleur de
// marque utilisée pour les surfaces/icônes/CTA. Texte/icônes DESSUS un fond accent plein doivent
// toujours utiliser onAccent (quasi-noir), jamais blanc.
val Accent:    Color get() = AppTheme.accentColor
val AccentDim: Color get() = AppTheme.accentColor.copy(alpha = 0.10f)
val OnAccent:  Color get() = if (AppTheme.isDark) Color(0xFF0A0A0A) else Color(0xFF1C1A12)

// État "terminé"/"confirmé" — délibérément un vert distinct de l'accent, pas la couleur de marque.
val Success = Color(0xFF1ED760)
val Red     = Color(0xFFFF4444)
// Same "dim" treatment as AccentDim — used behind destructive actions (e.g. "Désactiver") that
// follow the same tinted-pill style as accent buttons.
val RedDim: Color get() = Red.copy(alpha = 0.10f)
// Same "dim" treatment, for the "Activer" pill — a positive/go action distinct from the brand accent.
val SuccessDim: Color get() = Success.copy(alpha = 0.10f)
val Blue    = Color(0xFF0078D4)
val Orange  = Color(0xFFFF7A1A)
val Yellow  = Color(0xFFFFC107)

// Default dark palette is "Ardoise" (graphite, not near-black) — see ThemePresets' "Nuit" for the
// deeper near-#000 alternative, still selectable from Apparence.
val BgPrimary:     Color get() = if (AppTheme.customThemeEnabled) AppTheme.customBgPrimary     else if (AppTheme.isDark) Color(0xFF1C1C1E) else Color(0xFFFAF7EA)
val BgSurface:     Color get() = if (AppTheme.customThemeEnabled) AppTheme.customBgSurface     else if (AppTheme.isDark) Color(0xFF232325) else Color(0xFFFFFFFF)
val BgCard:        Color get() = if (AppTheme.customThemeEnabled) AppTheme.customBgCard        else if (AppTheme.isDark) Color(0xFF2A2A2D) else Color(0xFFFFFFFF)
val BgElevated:    Color get() = if (AppTheme.customThemeEnabled) AppTheme.customBgElevated    else if (AppTheme.isDark) Color(0xFF333336) else Color(0xFFF0EBD9)
val TextPrimary:   Color get() = if (AppTheme.customThemeEnabled) AppTheme.customTextPrimary   else if (AppTheme.isDark) Color(0xFFF5F5F0) else Color(0xFF1C1A12)
val TextSecondary: Color get() = if (AppTheme.customThemeEnabled) AppTheme.customTextSecondary else if (AppTheme.isDark) Color(0xFF9A9A94) else Color(0xFF6B6656)
val Border:        Color get() = if (AppTheme.customThemeEnabled) AppTheme.customBorder        else if (AppTheme.isDark) Color(0xFF3A3A3E) else Color(0xFFE3DCC4)

@Composable
fun BioKeyTheme(content: @Composable () -> Unit) {
    val accent    = AppTheme.accentColor
    val accentDim = AccentDim
    val onAccent  = OnAccent

    val colorScheme = if (AppTheme.isDark) darkColorScheme(
        primary            = accent,
        onPrimary          = onAccent,
        primaryContainer   = accentDim,
        onPrimaryContainer = accent,
        secondary          = Blue,
        onSecondary        = Color(0xFFF5F5F0),
        tertiary           = Orange,
        onTertiary         = onAccent,
        background         = BgPrimary,
        onBackground       = TextPrimary,
        surface            = BgSurface,
        onSurface          = TextPrimary,
        surfaceVariant     = BgCard,
        onSurfaceVariant   = TextSecondary,
        surfaceContainer   = BgCard,
        outline            = Border,
        outlineVariant     = BgElevated,
        error              = Red,
        onError            = onAccent,
    ) else lightColorScheme(
        primary            = accent,
        onPrimary          = onAccent,
        primaryContainer   = accentDim,
        onPrimaryContainer = onAccent,
        secondary          = Blue,
        onSecondary        = Color(0xFFFFFFFF),
        tertiary           = Orange,
        onTertiary         = onAccent,
        background         = BgPrimary,
        onBackground       = TextPrimary,
        surface            = BgSurface,
        onSurface          = TextPrimary,
        surfaceVariant     = BgCard,
        onSurfaceVariant   = TextSecondary,
        surfaceContainer   = BgCard,
        outline            = Border,
        outlineVariant     = BgElevated,
        error              = Red,
        onError            = Color(0xFFFFFFFF),
    )
    val typography = Typography(
        titleLarge  = Typography().titleLarge.copy(fontWeight  = FontWeight.Bold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall  = Typography().titleSmall.copy(fontWeight  = FontWeight.SemiBold),
    )

    // Keeps the system nav bar in lockstep with the app's own background instead of the stock
    // white/black system default.
    val navBarColor = BgPrimary
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.navigationBarColor = navBarColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !AppTheme.isDark
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

// Real Liquid Glass — the Kyant0/backdrop engine (io.github.kyant0:backdrop). True backdrop
// sampling: HomeScreen's wallpaper is recorded ONCE into a shared LayerBackdrop via
// Modifier.layerBackdrop(backdrop); every glass card then samples that SAME recording through
// drawBackdrop, so each card shows exactly the slice of the real background actually behind it.
//
// The 8 LiquidGlassDialog sliders (AppTheme.glass{Blur,Distortion,Reflection,Glow,Corner,Tint,
// Border,ShapeRadius}, 0..1 each) map onto the library's real parameters — see each usage below.
fun Modifier.liquidGlassCard(
    backdrop: Backdrop,
    topRadius: Dp? = null,
    bottomRadius: Dp? = null,
    blurOverride: Float? = null,
    distortionOverride: Float? = null,
    reflectionOverride: Float? = null,
    glowOverride: Float? = null,
    cornerOverride: Float? = null,
    tintOverride: Float? = null,
    borderOverride: Float? = null,
    shapeRadiusOverride: Float? = null,
    tintColor: Color = BgCard,
): Modifier = composed {
    val blurAmount        = (blurOverride ?: AppTheme.glassBlur).coerceIn(0f, 1f)
    val distortionAmount  = (distortionOverride ?: AppTheme.glassDistortion).coerceIn(0f, 1f)
    val reflectionAmount  = (reflectionOverride ?: AppTheme.glassReflection).coerceIn(0f, 1f)
    val glowAmount        = (glowOverride ?: AppTheme.glassGlow).coerceIn(0f, 1f)
    val cornerAmount      = (cornerOverride ?: AppTheme.glassCorner).coerceIn(0f, 1f)
    val tintAmount        = (tintOverride ?: AppTheme.glassTint).coerceIn(0f, 1f)
    val borderAmount      = (borderOverride ?: AppTheme.glassBorder).coerceIn(0f, 1f)
    val shapeRadiusAmount = (shapeRadiusOverride ?: AppTheme.glassShapeRadius).coerceIn(0f, 1f)

    val corner = 2.dp + (shapeRadiusAmount * 26.dp.value).dp
    val top    = topRadius ?: corner
    val bottom = bottomRadius ?: corner
    val shape  = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
    val accent = AppTheme.accentColor
    val tintAlpha = (0.15f + tintAmount * 0.40f).coerceIn(0.15f, 0.55f)

    this.drawBackdrop(
        backdrop = backdrop,
        shape    = { shape },
        effects  = {
            vibrancy()
            if (blurAmount > 0f) blur((blurAmount * 22f).dp.toPx())
            if (distortionAmount > 0f) {
                val refractionHeightPx = (6f + cornerAmount * 30f).dp.toPx()
                val refractionAmountPx = (distortionAmount * 50f).dp.toPx()
                lens(refractionHeightPx, refractionAmountPx, chromaticAberration = true)
            }
        },
        highlight = {
            if (reflectionAmount <= 0f && borderAmount <= 0f) null
            else Highlight(
                width = (0.5f + borderAmount * 2.5f).dp,
                alpha = (0.15f + reflectionAmount * 0.85f).coerceIn(0f, 1f),
                style = HighlightStyle.Default,
            )
        },
        shadow = {
            if (glowAmount <= 0f) null
            else Shadow(
                radius    = (8f + glowAmount * 20f).dp,
                offset    = DpOffset.Zero,
                color     = accent,
                alpha     = glowAmount * 0.55f,
                blendMode = BlendMode.Plus,
            )
        },
        onDrawSurface = {
            drawRect(tintColor.copy(alpha = tintAlpha))
        },
    )
}

// Short, tight fade at the leading/trailing edge of a scrollable list — a soft hint that there's
// more content past the edge instead of a hard visual cutoff. Purely cosmetic (not tied to actual
// scroll position/exhaustion): CompositingStrategy.Offscreen renders the content to an offscreen
// layer first so the DstIn gradient masks it by alpha rather than blending over whatever's beneath.
fun Modifier.verticalEdgeFade(edgeSize: Dp = 14.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = edgeSize.toPx()
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fadePx),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fadePx, endY = size.height),
            blendMode = BlendMode.DstIn,
        )
    }

fun Modifier.horizontalEdgeFade(edgeSize: Dp = 14.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = edgeSize.toPx()
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fadePx),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fadePx, endX = size.width),
            blendMode = BlendMode.DstIn,
        )
    }

// Provided by ScreenWallpaperBackdrop so every GlassCard on that screen samples the SAME recording
// of the real wallpaper behind it (true backdrop sampling), instead of each card drawing its own
// independent crop of the wallpaper scaled to its own tiny bounds — which is what card-local
// rendering looks like: a colorful blur inside each card while the rest of the screen stays flat.
val LocalScreenBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** Whether a wallpaper is actually being shown behind the UI: it only is when liquid glass is on and one is picked. */
val wallpaperBackdropActive: Boolean
    get() = AppTheme.liquidGlassEnabled && AppWallpapers.find(AppTheme.wallpaperKey) != null

/**
 * Wrap a whole screen's root content in this once, near the top of the composable, so every
 * [GlassCard] inside shares one recording of the real wallpaper instead of faking its own. Degrades
 * to plain [content] with no wallpaper drawn when liquid glass is off or no wallpaper is set —
 * GlassCard's own fallbackColor path then takes over for each card.
 */
@Composable
fun ScreenWallpaperBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val wallpaper = AppWallpapers.find(AppTheme.wallpaperKey)
    if (AppTheme.liquidGlassEnabled && wallpaper != null) {
        val backdrop = rememberLayerBackdrop { drawRect(BgPrimary); drawContent() }
        Box(modifier) {
            Image(
                painter            = rememberWallpaperPainter(wallpaper),
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.matchParentSize().layerBackdrop(backdrop),
            )
            CompositionLocalProvider(LocalScreenBackdrop provides backdrop) {
                content()
            }
        }
    } else {
        Box(modifier, content = content)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    fallbackColor: Color = BgCard,
    content: @Composable BoxScope.() -> Unit,
) {
    val wallpaper = AppWallpapers.find(AppTheme.wallpaperKey)
    val sharedBackdrop = LocalScreenBackdrop.current
    when {
        AppTheme.liquidGlassEnabled && wallpaper != null && sharedBackdrop != null -> {
            // Real backdrop sampling: this card shows exactly the slice of the actual wallpaper
            // behind it, recorded once for the whole screen by ScreenWallpaperBackdrop.
            Box(modifier = modifier.clip(RoundedCornerShape(radius))) {
                Box(Modifier.matchParentSize().liquidGlassCard(backdrop = sharedBackdrop, topRadius = radius, bottomRadius = radius))
                content()
            }
        }
        AppTheme.liquidGlassEnabled && wallpaper != null -> {
            // No screen-wide backdrop was provided — fall back to a local crop of the wallpaper so
            // glass still looks reasonable instead of a hard regression to flat color.
            val backdrop = rememberLayerBackdrop { drawRect(fallbackColor); drawContent() }
            Box(modifier = modifier.clip(RoundedCornerShape(radius))) {
                Image(
                    painter            = rememberWallpaperPainter(wallpaper),
                    contentDescription = null,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.matchParentSize().layerBackdrop(backdrop),
                )
                Box(Modifier.matchParentSize().liquidGlassCard(backdrop = backdrop, topRadius = radius, bottomRadius = radius))
                content()
            }
        }
        else -> Box(modifier = modifier.clip(RoundedCornerShape(radius)).background(fallbackColor), content = content)
    }
}

// Light frosted-glass strip for a screen header when a wallpaper is set — fixed/subtle and
// blur-only, independent of the LiquidGlassDialog sliders.
@Composable
fun HeaderBlurBackground(modifier: Modifier, wallpaperPainter: Painter) {
    Box(modifier = modifier) {
        Image(
            painter            = wallpaperPainter,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return@graphicsLayer
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(10f, 10f, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                    clip = true
                },
        )
        Box(modifier = Modifier.matchParentSize().background(BgPrimary.copy(alpha = 0.30f)))
    }
}

// Stand-in "wallpaper" for LiquidGlassDialog's live preview banner when no real one is picked for
// the preview — a grid on a dark field gives blur/edge-distortion something visible to bend.
@Composable
fun rememberGridPreviewPainter(): Painter {
    val bg   = Color(0xFF1A1A1E)
    val line = Accent.copy(alpha = 0.45f)
    val density = LocalDensity.current
    val stepPx  = with(density) { 20.dp.toPx() }
    val widthPx = with(density) { 1.dp.toPx() }
    return remember(bg, line, stepPx, widthPx) {
        object : Painter() {
            override val intrinsicSize = Size.Unspecified
            override fun DrawScope.onDraw() {
                drawRect(bg)
                var x = 0f
                while (x <= size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = widthPx)
                    x += stepPx
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = widthPx)
                    y += stepPx
                }
            }
        }
    }
}
