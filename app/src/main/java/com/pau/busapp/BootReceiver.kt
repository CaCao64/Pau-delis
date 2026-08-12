package com.pau.busapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val alerts = AlertManager.load(ctx)
            AlertManager.scheduleAll(ctx, alerts)
        }
    }
}
