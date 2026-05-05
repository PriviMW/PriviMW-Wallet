package com.privimemobile.protocol

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Applies a user-selected locale override, persisting the choice in SecureStorage.
 *
 * Call [applyLocale] in Activity.attachBaseContext() to set the locale before
 * Compose/views are inflated. Call [setLanguage] to change at runtime (triggers
 * activity recreation).
 */
object LocaleHelper {

    /** Language codes we support (matches values-XX resource directories). */
    val supportedLanguages = listOf(
        "" to "System Default",   // empty = follow device
        "en" to "English",
        "zh" to "简体中文",
        "es" to "Español",
        "ru" to "Русский",
        "be" to "Беларуская",
        "cs" to "Čeština",
        "de" to "Deutsch",
        "nl" to "Nederlands",
        "fr" to "Français",
        "id" to "Bahasa Indonesia",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
        "pt" to "Português",
        "sr" to "Српски",
        "fi" to "Suomi",
        "sv" to "Svenska",
        "th" to "ไทย",
        "tr" to "Türkçe",
        "vi" to "Tiếng Việt",
        "uk" to "Українська",
    )

    /** Map of lang code to native display name, for the settings picker. */
    private val nativeNames = supportedLanguages.toMap()

    fun getSelectedLanguage(): String {
        return SecureStorage.getString(SecureStorage.KEY_APP_LANGUAGE) ?: ""
    }

    fun setLanguage(languageCode: String) {
        SecureStorage.putString(SecureStorage.KEY_APP_LANGUAGE, languageCode)
    }

    /** Get the display name for the currently selected language. */
    fun getSelectedLanguageDisplay(): String {
        val code = getSelectedLanguage()
        return nativeNames[code] ?: "System Default"
    }

    /**
     * Wrap a context to use the user-selected locale. Call this in
     * Activity.attachBaseContext() BEFORE super.attachBaseContext().
     */
    fun applyLocale(context: Context): Context {
        val langCode = getSelectedLanguage()
        if (langCode.isEmpty()) return context // follow system

        val locale = Locale.forLanguageTag(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
