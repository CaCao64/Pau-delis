package com.pau.busapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class AppTheme(val label: String, val mode: Int) {
    SYSTEM("🌗  Automatique", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT( "☀️  Clair",       AppCompatDelegate.MODE_NIGHT_NO),
    DARK(  "🌙  Sombre",      AppCompatDelegate.MODE_NIGHT_YES)
}

object ThemeManager {
    private const val PREFS = "theme_prefs"
    private const val KEY   = "theme"

    fun get(ctx: Context): AppTheme {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return runCatching { AppTheme.valueOf(name) }.getOrDefault(AppTheme.SYSTEM)
    }

    fun set(ctx: Context, theme: AppTheme) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, theme.name).apply()
        AppCompatDelegate.setDefaultNightMode(theme.mode)
    }

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(get(ctx).mode)
    }
}
