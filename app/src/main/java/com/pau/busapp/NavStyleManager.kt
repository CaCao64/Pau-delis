package com.pau.busapp

import android.content.Context

enum class NavStyle(val label: String, val description: String) {
    A("Pill",          "Fond pilule, sans labels"),
    B("Indicateur",    "Barre au-dessus de l'onglet actif"),
    C("Labels actif",  "Label visible uniquement pour l'onglet actif"),
    D("Compact",       "Icônes seules, plus grandes"),
    E("Flottant",      "Barre flottante avec coins arrondis")
}

object NavStyleManager {
    private const val PREFS = "nav_style"
    private const val KEY   = "style"

    fun get(ctx: Context): NavStyle {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, NavStyle.A.name) ?: NavStyle.A.name
        return runCatching { NavStyle.valueOf(name) }.getOrDefault(NavStyle.A)
    }

    fun set(ctx: Context, style: NavStyle) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, style.name).apply()
    }
}
