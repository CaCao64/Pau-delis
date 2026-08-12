package com.pau.busapp

import android.content.Context

data class NavTabConfig(
    val id: String,       // clé stable : "map", "favs", "search", "alerts"
    val enabled: Boolean
)

object NavConfigManager {
    private const val PREFS  = "nav_config"
    private const val KEY_ORDER   = "order"
    private const val KEY_ENABLED = "enabled"

    // Onglets disponibles (Plus est fixe, non configurable)
    val ALL_TABS = listOf(
        Triple("map",      R.drawable.ic_map,      "Carte"),
        Triple("favs",     R.drawable.ic_star,     "Favoris"),
        Triple("search",   R.drawable.ic_search,   "Recherche"),
        Triple("alerts",   R.drawable.ic_bell,     "Alertes"),
        Triple("stops",    R.drawable.ic_list,     "Arrêts"),
        Triple("lines",    R.drawable.ic_route,    "Lignes"),
        Triple("settings", R.drawable.ic_settings, "Paramètres")
    )

    fun getOrder(ctx: Context): List<String> {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)
        if (saved != null) {
            val allIds = ALL_TABS.map { it.first }.toSet()
            val ids = saved.split(",").filter { it in allIds }
            // Ajouter les nouveaux onglets manquants à la fin
            val missing = ALL_TABS.map { it.first }.filter { it !in ids }
            return ids + missing
        }
        return ALL_TABS.map { it.first }
    }

    fun getEnabled(ctx: Context): Set<String> {
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENABLED, null)
        // Par défaut : map, favs, search, alerts cochés — stops, lines, settings non cochés
        return saved?.split(",")?.toSet() ?: setOf("map", "favs", "search", "alerts")
    }

    fun save(ctx: Context, order: List<String>, enabled: Set<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, order.joinToString(","))
            .putString(KEY_ENABLED, enabled.joinToString(","))
            .apply()
    }

    // Retourne les onglets visibles dans l'ordre (toujours "map" en premier si présent)
    fun getVisibleTabs(ctx: Context): List<String> {
        val order   = getOrder(ctx)
        val enabled = getEnabled(ctx)
        val visible = order.filter { it in enabled }
        // Toujours au moins 1 onglet, toujours max 4
        return visible.take(4).ifEmpty { listOf("map") }
    }
}
