package com.pau.busapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.Event
import com.google.firebase.analytics.FirebaseAnalytics.Param
import com.google.firebase.FirebaseApp

object AnalyticsTracker {

    private const val TAG = "FirebaseAnalytics"
    private const val MAX_VALUE_LENGTH = 100

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        resolve(context.applicationContext)
    }

    fun screenView(context: Context, screenName: String, screenClass: String = screenName) {
        val params = bundleOf(
            Param.SCREEN_NAME to safeValue(screenName),
            Param.SCREEN_CLASS to safeValue(screenClass),
            "screen_name" to safeValue(screenName),
            "screen_class" to safeValue(screenClass)
        )
        logEvent(context, Event.SCREEN_VIEW, params)
    }

    fun trackTab(context: Context, tabName: String, action: String = "open") {
        track(
            context = context,
            eventName = "tab_select",
            elementName = tabName,
            action = action,
            screenName = "main_nav",
            extra = mapOf("tab_name" to tabName)
        )
    }

    fun trackAction(
        context: Context,
        action: String,
        elementName: String,
        screenName: String? = null,
        extra: Map<String, String> = emptyMap()
    ) {
        val params = bundleOf(
            "action" to safeValue(action),
            "element_name" to safeValue(elementName)
        ).apply {
            screenName?.let { putString("screen_name", safeValue(it)) }
            extra.forEach { (key, value) -> putString(key, safeValue(value)) }
        }
        logEvent(context, "ui_action", params)
    }

    fun search(context: Context, query: String, screenName: String? = "Recherche", resultCount: Int? = null) {
        val params = bundleOf(
            "action" to "submit",
            "search_term" to safeValue(query),
            "query_length" to query.length.toString()
        ).apply {
            screenName?.let { putString("screen_name", safeValue(it)) }
            resultCount?.let { putString("result_count", it.toString()) }
        }
        logEvent(context, "search_submit", params)
    }

    fun openContent(
        context: Context,
        contentType: String,
        contentName: String,
        screenName: String? = null,
        extra: Map<String, String> = emptyMap()
    ) {
        track(
            context = context,
            eventName = "open_content",
            elementName = contentName,
            action = "open",
            screenName = screenName ?: contentType,
            extra = buildMap {
                put("content_type", contentType)
                putAll(extra)
            }
        )
    }

    private fun track(
        context: Context,
        eventName: String,
        elementName: String,
        action: String,
        screenName: String? = null,
        extra: Map<String, String> = emptyMap()
    ) {
        val params = bundleOf(
            "action" to safeValue(action),
            "element_name" to safeValue(elementName)
        ).apply {
            screenName?.let { putString("screen_name", safeValue(it)) }
            extra.forEach { (key, value) -> putString(key, safeValue(value)) }
        }
        logEvent(context, eventName, params)
    }

    private fun logEvent(context: Context, name: String, params: Bundle) {
        val firebase = resolve(context.applicationContext)
        if (firebase != null) {
            firebase.logEvent(name, params)
        } else {
            Log.d(TAG, "$name $params")
        }
    }

    private fun resolve(context: Context): FirebaseAnalytics? {
        analytics?.let { return it }
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (FirebaseApp.getApps(context).isEmpty()) {
                null
            } else {
                FirebaseAnalytics.getInstance(context).also { analytics = it }
            }
        }.getOrNull()
    }

    private fun safeValue(value: String): String {
        val normalized = value.replace('\n', ' ').replace('\r', ' ').trim()
        return if (normalized.length <= MAX_VALUE_LENGTH) normalized else normalized.take(MAX_VALUE_LENGTH)
    }

    private fun bundleOf(vararg pairs: Pair<String, String>): Bundle = Bundle().apply {
        pairs.forEach { putString(it.first, it.second) }
    }
}
