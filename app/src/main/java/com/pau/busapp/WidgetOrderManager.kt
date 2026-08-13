package com.pau.busapp

import android.content.Context

/**
 * Gère l'ordre global des entrées du widget : arrêts, lignes et bus à l'arrêt mélangés.
 * Clés : "stop:NomArrêt", "ligne:T3", "bus:NomArrêt|T3|LESCAR Soleil"
 */
object WidgetOrderManager {

    private const val PREFS    = "widget_order"
    private const val KEY_ORDER = "global_order"
    private const val KEY_ENABLED = "global_enabled"

    const val PREFIX_STOP  = "stop:"
    const val PREFIX_LINE  = "ligne:"
    const val PREFIX_BUS   = "bus:"

    fun getOrder(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)?.split("\n")?.filter { it.isNotEmpty() } ?: emptyList()

    fun getEnabled(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENABLED, null)?.split("\n")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun save(ctx: Context, order: List<String>, enabled: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.joinToString("\n"))
            .putString(KEY_ENABLED, enabled.joinToString("\n"))
            .apply()
    }

    fun getActiveEntries(ctx: Context): List<String> {
        val enabled = getEnabled(ctx)
        return getOrder(ctx).filter { it in enabled }
    }

    /** Ajoute une entrée si pas déjà présente. */
    fun addEntry(ctx: Context, key: String) {
        val order = getOrder(ctx).toMutableList()
        if (key !in order) order.add(key)
        val enabled = getEnabled(ctx).toMutableSet()
        save(ctx, order, enabled)
    }

    fun removeEntry(ctx: Context, key: String) {
        val order   = getOrder(ctx).filter { it != key }
        val enabled = getEnabled(ctx) - key
        save(ctx, order, enabled)
    }

    fun isEnabled(ctx: Context, key: String) = key in getEnabled(ctx)

    fun toggleEnabled(ctx: Context, key: String): Boolean {
        val enabled = getEnabled(ctx).toMutableSet()
        val nowEnabled = if (key in enabled) { enabled.remove(key); false } else { enabled.add(key); true }
        save(ctx, getOrder(ctx), enabled)
        return nowEnabled
    }

    // ── Migration depuis les anciens managers ─────────────────────────────────
    fun migrateIfNeeded(ctx: Context) {
        val existing   = getOrder(ctx).toMutableList()
        val enabledSet = getEnabled(ctx).toMutableSet()
        var changed    = false

        // 1. Corriger les entrées sans préfixe
        val toFix = existing.filter {
            !it.startsWith(PREFIX_STOP) && !it.startsWith(PREFIX_LINE) && !it.startsWith(PREFIX_BUS)
        }
        toFix.forEach { bare ->
            val idx = existing.indexOf(bare)
            if (idx >= 0) {
                val key = "$PREFIX_STOP$bare"
                existing[idx] = key
                if (bare in enabledSet) { enabledSet.remove(bare); enabledSet.add(key) }
                changed = true
            }
        }

        // 2. Ajouter les arrêts widget de l'ancien manager (toujours actifs)
        FavoritesManager.getWidgetStops(ctx).forEach { name ->
            val key = "$PREFIX_STOP$name"
            if (key !in existing) { existing.add(key); changed = true }
            enabledSet.add(key)  // s'assurer qu'ils sont bien actifs
        }

        // 3. Ajouter les lignes widget existantes
        WidgetLinesManager.getOrder(ctx).forEach { num ->
            val key = "$PREFIX_LINE$num"
            if (key !in existing) { existing.add(key); changed = true }
            if (WidgetLinesManager.isEnabled(ctx, num)) enabledSet.add(key)
        }

        // 4. Si enabled est vide mais order non vide → activer tout ce qui est un arrêt widget
        if (enabledSet.isEmpty() && existing.isNotEmpty()) {
            existing.filter { it.startsWith(PREFIX_STOP) }.forEach { enabledSet.add(it) }
            changed = true
        }

        if (changed || enabledSet != getEnabled(ctx)) save(ctx, existing, enabledSet)
    }
}
