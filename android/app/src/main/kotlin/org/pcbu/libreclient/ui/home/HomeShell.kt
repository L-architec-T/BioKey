package org.pcbu.libreclient.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.pcbu.libreclient.R
import org.pcbu.libreclient.pairing.PairingRepository
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.ui.appearance.ConfigDialog
import org.pcbu.libreclient.ui.components.AppTopBar
import org.pcbu.libreclient.ui.components.GlassBottomNavBar
import org.pcbu.libreclient.ui.components.GlassMenuPanel
import org.pcbu.libreclient.ui.components.MenuEntry
import org.pcbu.libreclient.ui.components.fullscreenMenuEntry
import org.pcbu.libreclient.ui.components.persistentNotificationMenuEntry
import org.pcbu.libreclient.ui.components.NavTab
import org.pcbu.libreclient.ui.devices.DeviceListScreen
import org.pcbu.libreclient.ui.language.AppLanguage
import org.pcbu.libreclient.ui.language.LanguageDialog
import org.pcbu.libreclient.ui.settings.SettingsScreen
import org.pcbu.libreclient.ui.theme.BgPrimary
import org.pcbu.libreclient.ui.theme.ScreenWallpaperBackdrop
import org.pcbu.libreclient.ui.theme.TextPrimary
import org.pcbu.libreclient.ui.theme.wallpaperBackdropActive

/**
 * Home of BioKey, built from the android-foundation components: top bar + full-page menu, floating
 * glass bottom bar, one shared wallpaper recording for every glass surface.
 */
@Composable
fun HomeShell(
    pairingRepository: PairingRepository,
    preferencesStore: PreferencesStore,
    onDeviceClick: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
) {
    val context = LocalContext.current
    val tabs = listOf(
        NavTab(stringResource(R.string.tab_devices), Icons.Outlined.Computer),
        NavTab(stringResource(R.string.tab_settings), Icons.Outlined.Settings),
    )
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showLanguage by rememberSaveable { mutableStateOf(false) }

    ScreenWallpaperBackdrop(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = if (wallpaperBackdropActive) Color.Transparent else BgPrimary,
            contentColor = TextPrimary,
            topBar = { AppTopBar(name = "BioKey", menuOpen = menuOpen, onMenuToggle = { menuOpen = !menuOpen }) },
            bottomBar = {
                GlassBottomNavBar(tabs = tabs, selectedIndex = tab, onSelect = { tab = it; menuOpen = false })
            },
        ) { padding ->
            val content = Modifier.padding(padding)
            if (menuOpen) {
                GlassMenuPanel(
                    modifier = content,
                    onDismiss = { menuOpen = false },
                    entries = listOf(
                        MenuEntry(Icons.Outlined.Palette, "Thèmes et apparence", "Thème, couleur, fond d'écran, effet verre") {
                            showAppearance = true
                        },
                        MenuEntry(Icons.Outlined.Language, "Langue / Language", AppLanguage.current(context).label) {
                            showLanguage = true
                        },
                        MenuEntry(Icons.Outlined.Share, "Partager BioKey", "Envoyer le lien de téléchargement") {
                            val link = "https://github.com/L-architec-T/biokey/releases/latest"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "BioKey : déverrouille ton PC avec l'empreinte de ton téléphone.\n$link")
                            }
                            context.startActivity(Intent.createChooser(send, "Partager BioKey"))
                        },
                        fullscreenMenuEntry(context),
                        org.pcbu.libreclient.analytics.analyticsMenuEntry(),
                        persistentNotificationMenuEntry(context, org.pcbu.libreclient.service.UnlockForegroundService.CHANNEL_ID),
                    ),
                )
            } else when (tab) {
                0 -> DeviceListScreen(
                    pairingRepository = pairingRepository,
                    preferencesStore = preferencesStore,
                    onDeviceClick = onDeviceClick,
                    onAddDeviceClick = onAddDeviceClick,
                    modifier = content,
                )
                else -> SettingsScreen(preferencesStore = preferencesStore, modifier = content)
            }
        }
    }

    if (showAppearance) ConfigDialog(onDismiss = { showAppearance = false })
    if (showLanguage) LanguageDialog(onDismiss = { showLanguage = false })
}
