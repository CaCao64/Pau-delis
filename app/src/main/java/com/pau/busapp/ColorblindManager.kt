package com.pau.busapp

import android.content.Context

enum class ColorblindMode(val label: String) {
    NORMAL("Aucun"),
    DEUTERANOMALIE("Deutéranomalie / Protanomalie (Rouge-Vert)"),
    TRITANOMALIE("Tritanomalie (Bleu-Jaune)"),
    HIGH_CONTRAST("Contraste élevé")
}

object ColorblindManager {
    private const val PREFS = "colorblind"
    private const val KEY   = "mode"

    fun get(ctx: Context): ColorblindMode {
        val name = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ColorblindMode.NORMAL.name) ?: ColorblindMode.NORMAL.name
        return runCatching { ColorblindMode.valueOf(name) }.getOrDefault(ColorblindMode.NORMAL)
    }

    fun set(ctx: Context, mode: ColorblindMode) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}
