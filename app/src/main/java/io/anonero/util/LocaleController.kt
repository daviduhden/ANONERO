package io.anonero.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.anonero.AnonConfig
import io.anonero.R
import java.util.Locale

/**
 * Per-application language selection.
 *
 * API 33+ uses the platform `LocaleManager`, which persists the choice and lets Android
 * recreate the app configuration. API 29–32 use a configuration-context wrapper plus an
 * in-process resource update, because the per-app locale API does not exist there.
 *
 * `SYSTEM` is the default and falls back to the device locale. A language change only
 * recreates the activity; it must not close the wallet, stop Tor or kill the process.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    SPANISH("es-ES", R.string.language_spanish),
    RUSSIAN("ru-RU", R.string.language_russian),
    CHINESE_SIMPLIFIED("zh-Hans", R.string.language_chinese),
}

object LocaleController {
    const val PREFS_LANGUAGE = "appLanguage"

    fun load(prefs: SharedPreferences): AppLanguage {
        val tag = prefs.getString(PREFS_LANGUAGE, "") ?: ""
        return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SYSTEM
    }

    /**
     * Applies the saved language during Application.onCreate. On API 33+ this also
     * reconciles a preference migrated from an older build with LocaleManager.
     */
    fun applySaved(context: Context) {
        val prefs = context.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)
        apply(context, load(prefs))
    }

    fun setLanguage(activity: Activity, prefs: SharedPreferences, language: AppLanguage) {
        prefs.edit(commit = true) { putString(PREFS_LANGUAGE, language.tag) }
        apply(activity, language)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            activity.recreate()
        }
    }

    private fun apply(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val wanted = if (language.tag.isEmpty()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(language.tag)
            }
            if (manager.applicationLocales != wanted) {
                manager.applicationLocales = wanted
            }
            return
        }

        val locale = localeFor(context, language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        // The foreground service resolves notification strings from the application
        // context, which may not share the activity's Resources instance on API 29–32.
        val app = context.applicationContext
        if (app != null && app !== context) {
            val appConfiguration = Configuration(app.resources.configuration)
            appConfiguration.setLocale(locale)
            appConfiguration.setLocales(LocaleList(locale))
            @Suppress("DEPRECATION")
            app.resources.updateConfiguration(
                appConfiguration,
                app.resources.displayMetrics
            )
        }
    }

    /**
     * Wraps an Activity base context on API 29–32. On newer releases the platform has
     * already applied the per-app locale before this runs.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val prefs = base.getSharedPreferences(AnonConfig.PREFS, Context.MODE_PRIVATE)
        val language = load(prefs)
        if (language == AppLanguage.SYSTEM) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(localeFor(base, language))
        configuration.setLocales(LocaleList(localeFor(base, language)))
        return base.createConfigurationContext(configuration)
    }

    private fun localeFor(context: Context, language: AppLanguage): Locale =
        if (language == AppLanguage.SYSTEM) {
            // Resources.getSystem() is not affected by our in-process override.
            Resources.getSystem().configuration.locales.get(0)
        } else {
            Locale.forLanguageTag(language.tag)
        }
}
