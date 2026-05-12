package com.pau.busapp

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class WidgetListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetListFactory(applicationContext, intent)
}

class WidgetListFactory(
    private val ctx: android.content.Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    companion object {
        val stopNames     = mutableListOf<String>()
        val stopTimes     = mutableListOf<String>()
        val stopKeys      = mutableListOf<String>()
        val stopTextSizes = mutableListOf<Int>()
    }

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDataSetChanged() {}
    override fun getCount() = stopNames.size
    override fun getItemId(pos: Int) = pos.toLong()
    override fun hasStableIds() = true
    override fun getViewTypeCount() = 1
    override fun getLoadingView() = null

    override fun getViewAt(pos: Int): RemoteViews {
        if (pos >= stopNames.size) return RemoteViews(ctx.packageName, R.layout.widget_stop_row)
        val views = RemoteViews(ctx.packageName, R.layout.widget_stop_row)
        val textSize = stopTextSizes.getOrElse(pos) { 12 }.toFloat()
        views.setTextViewText(R.id.row_stop_name, stopNames[pos])
        views.setTextViewTextSize(R.id.row_stop_name, android.util.TypedValue.COMPLEX_UNIT_SP, textSize + 1)
        views.setTextViewText(R.id.row_times, stopTimes.getOrElse(pos) { "Chargement..." })
        views.setTextViewTextSize(R.id.row_times, android.util.TypedValue.COMPLEX_UNIT_SP, textSize)

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
