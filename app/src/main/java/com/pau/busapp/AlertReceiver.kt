package com.pau.busapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlertReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "bus_alerts"
        const val ACTION_TOGGLE_TRACKING = "com.pau.busapp.TOGGLE_TRACKING"
        private val trackingDisabled = mutableSetOf<Int>()

        fun isTrackingEnabled(notifId: Int) = notifId !in trackingDisabled
        fun toggleTracking(notifId: Int) {
            if (notifId in trackingDisabled) trackingDisabled.remove(notifId)
            else trackingDisabled.add(notifId)
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_TRACKING) {
            // Déléguer au service
            val svcIntent = Intent(ctx, TrackingService::class.java).apply {
                action = TrackingService.ACTION_TOGGLE
                putExtra(TrackingService.EXTRA_NOTIF_ID,  intent.getIntExtra("notif_id", -1))
                putExtra(TrackingService.EXTRA_STOP_NAME, intent.getStringExtra("stop_name"))
                putExtra(TrackingService.EXTRA_LINE_NAME, intent.getStringExtra("line_name") ?: "")
                putExtra(TrackingService.EXTRA_DESTINATION, intent.getStringExtra("destination") ?: "")
                putExtra(TrackingService.EXTRA_HOUR,      intent.getIntExtra("hour", 0))
                putExtra(TrackingService.EXTRA_MINUTE,    intent.getIntExtra("minute", 0))
            }
            ctx.startService(svcIntent)
            return
        }

        val stopName = intent.getStringExtra("stop_name") ?: return
        val lineName = intent.getStringExtra("line_name") ?: ""
        val hour     = intent.getIntExtra("hour", 0)
        val minute   = intent.getIntExtra("minute", 0)
        val alertId  = intent.getLongExtra("alert_id", -1L)
        val notifId  = if (alertId != -1L) (alertId % Int.MAX_VALUE).toInt()
                       else System.currentTimeMillis().toInt()

        createChannel(ctx)
        if (alertId != -1L) AlertManager.deleteIfToday(ctx, alertId)

        // Démarrer le ForegroundService pour le suivi long
        val svcIntent = Intent(ctx, TrackingService::class.java).apply {
            action = TrackingService.ACTION_START
            putExtra(TrackingService.EXTRA_NOTIF_ID,  notifId)
            putExtra(TrackingService.EXTRA_STOP_NAME, stopName)
            putExtra(TrackingService.EXTRA_LINE_NAME, lineName)
            putExtra(TrackingService.EXTRA_DESTINATION, intent.getStringExtra("destination") ?: "")
            putExtra(TrackingService.EXTRA_HOUR,      hour)
            putExtra(TrackingService.EXTRA_MINUTE,    minute)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(svcIntent)
        } else {
            ctx.startService(svcIntent)
        }
    }

    private data class PassageInfo(
        val arrivee: String,
        val statut: PassageStatut,
        val heureReelle: Pair<Int, Int>?
    )

    private fun buildContent(passage: PassageInfo?, hour: Int, minute: Int, ctx: Context): Triple<String, Int, Int> {
        if (passage == null)
            return Triple(
                "Passage prevu a %02d:%02d — aucune donnee disponible".format(hour, minute),
                statusColor(PassageStatut.AVANCE, ctx), R.drawable.ic_bell
            )
        val (arrivee, statut, heureReelle) = passage
        val emoji  = when (statut) {
            PassageStatut.A_LHEURE  -> "🟢"
            PassageStatut.RETARD    -> "🕐"
            PassageStatut.AVANCE    -> "⚡"
            PassageStatut.ANNULE    -> "❌"
            PassageStatut.THEORIQUE -> ""
        }
        val suffix = if (statut == PassageStatut.THEORIQUE) "*" else ""
        val heure  = if (heureReelle != null) " — %02d:%02d".format(heureReelle.first, heureReelle.second) else ""
        val color = statusColor(statut, ctx)
        val icon   = when (statut) {
            PassageStatut.A_LHEURE  -> R.drawable.ic_notif_on_time
            PassageStatut.RETARD    -> R.drawable.ic_notif_late
            PassageStatut.AVANCE    -> R.drawable.ic_notif_early
            PassageStatut.ANNULE    -> R.drawable.ic_notif_cancelled
            PassageStatut.THEORIQUE -> R.drawable.ic_bell
        }
        val label = if (statut == PassageStatut.THEORIQUE) ctx.getString(R.string.tracking_next_theo) else ctx.getString(R.string.tracking_next_real)
        return Triple("$label : $emoji$arrivee$suffix$heure", color, icon)
    }

    private fun arrivalWaitMs(arrivee: String?): Long {
        if (arrivee == null) return 0L
        val t = PassageHelper.parseArrivee(arrivee) ?: return 0L
        val now = java.time.LocalTime.now()
        val arrivalMs = (t.toSecondOfDay() - now.toSecondOfDay()) * 1000L + 60_000L
        return if (arrivalMs > 0) arrivalMs else 0L
    }

    private suspend fun fetchNextPassage(ctx: Context, stopName: String, lineName: String, destination: String = ""): PassageInfo? {
        val stop = AppData.busStops.find { it.name == stopName } ?: return null
        if (stop.codes.isEmpty()) return null

        try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 5)
            val info  = infos.find { it.ligne == lineName && (destination.isEmpty() || it.destination.contains(destination, ignoreCase = true) || destination.contains(it.destination, ignoreCase = true)) }
            if (info != null) {
                val first = info.passages.firstOrNull() ?: return gtfsFallbackPassage(ctx, stop, lineName)
                val statut = if (first.type != "reel") {
                    PassageStatut.THEORIQUE
                } else {
                    val theoMinutes = info.passages
                        .filter { it.type == "theorique" }
                        .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                        .map { it.hour * 60 + it.minute }
                    PassageHelper.toStatut(PassageHelper.computeEcart(first, theoMinutes.ifEmpty { null }))
                }
                return PassageInfo(first.arrivee, statut, PassageHelper.parseArrivee(first.arrivee)?.let { Pair(it.hour, it.minute) })
            }
            return gtfsFallbackPassage(ctx, stop, lineName)
        } catch (_: Exception) {
            return gtfsFallbackPassage(ctx, stop, lineName)
        }
    }

    private suspend fun gtfsFallbackPassage(ctx: Context, stop: BusStop, lineName: String): PassageInfo? {
        return try {
            val now = java.time.LocalTime.now()
            var date = java.time.LocalDate.now()
            for (attempt in 0..6) {
                val time = if (attempt == 0) now else java.time.LocalTime.of(0, 0)
                val infos = GtfsReader.getTheoreticalPassages(ctx, stop.codes, time, date)
                val info = infos.find { it.ligne == lineName }
                val first = info?.passages?.firstOrNull()
                if (first != null) {
                    return PassageInfo(first.arrivee, PassageStatut.THEORIQUE, PassageHelper.parseArrivee(first.arrivee)?.let { Pair(it.hour, it.minute) })
                }
                date = date.plusDays(1)
            }
            null
        } catch (_: Exception) { null }
    }

    private fun showNotification(ctx: Context, title: String, body: String, color: Int, icon: Int,
                                 notifId: Int, stopName: String, lineName: String, hour: Int, minute: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(StopsWidgetProvider.EXTRA_STOP_NAME, "${WidgetOrderManager.PREFIX_STOP}$stopName")
            putExtra("highlight_line", lineName)
        }
        val pi = PendingIntent.getActivity(
            ctx, notifId + 30000, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val trackingEnabled = isTrackingEnabled(notifId)
        val toggleIntent = Intent(ctx, AlertReceiver::class.java).apply {
            action = ACTION_TOGGLE_TRACKING
            putExtra("notif_id", notifId)
            putExtra("stop_name", stopName)
            putExtra("line_name", lineName)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        val togglePi = PendingIntent.getBroadcast(
            ctx, notifId + 10000, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleLabel = if (trackingEnabled) ctx.getString(R.string.tracking_toggle_disable) else ctx.getString(R.string.tracking_toggle_resume)

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(icon)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(body.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setOngoing(trackingEnabled)
            .addAction(0, toggleLabel, togglePi)
            .build()
        nm.notify(notifId, notif)
    }

    private fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH)
                .apply { description = ctx.getString(R.string.notif_channel_desc) }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}
