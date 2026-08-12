package com.pau.busapp

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS = "settings"
    private const val KEY   = "locale"

    data class Language(val code: String, val flag: String, val label: String)

    val languages = listOf(
        Language("fr",    "🇫🇷", "Français"),
        Language("en",    "🇬🇧", "English"),
        Language("es",    "🇪🇸", "Español"),
        Language("it",    "🇮🇹", "Italiano"),
        Language("de",    "🇩🇪", "Deutsch"),
        Language("ar",    "🇸🇦", "العربية"),
        Language("hi",    "🇮🇳", "हिन्दी"),
        Language("pt",    "🇧🇷", "Português"),
        Language("nl",    "🇳🇱", "Nederlands"),
        Language("pl",    "🇵🇱", "Polski"),
        Language("ru",    "🇷🇺", "Русский"),
        Language("zh",    "🇨🇳", "中文"),
        Language("ja",    "🇯🇵", "日本語"),
        Language("ko",    "🇰🇷", "한국어"),
        Language("tr",    "🇹🇷", "Türkçe"),
    )

    fun getSaved(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "fr") ?: "fr"

    fun save(ctx: Context, code: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, code).apply()

    fun apply(ctx: Context): Context {
        val code   = getSaved(ctx)
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(ctx.resources.configuration)
        config.setLocale(locale)
        return ctx.createConfigurationContext(config)
    }
}
