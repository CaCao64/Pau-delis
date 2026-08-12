package com.pau.busapp

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class WidgetConfigActivity : AppCompatActivity() {

    companion object {
        private const val PREFS = "widget_prefs"
        private const val KEY_THEME = "widget_theme_"
        private const val KEY_OPACITY = "widget_opacity_"

        fun getTheme(ctx: Context, widgetId: Int): String =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME + widgetId, "system") ?: "system"

        fun getOpacity(ctx: Context, widgetId: Int): Int =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_OPACITY + widgetId, 220)

        fun isDark(ctx: Context, widgetId: Int): Boolean = when (getTheme(ctx, widgetId)) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightMode = ctx.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var currentOpacity = 220

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        currentOpacity = getOpacity(this, widgetId)
        val savedTheme = getTheme(this, widgetId)

        setContentView(R.layout.activity_widget_config)

        val rgTheme = findViewById<RadioGroup>(R.id.rg_theme)
        val rbSystem = findViewById<RadioButton>(R.id.rb_system)
        val rbDark = findViewById<RadioButton>(R.id.rb_dark)
        val rbLight = findViewById<RadioButton>(R.id.rb_light)
        val sbOpacity = findViewById<SeekBar>(R.id.sb_opacity)
        val tvLabel = findViewById<TextView>(R.id.tv_opacity_label)
        val previewBg = findViewById<android.view.View>(R.id.preview_bg)

        when (savedTheme) {
            "dark" -> rbDark.isChecked = true
            "light" -> rbLight.isChecked = true
            else -> rbSystem.isChecked = true
        }
        sbOpacity.progress = currentOpacity
        tvLabel.text = "${(currentOpacity * 100) / 255}%"
        updatePreview(previewBg, currentOpacity, savedTheme)

        rgTheme.setOnCheckedChangeListener { _, _ ->
            updatePreview(previewBg, sbOpacity.progress, selectedTheme(rbSystem, rbDark))
        }
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                currentOpacity = progress
                tvLabel.text = "${(progress * 100) / 255}%"
                updatePreview(previewBg, progress, selectedTheme(rbSystem, rbDark))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<android.widget.Button>(R.id.btn_save).setOnClickListener {
            val theme = selectedTheme(rbSystem, rbDark)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_THEME + widgetId, theme)
                .putInt(KEY_OPACITY + widgetId, currentOpacity)
                .apply()
            StopsWidgetProvider.requestUpdate(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }

    private fun selectedTheme(rbSystem: RadioButton, rbDark: RadioButton): String = when {
        rbDark.isChecked -> "dark"
        else -> if (rbSystem.isChecked) "system" else "light"
    }

    private fun updatePreview(view: android.view.View, opacity: Int, theme: String) {
        val dark = when (theme) {
            "dark" -> true
            "light" -> false
            else -> {
                val nightMode = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        val base = if (dark) Color.parseColor("#1B5E20") else Color.parseColor("#E8F5E9")
        view.setBackgroundColor(Color.argb(opacity, Color.red(base), Color.green(base), Color.blue(base)))
        val textColor = if (dark) Color.WHITE else Color.BLACK
        (view as? LinearLayout)?.let {
            (it.getChildAt(0) as? TextView)?.setTextColor(textColor)
            (it.getChildAt(1) as? TextView)?.setTextColor(textColor)
        }
    }
}
