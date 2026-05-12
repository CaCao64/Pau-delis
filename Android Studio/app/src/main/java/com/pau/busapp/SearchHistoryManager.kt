package com.pau.busapp

import android.content.Context

object SearchHistoryManager {
    private const val PREFS = "search_history"
    private const val KEY   = "history"
    private const val MAX   = 20

    fun getHistory(ctx: Context): MutableList<String> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return if (raw.isEmpty()) mutableListOf()
        else raw.split("|||").filter { it.isNotEmpty() }.toMutableList()
    }

    fun addQuery(ctx: Context, query: String) {
        if (query.isBlank()) return
        val list = getHistory(ctx)
        list.remove(query)
        list.add(0, query)
        if (list.size > MAX) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    fun removeQuery(ctx: Context, query: String) {
        val list = getHistory(ctx)
        list.remove(query)
        save(ctx, list)
    }

    private fun save(ctx: Context, list: List<String>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, list.joinToString("|||")).apply()
    }
}
