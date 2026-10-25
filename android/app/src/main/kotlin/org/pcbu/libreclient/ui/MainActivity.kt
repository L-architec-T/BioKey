package org.pcbu.libreclient.ui

import org.pcbu.libreclient.analytics.DashboardAnalytics

import org.pcbu.libreclient.ui.language.AppLanguage
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import org.pcbu.libreclient.data.prefs.AppearancePreferences
import org.pcbu.libreclient.pairing.PairingRepository
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.ui.nav.AppNavHost
import org.pcbu.libreclient.ui.theme.AppTheme
import org.pcbu.libreclient.ui.theme.BioKeyTheme
import org.pcbu.libreclient.update.AutoUpdateHost
import org.pcbu.libreclient.ui.components.SystemPromptsHost
import org.pcbu.libreclient.ui.components.appContentPadding
import org.pcbu.libreclient.ui.theme.BgPrimary
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    private val pairingRepository by lazy { PairingRepository(this) }
    private val preferencesStore by lazy { PreferencesStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DashBoard analytics (installs, sessions, retention) — see DashBoard/integration/README.md
        DashboardAnalytics.init(application, "pk_aa5fac8b6ac5440e68ac54c52b48ca00")

        // One small DataStore read before the first frame (tolerated at startup, same as the other
        // apps) — avoids a flash of default theme values while the saved appearance loads.
        val snapshot = runBlocking { AppearancePreferences(this@MainActivity).load() }
        AppTheme.isDark              = !snapshot.lightTheme
        AppTheme.accentColor         = Color(snapshot.accentColor)
        AppTheme.wallpaperKey        = snapshot.wallpaperKey
        AppTheme.wallpaperIntensity  = snapshot.wallpaperIntensity
        AppTheme.customWallpaperPath = snapshot.customWallpaperPath
        AppTheme.customThemeEnabled  = snapshot.customThemeEnabled
        AppTheme.customBgPrimary     = Color(snapshot.customBgPrimary)
        AppTheme.customBgSurface     = Color(snapshot.customBgSurface)
        AppTheme.customBgCard        = Color(snapshot.customBgCard)
        AppTheme.customBgElevated    = Color(snapshot.customBgElevated)
        AppTheme.customTextPrimary   = Color(snapshot.customTextPrimary)
        AppTheme.customTextSecondary = Color(snapshot.customTextSecondary)
        AppTheme.customBorder        = Color(snapshot.customBorder)
        AppTheme.liquidGlassEnabled  = snapshot.liquidGlassEnabled
        AppTheme.glassBlur           = snapshot.glassBlur
        AppTheme.glassDistortion     = snapshot.glassDistortion
        AppTheme.glassReflection     = snapshot.glassReflection
        AppTheme.glassGlow           = snapshot.glassGlow
        AppTheme.glassCorner         = snapshot.glassCorner
        AppTheme.glassTint           = snapshot.glassTint
        AppTheme.glassBorder         = snapshot.glassBorder
        AppTheme.glassShapeRadius    = snapshot.glassShapeRadius

        setContent {
            BioKeyTheme {
                AutoUpdateHost()
                SystemPromptsHost(persistentChannelId = org.pcbu.libreclient.service.UnlockForegroundService.CHANNEL_ID)
                // Insets once at the root (as in CallUp): safe drawing area normally, only the
                // keyboard in full screen — otherwise the screens' Scaffolds keep reserving the
                // camera-cutout band, an empty strip above the top bar.
                Surface(modifier = Modifier.fillMaxSize().appContentPadding(), color = BgPrimary) {
                    AppNavHost(pairingRepository = pairingRepository, preferencesStore = preferencesStore)
                }
            }
        }
    }
}
