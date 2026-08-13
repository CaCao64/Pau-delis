package com.pau.busapp

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.graphics.Color
import android.appwidget.AppWidgetManager

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetListFactory(applicationContext, intent)
}

class WidgetListFactory(
    private val ctx: android.content.Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        val lock = Any()
        val stopNames     = mutableListOf<String>()
        val stopTimes     = mutableListOf<String>()
        val stopKeys      = mutableListOf<String>()
        val stopTextSizes = mutableListOf<Int>()
    }

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDataSetChanged() {}
    override fun getCount() = synchronized(lock) { stopNames.size }
    override fun getItemId(pos: Int) = pos.toLong()
    override fun hasStableIds() = true
    override fun getViewTypeCount() = 1
    override fun getLoadingView() = null

    override fun getViewAt(pos: Int): RemoteViews = synchronized(lock) {
        if (pos >= stopNames.size) return RemoteViews(ctx.packageName, R.layout.widget_stop_row)
        val views = RemoteViews(ctx.packageName, R.layout.widget_stop_row)
        val textSize = stopTextSizes.getOrElse(pos) { 12 }.toFloat()
        views.setTextViewText(R.id.row_stop_name, stopNames[pos])
        views.setTextViewTextSize(R.id.row_stop_name, android.util.TypedValue.COMPLEX_UNIT_SP, textSize + 1)
        views.setTextViewText(R.id.row_times, stopTimes.getOrElse(pos) { "Chargement..." })
        views.setTextViewTextSize(R.id.row_times, android.util.TypedValue.COMPLEX_UNIT_SP, textSize)

        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isDark = WidgetConfigActivity.isDark(ctx, widgetId)
        val stopNameColor = if (isDark) Color.WHITE else Color.BLACK
        val timesColor = if (isDark) Color.parseColor("#CCFFD4") else Color.parseColor("#1B5E20")
        views.setTextColor(R.id.row_stop_name, stopNameColor)
        views.setTextColor(R.id.row_times, timesColor)

        // Fill-in intent pour ouvrir l'arrêt dans l'app
        val stopKey = stopKeys.getOrElse(pos) { stopNames[pos] }
        val fillIntent = Intent().apply {
            putExtra(StopsWidgetProvider.EXTRA_STOP_NAME, stopKey)
        }
        views.setOnClickFillInIntent(R.id.row_stop_name, fillIntent)
        views.setOnClickFillInIntent(R.id.row_times, fillIntent)
        return views
    }
}
