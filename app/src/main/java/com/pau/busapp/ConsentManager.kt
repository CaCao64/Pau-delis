package com.pau.busapp

import android.content.Context

object ConsentManager {
    private const val PREFS = "app_prefs"
    private const val KEY_ANALYTICS_CONSENT = "analytics_consent"

    fun hasDecision(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_ANALYTICS_CONSENT)

    fun isAnalyticsAllowed(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ANALYTICS_CONSENT, false)

    fun acceptAnalytics(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANALYTICS_CONSENT, true)
            .apply()
    }

    fun declineAnalytics(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ANALYTICS_CONSENT, false)
            .apply()
    }
}
