package org.pcbu.libreclient.ui.language

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language, without AppCompat: Android 13+ hands it to the system ([LocaleManager],
 * which also shows it in the system's app language settings and re-applies it on every
 * launch); older versions keep it in SharedPreferences and apply it in [wrap] from each
 * activity's attachBaseContext. Only languages with a translation are offered.
 */
object AppLanguage {
    enum class Choice(val tag: String, val label: String) {
        SYSTEM("", "Langue du système / System language"),
        FRENCH("fr", "Français"),
        ENGLISH("en", "English"),
    }

    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    fun current(context: Context): Choice {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.language.orEmpty()
        } else {
            prefs(context).getString(KEY_TAG, "").orEmpty()
        }
        return Choice.entries.firstOrNull { it.tag == tag } ?: Choice.SYSTEM
    }

    fun set(activity: Activity, choice: Choice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system recreates the activities itself.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(choice.tag)
        } else {
            prefs(activity).edit().putString(KEY_TAG, choice.tag).apply()
            activity.recreate()
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAG, "").orEmpty()
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
