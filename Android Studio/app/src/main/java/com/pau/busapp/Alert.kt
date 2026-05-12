package com.pau.busapp

import org.json.JSONArray
import org.json.JSONObject

// Gardé pour migration JSON des anciennes alertes
enum class AlertRecurrence {
    TODAY, EVERY_WEEK, ODD_WEEKS, EVEN_WEEKS, NO_SCHOOL_HOLIDAYS, SPECIFIC_DATES, WEEKDAYS
}

// Conditions combinables en AND — vide = tous les jours
enum class AlertCondition {
    ODD_WEEKS, EVEN_WEEKS, NO_SCHOOL_HOLIDAYS, SPECIFIC_DATES, WEEKDAYS
}

data class Alert(
    val id: Long = System.currentTimeMillis(),
    val stopName: String,
    val lineName: String,
    val hourMinute: Pair<Int, Int>,
    val minutesBefore: Int,
    val conditions: Set<AlertCondition> = emptySet(), // vide = tous les jours
    val isToday: Boolean = false,
    val weekdays: Set<Int> = emptySet(),
    val specificDates: List<String> = emptyList(),
    val excludedDates: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("stopName", stopName)
        put("lineName", lineName)
        put("hour", hourMinute.first)
        put("minute", hourMinute.second)
        put("minutesBefore", minutesBefore)
        put("conditions", JSONArray(conditions.map { it.name }))
        put("isToday", isToday)
        put("weekdays", JSONArray(weekdays.toList()))
        put("specificDates", JSONArray(specificDates))
        put("excludedDates", JSONArray(excludedDates))
        put("enabled", enabled)
        put("recurrence", if (isToday) "TODAY" else if (conditions.isEmpty()) "EVERY_WEEK" else conditions.first().name)
    }

    companion object {
        fun fromJson(o: JSONObject): Alert {
            val weekdays = mutableSetOf<Int>()
            o.optJSONArray("weekdays")?.let { a -> for (i in 0 until a.length()) weekdays.add(a.getInt(i)) }
            val dates = mutableListOf<String>()
            o.optJSONArray("specificDates")?.let { a -> for (i in 0 until a.length()) dates.add(a.getString(i)) }
            val excluded = mutableListOf<String>()
            o.optJSONArray("excludedDates")?.let { a -> for (i in 0 until a.length()) excluded.add(a.getString(i)) }

            val condArr = o.optJSONArray("conditions")
            val conditions: Set<AlertCondition>
            val isToday: Boolean

            if (condArr != null) {
                conditions = (0 until condArr.length()).mapNotNull {
                    runCatching { AlertCondition.valueOf(condArr.getString(it)) }.getOrNull()
                }.toSet()
                // isToday explicite, ou migration : conditions vides + recurrence=TODAY
                val explicitIsToday = o.optBoolean("isToday", false)
                val legacyRecurrence = o.optString("recurrence", "")
                isToday = explicitIsToday || (conditions.isEmpty() && legacyRecurrence == "TODAY")
            } else {
                // Migration depuis ancien format recurrence (une seule valeur)
                val rec = runCatching { AlertRecurrence.valueOf(o.optString("recurrence", "EVERY_WEEK")) }
                    .getOrDefault(AlertRecurrence.EVERY_WEEK)
                isToday = rec == AlertRecurrence.TODAY
                conditions = when (rec) {
                    AlertRecurrence.ODD_WEEKS          -> setOf(AlertCondition.ODD_WEEKS)
                    AlertRecurrence.EVEN_WEEKS         -> setOf(AlertCondition.EVEN_WEEKS)
                    AlertRecurrence.NO_SCHOOL_HOLIDAYS -> setOf(AlertCondition.NO_SCHOOL_HOLIDAYS)
                    AlertRecurrence.SPECIFIC_DATES     -> setOf(AlertCondition.SPECIFIC_DATES)
                    AlertRecurrence.WEEKDAYS           -> setOf(AlertCondition.WEEKDAYS)
                    else                               -> emptySet()
                }
            }

            return Alert(
                id            = o.getLong("id"),
                stopName      = o.getString("stopName"),
                lineName      = o.getString("lineName"),
                hourMinute    = Pair(o.getInt("hour"), o.getInt("minute")),
                minutesBefore = o.getInt("minutesBefore"),
                conditions    = conditions,
                isToday       = isToday,
                weekdays      = weekdays,
                specificDates = dates,
                excludedDates = excluded,
                enabled       = o.optBoolean("enabled", true)
            )
        }
    }
}
