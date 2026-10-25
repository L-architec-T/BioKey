package org.pcbu.libreclient.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * DashBoard analytics SDK — one file, no dependency. Copy it into any app (keep or change the
 * package), add the INTERNET permission, then in Application.onCreate():
 *
 *     DashboardAnalytics.init(this, "pk_…")
 *
 * Tracked automatically: install (first launch of a fresh install), update (version change),
 * sessions (new one after 30 min in background), app version, Android version, device model.
 * Optional: DashboardAnalytics.screen("Accueil"), DashboardAnalytics.event("achat", mapOf("plan" to "pro")).
 *
 * Privacy: the only identifier is a random install UUID generated on first launch (never the
 * Android ID / advertising ID); the server stores only an HMAC of it. No IP is stored.
 * Events are queued on disk and sent in batches over HTTPS, so offline use is not lost.
 */
object DashboardAnalytics {
    private const val DEFAULT_ENDPOINT = "https://analytics.larchitect.cloud"
    private const val PREFS = "dashboard_analytics"
    private const val SESSION_TIMEOUT_MS = 30 * 60_000L
    private const val MAX_QUEUE = 500
    private const val BATCH = 50

    private lateinit var prefs: SharedPreferences
    private lateinit var executor: ScheduledExecutorService
    private var key = ""
    private var endpoint = DEFAULT_ENDPOINT
    private var installId = ""
    private var appVersion = ""
    private var sessionId = ""
    private var lastBackground = 0L
    private var foregroundSince = 0L
    private var heartbeat: java.util.concurrent.ScheduledFuture<*>? = null
    private var started = 0
    private val queue = JSONArray()
    private val random = SecureRandom()

    @Volatile
    var enabled = true
        private set

    @JvmStatic
    @JvmOverloads
    fun init(app: Application, writeKey: String, endpoint: String = DEFAULT_ENDPOINT) {
        if (::executor.isInitialized) return
        key = writeKey
        this.endpoint = endpoint.trimEnd('/')
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        executor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "dashboard-analytics").apply { isDaemon = true } }
        enabled = prefs.getBoolean("enabled", true)
        appVersion = runCatching {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0).versionName
        }.getOrNull().orEmpty()

        executor.execute {
            restoreQueue()
            val existing = prefs.getString("install_id", null)
            installId = existing ?: UUID.randomUUID().toString().also { prefs.edit().putString("install_id", it).apply() }
            if (existing == null) {
                // A fresh install (not just the first launch after adding the SDK to an old install).
                val firstInstall = runCatching {
                    @Suppress("DEPRECATION")
                    app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
                }.getOrDefault(0L)
                if (System.currentTimeMillis() - firstInstall < 24 * 3600_000L) enqueue("install", null, null)
            } else {
                val previous = prefs.getString("version", null)
                if (previous != null && previous != appVersion) enqueue("update", null, mapOf("from" to previous, "to" to appVersion))
            }
            prefs.edit().putString("version", appVersion).apply()
        }
        executor.scheduleWithFixedDelay({ flush() }, 60, 60, TimeUnit.SECONDS)

        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                executor.execute {
                    if (started++ == 0) {
                        val now = System.currentTimeMillis()
                        if (sessionId.isEmpty() || now - lastBackground > SESSION_TIMEOUT_MS) {
                            sessionId = newSessionId()
                            enqueue("session_start", null, null)
                        }
                        enqueue("app_open", null, null)
                        foregroundSince = System.currentTimeMillis()
                        // Presence for the live "en ligne" counter; sent directly, never queued.
                        heartbeat = executor.scheduleWithFixedDelay({ ping() }, 30, 30, TimeUnit.SECONDS)
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                executor.execute {
                    started = (started - 1).coerceAtLeast(0)
                    if (started == 0) {
                        lastBackground = System.currentTimeMillis()
                        heartbeat?.cancel(false)
                        // Foreground time of this visit: the dashboard's "durée moyenne".
                        val fg = (lastBackground - foregroundSince).coerceAtLeast(0)
                        enqueue("app_close", null, mapOf("d" to fg))
                        flush()
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Screen view (shown under "Pages & écrans"). */
    @JvmStatic
    fun screen(name: String) = post { enqueue("screen_view", name, null) }

    /** Custom event: lowercase name `[a-z0-9_:.-]`, up to 10 string/number/boolean properties. */
    @JvmStatic
    @JvmOverloads
    fun event(name: String, props: Map<String, Any?>? = null) = post { enqueue(name.lowercase(), null, props) }

    /** Lets the user opt out; clears the pending queue. */
    @JvmStatic
    fun setEnabled(value: Boolean) = post {
        enabled = value
        prefs.edit().putBoolean("enabled", value).apply()
        if (!value) {
            while (queue.length() > 0) queue.remove(0)
            persistQueue()
        }
    }

    private fun post(block: () -> Unit) {
        if (::executor.isInitialized) executor.execute(block)
    }

    private fun newSessionId(): String {
        val bytes = ByteArray(12).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun enqueue(name: String, path: String?, props: Map<String, Any?>?) {
        if (!enabled) return
        val ev = JSONObject().put("n", name).put("t", System.currentTimeMillis())
        if (sessionId.isNotEmpty()) ev.put("s", sessionId)
        if (path != null) ev.put("u", path.take(120))
        if (!props.isNullOrEmpty()) {
            val p = JSONObject()
            props.entries.take(10).forEach { (k, v) -> if (v is String || v is Number || v is Boolean) p.put(k, v) }
            ev.put("p", p)
        }
        queue.put(ev)
        while (queue.length() > MAX_QUEUE) queue.remove(0)
        persistQueue()
        if (queue.length() >= 20) flush()
    }

    private fun persistQueue() = prefs.edit().putString("queue", queue.toString()).apply()

    private fun restoreQueue() {
        val saved = prefs.getString("queue", null) ?: return
        runCatching {
            val a = JSONArray(saved)
            for (i in 0 until a.length()) queue.put(a.getJSONObject(i))
        }
    }

    private fun ping() {
        if (!enabled || key.isEmpty() || installId.isEmpty()) return
        val ev = JSONObject().put("n", "ping").put("s", sessionId)
        runCatching { send(baseBody().put("e", JSONArray().put(ev)).toString().toByteArray()) }
    }

    private fun baseBody(): JSONObject = JSONObject()
        .put("k", key)
        .put("i", installId)
        .put("v", appVersion)
        .put("os", "Android " + Build.VERSION.RELEASE)
        .put("dev", (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).take(60))

    private fun flush() {
        if (!enabled || key.isEmpty() || installId.isEmpty()) return
        while (queue.length() > 0) {
            val n = minOf(BATCH, queue.length())
            val batch = JSONArray()
            for (i in 0 until n) batch.put(queue.getJSONObject(i))
            val body = baseBody().put("e", batch)
            val status = runCatching { send(body.toString().toByteArray()) }.getOrDefault(-1)
            // Keep the batch on network errors, 429 and 5xx; drop it on other 4xx (invalid data
            // or revoked key would otherwise block the queue forever).
            if (status == -1 || status == 429 || status >= 500) return
            for (i in 0 until n) queue.remove(0)
            persistQueue()
        }
    }

    private fun send(raw: ByteArray): Int {
        val conn = URL("$endpoint/v1/collect").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(raw.size)
            conn.outputStream.use { it.write(raw) }
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
