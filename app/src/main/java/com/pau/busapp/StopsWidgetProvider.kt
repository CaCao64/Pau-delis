package com.pau.busapp

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class StopsWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.pau.busapp.WIDGET_REFRESH"
        const val EXTRA_STOP_NAME = "extra_stop_name"

        private var updateJob: Job? = null
        private var lastUpdateMs = 0L
        private const val MIN_UPDATE_INTERVAL = 5_000L

        fun requestUpdate(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StopsWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                ctx.sendBroadcast(Intent(ctx, StopsWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                })
            }
        }

        private fun formatPassage(info: StopInfo, dayLabel: String, isReal: Boolean): String {
            val first = info.passages.firstOrNull() ?: return ""
            val dest = info.destination.split(" ").take(2).joinToString(" ")
            val statut = if (isReal && first.type == "reel") {
                val theoMinutes = info.passages.filter { it.type == "theorique" }
                    .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                    .map { it.hour * 60 + it.minute }
                PassageHelper.toStatut(PassageHelper.computeEcart(first, theoMinutes.ifEmpty { null }))
            } else PassageStatut.THEORIQUE
            val emoji = when (statut) {
                PassageStatut.A_LHEURE  -> "🟢"
                PassageStatut.RETARD    -> "🕐"
                PassageStatut.AVANCE    -> "⚡"
                PassageStatut.ANNULE    -> "❌"
                PassageStatut.THEORIQUE -> "*"
            }
            return "$dayLabel[${info.ligne} → $dest] : ${first.arrivee}$emoji"
        }

        // Vérifie si une date est un jour férié français
        private fun isFrenchHoliday(cal: Calendar): Boolean {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)

            // Jours fixes
            if (month == 1  && day == 1)  return true // Nouvel An
            if (month == 5  && day == 1)  return true // Fête du Travail
            if (month == 5  && day == 8)  return true // Victoire 1945
            if (month == 7  && day == 14) return true // Fête Nationale
            if (month == 8  && day == 15) return true // Assomption
            if (month == 11 && day == 1)  return true // Toussaint
            if (month == 11 && day == 11) return true // Armistice
            if (month == 12 && day == 25) return true // Noël

            // Pâques (algorithme de Meeus/Jones/Butcher)
            val a = year % 19; val b = year / 100; val c = year % 100
            val d = b / 4; val e = b % 4; val f = (b + 8) / 25
            val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val easterMonth = (h + l - 7 * m + 114) / 31
            val easterDay = ((h + l - 7 * m + 114) % 31) + 1

            val easter = Calendar.getInstance().also {
                it.set(year, easterMonth - 1, easterDay)
            }

            fun dayOffset(offset: Int): Pair<Int, Int> {
                val c2 = easter.clone() as Calendar
                c2.add(Calendar.DAY_OF_YEAR, offset)
                return Pair(c2.get(Calendar.MONTH) + 1, c2.get(Calendar.DAY_OF_MONTH))
            }

            val (lmM, lmD) = dayOffset(1)   // Lundi de Pâques
            val (ascM, ascD) = dayOffset(39) // Ascension
            val (pentM, pentD) = dayOffset(50) // Lundi de Pentecôte

            if (month == lmM   && day == lmD)   return true
            if (month == ascM  && day == ascD)   return true
            if (month == pentM && day == pentD)  return true

            return false
        }

        fun isNetworkAvailable(ctx: Context): Boolean {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        private fun computeContextStatus(socketOk: Boolean, ctx: Context, isDark: Boolean): Pair<String, Int> {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val blue = android.graphics.Color.parseColor(if (isDark) "#80D4FF" else "#006699")

            if (!socketOk) return Pair(ctx.getString(R.string.status_offline), android.graphics.Color.parseColor(if (isDark) "#FF8888" else "#CC0000"))
            if (isFrenchHoliday(cal)) return Pair(ctx.getString(R.string.status_holiday), blue)
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) return Pair(ctx.getString(R.string.status_sunday), blue)
            if (hour >= 20 || hour < 6) return Pair(ctx.getString(R.string.status_night), blue)
            return Pair(ctx.getString(R.string.api_online), if (isDark) android.graphics.Color.parseColor("#81C784") else android.graphics.Color.parseColor("#2E7D32"))
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> setupListAdapter(ctx, mgr, id) }
        triggerUpdate(ctx, mgr, ids)
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, opts: Bundle?) {
        setupListAdapter(ctx, mgr, id)
        triggerUpdate(ctx, mgr, intArrayOf(id))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StopsWidgetProvider::class.java))
            triggerUpdate(ctx, mgr, ids)
        }
    }

    override fun onDisabled(ctx: Context) {
        super.onDisabled(ctx)
        updateJob?.cancel()
    }

    private fun applyTheme(ctx: Context, views: RemoteViews, widgetId: Int) {
        val isDark = WidgetConfigActivity.isDark(ctx, widgetId)
        val opacity = WidgetConfigActivity.getOpacity(ctx, widgetId)
        val baseColor = android.graphics.Color.parseColor(if (isDark) "#0D3B14" else "#E8F5E9")
        val widgetBgColor = android.graphics.Color.argb(opacity, android.graphics.Color.red(baseColor), android.graphics.Color.green(baseColor), android.graphics.Color.blue(baseColor))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                R.id.widget_root,
                "setBackgroundTintList",
                android.content.res.ColorStateList.valueOf(widgetBgColor)
            )
        } else {
            views.setInt(R.id.widget_root, "setBackgroundColor", widgetBgColor)
        }

        val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        views.setTextColor(R.id.widget_title, textColor)
        views.setTextColor(R.id.btn_refresh, textColor)
    }

    private fun setupListAdapter(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(ctx.packageName, R.layout.widget_stops)
        applyTheme(ctx, views, widgetId)

        // Adapter pour la liste défilable
        val svcIntent = Intent(ctx, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = android.net.Uri.parse("widget://$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_list, svcIntent)

        // PendingIntent template pour clic sur un arrêt → ouvrir DetailsFragment
        val templateIntent = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_STOP_NAME, "")  // placeholder surchargé par le fill-in
        }
        val templatePiFlags = if (android.os.Build.VERSION.SDK_INT >= 31)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val templatePi = PendingIntent.getActivity(ctx, widgetId + 2000, templateIntent, templatePiFlags)
        views.setPendingIntentTemplate(R.id.widget_list, templatePi)

        val refreshPi = PendingIntent.getBroadcast(ctx, widgetId,
            Intent(ctx, StopsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPi)
        views.setTextViewText(R.id.widget_title, "🚌 ${ctx.getString(R.string.app_name)}  ·  ${ctx.getString(R.string.widget_loading)}")
        views.setViewVisibility(R.id.tv_widget_status, View.GONE)
        mgr.updateAppWidget(widgetId, views)
    }

    private fun triggerUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateMs < MIN_UPDATE_INTERVAL && updateJob?.isActive == true) return
        lastUpdateMs = now
        updateJob?.cancel()
        val pending = goAsync()
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            try { ids.forEach { id -> updateWidget(ctx, mgr, id) } }
            finally { pending.finish() }
        }
    }

    private suspend fun updateWidget(ctx: Context, mgr: AppWidgetManager, widgetId: Int) {
        try {
            DisruptionAlertManager.checkForNewDisruptions(ctx)
        } catch (_: Exception) {}

        WidgetStopConfigManager.removeExpiredStops(ctx)
        WidgetOrderManager.migrateIfNeeded(ctx)

        val entries  = WidgetOrderManager.getActiveEntries(ctx)
        val timeFmt  = SimpleDateFormat("HH:mm", Locale.FRANCE)

        // Init liste widget
        synchronized(WidgetListFactory.lock) {
            WidgetListFactory.stopNames.clear()
            WidgetListFactory.stopTimes.clear()
            WidgetListFactory.stopKeys.clear()
            WidgetListFactory.stopTextSizes.clear()
            entries.forEach { key ->
                val label = when {
                    key.startsWith(WidgetOrderManager.PREFIX_LINE) ->
                        "Ligne ${key.removePrefix(WidgetOrderManager.PREFIX_LINE)}"
                    key.startsWith(WidgetOrderManager.PREFIX_BUS) -> {
                        val parts = key.removePrefix(WidgetOrderManager.PREFIX_BUS).split("|")
                        "[${parts.getOrElse(1){"?"}}] ${parts.getOrElse(0){"?"}}"
                    }
                    else -> key.removePrefix(WidgetOrderManager.PREFIX_STOP)
                }
                WidgetListFactory.stopNames.add(label)
                WidgetListFactory.stopTimes.add(ctx.getString(R.string.widget_loading))
                WidgetListFactory.stopKeys.add(key)
                WidgetListFactory.stopTextSizes.add(12)
            }
        }
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)

        if (entries.isEmpty()) return

        // Afficher "Chargement..." avec statut réseau initial
        val networkOk = isNetworkAvailable(ctx)
        val headerViews = RemoteViews(ctx.packageName, R.layout.widget_stops)
        val isDark = WidgetConfigActivity.isDark(ctx, widgetId)
        applyTheme(ctx, headerViews, widgetId)
        val refreshPi = PendingIntent.getBroadcast(ctx, widgetId,
            Intent(ctx, StopsWidgetProvider::class.java).apply { action = ACTION_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        headerViews.setOnClickPendingIntent(R.id.btn_refresh, refreshPi)
        headerViews.setViewVisibility(R.id.tv_widget_status, View.VISIBLE)
        run {
            val (t, c) = computeContextStatus(networkOk, ctx, isDark)
            headerViews.setTextViewText(R.id.tv_widget_status, t)
            headerViews.setTextColor(R.id.tv_widget_status, c)
        }
        headerViews.setTextViewText(R.id.widget_title, "🚌 ${ctx.getString(R.string.app_name)}  ·  ${ctx.getString(R.string.widget_loading)}")
        mgr.updateAppWidget(widgetId, headerViews)

        val location = getLastLocation(ctx)
        var apiReachedAtLeastOnce = false

        for ((i, key) in entries.withIndex()) {
            val cfg = WidgetStopConfigManager.get(ctx, key)
            if (!WidgetStopConfigManager.isActiveToday(cfg)) {
                synchronized(WidgetListFactory.lock) {
                    if (i < WidgetListFactory.stopTimes.size) WidgetListFactory.stopTimes[i] = ctx.getString(R.string.widget_inactive_today)
                }
                mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
                continue
            }

            when {
                key.startsWith(WidgetOrderManager.PREFIX_STOP) -> {
                    val stopName = key.removePrefix(WidgetOrderManager.PREFIX_STOP)
                    val stop = AppData.busStops.find { it.name == stopName }
                    val (time, apiOk) = if (stop != null) fetchTimeWithStatus(ctx, stop) else "—" to false
                    if (apiOk) apiReachedAtLeastOnce = true
                    synchronized(WidgetListFactory.lock) {
                        if (i < WidgetListFactory.stopTimes.size) WidgetListFactory.stopTimes[i] = time
                        if (i < WidgetListFactory.stopTextSizes.size) WidgetListFactory.stopTextSizes[i] = cfg.textSize
                    }
                }
                key.startsWith(WidgetOrderManager.PREFIX_LINE) -> {
                    val num = key.removePrefix(WidgetOrderManager.PREFIX_LINE)
                    val (stopLabel, time, apiOk) = fetchLineNextStopWithStatus(ctx, num, location)
                    if (apiOk) apiReachedAtLeastOnce = true
                    synchronized(WidgetListFactory.lock) {
                        if (i < WidgetListFactory.stopNames.size)
                            WidgetListFactory.stopNames[i] = "Ligne $num — $stopLabel"
                        if (i < WidgetListFactory.stopTimes.size) WidgetListFactory.stopTimes[i] = time
                        if (i < WidgetListFactory.stopTextSizes.size) WidgetListFactory.stopTextSizes[i] = cfg.textSize
                    }
                }
                key.startsWith(WidgetOrderManager.PREFIX_BUS) -> {
                    val parts    = key.removePrefix(WidgetOrderManager.PREFIX_BUS).split("|")
                    val stopName = parts.getOrElse(0) { "" }
                    val lineNum  = parts.getOrElse(1) { "" }
                    val dest     = parts.getOrElse(2) { "" }.split(" ").take(2).joinToString(" ")
                    val label = "[$lineNum] $stopName → $dest"
                    synchronized(WidgetListFactory.lock) {
                        if (i < WidgetListFactory.stopNames.size) WidgetListFactory.stopNames[i] = label
                    }
                    // Récupérer le prochain passage pour cette ligne à cet arrêt
                    val stop = AppData.busStops.find { it.name == stopName }
                    val (time, apiOk) = if (stop != null) {
                        try {
                            val (arrivee, connected) = withTimeout(25_000L) {
                                try {
                                    val infos = IdelisApi.getStopMonitoring(stop.codes.firstOrNull() ?: "", 5)
                                    val info = infos.find { it.ligne == lineNum }
                                    val first = info?.passages?.firstOrNull()
                                    if (first != null) {
                                        val statut = if (first.type == "reel") {
                                            val theoMinutes = info.passages.filter { it.type == "theorique" }
                                                .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                                                .map { it.hour * 60 + it.minute }
                                            PassageHelper.toStatut(PassageHelper.computeEcart(first, theoMinutes.ifEmpty { null }))
                                        } else PassageStatut.THEORIQUE
                                        val emoji = when (statut) {
                                            PassageStatut.A_LHEURE  -> "🟢"
                                            PassageStatut.RETARD    -> "🕐"
                                            PassageStatut.AVANCE    -> "⚡"
                                            PassageStatut.ANNULE    -> "❌"
                                            PassageStatut.THEORIQUE -> "*"
                                        }
                                        "${first.arrivee}$emoji" to true
                                    } else {
                                        val gtfsTime = gtfsTimeForLine(ctx, stop, lineNum)
                                        val suffix = if (gtfsTime != "—") "*" else ""
                                        "$gtfsTime$suffix" to true
                                    }
                                } catch (e: Exception) {
                                    val gtfsTime = gtfsTimeForLine(ctx, stop, lineNum)
                                    val suffix = if (gtfsTime != "—") "*" else ""
                                    "$gtfsTime$suffix" to (e.message?.contains("500") == true)
                                }
                            }
                            if (connected) {
                                arrivee to true
                            } else {
                                val gtfsTime = gtfsTimeForLine(ctx, stop, lineNum)
                                val suffix = if (gtfsTime != "—") "*" else ""
                                "$gtfsTime$suffix" to false
                            }
                        } catch (_: TimeoutCancellationException) {
                            val gtfsTime = gtfsTimeForLine(ctx, stop, lineNum)
                            val suffix = if (gtfsTime != "—") "*" else ""
                            "$gtfsTime$suffix" to false
                        }
                    } else "—" to false
                    if (apiOk) apiReachedAtLeastOnce = true
                    synchronized(WidgetListFactory.lock) {
                        if (i < WidgetListFactory.stopTimes.size) WidgetListFactory.stopTimes[i] = time
                        if (i < WidgetListFactory.stopTextSizes.size) WidgetListFactory.stopTextSizes[i] = cfg.textSize
                    }
                }
            }
            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
        }

        // Statut final basé sur les appels réels
        val finalOnline = networkOk && apiReachedAtLeastOnce
        val (finalStatus, finalColor) = computeContextStatus(finalOnline, ctx, isDark)
        headerViews.setTextViewText(R.id.tv_widget_status, finalStatus)
        headerViews.setTextColor(R.id.tv_widget_status, finalColor)
        headerViews.setTextViewText(R.id.widget_title, "🚌 ${ctx.getString(R.string.app_name)}  ·  ${timeFmt.format(Date())}")
        mgr.updateAppWidget(widgetId, headerViews)
    }

    private suspend fun gtfsTimeForLine(ctx: Context, stop: BusStop, lineNum: String): String {
        return try {
            val now = java.time.LocalTime.now()
            val infos = GtfsReader.getTheoreticalPassages(ctx, stop.codes, now, java.time.LocalDate.now())
            val info = infos.find { it.ligne == lineNum }
            info?.passages?.firstOrNull()?.arrivee?.let { "$it*" } ?: "—"
        } catch (_: Exception) { "—" }
    }

    // Wrapper avec timeout 25s + fallback GTFS — retourne aussi si l'API a répondu
    private suspend fun fetchTimeWithStatus(ctx: Context, stop: BusStop): Pair<String, Boolean> {
        return try {
            val (text, apiConnected) = withTimeout(25_000L) { fetchTimeRealOnly(ctx, stop) }
            if (apiConnected) {
                val display = if (text.isNullOrEmpty()) gtfsTime(ctx, stop) else text
                display to true
            } else {
                gtfsTime(ctx, stop) to false
            }
        } catch (_: TimeoutCancellationException) {
            gtfsTime(ctx, stop) to false
        }
    }

    // Tente l'API temps réel. Retourne (texte|null, apiJoignable).
    // null texte = API joignable mais pas de données → utiliser GTFS mais marquer online.
    private suspend fun fetchTimeRealOnly(ctx: Context, stop: BusStop): Pair<String?, Boolean> {
        if (stop.codes.isEmpty()) return null to false
        return try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 3)
            val parts = infos.mapNotNull { info ->
                if (info.passages.isEmpty()) null else formatPassage(info, "", true)
            }
            (if (parts.isEmpty()) null else parts.joinToString("\n")) to true
        } catch (e: Exception) {
            // HTTP 500 = API joignable mais pas de données (nuit, dimanche…) → online
            // Autre exception = erreur réseau → offline
            val apiReachable = e.message?.contains("500") == true
            null to apiReachable
        }
    }

    // Ligne + statut API
    private suspend fun fetchLineNextStopWithStatus(ctx: Context, lineNum: String, location: Pair<Double, Double>?): Triple<String, String, Boolean> {
        val line = AppData.busLines.find { it.number == lineNum }
            ?: return Triple("?", "Ligne inconnue", false)
        val allStopNames = (line.stopsDir1 + line.stopsDir2).distinct()
        val stops = allStopNames.mapNotNull { name ->
            AppData.busStops.find { it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) }
        }
        if (stops.isEmpty()) return Triple("?", "Pas d'arrêt", false)
        val nearest = if (location != null)
            stops.minByOrNull { s -> val dLat = s.lat - location.first; val dLon = s.lon - location.second; dLat*dLat + dLon*dLon } ?: stops.first()
        else stops.first()
        val (time, apiOk) = fetchTimeWithStatus(ctx, nearest)
        return Triple(nearest.name, time, apiOk)
    }

    // Wrapper avec timeout 25s + fallback GTFS (sans statut)
    private suspend fun fetchTimeWithTimeout(ctx: Context, stop: BusStop): String =
        fetchTimeWithStatus(ctx, stop).first

    // Trouver l'arrêt le plus proche d'une ligne + son prochain passage (sans statut)
    private suspend fun fetchLineNextStop(ctx: Context, lineNum: String, location: Pair<Double, Double>?): Pair<String, String> {
        val line = AppData.busLines.find { it.number == lineNum }
            ?: return "?" to "Ligne inconnue"

        val allStopNames = (line.stopsDir1 + line.stopsDir2).distinct()
        val stops = allStopNames.mapNotNull { name ->
            AppData.busStops.find { it.name.equals(name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) }
        }
        if (stops.isEmpty()) return "?" to "Pas d'arrêt"

        val nearestStop = if (location != null) {
            stops.minByOrNull { stop ->
                val dLat = stop.lat - location.first
                val dLon = stop.lon - location.second
                dLat * dLat + dLon * dLon
            } ?: stops.first()
        } else stops.first()

        val time = try {
            withTimeout(25_000L) { fetchTime(ctx, nearestStop) }
        } catch (_: TimeoutCancellationException) {
            gtfsTime(ctx, nearestStop)
        }
        return nearestStop.name to time
    }

    // Récupère la dernière position connue (ne demande pas la permission)
    @SuppressLint("MissingPermission")
    private fun getLastLocation(ctx: Context): Pair<Double, Double>? {
        return try {
            val pm = ctx.packageManager
            val fineOk = pm.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, ctx.packageName) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val coarseOk = pm.checkPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION, ctx.packageName) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!fineOk && !coarseOk) return null

            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val providers = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER
            )
            val loc = providers.mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time } ?: return null
            Pair(loc.latitude, loc.longitude)
        } catch (_: Exception) { null }
    }

    private suspend fun fetchTime(ctx: Context, stop: BusStop): String {
        // Dimanche + arrêt sans bus le dimanche
        val isSunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        if (isSunday) {
            val hasSundayLine = stop.lines.any { ln ->
                val line = AppData.busLines.find { it.number == ln }
                line?.type == LineType.FEBUS || line?.type == LineType.DIMANCHE || line?.type == LineType.SPECIAL
            }
            if (!hasSundayLine) return ctx.getString(R.string.widget_no_sunday)
        }

        if (stop.codes.isEmpty()) return gtfsTime(ctx, stop)
        return try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 3)
            if (infos.isEmpty()) return gtfsTime(ctx, stop)
            val parts = infos.mapNotNull { info ->
                if (info.passages.isEmpty()) return@mapNotNull null
                formatPassage(info, "", true)
            }
            if (parts.isEmpty()) gtfsTime(ctx, stop) else parts.joinToString("\n")
        } catch (_: Exception) { gtfsTime(ctx, stop) }
    }

    private suspend fun gtfsTime(ctx: Context, stop: BusStop): String {
        return try {
            var date = java.time.LocalDate.now()
            for (attempt in 0..6) {
                val time = if (attempt == 0) java.time.LocalTime.now() else java.time.LocalTime.of(0, 0)
                val infos = GtfsReader.getTheoreticalPassages(ctx, stop.codes, time, date)
                if (infos.isNotEmpty()) {
                    val dayLabel = when (attempt) {
                        0 -> ""
                        1 -> "Dem. "
                        else -> {
                            val days = arrayOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
                            "${days[date.dayOfWeek.value - 1]} "
                        }
                    }
                    val parts = infos.mapNotNull { info ->
                        if (info.passages.isEmpty()) return@mapNotNull null
                        formatPassage(info, dayLabel, false)
                    }
                    if (parts.isNotEmpty()) return parts.joinToString("\n")
                }
                date = date.plusDays(1)
            }
            "—"
        } catch (_: Exception) { "—" }
    }
}
