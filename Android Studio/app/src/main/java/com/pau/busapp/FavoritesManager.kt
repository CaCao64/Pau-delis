package com.pau.busapp

import android.content.Context
import android.content.SharedPreferences

object FavoritesManager {
    private const val PREFS = "favorites"
    private const val KEY_STOPS   = "fav_stops"
    private const val KEY_LINES   = "fav_lines"
    private const val KEY_DEFAULT = "default_stop"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Arrêts ────────────────────────────────────────────────────────────────
    fun getFavStops(ctx: Context): MutableSet<String> =
        prefs(ctx).getStringSet(KEY_STOPS, mutableSetOf())!!.toMutableSet()

    fun toggleStop(ctx: Context, name: String): Boolean {
        val set = getFavStops(ctx)
        val added = if (name in set) {
            set.remove(name)
            val widget = getWidgetStops(ctx).filter { it != name }
            val order = getOrderedStops(ctx).filter { it != name }
            prefs(ctx).edit()
                .putStringSet(KEY_STOPS, set)
                .putString(KEY_WIDGET, widget.joinToString(","))
                .putString(KEY_STOP_ORDER, order.joinToString("|"))
                .apply()
            false
        } else {
            set.add(name)
            val order = getOrderedStops(ctx).filter { it != name } + name
            prefs(ctx).edit()
                .putStringSet(KEY_STOPS, set)
                .putString(KEY_STOP_ORDER, order.joinToString("|"))
                .apply()
            true
        }
        return added
    }

    fun isStopFav(ctx: Context, name: String) = name in getFavStops(ctx)

    fun getDefaultStop(ctx: Context): String =
        prefs(ctx).getString(KEY_DEFAULT, null)
            ?: getFavStops(ctx).sorted().firstOrNull()
            ?: ""

    fun setDefaultStop(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_DEFAULT, name).apply()
    }

    fun isDefaultStop(ctx: Context, name: String) = getDefaultStop(ctx) == name

    // ── Widget ───────────────────────────────────────────────────────────────
    private const val KEY_WIDGET = "widget_stops"
    const val MAX_WIDGET = 6

    fun getWidgetStops(ctx: Context): List<String> {
        val favs = getFavStops(ctx)
        return prefs(ctx).getString(KEY_WIDGET, null)
            ?.split(",")?.filter { it.isNotEmpty() && it in favs } ?: emptyList()
    }

    fun toggleWidgetStop(ctx: Context, name: String): Boolean {
        val list = getWidgetStops(ctx).toMutableList()
        return if (name in list) {
            list.remove(name)
            prefs(ctx).edit().putString(KEY_WIDGET, list.joinToString(",")).apply()
            false
        } else {
            if (list.size >= MAX_WIDGET) return false
            list.add(name)
            prefs(ctx).edit().putString(KEY_WIDGET, list.joinToString(",")).apply()
            true
        }
    }

    fun saveWidgetOrder(ctx: Context, ordered: List<String>) {
        prefs(ctx).edit().putString(KEY_WIDGET, ordered.joinToString(",")).apply()
    }

    fun isWidgetStop(ctx: Context, name: String) = name in getWidgetStops(ctx)

    // ── Ordre ────────────────────────────────────────────────────────────────
    private const val KEY_STOP_ORDER = "fav_stops_order"
    private const val KEY_LINE_ORDER = "fav_lines_order"

    fun getOrderedStops(ctx: Context): List<String> {
        val favs = getFavStops(ctx)
        val saved = prefs(ctx).getString(KEY_STOP_ORDER, null)
            ?.split("|")?.filter { it in favs } ?: emptyList()
        // Nouveaux favoris non encore dans l'ordre : ajoutés à la fin (plus récents en bas)
        val missing = favs.filter { it !in saved }
        return saved + missing
    }

    fun saveStopOrder(ctx: Context, order: List<String>) {
        prefs(ctx).edit().putString(KEY_STOP_ORDER, order.joinToString("|")).apply()
    }

    fun getOrderedLines(ctx: Context): List<String> {
        val favs = getFavLines(ctx)
        val saved = prefs(ctx).getString(KEY_LINE_ORDER, null)
            ?.split("|")?.filter { it in favs } ?: emptyList()
        val missing = favs.filter { it !in saved }
        return saved + missing
    }

    fun saveLineOrder(ctx: Context, order: List<String>) {
        prefs(ctx).edit().putString(KEY_LINE_ORDER, order.joinToString("|")).apply()
    }

    // ── Bus favoris (arrêt + ligne + direction) ───────────────────────────────
    private const val KEY_FAV_BUSES       = "fav_buses"
    private const val KEY_FAV_BUSES_ORDER = "fav_buses_order"

    fun getFavBuses(ctx: Context): List<String> {
        val set = prefs(ctx).getStringSet(KEY_FAV_BUSES, mutableSetOf())!!.toSet()
        val order = prefs(ctx).getString(KEY_FAV_BUSES_ORDER, null)
            ?.split("|")?.filter { it in set } ?: emptyList()
        val missing = set.filter { it !in order }
        return order + missing
    }

    fun busKey(stopName: String, ligne: String, destination: String) = "$stopName|$ligne|$destination"

    fun toggleBus(ctx: Context, stopName: String, ligne: String, destination: String): Boolean {
        val key = busKey(stopName, ligne, destination)
        val set = prefs(ctx).getStringSet(KEY_FAV_BUSES, mutableSetOf())!!.toMutableSet()
        val added = if (key in set) {
            set.remove(key)
            val order = getFavBuses(ctx).filter { it != key }
            prefs(ctx).edit()
                .putStringSet(KEY_FAV_BUSES, set)
                .putString(KEY_FAV_BUSES_ORDER, order.joinToString("|"))
                .apply()
            false
        } else {
            set.add(key)
            val order = getFavBuses(ctx).filter { it != key } + key
            prefs(ctx).edit()
                .putStringSet(KEY_FAV_BUSES, set)
                .putString(KEY_FAV_BUSES_ORDER, order.joinToString("|"))
                .apply()
            true
        }
        return added
    }

    fun isBusFav(ctx: Context, stopName: String, ligne: String, destination: String) =
        busKey(stopName, ligne, destination) in (prefs(ctx).getStringSet(KEY_FAV_BUSES, mutableSetOf()) ?: emptySet())

    // ── Lignes ────────────────────────────────────────────────────────────────
    fun getFavLines(ctx: Context): MutableSet<String> =
        prefs(ctx).getStringSet(KEY_LINES, mutableSetOf())!!.toMutableSet()

    fun toggleLine(ctx: Context, number: String): Boolean {
        val set = getFavLines(ctx)
        val added = if (number in set) {
            set.remove(number)
            val order = getOrderedLines(ctx).filter { it != number }
            prefs(ctx).edit()
                .putStringSet(KEY_LINES, set)
                .putString(KEY_LINE_ORDER, order.joinToString("|"))
                .apply()
            false
        } else {
            set.add(number)
            val order = getOrderedLines(ctx).filter { it != number } + number
            prefs(ctx).edit()
                .putStringSet(KEY_LINES, set)
                .putString(KEY_LINE_ORDER, order.joinToString("|"))
                .apply()
            true
        }
        return added
    }

    fun isLineFav(ctx: Context, number: String) = number in getFavLines(ctx)
}
