package org.pcbu.libreclient.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * The unlock service must survive Doze/App Standby to receive UDP wake
 * broadcasts while the screen is off, which needs this exemption on top of
 * the foreground-service type — Samsung's own battery manager kills
 * foreground services too if the app isn't allowlisted.
 */
fun requestIgnoreBatteryOptimizations(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
