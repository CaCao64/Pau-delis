package com.pau.busapp

import org.json.JSONArray
import org.json.JSONObject

enum class WidgetCondition {
    ODD_WEEKS, EVEN_WEEKS, WEEKDAYS, NO_HOLIDAYS, SPECIFIC_DATES
}

data class WidgetStopConfig(
    val textSize: Int = 12,
    val conditions: Set<WidgetCondition> = emptySet(),  // vide = tous les jours
    val weekdays: Set<Int> = emptySet(),
    val specificDates: List<String> = emptyList()
) {
    fun toJson() = JSONObject().apply {
        put("textSize", textSize)
        put("conditions", JSONArray(conditions.map { it.name }))
        put("weekdays", JSONArray(weekdays.toList()))
        put("specificDates", JSONArray(specificDates))
    }

    companion object {
        fun fromJson(j: JSONObject) = WidgetStopConfig(
            textSize   = j.optInt("textSize", 12),
            conditions = j.optJSONArray("conditions")?.let { a ->
                (0 until a.length()).mapNotNull {
                    runCatching { WidgetCondition.valueOf(a.getString(it)) }.getOrNull()
                }.toSet()
            } ?: run {
                // Migration depuis l'ancien format "recurrence"
                val old = j.optString("recurrence", "")
                when (old) {
                    "ODD_WEEKS"      -> setOf(WidgetCondition.ODD_WEEKS)
                    "EVEN_WEEKS"     -> setOf(WidgetCondition.EVEN_WEEKS)
                    "WEEKDAYS"       -> setOf(WidgetCondition.WEEKDAYS)
                    "NO_HOLIDAYS"    -> setOf(WidgetCondition.NO_HOLIDAYS)
                    "SPECIFIC_DATES" -> setOf(WidgetCondition.SPECIFIC_DATES)
                    else             -> emptySet()
                }
            },
            weekdays   = j.optJSONArray("weekdays")?.let { a ->
                (0 until a.length()).map { a.getInt(it) }.toSet()
            } ?: emptySet(),
            specificDates = j.optJSONArray("specificDates")?.let { a ->
                (0 until a.length()).map { a.getString(it) }
            } ?: emptyList()
        )
    }
}
