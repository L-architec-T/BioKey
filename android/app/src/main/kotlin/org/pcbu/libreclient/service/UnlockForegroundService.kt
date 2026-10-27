package org.pcbu.libreclient.service

import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.MacroDto
import org.pcbu.libreclient.premium.PremiumAccess
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.media.AudioAttributes
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.pcbu.libreclient.R
import org.pcbu.libreclient.protocol.model.PcbuJson
import org.pcbu.libreclient.protocol.model.UdpBroadcast
import org.pcbu.libreclient.protocol.model.UnlockErrors
import org.pcbu.libreclient.storage.DeviceIdentityStore
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.storage.SecretsStore
import org.pcbu.libreclient.storage.db.PairedDeviceEntity
import org.pcbu.libreclient.storage.db.PcbuDatabase
import org.pcbu.libreclient.ui.MainActivity
import org.pcbu.libreclient.storage.db.displayName
import org.pcbu.libreclient.unlock.AuthProgram
import org.pcbu.libreclient.unlock.BlePresenceAdvertiser
import org.pcbu.libreclient.unlock.BlePresenceBeacon
import org.pcbu.libreclient.unlock.LockBroadcastSender
import org.pcbu.libreclient.unlock.UnlockSessionError
import org.pcbu.libreclient.unlock.UnlockTcpSession
import org.pcbu.libreclient.unlock.UnlockUdpListener

/**
 * Keeps a UDP socket bound on this phone's paired udpPort so a PC can wake
 * it for an unlock. Per-broadcast handling: UDP wake -> TCP session
 * (DEVICE_ID first, per protocol) -> decrypt challenge -> biometric prompt
 * via [UnlockChallengeActivity] -> encrypted response.
 *
 * Holds a wake lock + Wi-Fi lock for the whole time it runs (see
 * [acquireLocks]) so the UDP listener keeps receiving broadcasts with the
 * screen off. The listener socket rebinds on a Wi-Fi network change (see
 * [resetListenerForNetworkChange]) and periodically regardless of any
 * trigger (see [listenLoop]'s PERIODIC_REBIND_INTERVAL_MS), since real
 * devices went silently deaf with no network change at all. A
 * [UnlockServiceHeartbeatWorker] periodic job is the last line of defense
 * against this service getting killed outright (Samsung's own battery
 * management in particular) despite everything above.
 */
class UnlockForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // @Volatile: written from listenLoop() (Dispatchers.IO) and, since resetListenerForNetworkChange(),
    // also from wifiNetworkCallback (ConnectivityManager's own callback thread) — needs cross-thread
    // visibility even though there's no compound read-modify-write across the two writers.
    @Volatile private var listener: UnlockUdpListener? = null
    private var listenLoopJob: Job? = null
    private val notificationIdCounter = AtomicInteger(1)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // PC sends the same wake both over UDP and FCM (see PcbuFirebaseMessagingService), tagged
    // with an identical wakeId (see UdpBroadcast.wakeId) — this stops the second one from
    // opening its own duplicate challenge (and beeping/vibrating again) a few hundred ms after
    // the first. Keyed by that ID rather than a reconstructed deviceId:IP:port so identity is
    // exact instead of inferred; retention can be generous (WAKE_ID_RETENTION_MS) since a real
    // duplicate always carries the same ID no matter the delay between channels, while a
    // genuinely new unlock attempt always gets a fresh one from the PC.
    private val recentWakeIds = ConcurrentHashMap<String, Long>()

    // Wi-Fi can report onAvailable several times for one reconnect, and the send itself is
    // near-instant (just an encrypted UDP packet), so a plain in-flight lock released right
    // after sending wouldn't dedupe anything. Each value here is "locked until" (epoch millis),
    // held through SELECT_USER_COOLDOWN_MS *after* the send completes, not just during it —
    // see [tryAcquireSelectUserLock]/[releaseSelectUserLock].
    private val recentSelectUserDeviceIds = ConcurrentHashMap<String, Long>()

    private var connectivityManager: ConnectivityManager? = null
    // Proximity lock: heartbeats only make sense (and only get through) while on Wi-Fi.
    @Volatile private var wifiConnected = false
    // A presence went out since the last Wi-Fi (re)connect, so the PC is actually armed.
    @Volatile private var presenceArmed = false
    private var outOfRangeJob: Job? = null
    private lateinit var bleAdvertiser: BlePresenceAdvertiser
    private lateinit var premium: PremiumAccess
    // The beacon went out at least once, so PCs may be armed and need an explicit disarm.
    @Volatile private var bleArmed = false
    @Volatile private var bleDisarming = false

    private lateinit var db: PcbuDatabase
    private lateinit var secrets: SecretsStore
    private lateinit var identity: DeviceIdentityStore
    private lateinit var preferences: PreferencesStore

    override fun onCreate() {
        super.onCreate()
        db = PcbuDatabase.getInstance(this)
        secrets = SecretsStore(this)
        identity = DeviceIdentityStore(this)
        preferences = PreferencesStore(this)
        createNotificationChannel()
        registerWifiCallback()
        premium = PremiumAccess.get(this)
        startPresenceLoop()
        bleAdvertiser = BlePresenceAdvertiser(this)
        startBlePresenceLoop()
        startMacroPremiumSync()
        UnlockServiceHeartbeatWorker.schedule(this)
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // startForeground() first even though we're about to stop: the PendingIntent behind
            // the notification's action is a startForegroundService() call, and Android requires
            // that be answered within a few seconds regardless of what we do right after — skipping
            // it risks a ForegroundServiceDidNotStartInTimeException on the rare path where this
            // fires after the process was already gone (e.g. killed and not yet restarted).
            startForeground(NOTIFICATION_ID, buildNotification())
            stopListeningByUserRequest()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_APPROVE || intent?.action == ACTION_DENY) {
            // Same defensive startForeground() as ACTION_STOP above; this is a no-op update
            // when (as expected) the service is already in the foreground.
            startForeground(NOTIFICATION_ID, buildNotification())
            intent.getStringExtra(EXTRA_REQUEST_ID)?.let { requestId ->
                val resolution = if (intent.action == ACTION_APPROVE) ChallengeResolution.Approved() else ChallengeResolution.Denied
                UnlockChallengeBridge.complete(requestId, resolution)
            }
            val challengeNotificationId = intent.getIntExtra(EXTRA_CHALLENGE_NOTIFICATION_ID, -1)
            if (challengeNotificationId != -1) {
                getSystemService(NotificationManager::class.java).cancel(challengeNotificationId)
            }
            return START_STICKY
        }

        getSystemService(NotificationManager::class.java).cancel(STOPPED_NOTIFICATION_ID)
        startForeground(NOTIFICATION_ID, buildNotification())
        acquireLocks()
        if (listenLoopJob == null) {
            // Real device (2026-08-22): this used to bind a socket right here, synchronously and
            // unguarded, whenever `listener` was momentarily null (e.g. resetListenerForNetworkChange
            // or the periodic rebind in listenLoop() null it out just before closing the old one). A
            // concurrent FCM-triggered onStartCommand() landing in that window raced the still-open
            // old socket for the same port and crashed the whole process with an uncaught
            // BindException: EADDRINUSE — silencing this phone until the app was reopened by hand.
            // listenLoop() already binds safely (createUdpListener() catches bind failures and
            // retries); just make sure it's running, and let it own every (re)bind.
            listenLoopJob = scope.launch { listenLoop() }
        }
        intent?.getStringExtra(EXTRA_REMOTE_WAKE_BROADCAST)?.let { json ->
            scope.launch { dispatchBroadcast(PcbuJson.decodeFromString(UdpBroadcast.serializer(), json)) }
        }
        return START_STICKY
    }

    /**
     * The only path that stops this service, besides the OS itself killing the
     * process (which START_STICKY then recovers from) — triggered solely by the
     * user tapping "Arrêter" on the notification or in Settings, never automatically.
     * Leaves a plain, dismissible notification behind with a "Démarrer" action so
     * listening can be resumed without opening the app.
     */
    private fun stopListeningByUserRequest() {
        // Plain thread, not [scope]: onDestroy cancels it right after. Stopping BioKey on purpose
        // shouldn't make the PCs lock as if this phone had left.
        Thread {
            runCatching {
                runBlocking {
                    sendPresenceOffToAllPairedDevices()
                    advertiseBleDisarm()
                }
            }
        }.start()
        stopForeground(STOP_FOREGROUND_REMOVE)
        showStoppedNotification()
        stopSelf()
    }

    override fun onDestroy() {
        listener?.close()
        listener = null
        connectivityManager?.unregisterNetworkCallback(wifiNetworkCallback)
        scope.cancel()
        // A user stop is still advertising its disarm beacon on its own thread; it stops it itself.
        if (!bleDisarming) bleAdvertiser.stop()
        releaseLocks()
        _isRunning.value = false
        super.onDestroy()
    }

    /** Wi-Fi transitions: rebind the UDP listener, pre-select the user on reconnect, and track
     * whether presence heartbeats (see [startPresenceLoop]) can get through. */
    private fun registerWifiCallback() {
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivityManager?.registerNetworkCallback(request, wifiNetworkCallback)
    }

    private val wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            wifiConnected = false
            if (presenceArmed) scheduleOutOfRangeNotification()
            presenceArmed = false
        }

        override fun onAvailable(network: Network) {
            wifiConnected = true
            outOfRangeJob?.cancel()
            outOfRangeJob = null
            resetListenerForNetworkChange()
            scope.launch { selectUserOnAllPairedDevices() }
        }
    }

    /**
     * Forces listenLoop() to bind a fresh socket on the next iteration. Needed because a Wi-Fi
     * network transition doesn't always invalidate the fd (that's [UnlockUdpListener.isDead]'s
     * case, surfacing as EBADF) — a real device (2026-08-22) stayed on the *same* app process
     * across a Wi-Fi toggle, socket never closed by the OS, yet silently stopped receiving
     * broadcasts on the new interface, timing out forever instead of erroring. Rebinding
     * unconditionally on every new Wi-Fi network (same trigger as [selectUserOnAllPairedDevices])
     * covers that case too, at the cost of a harmless rebind on transitions where the old socket
     * would have kept working fine.
     */
    private fun resetListenerForNetworkChange() {
        val old = listener
        listener = null
        old?.close()
    }

    /**
     * Proximity lock. The PC does the locking (pcbu-lockd, once heartbeats stop for the chosen
     * timeout): a phone-side "Wi-Fi lost" trigger can't work, since by then there's no network
     * left to send the lock on. Heartbeats every [PRESENCE_INTERVAL_MS] leave room for a couple
     * of lost UDP packets even at the shortest timeout. Turning the option off disarms the PCs
     * explicitly instead of letting them time out and lock.
     */
    private fun startPresenceLoop() = scope.launch {
        combine(preferences.lockOnWifiDisconnectEnabled, premium.isPremium, preferences.lockTimeoutSeconds) { enabled, isPremium, timeout ->
            (enabled && isPremium) to timeout
        }
            .distinctUntilChanged()
            .collectLatest { (enabled, timeoutSeconds) ->
                if (!enabled) {
                    presenceArmed = false
                    sendPresenceOffToAllPairedDevices()
                    return@collectLatest
                }
                while (isActive) {
                    if (wifiConnected) sendPresenceToAllPairedDevices(timeoutSeconds)
                    delay(PRESENCE_INTERVAL_MS)
                }
            }
    }

    private suspend fun sendPresenceToAllPairedDevices(timeoutSeconds: Int) {
        for (device in db.pairedDeviceDao().getAll()) {
            val encKey = secrets.readEncKey(device.deviceId) ?: continue
            try {
                LockBroadcastSender.sendPresence(this@UnlockForegroundService, device.deviceId, encKey, timeoutSeconds)
                presenceArmed = true
            } catch (e: Exception) {
                // Best-effort per device; one paired PC's send failing shouldn't stop the others.
            }
        }
    }

    /**
     * Bluetooth proximity lock: advertises the signed [BlePresenceBeacon] for as long as the
     * option is on. With several paired PCs (one beacon fits per advertisement) it rotates
     * through them every [BLE_ROTATE_MS], each PC seeing its own beacon well within its timeout.
     */
    private fun startBlePresenceLoop() = scope.launch {
        combine(preferences.lockOnBluetoothEnabled, premium.isPremium, preferences.lockTimeoutSeconds) { enabled, isPremium, timeout ->
            (enabled && isPremium) to timeout
        }
            .distinctUntilChanged()
            .collectLatest { (enabled, timeoutSeconds) ->
                if (!enabled) {
                    advertiseBleDisarm()
                    return@collectLatest
                }
                var index = 0
                while (isActive) {
                    val devices = pairedDevicesWithKeys()
                    if (devices.isNotEmpty() && bleAdvertiser.hasPermission()) {
                        val (device, encKey) = devices[index++ % devices.size]
                        val slot = BlePresenceBeacon.currentSlot()
                        bleAdvertiser.advertise(BlePresenceBeacon.payload(device.deviceId, encKey, slot, timeoutSeconds, disarm = false))
                        bleArmed = true
                    }
                    delay(BLE_ROTATE_MS)
                }
            }
    }

    /** Disarm beacons for a few seconds so every PC catches one, then stop advertising. */
    private suspend fun advertiseBleDisarm() {
        if (!bleArmed) return
        bleDisarming = true
        try {
            val devices = pairedDevicesWithKeys()
            val until = System.currentTimeMillis() + BLE_DISARM_DURATION_MS
            var index = 0
            while (devices.isNotEmpty() && System.currentTimeMillis() < until) {
                val (device, encKey) = devices[index++ % devices.size]
                bleAdvertiser.advertise(BlePresenceBeacon.payload(device.deviceId, encKey, BlePresenceBeacon.currentSlot(), 60, disarm = true))
                delay(BLE_ROTATE_MS)
            }
        } finally {
            bleAdvertiser.stop()
            bleArmed = false
            bleDisarming = false
        }
    }

    /**
     * Macros are Premium, but pcbu-lockd stores and replays them on its own, so hiding the
     * editor isn't enough: without Premium every PC gets an empty macro, and the ones saved on
     * this phone are pushed back as soon as Premium is (re)acquired.
     */
    private fun startMacroPremiumSync() = scope.launch {
        var previous: Boolean? = null
        premium.isPremium.distinctUntilChanged().collect { isPremium ->
            val firstValue = previous == null
            previous = isPremium
            // Already Premium at start: the PCs hold the right macros, nothing to resend.
            if (firstValue && isPremium) return@collect
            val emptyMacro = PcbuJson.encodeToString(MacroDto.serializer(), MacroDto())
            for ((device, encKey) in pairedDevicesWithKeys()) {
                val stored = device.macroStepsJson ?: continue
                runCatching {
                    LockBroadcastSender.sendMacro(this@UnlockForegroundService, device.deviceId, encKey, if (isPremium) stored else emptyMacro)
                }
            }
        }
    }

    private suspend fun pairedDevicesWithKeys(): List<Pair<PairedDeviceEntity, String>> =
        db.pairedDeviceDao().getAll().mapNotNull { device -> secrets.readEncKey(device.deviceId)?.let { device to it } }

    private suspend fun sendPresenceOffToAllPairedDevices() {
        for (device in db.pairedDeviceDao().getAll()) {
            val encKey = secrets.readEncKey(device.deviceId) ?: continue
            runCatching { LockBroadcastSender.sendPresenceOff(this@UnlockForegroundService, device.deviceId, encKey) }
        }
    }

    /** Mirrors pcbu-lockd's own timeout: if Wi-Fi isn't back by then, the PCs have locked
     * (or had nothing unlocked to lock). Cancelled by a reconnect in [wifiNetworkCallback]. */
    private fun scheduleOutOfRangeNotification() {
        outOfRangeJob?.cancel()
        outOfRangeJob = scope.launch {
            if (!preferences.lockOnWifiDisconnectEnabled.first() || !premium.isPremium.first()) return@launch
            delay(preferences.lockTimeoutSeconds.first() * 1_000L)
            if (wifiConnected) return@launch
            val names = db.pairedDeviceDao().getAll().joinToString(", ") { it.displayName }
            if (names.isEmpty()) return@launch
            val notification = Notification.Builder(this@UnlockForegroundService, CHALLENGE_CHANNEL_ID)
                .setContentTitle("BioKey")
                .setContentText("Hors de portée — $names verrouillé")
                .setSmallIcon(R.drawable.ic_notification_fingerprint)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(notificationIdCounter.incrementAndGet(), notification)
        }
    }

    /** Reverse of the proximity lock: fires every time Wi-Fi (re)connects, not just
     * after a disconnect, since the greeter may already be sitting on the wrong tile
     * (e.g. after a reboot) rather than only right after this phone left range. The only
     * selectUser trigger: a wake broadcast means pcbu_auth's PAM conversation is already
     * running, which pcbu-lockd always ignores (IsAuthInProgress). pcbu-lockd also checks
     * the PC is actually on its greeter or lock screen before pressing any key. */
    private suspend fun selectUserOnAllPairedDevices() {
        for (device in db.pairedDeviceDao().getAll()) sendSelectUser(device)
    }

    /** Deduped per device (see [tryAcquireSelectUserLock]) against repeated Wi-Fi callbacks. */
    private suspend fun sendSelectUser(device: PairedDeviceEntity) {
        if (!preferences.selectUserOnWifiConnectEnabled.first()) return
        if (!tryAcquireSelectUserLock(device.deviceId)) return
        try {
            val encKey = secrets.readEncKey(device.deviceId) ?: return
            LockBroadcastSender.sendSelectUser(this@UnlockForegroundService, device.deviceId, encKey)
        } catch (e: Exception) {
            // Best-effort; the wake broadcast's own challenge still proceeds independently.
        } finally {
            releaseSelectUserLock(device.deviceId)
        }
    }

    /** Claims the lock for [deviceId] if it's free (never locked, or its cooldown from a
     * previous send already elapsed); returns false without side effects if another call
     * (in flight or still cooling down) already holds it. */
    private fun tryAcquireSelectUserLock(deviceId: String): Boolean {
        val now = System.currentTimeMillis()
        var acquired = false
        recentSelectUserDeviceIds.compute(deviceId) { _, lockedUntil ->
            if (lockedUntil == null || lockedUntil <= now) {
                acquired = true
                Long.MAX_VALUE // in flight: locked unconditionally until releaseSelectUserLock runs
            } else {
                lockedUntil // still locked; leave as-is
            }
        }
        return acquired
    }

    private fun releaseSelectUserLock(deviceId: String) {
        recentSelectUserDeviceIds[deviceId] = System.currentTimeMillis() + SELECT_USER_COOLDOWN_MS
    }

    /**
     * Without these, a real device (confirmed on a Samsung A33) simply never
     * receives the PC's UDP wake broadcasts once the screen has been off for
     * a while: being Doze/App-Standby-whitelisted doesn't stop the Wi-Fi
     * radio itself from cutting its receive duty cycle to save power, so
     * incoming broadcast packets are silently missed at the radio level —
     * invisible to deviceidle/appops, and nothing a full-screen-intent
     * notification can fix, since it can only fire for a broadcast we
     * actually received. WIFI_MODE_FULL_HIGH_PERF keeps the radio at full
     * receive power; the partial wake lock keeps the CPU able to act on
     * what it receives. Both are held for the service's whole lifetime,
     * same as any app with a persistent background socket (VoIP, chat).
     */
    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pcbu:unlock_listener").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION") // WIFI_MODE_FULL_HIGH_PERF: still the correct mode for "never drop RX duty cycle".
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pcbu:unlock_listener").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun listenLoop() {
        var listenerBoundAt = 0L
        while (scope.isActive) {
            val currentListener = listener
            if (currentListener == null) {
                listener = createUdpListener()
                listenerBoundAt = System.currentTimeMillis()
                continue
            }
            // Periodic self-heal, independent of any external trigger (Wi-Fi callback, FCM
            // wake): real devices (2026-08-22) went silently deaf on this socket with no Wi-Fi
            // transition at all — just the PC rebooting while the phone's network stayed the
            // same — and stayed deaf until the service was manually stopped/restarted. isDead
            // only catches the OS invalidating the fd (EBADF); this catches the socket staying
            // "open" but no longer actually receiving, by rebinding on a timer regardless of
            // whether anything told us to. Harmless on a socket that was working fine.
            if (System.currentTimeMillis() - listenerBoundAt > PERIODIC_REBIND_INTERVAL_MS) {
                listener = null
                currentListener.close()
                continue
            }
            val broadcast = withContext(Dispatchers.IO) { currentListener.receiveOnce(timeoutMs = 2_000) }
            if (broadcast != null) {
                dispatchBroadcast(broadcast)
            } else if (currentListener.isDead) {
                // Socket died out from under us (e.g. a Wi-Fi network transition, see
                // [UnlockUdpListener.isDead]) rather than us closing it on purpose — replace it
                // instead of spinning on a socket that will only ever fail again.
                listener = null
            }
        }
    }

    private suspend fun createUdpListener(): UnlockUdpListener? = try {
        withContext(Dispatchers.IO) { UnlockUdpListener(identity.udpListenPort) }
    } catch (e: Exception) {
        delay(1_000) // port likely still settling from the previous socket; avoid a tight retry loop
        null
    }

    /** Common entry point for a wake trigger, whether it arrived over the local UDP
     * socket or as an FCM push (see [PcbuFirebaseMessagingService]) — both just carry
     * "someone paired with deviceId X wants to talk, at this address". */
    private suspend fun dispatchBroadcast(broadcast: UdpBroadcast) {
        val device = db.pairedDeviceDao().findById(broadcast.deviceId) ?: return // not one of ours: ignore silently
        if (isDuplicateBroadcast(broadcast)) return
        scope.launch { handleChallenge(device, broadcast) }
    }

    private fun isDuplicateBroadcast(broadcast: UdpBroadcast): Boolean {
        val now = System.currentTimeMillis()
        recentWakeIds.entries.removeAll { now - it.value > WAKE_ID_RETENTION_MS }
        return recentWakeIds.putIfAbsent(broadcast.wakeId, now) != null
    }

    private suspend fun handleChallenge(device: PairedDeviceEntity, broadcast: UdpBroadcast) {
        Log.d(TAG, "handleChallenge: connecting to ${broadcast.pcbuIP}:${broadcast.pcbuPort} for device ${device.deviceId}") // TEMP diagnostic (2026-08-22)
        val encKey = secrets.readEncKey(device.deviceId) ?: return

        val session = try {
            withContext(Dispatchers.IO) {
                UnlockTcpSession.connect(broadcast.pcbuIP, broadcast.pcbuPort, device.deviceId, encKey)
            }
        } catch (e: UnlockSessionError.Network) {
            // TEMP diagnostic (2026-08-22): desktop's module.log shows every recent attempt
            // dying at "Reading packet header failed" (read() == 0, i.e. this phone closed the
            // socket before ever writing DEVICE_ID) — this is the only place that can show why.
            Log.w(TAG, "TCP connect to ${broadcast.pcbuIP}:${broadcast.pcbuPort} failed", e)
            return // PC connection dropped/unreachable; nothing to respond to
        }

        session.use {
            val challenge = try {
                withContext(Dispatchers.IO) { it.readChallenge() }
            } catch (e: UnlockSessionError) {
                Log.w(TAG, "readChallenge() failed", e) // TEMP diagnostic (2026-08-22)
                return
            }
            Log.d(TAG, "readChallenge() succeeded for user ${challenge.user}") // TEMP diagnostic (2026-08-22)

            var passwordKeyOverride: String? = null
            var keyUnavailable = false
            // A biometric-gated passwordKey only comes out of the fingerprint prompt, so auto
            // unlock and the watch notification's Approve button would just send APP_ERROR to the PC.
            val gated = secrets.isPasswordKeyBiometricGated(device.deviceId)
            val approved = if (!gated && shouldAutoUnlock()) {
                // Auto unlock skips the biometric prompt entirely (see shouldAutoUnlock below),
                // so this is the only signal the user gets that their PC was just unlocked
                // without their direct confirmation.
                showAutoUnlockNotification(device.displayName, AuthProgram.parse(challenge.program))
                true
            } else {
                val requestId = UUID.randomUUID().toString()
                val deferred = UnlockChallengeBridge.register(requestId)
                val challengeIntent = UnlockChallengeActivity.intentFor(
                    this@UnlockForegroundService, requestId, device.deviceId, device.displayName, challenge.user, challenge.program,
                )
                val notificationId = notificationIdCounter.incrementAndGet()
                // Always route through the notification, never startActivity() directly: this
                // call runs from a background service with no visible window, and Android's
                // background-activity-launch restrictions silently block a direct startActivity()
                // in that state (confirmed via ActivityTaskManager's "Background activity launch
                // blocked" log) whenever KeyguardManager.isKeyguardLocked() reports false despite
                // the screen being off (e.g. a trust agent keeping the device "unlocked"). The
                // notification path is the only one Android guarantees works regardless of
                // keyguard state (same mechanism incoming-call UIs use); when the screen is
                // already on it still shows immediately as a heads-up the user can tap.
                showChallengeNotification(
                    notificationId, challengeIntent, requestId, device.displayName, AuthProgram.parse(challenge.program),
                    directUnlock = preferences.directUnlockEnabled.first(),
                    wearableNotification = !gated && preferences.wearableNotificationEnabled.first(),
                )
                // The full-screen intent only takes over a locked or dark screen; on a phone in
                // use it stays a heads-up to tap. With "draw over other apps" granted, Android
                // lets a background service start the prompt itself (the notification stays as
                // a fallback, and tapping it just reaches the same singleTop activity).
                if (preferences.directUnlockEnabled.first() && Settings.canDrawOverlays(this@UnlockForegroundService)) {
                    runCatching { startActivity(challengeIntent) }.onFailure { Log.w(TAG, "direct prompt launch failed", it) }
                }
                val resolution = withTimeoutOrNull(BIOMETRIC_PROMPT_TIMEOUT_MS) { deferred.await() } ?: ChallengeResolution.Denied
                getSystemService(NotificationManager::class.java).cancel(notificationId)
                if (resolution is ChallengeResolution.KeyUnavailable) keyUnavailable = true
                if (resolution is ChallengeResolution.Approved) {
                    passwordKeyOverride = resolution.passwordKeyOverride
                    // "Chiffrement biométrique" turned off since pairing: the fingerprint just
                    // decrypted the key, the only moment it can be stored plain again.
                    val plainKey = resolution.passwordKeyOverride
                    if (gated && plainKey != null && !preferences.biometricEncryptionEnabled.first()) {
                        secrets.storePasswordKey(device.deviceId, plainKey, useBiometricGate = false)
                    }
                    showUnlockSuccessNotification(device.displayName, AuthProgram.parse(challenge.program))
                }
                resolution is ChallengeResolution.Approved
            }
            try {
                if (approved) {
                    // Null for a biometric-gated device approved without the prompt (auto unlock,
                    // notification button): the PC then gets APP_ERROR, which is the point of the gate.
                    val passwordKey = passwordKeyOverride ?: secrets.readPasswordKey(device.deviceId)
                    if (passwordKey != null) {
                        Log.d(TAG, "calling respondSuccess()") // TEMP diagnostic (2026-08-22)
                        it.respondSuccess(challenge.unlockToken, passwordKey)
                        Log.d(TAG, "respondSuccess() returned normally; pendingBytesAvailable=${it.pendingBytesAvailable()}") // TEMP diagnostic (2026-08-22)
                        preferences.incrementUnlockSuccess()
                    } else {
                        Log.w(TAG, "no passwordKey stored for device ${device.deviceId}; responding APP_ERROR") // TEMP diagnostic (2026-08-22)
                        showKeyUnavailableNotification(device.displayName)
                        it.respondError(UnlockErrors.APP_ERROR)
                    }
                } else if (keyUnavailable) {
                    showKeyUnavailableNotification(device.displayName)
                    it.respondError(UnlockErrors.APP_ERROR)
                } else {
                    preferences.incrementUnlockDenied()
                    it.respondError(UnlockErrors.CANCEL)
                }
            } catch (e: UnlockSessionError) {
                // PC already gave up waiting; nothing more to do.
                Log.w(TAG, "respond() failed", e) // TEMP diagnostic (2026-08-22)
            }
        }
    }

    /**
     * Auto unlock skips the biometric prompt entirely — an explicit, opt-in
     * security tradeoff (Settings > Unlocking), off by default like
     * pcbu-desktop's own equivalent. "Only if phone is unlocked" narrows it
     * further: a stolen-but-locked phone still can't silently unlock the PC.
     */
    private suspend fun shouldAutoUnlock(): Boolean {
        if (!preferences.autoUnlockEnabled.first()) return false
        if (!preferences.autoUnlockOnlyIfPhoneUnlocked.first()) return true
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        return keyguardManager?.isDeviceLocked != true
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Écoute de déverrouillage PC", NotificationManager.IMPORTANCE_LOW),
        )
        // IMPORTANCE_HIGH is required for setFullScreenIntent() to actually launch over a
        // locked keyguard rather than sitting as a silent, un-promoted notification.
        // A distinct channel ID (not CHALLENGE_CHANNEL_ID's old name) because a channel's
        // sound/vibration can only be set at creation — reusing the old ID on an existing
        // install would silently keep whatever (no) sound it already had.
        val challengeChannel = NotificationChannel(CHALLENGE_CHANNEL_ID, "Demande de déverrouillage PC", NotificationManager.IMPORTANCE_HIGH).apply {
            val soundUri = Uri.parse("android.resource://$packageName/${R.raw.unlock_beep}")
            setSound(soundUri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            vibrationPattern = SHORT_VIBRATION_PATTERN
            enableVibration(true)
        }
        manager.createNotificationChannel(challengeChannel)
    }

    private fun showAutoUnlockNotification(deviceName: String, program: AuthProgram) {
        // The user only cares whether the PC was opened or an action was allowed, not which
        // elevation program (sudo, polkit…) asked, so it is shown as the password it replaces.
        val title = if (program.isElevation) "Mot de passe envoyé automatiquement à $deviceName"
            else "$deviceName déverrouillé automatiquement"
        val notification = Notification.Builder(this, CHALLENGE_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification_fingerprint)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationIdCounter.incrementAndGet(), notification)
    }

    /** The PC only shows a generic APP_ERROR, so the actual reason and fix are spelled out here. */
    private fun showKeyUnavailableNotification(deviceName: String) {
        val text = "Empreinte ajoutée ou modifiée sur ce téléphone : supprimez puis réappairez ce PC."
        val notification = Notification.Builder(this, CHALLENGE_CHANNEL_ID)
            .setContentTitle("Déverrouillage impossible sur $deviceName")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification_fingerprint)
            .setCategory(Notification.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationIdCounter.incrementAndGet(), notification)
    }

    private fun showUnlockSuccessNotification(deviceName: String, program: AuthProgram) {
        val title = if (program.isElevation) "Mot de passe envoyé à $deviceName" else "$deviceName déverrouillé"
        val notification = Notification.Builder(this, CHALLENGE_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification_fingerprint)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notificationIdCounter.incrementAndGet(), notification)
    }

    private fun showChallengeNotification(
        notificationId: Int,
        challengeIntent: Intent,
        requestId: String,
        deviceName: String,
        program: AuthProgram,
        directUnlock: Boolean,
        wearableNotification: Boolean,
    ) {
        val contentIntent = PendingIntent.getActivity(
            this, notificationId, challengeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (program.isElevation) "$deviceName demande votre mot de passe" else "Déverrouiller $deviceName ?"
        val text = if (program.isElevation) "Touchez pour utiliser votre empreinte."
            else "Touchez pour confirmer avec votre empreinte."
        val builder = Notification.Builder(this, CHALLENGE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_fingerprint)
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        // Settings > Unlocking > Déverrouillage direct: on (default), the full-screen intent
        // auto-launches the biometric prompt immediately, matching pcbu-desktop's own "Direct
        // unlocking" default. Off: a plain heads-up notification the user must tap first.
        if (directUnlock) {
            builder.setFullScreenIntent(contentIntent, true)
        }

        // Settings > Unlocking > Notification montre connectée: Approve/Deny actions that resolve
        // the challenge directly, without a biometric prompt. Needed because a bridged Wear OS
        // notification can show actions but can't open a phone Activity or run BiometricPrompt —
        // approving this way trades the biometric factor for "paired watch on wrist", the same
        // tradeoff pcbu-desktop's own toggle makes.
        if (wearableNotification) {
            val approveIntent = PendingIntent.getForegroundService(
                this, notificationId * 2, challengeActionIntent(ACTION_APPROVE, requestId, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val denyIntent = PendingIntent.getForegroundService(
                this, notificationId * 2 + 1, challengeActionIntent(ACTION_DENY, requestId, notificationId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val actionIcon = Icon.createWithResource(this, R.drawable.ic_notification_fingerprint)
            builder.addAction(Notification.Action.Builder(actionIcon, "Approuver", approveIntent).build())
            builder.addAction(Notification.Action.Builder(actionIcon, "Refuser", denyIntent).build())
        }

        getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
    }

    private fun challengeActionIntent(action: String, requestId: String, notificationId: Int): Intent =
        Intent(this, UnlockForegroundService::class.java).apply {
            this.action = action
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_CHALLENGE_NOTIFICATION_ID, notificationId)
        }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getForegroundService(
            this, 0, Intent(this, UnlockForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BioKey")
            .setContentText("En écoute pour déverrouiller vos PC appairés")
            .setSmallIcon(R.drawable.ic_notification_fingerprint)
            .setContentIntent(openAppIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification_fingerprint), "Arrêter", stopIntent,
                ).build(),
            )
            .setOngoing(true)
            .build()
    }

    /**
     * Left behind after an explicit user stop (see [stopListeningByUserRequest]):
     * plain and dismissible, unlike the listening notification, since nothing is
     * running anymore — but keeps a one-tap way back in without opening the app.
     */
    private fun showStoppedNotification() {
        val startIntent = PendingIntent.getForegroundService(
            this, 0, Intent(this, UnlockForegroundService::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BioKey")
            .setContentText("Écoute arrêtée. Touchez pour redémarrer.")
            .setSmallIcon(R.drawable.ic_notification_fingerprint)
            .setContentIntent(startIntent)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification_fingerprint), "Démarrer", startIntent,
                ).build(),
            )
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(STOPPED_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "UnlockForegroundService"
        private const val NOTIFICATION_ID = 1
        // Negative and distinct from notificationIdCounter's 2, 3, 4... range (challenge
        // notifications), so stopping the service can never collide with a pending challenge.
        private const val STOPPED_NOTIFICATION_ID = -1
        const val CHANNEL_ID = "pcbu_unlock_service"
        private const val CHALLENGE_CHANNEL_ID = "pcbu_unlock_challenge_v2"
        private val SHORT_VIBRATION_PATTERN = longArrayOf(0, 80)
        private const val BIOMETRIC_PROMPT_TIMEOUT_MS = 60_000L
        private const val WAKE_ID_RETENTION_MS = 60_000L
        private const val SELECT_USER_COOLDOWN_MS = 10_000L
        private const val PRESENCE_INTERVAL_MS = 10_000L
        private const val BLE_ROTATE_MS = 2_000L
        private const val BLE_DISARM_DURATION_MS = 8_000L
        private const val PERIODIC_REBIND_INTERVAL_MS = 3 * 60_000L
        private const val EXTRA_REMOTE_WAKE_BROADCAST = "remoteWakeBroadcast"
        private const val EXTRA_REQUEST_ID = "requestId"
        private const val EXTRA_CHALLENGE_NOTIFICATION_ID = "challengeNotificationId"
        private const val ACTION_STOP = "org.pcbu.libreclient.action.STOP_LISTENING"
        private const val ACTION_APPROVE = "org.pcbu.libreclient.action.APPROVE_CHALLENGE"
        private const val ACTION_DENY = "org.pcbu.libreclient.action.DENY_CHALLENGE"

        private val _isRunning = MutableStateFlow(false)

        /** Lets Settings show a single start/stop button reflecting the service's real state,
         * instead of two static buttons that don't know whether it's already running. */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UnlockForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UnlockForegroundService::class.java))
        }

        /**
         * Entry point for [PcbuFirebaseMessagingService]: an FCM push carries the exact
         * same payload as a UDP wake broadcast (deviceId/pcbuIP/pcbuPort/isManual) — FCM
         * is only a more reliable way to deliver that "someone wants to talk" nudge past
         * the phone's radio power-saving, the actual challenge/response still happens
         * over a direct TCP connection to the PC.
         */
        fun wakeFromRemote(context: Context, broadcast: UdpBroadcast) {
            val intent = Intent(context, UnlockForegroundService::class.java).apply {
                putExtra(EXTRA_REMOTE_WAKE_BROADCAST, PcbuJson.encodeToString(UdpBroadcast.serializer(), broadcast))
            }
            context.startForegroundService(intent)
        }
    }
}
