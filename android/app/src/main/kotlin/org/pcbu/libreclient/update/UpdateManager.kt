package org.pcbu.libreclient.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// android-foundation auto-update (source: Donova's UpdateManager). Polls the latest GitHub Release
// of this app's public APK repo; the release is produced by publish.gradle on every assembleRelease.
private const val GITHUB_REPO = "L-architec-T/biokey"
private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

// Dedicated subclass: a manifest can't declare androidx FileProvider twice (apps may already have
// their own), so the update provider gets its own component name.
class UpdateFileProvider : FileProvider()

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
)

object UpdateManager {

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()

            val tagName = json.optString("tag_name", "")
            // "-" is the placeholder written to release_notes.txt when no real notes were drafted.
            val notes   = json.optString("body", "").trim().let { if (it == "-") "" else it }
            val assets  = json.optJSONArray("assets") ?: return@withContext null

            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name", "").endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
            if (apkUrl.isBlank() || tagName.isBlank()) return@withContext null

            val remoteVersion  = tagName.trimStart('v')
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: ""

            // Only offer strictly newer versions (Donova used !=, which also offered downgrades).
            if (!isNewer(remoteVersion, currentVersion)) return@withContext null

            UpdateInfo(tagName, remoteVersion, apkUrl, notes)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadApk(context: Context, info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(context.applicationContext.cacheDir, "update.apk")
            apkFile.delete()
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout    = 120_000
            if (conn.responseCode != 200) return@withContext null
            conn.inputStream.use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output, 8192) }
            }
            conn.disconnect()
            apkFile
        } catch (e: Exception) {
            null
        }
    }

    fun launchInstaller(context: Context, apk: File) {
        if (!apk.exists() || apk.length() == 0L) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update.provider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // Numeric comparison of dotted versions ("0.0.10" > "0.0.9"); non-numeric parts count as 0.
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val d = r.getOrElse(i) { 0 } - c.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
