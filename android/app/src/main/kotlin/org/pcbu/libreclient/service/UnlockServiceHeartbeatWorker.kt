package org.pcbu.libreclient.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.storage.SecretsStore
import org.pcbu.libreclient.storage.db.PcbuDatabase
import org.pcbu.libreclient.unlock.LockBroadcastSender

/**
 * Periodic safety net for [UnlockForegroundService], with two independent jobs:
 *
 * 1. Real devices (Samsung's own "put unused apps to sleep" battery management in particular,
 *    on top of everything else stock Android can do) have been seen killing the foreground
 *    service outright despite it being battery-optimization-exempt and holding a wake lock —
 *    with nothing left running afterward to relaunch it. Re-issuing [UnlockForegroundService.start]
 *    is a no-op if it's already running.
 *
 * 2. GDM never starts a PAM conversation for anyone until a user tile is selected — a freshly
 *    booted PC sits on the bare user list with pcbu_auth never running, so it can never wake this
 *    phone to ask it to select a user (see [UnlockForegroundService.sendSelectUser]'s own trigger,
 *    which only fires from a wake this daemon never receives, or from the Wi-Fi callback firing
 *    once at service creation — nothing re-fires it while the service just stays alive). This
 *    worker sends selectUser directly, independent of the service's lifecycle, so a paired PC
 *    stuck on its login screen gets un-stuck within 15 minutes even if nothing else does.
 *    pcbu-lockd now also does this on its own startup (no phone needed at all) — this is the
 *    belt-and-suspenders fallback for whenever that isn't enough (PC already up, phone was out
 *    of range then, etc.).
 *
 * WorkManager's periodic jobs are backed by JobScheduler, survive process death, and reschedule
 * themselves — so this fires every 15 minutes, the shortest interval the platform allows.
 */
class UnlockServiceHeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val devices = PcbuDatabase.getInstance(applicationContext).pairedDeviceDao().getAll()
        if (devices.isEmpty()) return Result.success()

        UnlockForegroundService.start(applicationContext)

        val preferences = PreferencesStore(applicationContext)
        if (preferences.selectUserOnWifiConnectEnabled.first()) {
            val secrets = SecretsStore(applicationContext)
            for (device in devices) {
                val encKey = secrets.readEncKey(device.deviceId) ?: continue
                runCatching { LockBroadcastSender.sendSelectUser(applicationContext, device.deviceId, encKey) }
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "unlock_service_heartbeat"

        /** KEEP (not REPLACE): called from multiple entry points (boot, service startup) that
         * may race or repeat; the first successful schedule is all that's needed. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UnlockServiceHeartbeatWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
