package com.pau.busapp

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.util.Calendar

object WidgetStopConfigManager {

    private const val PREFS = "widget_stop_configs"

    fun get(ctx: Context, stopName: String): WidgetStopConfig {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(stopName, null) ?: return WidgetStopConfig()
        return runCatching { WidgetStopConfig.fromJson(JSONObject(raw)) }
            .getOrDefault(WidgetStopConfig())
    }

    fun save(ctx: Context, stopName: String, config: WidgetStopConfig) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(stopName, config.toJson().toString()).apply()
    }

    fun removeExpiredStops(ctx: Context) {
        val today = java.time.LocalDate.now()
        val todayStr = "%04d-%02d-%02d".format(today.year, today.monthValue, today.dayOfMonth)
        val widgetStops = FavoritesManager.getWidgetStops(ctx).toMutableList()
        var changed = false
        widgetStops.removeAll { stopName ->
            val cfg = get(ctx, stopName)
            val expired = WidgetCondition.SPECIFIC_DATES in cfg.conditions
                && cfg.specificDates.isNotEmpty()
                && cfg.specificDates.all { it < todayStr }
            if (expired) changed = true
            expired
        }
        if (changed) FavoritesManager.saveWidgetOrder(ctx, widgetStops)
    }

    fun isActiveToday(config: WidgetStopConfig): Boolean {
        if (config.conditions.isEmpty()) return true  // aucune condition = tous les jours

        val today = LocalDate.now()
        val todayStr = "%04d-%02d-%02d".format(today.year, today.monthValue, today.dayOfMonth)
        val cal = Calendar.getInstance()
        val calDay = cal.get(Calendar.DAY_OF_WEEK)
        val week = cal.get(Calendar.WEEK_OF_YEAR)

        // Toutes les conditions actives doivent être satisfaites (AND)
        return config.conditions.all { cond ->
            when (cond) {
                WidgetCondition.ODD_WEEKS      -> week % 2 == 1
                WidgetCondition.EVEN_WEEKS     -> week % 2 == 0
                WidgetCondition.WEEKDAYS       -> calDay in config.weekdays
                WidgetCondition.NO_HOLIDAYS    -> !AlertManager.isSchoolHoliday(todayStr)
                WidgetCondition.SPECIFIC_DATES -> todayStr in config.specificDates
            }
        }
    }
}
