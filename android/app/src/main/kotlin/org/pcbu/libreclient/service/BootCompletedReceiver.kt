package org.pcbu.libreclient.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pcbu.libreclient.storage.db.PcbuDatabase

/** Restarts the unlock listener after reboot, if at least one PC is paired. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasDevices = PcbuDatabase.getInstance(context).pairedDeviceDao().getAll().isNotEmpty()
                if (hasDevices) {
                    UnlockForegroundService.start(context)
                    UnlockServiceHeartbeatWorker.schedule(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
