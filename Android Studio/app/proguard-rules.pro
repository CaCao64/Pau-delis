# ── OSMDroid ─────────────────────────────────────────────────────────────────
-keep class org.osmdroid.** { *; }

# ── OkHttp (utilisé en interne) ───────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Coroutines ────────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }

# ── Modèles JSON sérialisés (Alert, BusStop, BusLine…) ───────────────────────
-keep class com.pau.busapp.Alert { *; }
-keep class com.pau.busapp.BusStop { *; }
-keep class com.pau.busapp.BusLine { *; }
-keep class com.pau.busapp.Passage { *; }
-keep class com.pau.busapp.StopInfo { *; }

# ── Widget RemoteViews ────────────────────────────────────────────────────────
-keep class com.pau.busapp.WidgetListFactory { *; }
-keep class com.pau.busapp.WidgetListService { *; }
-keep class com.pau.busapp.StopsWidgetProvider { *; }
-keep class com.pau.busapp.WidgetConfigActivity { *; }

# ── Receivers / Services ──────────────────────────────────────────────────────
-keep class com.pau.busapp.AlertReceiver { *; }
-keep class com.pau.busapp.BootReceiver { *; }
-keep class com.pau.busapp.TrackingService { *; }

# ── Enums (nécessaires pour valueOf dans la désérialisation JSON) ─────────────
-keepclassmembers enum com.pau.busapp.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── SafeLinearLayout (référencé dans les layouts XML) ────────────────────────
-keep class com.pau.busapp.SafeLinearLayout { *; }

# ── Obfuscation maximale pour tout le reste ───────────────────────────────────
-repackageclasses 'x'
-allowaccessmodification
-optimizationpasses 5
-dontusemixedcaseclassnames
