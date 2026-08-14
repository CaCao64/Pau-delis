package com.pau.busapp

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.osmdroid.config.Configuration
import java.io.File

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Appliquer le thème sauvegardé avant tout affichage
        ThemeManager.apply(this)
        val allowed = ConsentManager.hasDecision(this) && ConsentManager.isAnalyticsAllowed(this)
        AnalyticsTracker.applyConsent(this, allowed)
        val prefs = getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(applicationContext, prefs)
        Configuration.getInstance().userAgentValue = packageName
        val base = File(cacheDir, "osmdroid")
        if (!base.exists()) base.mkdirs()
        Configuration.getInstance().osmdroidBasePath = base
        val tiles = File(base, "tiles")
        if (!tiles.exists()) tiles.mkdirs()
        Configuration.getInstance().osmdroidTileCache = tiles
    }
}
