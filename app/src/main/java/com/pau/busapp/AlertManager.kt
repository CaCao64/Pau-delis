package com.pau.busapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

object AlertManager {

    private const val PREFS = "alerts_prefs"
    private const val KEY   = "alerts_json"

    // Zones académiques Bordeaux (Pyrénées-Atlantiques = zone B)
    // Vacances scolaires zone B 2025-2026 (approximatif, à mettre à jour)
    private val SCHOOL_HOLIDAYS = listOf(
        "2025-10-18" to "2025-11-03",
        "2025-12-20" to "2026-01-05",
        "2026-02-14" to "2026-03-02",
        "2026-04-11" to "2026-04-27",
        "2026-07-04" to "2026-09-01"
    )

    fun load(ctx: Context): MutableList<Alert> {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull {
            runCatching { Alert.fromJson(arr.getJSONObject(it)) }.getOrNull()
        }.toMutableList()
    }

    fun save(ctx: Context, alerts: List<Alert>) {
        val arr = JSONArray(alerts.map { it.toJson() })
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    fun scheduleAll(ctx: Context, alerts: List<Alert>) {
        alerts.filter { it.enabled }.forEach { schedule(ctx, it) }
    }

    fun schedule(ctx: Context, alert: Alert) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTriggers = nextTriggerTimes(alert, count = 30)
        nextTriggers.forEachIndexed { idx, triggerMs ->
            val pi = pendingIntent(ctx, alert, idx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
        }
    }

    fun cancel(ctx: Context, alert: Alert) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (idx in 0 until 30) am.cancel(pendingIntent(ctx, alert, idx))
    }

    fun nextTriggerTimes(alert: Alert, count: Int = 1): List<Long> {
        val (h, m) = alert.hourMinute
        val now = LocalDate.now()
        val result = mutableListOf<Long>()
        var day = now
        var checked = 0
        while (result.size < count && checked < 400) {
            checked++
            if (matchesDay(alert, day)) {
                val dt = LocalDateTime.of(day, java.time.LocalTime.of(h, m))
                    .minusMinutes(alert.minutesBefore.toLong())
                val ms = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (ms > System.currentTimeMillis() - 60_000) result.add(ms)
            }
            day = day.plusDays(1)
        }
        return result
    }

    fun cleanupPastTodayAlerts(ctx: Context) {
        val alerts = load(ctx)  // fromJson effectue la migration isToday automatiquement
        val now = System.currentTimeMillis()
        val toRemove = alerts.filter { a ->
            if (!a.isToday) return@filter false
            val (h, m) = a.hourMinute
            val alertTime = LocalDateTime.of(LocalDate.now(), java.time.LocalTime.of(h, m))
            val alertMs = alertTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            alertMs < now - 60_000
        }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { cancel(ctx, it) }
            save(ctx, alerts.filter { it !in toRemove })
        }
    }

    private fun matchesDay(alert: Alert, day: LocalDate): Boolean {
        if (alert.isToday) return day == LocalDate.now()

        // Dates exclues — priorité sur tout le reste
        if (day.toString() in alert.excludedDates) return false

        if (alert.conditions.isEmpty()) return true

        val weekNum = weekNumber(day)
        return alert.conditions.all { cond ->
            when (cond) {
                AlertCondition.ODD_WEEKS          -> weekNum % 2 == 1
                AlertCondition.EVEN_WEEKS         -> weekNum % 2 == 0
                AlertCondition.NO_SCHOOL_HOLIDAYS -> !isSchoolHoliday(day) && !isFrenchHoliday(day)
                AlertCondition.SPECIFIC_DATES     -> day.toString() in alert.specificDates
                AlertCondition.WEEKDAYS           -> dayOfWeekToCalendar(day.dayOfWeek) in alert.weekdays
            }
        }
    }

    fun deleteIfToday(ctx: Context, alertId: Long) {
        val alerts = load(ctx)
        val alert  = alerts.find { it.id == alertId } ?: return
        if (alert.isToday) {
            cancel(ctx, alert)
            save(ctx, alerts.filter { it.id != alertId })
        }
    }

    fun isSchoolHoliday(dateIso: String): Boolean =
        SCHOOL_HOLIDAYS.any { (start, end) -> dateIso >= start && dateIso <= end }

    private fun isSchoolHoliday(day: LocalDate): Boolean =
        isSchoolHoliday(day.toString())

    fun isFrenchHoliday(day: LocalDate): Boolean {
        val y = day.year; val mo = day.monthValue; val d = day.dayOfMonth
        if (mo == 1  && d == 1)  return true
        if (mo == 5  && d == 1)  return true
        if (mo == 5  && d == 8)  return true
        if (mo == 7  && d == 14) return true
        if (mo == 8  && d == 15) return true
        if (mo == 11 && d == 1)  return true
        if (mo == 11 && d == 11) return true
        if (mo == 12 && d == 25) return true
        val a = y % 19; val b = y / 100; val c = y % 100
        val dd = b / 4; val e = b % 4; val f = (b + 8) / 25
        val g = (b - f + 1) / 3; val h = (19 * a + b - dd - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val em = (h + l - 7 * m + 114) / 31; val ed = ((h + l - 7 * m + 114) % 31) + 1
        val easter = LocalDate.of(y, em, ed)
        return day == easter.plusDays(1)   // Lundi de Pâques
            || day == easter.plusDays(39)  // Ascension
            || day == easter.plusDays(50)  // Lundi de Pentecôte
    }

    private fun weekNumber(day: LocalDate): Int {
        val cal = Calendar.getInstance()
        cal.set(day.year, day.monthValue - 1, day.dayOfMonth)
        return cal.get(Calendar.WEEK_OF_YEAR)
    }

    private fun dayOfWeekToCalendar(dow: DayOfWeek): Int = when (dow) {
        DayOfWeek.MONDAY    -> Calendar.MONDAY
        DayOfWeek.TUESDAY   -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY  -> Calendar.THURSDAY
        DayOfWeek.FRIDAY    -> Calendar.FRIDAY
        DayOfWeek.SATURDAY  -> Calendar.SATURDAY
        DayOfWeek.SUNDAY    -> Calendar.SUNDAY
    }

    private fun pendingIntent(ctx: Context, alert: Alert, idx: Int): PendingIntent {
        val intent = Intent(ctx, AlertReceiver::class.java).apply {
            putExtra("alert_id", alert.id)
            putExtra("stop_name", alert.stopName)
            putExtra("line_name", alert.lineName)
            putExtra("destination", alert.destination)
            putExtra("minutes_before", alert.minutesBefore)
            putExtra("hour", alert.hourMinute.first)
            putExtra("minute", alert.hourMinute.second)
        }
        val reqCode = (alert.id % Int.MAX_VALUE).toInt() + idx
        return PendingIntent.getBroadcast(
            ctx, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
