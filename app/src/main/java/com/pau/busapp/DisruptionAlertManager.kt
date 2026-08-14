package com.pau.busapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.UUID

object DisruptionAlertManager {
    private const val PREFS = "disruptions_prefs"
    private const val KEY_ENABLED = "traffic_alerts_enabled"
    private const val KEY_MODE = "traffic_alerts_mode" // "favorites" or "all"
    private const val KEY_NOTIFIED = "notified_disruptions_set"
    private const val CHANNEL_ID = "bus_disruptions"

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

    fun getMode(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, "favorites") ?: "favorites"

    fun setMode(ctx: Context, mode: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .apply()

    private fun getNotified(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_NOTIFIED, emptySet()) ?: emptySet()

    private fun addNotified(ctx: Context, hash: String) {
        val current = getNotified(ctx).toMutableSet()
        current.add(hash)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_NOTIFIED, current)
            .apply()
    }

    suspend fun checkForNewDisruptions(ctx: Context) {
        if (!isEnabled(ctx)) return

        val mode = getMode(ctx)
        val notices = try {
            TrafficNoticesRepository.loadNotices(forceRefresh = true)
        } catch (e: Exception) {
            return
        }

        if (notices.isEmpty()) return

        val favStops = FavoritesManager.getFavStops(ctx)
        val favLines = FavoritesManager.getFavLines(ctx)
        val notified = getNotified(ctx)

        notices.forEach { notice ->
            val noticeHash = "${notice.title}|${notice.summary}"
            if (noticeHash in notified) return@forEach

            // Filter by mode
            val shouldNotify = if (mode == "all") {
                true
            } else {
                // Mode favorites
                val matchesLine = notice.lineCodes.any { code ->
                    favLines.any { it.equals(code, ignoreCase = true) }
                }
                val matchesStop = favStops.any { stop ->
                    notice.title.contains(stop, ignoreCase = true) ||
                            notice.summary.contains(stop, ignoreCase = true)
                }
                matchesLine || matchesStop
            }

            if (shouldNotify) {
                sendNotification(ctx, notice)
                addNotified(ctx, noticeHash)
            }
        }
    }

    private fun sendNotification(ctx: Context, notice: TrafficNotice) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Infos trafic & Perturbations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alertes en cas de perturbations sur les lignes et arrêts"
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(notice.sourceUrl))
        val pi = PendingIntent.getActivity(
            ctx,
            UUID.randomUUID().hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setColor(android.graphics.Color.parseColor("#E65100")) // Orange
            .setContentTitle(notice.title)
            .setContentText(notice.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.summary))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(UUID.randomUUID().hashCode(), notif)
    }
}
