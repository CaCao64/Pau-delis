package com.pau.busapp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pau.busapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    var currentSelectedId = R.id.nav_map
    var pendingScrollStopName: String? = null
    var pendingScrollLineNumber: String? = null

    // Lanceur permission notification — stocké en attendant le résultat
    private var pendingAlertStopName: String? = null
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingAlertStopName != null) {
                openAddAlert(pendingAlertStopName ?: "")
            } else {
                // Vient du clic onglet Alertes
                fadeTransition {
                    clearBack()
                    commitShowFragment(AlertsFragment(), false)
                }
                setSelected(R.id.nav_alerts)
            }
        } else {
            showPermissionDeniedDialog(getString(R.string.permission_notif_denied))
        }
        pendingAlertStopName = null
    }

    private val mapFragment: MapFragment by lazy {
        supportFragmentManager.findFragmentByTag("MAP") as? MapFragment ?: MapFragment()
    }

    internal data class NavItem(
        val containerId: Int,
        val iconId: Int,
        val labelId: Int,
        val pillId: Int,
        val fragment: () -> Fragment
    )

    // navItems est maintenant dynamique via rebuildNav()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.apply(base))
    }

    override fun recreate() {
        super.recreate()
        overridePendingTransition(R.anim.theme_fade_in, R.anim.theme_fade_out)
    }

    override fun onResume() {
        super.onResume()
        overridePendingTransition(R.anim.theme_fade_in, R.anim.theme_fade_out)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_tab", currentSelectedId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, mapFragment, "MAP")
                .commitNow()
        }

        initNavViewCache()
        setupNavListeners()
        // Gestion du bouton retour
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        rebuildNav()
        applyNavStyle(NavStyleManager.get(this))
        setApiOnline(true)

        val alerts = AlertManager.load(this)
        AlertManager.scheduleAll(this, alerts)
        AlertManager.cleanupPastTodayAlerts(this)

        if (savedInstanceState == null) {
            val tx = supportFragmentManager.beginTransaction()
            supportFragmentManager.fragments.forEach { f ->
                if (f !== mapFragment && f.isAdded && !f.isHidden) tx.hide(f)
            }
            tx.show(mapFragment).commitAllowingStateLoss()
            setSelected(R.id.nav_map)
        } else {
            // Restaurer l'onglet actif après recreate() (changement de thème/langue)
            val savedTab = savedInstanceState.getInt("selected_tab", R.id.nav_map)
            setSelected(savedTab)
        }


        handleWidgetIntent(intent)

        if (!TutorialManager.isDone(this)) {
            val rootView = binding.root as? ViewGroup ?: return
            TutorialOverlay(this, rootView) {}.show()
        }
    }

    fun showTutorial(tabId: Int? = null) {
        val rootView = binding.root as? ViewGroup ?: return
        val overlay = TutorialOverlay(this, rootView) {}
        tabId?.let {
            val stepIndex = when (it) {
                R.id.nav_map -> 1
                R.id.nav_favs -> 4
                R.id.nav_search -> 6
                R.id.nav_alerts -> 7
                R.id.nav_settings -> 10
                else -> 0
            }
            overlay.setCurrentStep(stepIndex)
        }
        overlay.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val key = intent?.getStringExtra(StopsWidgetProvider.EXTRA_STOP_NAME) ?: return
        val openMode = intent.getStringExtra("open_mode")
        val highlightLine = intent?.getStringExtra("highlight_line")
        // Clés préfixées : "stop:NomArrêt", "bus:NomArrêt|ligne|dest", "ligne:T3"
        val stopName = when {
            key.startsWith(WidgetOrderManager.PREFIX_STOP) -> key.removePrefix(WidgetOrderManager.PREFIX_STOP)
            key.startsWith(WidgetOrderManager.PREFIX_BUS)  -> key.removePrefix(WidgetOrderManager.PREFIX_BUS).substringBefore("|")
            else -> key  // ancienne clé sans préfixe ou ligne — on tente tel quel
        }
        val stop = AppData.busStops.find { it.name == stopName } ?: return
        if (openMode == "stop_list") {
            pendingScrollStopName = stopName
            AnalyticsTracker.openContent(this, "notification_stop_list", stopName, "Arrêts", mapOf("highlight_line" to (highlightLine ?: "")))
            fadeTransition {
                clearBack()
                commitShowFragment(StopListFragment(), false)
            }
            setSelected(R.id.nav_stops)
        } else {
            pendingScrollStopName = stopName
            AnalyticsTracker.openContent(this, "widget_stop_detail", stopName, "Carte", mapOf("highlight_line" to (highlightLine ?: "")))
            showFragment(DetailsFragment.newInstance(stop, highlightLine), true)
        }
    }

    fun applyNavStyle(style: NavStyle) {
        val nav = binding.bottomNav

        navItems.forEach { item ->
            val container = nav.findViewById<LinearLayout>(item.containerId) ?: return@forEach
            val bar = container.findViewWithTag<View>("top_bar_${item.containerId}")
            bar?.let { container.removeView(it) }
        }

        when (style) {
            NavStyle.A -> {
                nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).also { it.height = dpToPx(56) }
                navItems.forEach { item ->
                    nav.findViewById<TextView>(item.labelId)?.visibility = View.GONE
                }
                setSelected(currentSelectedId, trackAnalytics = false)
            }
            NavStyle.B -> {
                nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).also { it.height = dpToPx(64) }
                navItems.forEach { item ->
                    nav.findViewById<TextView>(item.labelId)?.apply {
                        visibility = View.VISIBLE
                        textSize = 11f
                    }
                    nav.findViewById<View>(item.pillId)?.background = null
                }
                setSelected(currentSelectedId, trackAnalytics = false)
            }
            NavStyle.C -> {
                nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).also { it.height = dpToPx(64) }
                setSelected(currentSelectedId, trackAnalytics = false)
            }
            NavStyle.D -> {
                nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).also { it.height = dpToPx(52) }
                navItems.forEach { item ->
                    nav.findViewById<ImageView>(item.iconId)?.let { icon ->
                        icon.layoutParams = icon.layoutParams.also { it.width = dpToPx(28); it.height = dpToPx(28) }
                    }
                    nav.findViewById<TextView>(item.labelId)?.visibility = View.GONE
                    nav.findViewById<View>(item.pillId)?.background = null
                }
                setSelected(currentSelectedId, trackAnalytics = false)
            }
            NavStyle.E -> {
                nav.layoutParams = (nav.layoutParams as LinearLayout.LayoutParams).also { it.height = dpToPx(64) }
                nav.background = ContextCompat.getDrawable(this, R.drawable.bg_nav_floating)
                nav.elevation = 12f
                navItems.forEach { item ->
                    nav.findViewById<TextView>(item.labelId)?.visibility = View.GONE
                    nav.findViewById<View>(item.pillId)?.background = null
                }
                setSelected(currentSelectedId, trackAnalytics = false)
            }
        }
    }

    // Mapping id texte → NavItem
    private val tabIdMap = mapOf(
        "map"      to NavItem(R.id.nav_map,      R.id.nav_map_icon,      R.id.nav_map_label,      R.id.nav_map_pill)      { mapFragment },
        "favs"     to NavItem(R.id.nav_favs,     R.id.nav_favs_icon,     R.id.nav_favs_label,     R.id.nav_favs_pill)     { FavoritesFragment() },
        "search"   to NavItem(R.id.nav_search,   R.id.nav_search_icon,   R.id.nav_search_label,   R.id.nav_search_pill)   { SearchFragment() },
        "alerts"   to NavItem(R.id.nav_alerts,   R.id.nav_alerts_icon,   R.id.nav_alerts_label,   R.id.nav_alerts_pill)   { AlertsFragment() },
        "stops"    to NavItem(R.id.nav_stops,    R.id.nav_stops_icon,    R.id.nav_stops_label,    R.id.nav_stops_pill)    { StopListFragment() },
        "lines"    to NavItem(R.id.nav_lines,    R.id.nav_lines_icon,    R.id.nav_lines_label,    R.id.nav_lines_pill)    { LinesFragment() },
        "settings" to NavItem(R.id.nav_settings, R.id.nav_settings_icon, R.id.nav_settings_label, R.id.nav_settings_pill) { SettingsFragment() }
    )
    private val moreItem = NavItem(R.id.nav_more, R.id.nav_more_icon, R.id.nav_more_label, R.id.nav_more_pill) { MoreFragment() }

    private lateinit var navViewCache: Map<Int, View>
    private var lastTrackedTabId: Int? = null

    private fun initNavViewCache() {
        val allItems = tabIdMap.values.toList() + moreItem
        navViewCache = allItems.mapNotNull { item ->
            findViewById<View>(item.containerId)?.let { item.containerId to it }
        }.toMap()
    }

    private fun setupNavListeners() {
        (tabIdMap.values + moreItem).forEach { item ->
            findViewById<View>(item.containerId).setOnClickListener {
                // Onglet Alertes → vérifier permission notif d'abord
                if (item.containerId == R.id.nav_alerts) {
                    checkNotifPermissionThenOpenAlerts(); return@setOnClickListener
                }
                fadeTransition {
                    clearBack()
                    if (item.fragment() === mapFragment) {
                        val tx = supportFragmentManager.beginTransaction()
                        supportFragmentManager.fragments.forEach { f ->
                            if (f !== mapFragment && f.isAdded && !f.isHidden) tx.hide(f)
                        }
                        tx.show(mapFragment)
                        tx.commitNowAllowingStateLoss()
                    } else {
                        commitShowFragment(item.fragment(), false)
                    }
                }
                setSelected(item.containerId)
            }
        }
    }

    fun rebuildNav() {
        val visibleIds = NavConfigManager.getVisibleTabs(this)
        val nav        = binding.bottomNav

        navViewCache.values.forEach { v ->
            (v.parent as? ViewGroup)?.removeView(v)
        }

        visibleIds.forEach { id ->
            val item = tabIdMap[id] ?: return@forEach
            navViewCache[item.containerId]?.let { v ->
                v.visibility = View.VISIBLE
                nav.addView(v)
            }
        }
        navViewCache[moreItem.containerId]?.let { v ->
            v.visibility = View.VISIBLE
            nav.addView(v)
        }

        _currentNavItems = visibleIds.mapNotNull { tabIdMap[it] } + moreItem
    }

    private var _currentNavItems: List<NavItem> = emptyList()
    internal val navItems: List<NavItem> get() = _currentNavItems

    fun setSelected(selectedId: Int, trackAnalytics: Boolean = true) {
        currentSelectedId = selectedId
        val tabName = when (selectedId) {
            R.id.nav_map -> "Carte"
            R.id.nav_favs -> "Favoris"
            R.id.nav_search -> "Recherche"
            R.id.nav_alerts -> "Alertes"
            R.id.nav_stops -> "Arrêts"
            R.id.nav_lines -> "Lignes"
            R.id.nav_settings -> "Paramètres"
            R.id.nav_more -> "Plus"
            else -> "Inconnu ($selectedId)"
        }
        if (trackAnalytics && lastTrackedTabId != selectedId) {
            AnalyticsTracker.trackTab(this, tabName)
            AnalyticsTracker.screenView(this, tabName, "MainActivity")
            lastTrackedTabId = selectedId
        }

        val style         = NavStyleManager.get(this)
        val activeColor   = ContextCompat.getColor(this, R.color.green_primary)
        val inactiveColor = Color.parseColor("#9E9E9E")

        val nav = binding.bottomNav
        navItems.forEach { item ->
            val isActive = item.containerId == selectedId
            val color    = if (isActive) activeColor else inactiveColor

            nav.findViewById<ImageView>(item.iconId)?.setColorFilter(color)

            val pill = nav.findViewById<View>(item.pillId)
            when (style) {
                NavStyle.A -> pill?.background = if (isActive)
                    ContextCompat.getDrawable(this, R.drawable.bg_nav_pill) else null
                else -> pill?.background = null
            }

            val label = nav.findViewById<TextView>(item.labelId)
            when (style) {
                NavStyle.A, NavStyle.D -> label?.visibility = View.GONE
                NavStyle.C -> {
                    label?.visibility = if (isActive) View.VISIBLE else View.GONE
                    label?.setTextColor(activeColor)
                    label?.setTypeface(null, android.graphics.Typeface.BOLD)
                }
                NavStyle.B, NavStyle.E -> {
                    label?.visibility = View.VISIBLE
                    label?.setTextColor(color)
                    label?.setTypeface(null, if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
            }

            if (style == NavStyle.B) {
                val container = nav.findViewById<LinearLayout>(item.containerId) ?: return@forEach
                val barTag    = "top_bar_${item.containerId}"
                var bar       = container.findViewWithTag<View>(barTag)
                if (bar == null) {
                    bar = View(this).apply {
                        tag            = barTag
                        layoutParams   = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(3)
                        )
                    }
                    container.addView(bar, 0)
                }
                bar.setBackgroundColor(if (isActive) activeColor else Color.TRANSPARENT)
            }
        }
    }

    // Texte et couleur du dernier statut API — mémorisé pour les fragments qui s'ouvrent après
    private var lastApiText  = ""
    private var lastApiColor = 0
    private var lastApiBgColor = 0
    private var lastApiOnline = true

    fun setApiOnline(online: Boolean, error: String? = null) {
        val greenColor  = 0xFF00843D.toInt()
        val redColor    = 0xFFF44336.toInt()
        val blueColor   = 0xFF1565C0.toInt()
        val is500 = error?.contains("HTTP 500") == true
        val cleanError = if (!online) {
            val e = error?.lowercase() ?: ""
            when {
                is500 -> getString(R.string.api_offline_http500)
                e.contains("unable to resolve") || e.contains("no address") ||
                e.contains("network") || e.contains("connect") || e.contains("timeout") ||
                e.contains("unreachable") || e.contains("sockettimeout") || e.contains("failed to connect")
                -> getString(R.string.api_offline_network)
                else -> error ?: getString(R.string.api_offline)
            }
        } else null
        lastApiText    = if (online) "● ${getString(R.string.api_online).removePrefix("● ")}" else "● $cleanError"
        lastApiColor   = if (online) greenColor else if (is500) blueColor else redColor
        lastApiBgColor = if (online) 0xCC00843D.toInt() else if (is500) 0xCC1565C0.toInt() else 0xCCF44336.toInt()
        lastApiOnline  = online
        refreshApiStatusViews()
    }

    fun refreshApiStatusViews() {
        if (lastApiText.isEmpty()) return

        // Bandeau carte — chercher dans le fragment MapFragment visible
        supportFragmentManager.fragments.filterIsInstance<MapFragment>().firstOrNull()?.view
            ?.findViewById<TextView>(R.id.tv_map_api_status)?.apply {
                visibility = View.VISIBLE
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(lastApiBgColor)
                text = lastApiText
            }

        // Indicateur favoris
        supportFragmentManager.fragments.filterIsInstance<FavoritesFragment>().firstOrNull()?.view
            ?.findViewById<TextView>(R.id.tv_api_status_favs)?.apply {
                visibility = View.VISIBLE
                setTextColor(lastApiColor)
                text = lastApiText
            }

        // Indicateur arrêts
        supportFragmentManager.fragments.filterIsInstance<StopListFragment>().firstOrNull()?.view
            ?.findViewById<TextView>(R.id.tv_api_status_stops)?.apply {
                visibility = View.VISIBLE
                setTextColor(lastApiColor)
                text = lastApiText
            }

        // Indicateur lignes
        supportFragmentManager.fragments.filterIsInstance<LinesFragment>().firstOrNull()?.view
            ?.findViewById<TextView>(R.id.tv_api_status_lines)?.apply {
                visibility = View.VISIBLE
                setTextColor(lastApiColor)
                text = lastApiText
            }
    }

    fun requestNotifThenAddAlert(stopName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            openAddAlert(stopName); return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            openAddAlert(stopName); return
        }
        pendingAlertStopName = stopName
        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    fun showPermissionDeniedDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(getString(R.string.permission_settings)) { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                })
            }
            .setNegativeButton(getString(R.string.permission_close), null)
            .show()
    }

    fun checkNotifPermissionThenOpenAlerts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission ok → ouvrir directement
            fadeTransition {
                clearBack()
                commitShowFragment(AlertsFragment(), false)
            }
            setSelected(R.id.nav_alerts)
            return
        }
        // Pas de permission → demander
        pendingAlertStopName = null
        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    fun openDetails(stop: BusStop) {
        AnalyticsTracker.openContent(this, "stop_detail", stop.name, "main")
        showFragment(DetailsFragment.newInstance(stop, null), true)
    }

    fun openLineDetail(line: BusLine) {
        AnalyticsTracker.openContent(this, "line_detail", line.number, "main")
        showFragment(LineDetailFragment.newInstance(line), true)
    }

    fun openAddAlert(stopName: String = "") {
        AnalyticsTracker.trackAction(this, "open", "add_alert_dialog", "Alertes", mapOf("stop_name" to stopName))
        AddAlertDialog.newInstance(stopName).show(supportFragmentManager, "add_alert")
    }
    fun refreshAlerts() {
        (supportFragmentManager.findFragmentById(R.id.fragment_container) as? AlertsFragment)?.refresh()
    }

    fun locateOnMap(lat: Double, lon: Double) {
        AnalyticsTracker.trackAction(this, "locate", "map_marker", "Carte", mapOf("lat" to lat.toString(), "lon" to lon.toString()))
        mapFragment.zoomTo(lat, lon)
        fadeTransition {
            clearBack()
            val tx = supportFragmentManager.beginTransaction()
            supportFragmentManager.fragments.forEach { f ->
                if (f !== mapFragment && f.isAdded && !f.isHidden) tx.hide(f)
            }
            tx.show(mapFragment)
            tx.commitNowAllowingStateLoss()
        }
        setSelected(R.id.nav_map)
    }

    private fun clearBack() {
        supportFragmentManager.popBackStackImmediate(
            null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    fun showFragment(f: Fragment, backStack: Boolean) {
        commitShowFragment(f, backStack)
    }

    private fun commitShowFragment(f: Fragment, backStack: Boolean) {
        val tx = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.forEach { existing ->
            if (existing.isAdded && !existing.isHidden && existing.view != null) tx.hide(existing)
        }
        if (f.isAdded) tx.show(f) else tx.add(R.id.fragment_container, f)
        if (backStack) {
            tx.addToBackStack(null)
            tx.commitAllowingStateLoss()
            supportFragmentManager.executePendingTransactions()
        } else {
            tx.commitNowAllowingStateLoss()
        }
    }

    private fun fadeTransition(action: () -> Unit) {
        val container = findViewById<View>(R.id.fragment_container)
        container.animate().cancel()
        container.animate()
            .alpha(0f).setDuration(120)
            .withEndAction {
                action()
                container.animate().alpha(1f).setDuration(180).start()
            }.start()
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
