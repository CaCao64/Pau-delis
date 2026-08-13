package com.pau.busapp

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class TrackingService : Service() {

    companion object {
        const val ACTION_START   = "com.pau.busapp.TRACKING_START"
        const val ACTION_STOP    = "com.pau.busapp.TRACKING_STOP"
        const val ACTION_TOGGLE  = "com.pau.busapp.TRACKING_TOGGLE"
        const val ACTION_REFRESH = "com.pau.busapp.TRACKING_REFRESH"

        const val EXTRA_NOTIF_ID  = "notif_id"
        const val EXTRA_STOP_NAME = "stop_name"
        const val EXTRA_LINE_NAME = "line_name"
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_HOUR      = "hour"
        const val EXTRA_MINUTE    = "minute"

        // true = suivi actif, false = en pause
        private val trackingEnabled = mutableMapOf<Int, Boolean>()
        fun isEnabled(id: Int) = trackingEnabled[id] != false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs  = mutableMapOf<Int, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_START -> {
                val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: return START_NOT_STICKY
                val lineName = intent.getStringExtra(EXTRA_LINE_NAME) ?: ""
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: ""
                val hour     = intent.getIntExtra(EXTRA_HOUR, 0)
                val minute   = intent.getIntExtra(EXTRA_MINUTE, 0)

                trackingEnabled[notifId] = true

                // Démarrer en foreground tout de suite
                startForeground(notifId, buildNotif(notifId, stopName, lineName,
                    getString(R.string.tracking_starting), 0xFF1565C0.toInt(), R.drawable.ic_bell, hour, minute, destination = destination))

                jobs[notifId]?.cancel()
                jobs[notifId] = scope.launch {
                    trackLoop(notifId, stopName, lineName, hour, minute, destination)
                    jobs.remove(notifId)
                    trackingEnabled.remove(notifId)
                    if (jobs.isEmpty()) stopSelf()
                }
            }

            ACTION_TOGGLE -> {
                val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: return START_NOT_STICKY
                val lineName = intent.getStringExtra(EXTRA_LINE_NAME) ?: ""
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: ""
                val hour     = intent.getIntExtra(EXTRA_HOUR, 0)
                val minute   = intent.getIntExtra(EXTRA_MINUTE, 0)

                val nowEnabled = !isEnabled(notifId)
                trackingEnabled[notifId] = nowEnabled

                // Mettre à jour la notification immédiatement pour refléter le nouvel état
                scope.launch {
                    val (passage, isConn) = fetchNextPassage(stopName, lineName, destination)
                    val (body, color, icon) = buildContent(passage, hour, minute)
                    val statusLabel = if (isConn) getString(R.string.tracking_connection_lost)
                                      else if (nowEnabled) getString(R.string.tracking_active)
                                      else getString(R.string.tracking_disabled)
                    val dest = passage?.destination ?: destination
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(notifId, buildNotif(notifId, stopName, lineName,
                        "$body\n$statusLabel", color, icon, hour, minute, destination = dest))
                }
            }

            ACTION_REFRESH -> {
                val notifId  = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                val stopName = intent.getStringExtra(EXTRA_STOP_NAME) ?: return START_NOT_STICKY
                val lineName = intent.getStringExtra(EXTRA_LINE_NAME) ?: ""
                val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: ""
                val hour     = intent.getIntExtra(EXTRA_HOUR, 0)
                val minute   = intent.getIntExtra(EXTRA_MINUTE, 0)
                scope.launch {
                    val (passage, isConn) = fetchNextPassage(stopName, lineName, destination)
                    val (body, color, icon) = buildContent(passage, hour, minute)
                    val dest = passage?.destination ?: destination
                    val statusLabel = if (isConn) getString(R.string.tracking_connection_lost)
                                      else if (isEnabled(notifId)) getString(R.string.tracking_active)
                                      else getString(R.string.tracking_disabled)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(notifId, buildNotif(notifId, stopName, lineName,
                        "$body\n$statusLabel", color, icon, hour, minute, destination = dest))
                }
            }

            ACTION_STOP -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notifId)
                jobs[notifId]?.cancel()
                jobs.remove(notifId)
                trackingEnabled.remove(notifId)
                if (jobs.isEmpty()) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ── Boucle principale ─────────────────────────────────────────────────────

    private suspend fun trackLoop(notifId: Int, stopName: String, lineName: String, hour: Int, minute: Int, destination: String = "") {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Fetch initial
        val (first, isConn0) = fetchNextPassage(stopName, lineName, destination)
        val (body0, color0, icon0) = buildContent(first, hour, minute)
        val dest0 = first?.destination ?: destination
        val statusText0 = if (isConn0) getString(R.string.tracking_connection_lost) else getString(R.string.tracking_active)
        nm.notify(notifId, buildNotif(notifId, stopName, lineName,
            "$body0\n$statusText0", color0, icon0, hour, minute, destination = dest0))

        val firstHeureReelle = first?.heureReelle
        var lastDest = dest0

        // Durée max de suivi : heure prévue + 15 minutes
        val maxTrackUntil = System.currentTimeMillis() +
            java.util.concurrent.TimeUnit.MINUTES.toMillis(15) +
            ((hour * 60 + minute) - (java.util.Calendar.getInstance().let {
                it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
            }).coerceAtLeast(0)) * 60_000L

        while (currentCoroutineContext().isActive) {
            delay(40_000)

            // Arrêter si on dépasse la durée max
            if (System.currentTimeMillis() > maxTrackUntil + 15 * 60_000L) {
                nm.notify(notifId, buildNotif(notifId, stopName, lineName,
                    getString(R.string.tracking_finished), 0xFF888888.toInt(), R.drawable.ic_notif_on_time,
                    hour, minute, ongoing = false))
                break
            }

            // Si suivi en pause, ne pas mettre à jour la notif mais continuer à tourner
            if (!isEnabled(notifId)) continue

            val (updated, isConnectionLost) = fetchNextPassage(stopName, lineName, destination)
            if (updated?.destination?.isNotEmpty() == true) lastDest = updated.destination

            // Détecter si le bus est passé
            val cal = java.util.Calendar.getInstance()
            val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val hasPassed = if (!isConnectionLost) {
                if (firstHeureReelle != null) {
                    val firstMin = firstHeureReelle.first * 60 + firstHeureReelle.second
                    // L'heure actuelle doit être APRÈS le passage prévu avant de conclure qu'il est passé
                    nowMin >= firstMin && (updated == null || (updated.heureReelle != null && updated.heureReelle != firstHeureReelle))
                } else {
                    nowMin > (hour * 60 + minute) + 3
                }
            } else {
                false
            }

            if (hasPassed) {
                val origHeure = firstHeureReelle?.let { (h, m) -> fmtHeure(h, m) }
                    ?: fmtHeure(hour, minute)
                nm.notify(notifId, buildNotif(notifId, stopName, lineName,
                    getString(R.string.tracking_passed_explicit), 0xFF888888.toInt(),
                    R.drawable.ic_notif_bus, hour, minute, ongoing = false, destination = lastDest))
                break
            }

            val (bodyU, colorU, iconU) = buildContent(updated, hour, minute)
            val statusTextU = if (isConnectionLost) getString(R.string.tracking_connection_lost) else getString(R.string.tracking_active)
            nm.notify(notifId, buildNotif(notifId, stopName, lineName,
                "$bodyU\n$statusTextU", colorU, iconU, hour, minute, destination = lastDest))
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotif(
        notifId: Int, stopName: String, lineName: String,
        body: String, color: Int, icon: Int, hour: Int, minute: Int,
        ongoing: Boolean = isEnabled(notifId),
        destination: String = ""
    ): android.app.Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(StopsWidgetProvider.EXTRA_STOP_NAME,
                "${WidgetOrderManager.PREFIX_STOP}$stopName")
            putExtra("highlight_line", lineName)
        }
        val openPi = PendingIntent.getActivity(
            this, notifId + 30000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nowEnabled = isEnabled(notifId)
        val toggleIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_TOGGLE
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_STOP_NAME, stopName)
            putExtra(EXTRA_LINE_NAME, lineName)
            putExtra(EXTRA_DESTINATION, destination)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        // requestCode différent selon l'état pour forcer Android à recréer le PendingIntent
        val togglePi = PendingIntent.getService(
            this, notifId + if (nowEnabled) 20000 else 21000, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleLabel = if (nowEnabled) getString(R.string.tracking_toggle_disable) else getString(R.string.tracking_toggle_resume)

        val refreshIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_REFRESH
            putExtra(EXTRA_NOTIF_ID, notifId)
            putExtra(EXTRA_STOP_NAME, stopName)
            putExtra(EXTRA_LINE_NAME, lineName)
            putExtra(EXTRA_DESTINATION, destination)
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
        val refreshPi = PendingIntent.getService(
            this, notifId + 40000, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val firstLine = body.lines().first()
        val dirPart = if (destination.isNotEmpty()) " → ${destination.split(" ").take(2).joinToString(" ")}" else ""
        val title = "🚌 $lineName$dirPart — $stopName"

        return NotificationCompat.Builder(this, AlertReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_bus)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(firstLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPi)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .addAction(0, toggleLabel, togglePi)
            .addAction(0, getString(R.string.tracking_refresh), refreshPi)
            .build()
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private data class FetchResult(val passage: PassageInfo?, val isConnectionLost: Boolean)
    private data class PassageInfo(val arrivee: String, val statut: PassageStatut, val heureReelle: Pair<Int,Int>?, val destination: String = "")

    private suspend fun fetchNextPassage(stopName: String, lineName: String, destination: String = ""): FetchResult {
        val stop = AppData.busStops.find { it.name == stopName } ?: return FetchResult(null, false)
        if (stop.codes.isEmpty()) return FetchResult(gtfsFallback(stop, lineName, destination), false)
        return try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 5)
            val info  = infos.find { it.ligne == lineName && (destination.isEmpty() || it.destination.contains(destination, ignoreCase = true) || destination.contains(it.destination, ignoreCase = true)) }
            val passageInfo = if (info != null) {
                val first = info.passages.firstOrNull() ?: return FetchResult(gtfsFallback(stop, lineName, destination), false)
                val statut = if (first.type != "reel") PassageStatut.THEORIQUE
                else {
                    val theoMin = info.passages.filter { it.type == "theorique" }
                        .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                        .map { it.hour * 60 + it.minute }
                    PassageHelper.toStatut(PassageHelper.computeEcart(first, theoMin.ifEmpty { null }))
                }
                PassageInfo(first.arrivee, statut,
                    PassageHelper.parseArrivee(first.arrivee)?.let { Pair(it.hour, it.minute) },
                    info.destination)
            } else gtfsFallback(stop, lineName, destination)
            FetchResult(passageInfo, false)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val isConn = e is java.io.IOException || msg.contains("timeout", ignoreCase = true) || msg.contains("connect", ignoreCase = true)
            FetchResult(gtfsFallback(stop, lineName, destination), isConn)
        }
    }

    private suspend fun gtfsFallback(stop: BusStop, lineName: String, destination: String = ""): PassageInfo? {
        return try {
            val infos = GtfsReader.getTheoreticalPassages(this, stop.codes,
                java.time.LocalTime.now(), java.time.LocalDate.now())
            val first = infos.find { it.ligne == lineName && (destination.isEmpty() || it.destination.contains(destination, ignoreCase = true) || destination.contains(it.destination, ignoreCase = true)) }?.passages?.firstOrNull() ?: return null
            PassageInfo(first.arrivee, PassageStatut.THEORIQUE,
                PassageHelper.parseArrivee(first.arrivee)?.let { Pair(it.hour, it.minute) },
                destination)
        } catch (_: Exception) { null }
    }

    // Remplace HH:MM par HHhMM pour éviter l'auto-lien Samsung dans les notifications
    private fun fmtHeure(h: Int, m: Int) = "%02dh%02d".format(h, m)
    private fun fmtHeureStr(arrivee: String) = arrivee.replace(":", "h")

    private fun buildContent(passage: PassageInfo?, hour: Int, minute: Int): Triple<String, Int, Int> {
        if (passage == null) return Triple(
            getString(R.string.tracking_no_data).format(fmtHeure(hour, minute)),
            statusColor(PassageStatut.AVANCE, this), R.drawable.ic_bell)
        val emoji = when (passage.statut) {
            PassageStatut.A_LHEURE -> "🟢"; PassageStatut.RETARD -> "🕐"
            PassageStatut.AVANCE -> "⚡"; PassageStatut.ANNULE -> "❌"; PassageStatut.THEORIQUE -> ""
        }
        val suffix = if (passage.statut == PassageStatut.THEORIQUE) "*" else ""
        val heure  = passage.heureReelle?.let { " — ${fmtHeure(it.first, it.second)}" } ?: ""
        val color = statusColor(passage.statut, this)
        val icon = when (passage.statut) {
            PassageStatut.A_LHEURE -> R.drawable.ic_notif_on_time; PassageStatut.RETARD -> R.drawable.ic_notif_late
            PassageStatut.AVANCE -> R.drawable.ic_notif_early; PassageStatut.ANNULE -> R.drawable.ic_notif_cancelled
            PassageStatut.THEORIQUE -> R.drawable.ic_bell
        }
        val label = if (passage.statut == PassageStatut.THEORIQUE) getString(R.string.tracking_next_theo) else getString(R.string.tracking_next_real)
        return Triple("$label : $emoji${fmtHeureStr(passage.arrivee)}$suffix$heure", color, icon)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
