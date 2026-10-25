package org.pcbu.libreclient.ui.util

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * On Android 14+, a notification's full-screen intent (see UnlockForegroundService's
 * challenge notification) needs this explicitly granted, otherwise it silently stays
 * a plain heads-up notification the user must find and tap manually.
 */
fun requestFullScreenIntentPermission(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
    if (notificationManager.canUseFullScreenIntent()) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        Uri.parse("package:${context.packageName}"),
    )
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
