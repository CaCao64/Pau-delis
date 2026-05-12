package com.pau.busapp

import android.content.Context
import org.json.JSONObject

object WidgetLinesManager {

    private const val PREFS      = "widget_lines"
    private const val KEY_ORDER  = "order"
    private const val KEY_ENABLED = "enabled"
    const val MAX_LINES = 3

    fun getOrder(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    fun getEnabled(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENABLED, null)?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun saveOrder(ctx: Context, order: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.joinToString(",")).apply()
    }

    fun saveEnabled(ctx: Context, enabled: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENABLED, enabled.joinToString(",")).apply()
    }

    /** Retourne les lignes actives dans l'ordre. */
    fun getActiveLines(ctx: Context): List<String> {
        val enabled = getEnabled(ctx)
        return getOrder(ctx).filter { it in enabled }
    }

    /** Ajoute une ligne si pas déjà présente. */
    fun addLine(ctx: Context, num: String) {
        val order = getOrder(ctx).toMutableList()
        if (num !in order) order.add(num)
        saveOrder(ctx, order)
    }

    fun removeLine(ctx: Context, num: String) {
        val order   = getOrder(ctx).filter { it != num }
        val enabled = getEnabled(ctx) - num
        saveOrder(ctx, order)
        saveEnabled(ctx, enabled)
    }

    fun isEnabled(ctx: Context, num: String) = num in getEnabled(ctx)
}
