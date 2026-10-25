package com.security.__PKG__.ui.components

// Canonical source: android-foundation/design-system/components/SystemPrompts.kt
// Copied into every app, with __PKG__ replaced (and the GlassAlertDialog / theme imports adjusted
// to where the app keeps them). Improve it in android-foundation, not in a single app.

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.Settings
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.security.__PKG__.ui.appearance.GlassAlertDialog
import com.security.__PKG__.ui.theme.Accent
import com.security.__PKG__.ui.theme.TextPrimary
import com.security.__PKG__.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val PREFS = "foundation_system"
private const val KEY_FULLSCREEN = "fullscreen"
private const val KEY_FULLSCREEN_ASKED = "fullscreen_asked"
private const val KEY_PERSISTENT_ASKED = "persistent_notification_asked"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen display (status + navigation bars hidden, portrait): a per-app choice, offered once
 * by [SystemPromptsHost] and switchable afterwards from the menu ([fullscreenMenuEntry]). Bars come
 * back temporarily with a swipe from the edge.
 */
object FullscreenMode {
    var enabled by mutableStateOf(false)
        private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        enabled = prefs(context).getBoolean(KEY_FULLSCREEN, false)
        loaded = true
    }

    fun set(context: Context, value: Boolean) {
        enabled = value
        loaded = true
        prefs(context).edit().putBoolean(KEY_FULLSCREEN, value).apply()
    }
}

/**
 * Root content padding for every app (instead of `safeDrawingPadding()`): the safe drawing area
 * normally, but in [FullscreenMode] only the keyboard — otherwise the display cutout (camera
 * hole/notch) keeps an empty band at the top even though the status bar is hidden.
 */
@Composable
fun Modifier.appContentPadding(): Modifier =
    windowInsetsPadding(if (FullscreenMode.enabled) WindowInsets.ime else WindowInsets.safeDrawing)

private fun Activity.applyFullscreen(enabled: Boolean) {
    // Let the window extend into the cutout area in full screen (default mode keeps it out of the
    // cutout band on pre-edge-to-edge Android versions, leaving a black strip at the top).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val mode = if (enabled) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        if (window.attributes.layoutInDisplayCutoutMode != mode) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = mode }
        }
    }
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (enabled) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** Opens the system settings of one notification channel, where the user can switch it off. */
fun openNotificationChannelSettings(context: Context, channelId: String) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Whether the persistent notification of [channelId] is currently shown (channel exists and is on). */
private fun isChannelVisible(context: Context, channelId: String): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return false
    if (!nm.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    val channel = nm.getNotificationChannel(channelId) ?: return false
    return channel.importance != NotificationManager.IMPORTANCE_NONE
}

/** Menu row that toggles [FullscreenMode]. */
fun fullscreenMenuEntry(context: Context): MenuEntry = MenuEntry(
    icon = if (FullscreenMode.enabled) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
    title = "Plein écran",
    subtitle = if (FullscreenMode.enabled) "Activé — toucher pour réafficher les barres" else "Masquer les barres système",
    onClick = { FullscreenMode.set(context, !FullscreenMode.enabled) },
)

/** Menu row that opens the settings of the persistent (foreground service) notification channel. */
fun persistentNotificationMenuEntry(context: Context, channelId: String): MenuEntry = MenuEntry(
    icon = Icons.Outlined.NotificationsOff,
    title = "Notification permanente",
    subtitle = "La masquer (le service continue de tourner)",
    onClick = { openNotificationChannelSettings(context, channelId) },
)

/**
 * Place once inside the app theme, next to AutoUpdateHost(). Applies [FullscreenMode] to the
 * activity window (and re-applies it whenever the window regains focus: a dialog or the keyboard
 * brings the bars back), then offers, once each and a little after launch:
 *  - to switch to full screen;
 *  - when [persistentChannelId] is given and that notification is showing, to hide it (the
 *    service keeps running, only the notification goes away — Android lets the user switch off
 *    the channel of a foreground service).
 * [enabled] = false skips the proposals for this launch (e.g. opened from a widget deep link).
 */
@Composable
fun SystemPromptsHost(persistentChannelId: String? = null, enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    remember(context) { FullscreenMode.load(context) }

    val fullscreen = FullscreenMode.enabled
    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        activity?.applyFullscreen(fullscreen)
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus && fullscreen) activity?.applyFullscreen(true)
        }
        window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(listener)
        onDispose { window?.decorView?.viewTreeObserver?.removeOnWindowFocusChangeListener(listener) }
    }

    var prompt by remember { mutableStateOf<SystemPrompt?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        // Leaves the first frame (and a possible update popup) alone before asking anything.
        delay(1_500L)
        val p = prefs(context)
        prompt = when {
            !p.getBoolean(KEY_FULLSCREEN_ASKED, false) && !FullscreenMode.enabled -> SystemPrompt.Fullscreen
            persistentChannelId != null && !p.getBoolean(KEY_PERSISTENT_ASKED, false) &&
                isChannelVisible(context, persistentChannelId) -> SystemPrompt.PersistentNotification
            else -> null
        }
    }

    fun answered(key: String, next: SystemPrompt?) {
        prefs(context).edit().putBoolean(key, true).apply()
        prompt = next
    }
    // After the first answer, chain to the notification proposal in the same launch if it applies.
    fun afterFullscreen(): SystemPrompt? =
        if (persistentChannelId != null && !prefs(context).getBoolean(KEY_PERSISTENT_ASKED, false) &&
            isChannelVisible(context, persistentChannelId)) SystemPrompt.PersistentNotification else null

    when (prompt) {
        SystemPrompt.Fullscreen -> ProposalDialog(
            title = "Plein écran",
            text = "Afficher l'app en plein écran (portrait) ? Les barres du haut et du bas sont masquées ; " +
                "un glissement depuis le bord les fait réapparaître. Modifiable à tout moment depuis le menu.",
            confirm = "ACTIVER",
            onConfirm = { FullscreenMode.set(context, true); answered(KEY_FULLSCREEN_ASKED, afterFullscreen()) },
            onDismiss = { answered(KEY_FULLSCREEN_ASKED, afterFullscreen()) },
        )
        SystemPrompt.PersistentNotification -> ProposalDialog(
            title = "Notification permanente",
            text = "Le service de l'app affiche une notification permanente. Vous pouvez la masquer : " +
                "le service continue de fonctionner normalement. Dans l'écran qui s'ouvre, désactivez la catégorie.",
            confirm = "MASQUER",
            onConfirm = {
                answered(KEY_PERSISTENT_ASKED, null)
                openNotificationChannelSettings(context, persistentChannelId!!)
            },
            onDismiss = { answered(KEY_PERSISTENT_ASKED, null) },
        )
        null -> Unit
    }
}

private enum class SystemPrompt { Fullscreen, PersistentNotification }

@Composable
private fun ProposalDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("NON MERCI", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        },
    )
}
