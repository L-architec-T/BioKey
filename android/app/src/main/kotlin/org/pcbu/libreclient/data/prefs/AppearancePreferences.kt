package org.pcbu.libreclient.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Default accent: lemon (#CCFF00, the palette's citron). Used until the user picks another one, and by "Réinitialiser l'apparence". */
const val DEFAULT_ACCENT_ARGB = 0xFFF5C400.toInt() // same yellow as Donova

private val Context.appearanceDataStore by preferencesDataStore(name = "biokey_appearance_prefs")

/** Everything the "Apparence" panel (ported from Donova) reads and writes — see ui/appearance. */
data class AppearanceSnapshot(
    val lightTheme: Boolean,
    val accentColor: Int,
    val wallpaperKey: String,
    val wallpaperIntensity: Float,
    val customWallpaperPath: String?,
    val customThemeEnabled: Boolean,
    val customBgPrimary: Int,
    val customBgSurface: Int,
    val customBgCard: Int,
    val customBgElevated: Int,
    val customTextPrimary: Int,
    val customTextSecondary: Int,
    val customBorder: Int,
    val liquidGlassEnabled: Boolean,
    val glassBlur: Float,
    val glassDistortion: Float,
    val glassReflection: Float,
    val glassGlow: Float,
    val glassCorner: Float,
    val glassTint: Float,
    val glassBorder: Float,
    val glassShapeRadius: Float,
)

class AppearancePreferences(private val context: Context) {

    private object Keys {
        val LIGHT_THEME = booleanPreferencesKey("light_theme")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val WALLPAPER_KEY = stringPreferencesKey("wallpaper_key")
        val WALLPAPER_INTENSITY = floatPreferencesKey("wallpaper_intensity")
        val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
        val CUSTOM_THEME_ON = booleanPreferencesKey("custom_theme_on")
        val CUSTOM_BG_PRIMARY = intPreferencesKey("custom_bg_primary")
        val CUSTOM_BG_SURFACE = intPreferencesKey("custom_bg_surface")
        val CUSTOM_BG_CARD = intPreferencesKey("custom_bg_card")
        val CUSTOM_BG_ELEVATED = intPreferencesKey("custom_bg_elevated")
        val CUSTOM_TEXT_PRIMARY = intPreferencesKey("custom_text_primary")
        val CUSTOM_TEXT_SECONDARY = intPreferencesKey("custom_text_secondary")
        val CUSTOM_BORDER = intPreferencesKey("custom_border")
        val LIQUID_GLASS_ON = booleanPreferencesKey("liquid_glass_on")
        val GLASS_BLUR = floatPreferencesKey("glass_blur")
        val GLASS_DISTORTION = floatPreferencesKey("glass_distortion")
        val GLASS_REFLECTION = floatPreferencesKey("glass_reflection")
        val GLASS_GLOW = floatPreferencesKey("glass_glow")
        val GLASS_CORNER = floatPreferencesKey("glass_corner")
        val GLASS_TINT = floatPreferencesKey("glass_tint")
        val GLASS_BORDER = floatPreferencesKey("glass_border")
        val GLASS_SHAPE_RADIUS = floatPreferencesKey("glass_shape_radius")
    }

    suspend fun load(): AppearanceSnapshot {
        val prefs = context.appearanceDataStore.data.first()
        return AppearanceSnapshot(
            lightTheme = prefs[Keys.LIGHT_THEME] ?: false,
            accentColor = prefs[Keys.ACCENT_COLOR] ?: DEFAULT_ACCENT_ARGB,
            wallpaperKey = prefs[Keys.WALLPAPER_KEY] ?: "",
            wallpaperIntensity = prefs[Keys.WALLPAPER_INTENSITY] ?: 1f,
            customWallpaperPath = prefs[Keys.CUSTOM_WALLPAPER_PATH],
            customThemeEnabled = prefs[Keys.CUSTOM_THEME_ON] ?: false,
            customBgPrimary = prefs[Keys.CUSTOM_BG_PRIMARY] ?: 0xFF0A0A0A.toInt(),
            customBgSurface = prefs[Keys.CUSTOM_BG_SURFACE] ?: 0xFF121212.toInt(),
            customBgCard = prefs[Keys.CUSTOM_BG_CARD] ?: 0xFF17171A.toInt(),
            customBgElevated = prefs[Keys.CUSTOM_BG_ELEVATED] ?: 0xFF1E1E22.toInt(),
            customTextPrimary = prefs[Keys.CUSTOM_TEXT_PRIMARY] ?: 0xFFF5F5F0.toInt(),
            customTextSecondary = prefs[Keys.CUSTOM_TEXT_SECONDARY] ?: 0xFF9A9A94.toInt(),
            customBorder = prefs[Keys.CUSTOM_BORDER] ?: 0xFF2C2C30.toInt(),
            liquidGlassEnabled = prefs[Keys.LIQUID_GLASS_ON] ?: false,
            glassBlur = prefs[Keys.GLASS_BLUR] ?: 0f,
            glassDistortion = prefs[Keys.GLASS_DISTORTION] ?: 1f,
            glassReflection = prefs[Keys.GLASS_REFLECTION] ?: 0.3f,
            glassGlow = prefs[Keys.GLASS_GLOW] ?: 0f,
            glassCorner = prefs[Keys.GLASS_CORNER] ?: 0f,
            glassTint = prefs[Keys.GLASS_TINT] ?: 0f,
            glassBorder = prefs[Keys.GLASS_BORDER] ?: 0.5f,
            glassShapeRadius = prefs[Keys.GLASS_SHAPE_RADIUS] ?: 0.4f,
        )
    }

    suspend fun setLightTheme(v: Boolean) = edit { it[Keys.LIGHT_THEME] = v }
    suspend fun setAccentColor(v: Int) = edit { it[Keys.ACCENT_COLOR] = v }
    suspend fun setWallpaperKey(v: String) = edit { it[Keys.WALLPAPER_KEY] = v }
    suspend fun setWallpaperIntensity(v: Float) = edit { it[Keys.WALLPAPER_INTENSITY] = v }
    suspend fun getCustomWallpaperPath(): String? = context.appearanceDataStore.data.first()[Keys.CUSTOM_WALLPAPER_PATH]
    suspend fun setCustomWallpaperPath(v: String?) = edit {
        if (v == null) it.remove(Keys.CUSTOM_WALLPAPER_PATH) else it[Keys.CUSTOM_WALLPAPER_PATH] = v
    }
    suspend fun setCustomThemeEnabled(v: Boolean) = edit { it[Keys.CUSTOM_THEME_ON] = v }
    suspend fun setCustomBgPrimary(v: Int) = edit { it[Keys.CUSTOM_BG_PRIMARY] = v }
    suspend fun setCustomBgSurface(v: Int) = edit { it[Keys.CUSTOM_BG_SURFACE] = v }
    suspend fun setCustomBgCard(v: Int) = edit { it[Keys.CUSTOM_BG_CARD] = v }
    suspend fun setCustomBgElevated(v: Int) = edit { it[Keys.CUSTOM_BG_ELEVATED] = v }
    suspend fun setCustomTextPrimary(v: Int) = edit { it[Keys.CUSTOM_TEXT_PRIMARY] = v }
    suspend fun setCustomTextSecondary(v: Int) = edit { it[Keys.CUSTOM_TEXT_SECONDARY] = v }
    suspend fun setCustomBorder(v: Int) = edit { it[Keys.CUSTOM_BORDER] = v }
    suspend fun setLiquidGlassEnabled(v: Boolean) = edit { it[Keys.LIQUID_GLASS_ON] = v }
    suspend fun setGlassBlur(v: Float) = edit { it[Keys.GLASS_BLUR] = v }
    suspend fun setGlassDistortion(v: Float) = edit { it[Keys.GLASS_DISTORTION] = v }
    suspend fun setGlassReflection(v: Float) = edit { it[Keys.GLASS_REFLECTION] = v }
    suspend fun setGlassGlow(v: Float) = edit { it[Keys.GLASS_GLOW] = v }
    suspend fun setGlassCorner(v: Float) = edit { it[Keys.GLASS_CORNER] = v }
    suspend fun setGlassTint(v: Float) = edit { it[Keys.GLASS_TINT] = v }
    suspend fun setGlassBorder(v: Float) = edit { it[Keys.GLASS_BORDER] = v }
    suspend fun setGlassShapeRadius(v: Float) = edit { it[Keys.GLASS_SHAPE_RADIUS] = v }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.appearanceDataStore.edit(block)
    }
}
