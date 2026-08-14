package com.pau.busapp

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.pau.busapp.databinding.FragmentSettingsBinding
import org.json.JSONArray
import org.json.JSONObject

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> writeExport(uri) }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> readImport(uri) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        updateCount()
        updateLanguageLabel()

        b.btnChangeLanguage.setOnClickListener { showLanguagePicker() }
        b.btnNavStyle.setOnClickListener { showNavStylePicker() }

        b.btnTheme.setOnClickListener { showThemePicker() }
        b.btnNavConfig.setOnClickListener {
            (activity as? MainActivity)?.showFragment(NavConfigFragment(), true)
        }
        b.btnWidgetConfig.setOnClickListener {
            (activity as? MainActivity)?.showFragment(WidgetStopsFragment(), true)
        }
        updateNavStyleLabel()
        updateThemeLabel()
        b.btnExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "favoris_buspau.json")
            }
            exportLauncher.launch(intent)
        }

        // Emulation UI toggle: show/hide in map based on user preference
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        b.switchEmulation.isChecked = prefs.getBoolean("show_emulation", false)
        b.switchEmulation.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("show_emulation", isChecked).apply()
            activity?.recreate()
        }

        b.btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            importLauncher.launch(intent)
        }

        // Perturbations & Info Trafic config
        b.switchTrafficAlerts.isChecked = DisruptionAlertManager.isEnabled(ctx)
        b.switchTrafficAlerts.setOnCheckedChangeListener { _, isChecked ->
            DisruptionAlertManager.setEnabled(ctx, isChecked)
            if (isChecked) {
                (activity as? MainActivity)?.checkNotifPermissionThenOpenAlerts()
            }
            updateTrafficAlertsUI()
        }

        b.btnTrafficAlertsMode.setOnClickListener {
            val modes = arrayOf(
                getString(R.string.settings_traffic_mode_favorites_only),
                getString(R.string.settings_traffic_mode_all_lines)
            )
            val currentMode = DisruptionAlertManager.getMode(ctx)
            val selectedIdx = if (currentMode == "favorites") 0 else 1
            ModernDialogs.showChoice(
                context = ctx,
                title = getString(R.string.settings_traffic_alerts_mode_title),
                items = modes.toList(),
                selectedIndex = selectedIdx
            ) { which ->
                val chosenMode = if (which == 0) "favorites" else "all"
                DisruptionAlertManager.setMode(ctx, chosenMode)
                updateTrafficAlertsUI()
            }
        }
        updateTrafficAlertsUI()
    }

    private fun updateTrafficAlertsUI() {
        val ctx = context ?: return
        val enabled = DisruptionAlertManager.isEnabled(ctx)
        b.layoutTrafficAlertsMode.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        val mode = DisruptionAlertManager.getMode(ctx)
        b.tvTrafficAlertsMode.text = if (mode == "favorites") {
            getString(R.string.settings_traffic_mode_current_favorites_only)
        } else {
            getString(R.string.settings_traffic_mode_current_all_lines)
        }
    }

    private fun updateThemeLabel() {
        b.tvCurrentTheme.text = ThemeManager.get(requireContext()).label
    }

    private fun showThemePicker() {
        val themes  = AppTheme.values()
        val items   = themes.map { it.label }.toTypedArray()
        val current = themes.indexOf(ThemeManager.get(requireContext()))
        ModernDialogs.showChoice(
            context = requireContext(),
            title = getString(R.string.settings_theme_picker_title),
            items = items.toList(),
            selectedIndex = current
        ) { which ->
            ThemeManager.set(requireContext(), themes[which])
            updateThemeLabel()
        }
    }

    private fun updateNavStyleLabel() {
        val style = NavStyleManager.get(requireContext())
        b.tvCurrentNavStyle.text = style.label
    }

    private fun showNavStylePicker() {
        val styles  = NavStyle.values()
        val items   = styles.map { it.label }.toTypedArray()
        val current = styles.indexOf(NavStyleManager.get(requireContext()))
        ModernDialogs.showChoice(
            context = requireContext(),
            title = getString(R.string.settings_nav_style_picker_title),
            items = items.toList(),
            selectedIndex = current
        ) { which ->
            val chosen = styles[which]
            NavStyleManager.set(requireContext(), chosen)
            updateNavStyleLabel()
            (activity as? MainActivity)?.applyNavStyle(chosen)
            (activity as? MainActivity)?.setSelected(
                (activity as? MainActivity)?.currentSelectedId ?: R.id.nav_map
            )
        }
    }

    private fun updateLanguageLabel() {
        val code = LocaleHelper.getSaved(requireContext())
        val lang = LocaleHelper.languages.find { it.code == code }
        b.tvCurrentLanguage.text = "${lang?.flag ?: "ðŸŒ"}  ${lang?.label ?: code}"
    }

    private fun showLanguagePicker() {
        val ctx = requireContext()
        val content = layoutInflater.inflate(R.layout.dialog_language_picker, null, false)
        val container = content.findViewById<LinearLayout>(R.id.llLanguageList)
        val closeBtn = content.findViewById<MaterialButton>(R.id.btnCloseLanguage)
        val langs = LocaleHelper.languages
        val currentCode = LocaleHelper.getSaved(ctx)
        val density = resources.displayMetrics.density
        lateinit var dialog: AlertDialog
        var pendingLocaleCode: String? = null

        langs.forEachIndexed { index, lang ->
            val selected = lang.code == currentCode
            val card = MaterialCardView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { if (index > 0) it.topMargin = (10 * density).toInt() }
                radius = 18 * density
                cardElevation = 0f
                strokeWidth = if (selected) (2 * density).toInt() else (1 * density).toInt()
                setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, if (selected) R.color.green_primary else R.color.divider)
                ))
                setCardBackgroundColor(ContextCompat.getColor(ctx, if (selected) R.color.green_light else R.color.surface))
                isClickable = true
                isFocusable = true
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    (16 * density).toInt(),
                    (14 * density).toInt(),
                    (16 * density).toInt(),
                    (14 * density).toInt()
                )
            }

            val radio = android.widget.RadioButton(ctx).apply {
                isChecked = selected
                isClickable = false
                buttonTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.green_primary))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(radio)

            val label = android.widget.TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.marginStart = (14 * density).toInt()
                }
                text = "${lang.flag}  ${lang.label}"
                textSize = 17f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            }
            row.addView(label)

            card.addView(row)
            card.setOnClickListener {
                if (lang.code == currentCode) {
                    dialog.dismiss()
                    return@setOnClickListener
                }
                LocaleHelper.save(ctx, lang.code)
                pendingLocaleCode = lang.code
                dialog.dismiss()
            }

            container.addView(card)
        }

        dialog = AlertDialog.Builder(ctx)
            .setView(content)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                (resources.displayMetrics.heightPixels * 0.8f).toInt()
            )
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            pendingLocaleCode?.let { code ->
                pendingLocaleCode = null
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
            }
        }
        dialog.show()
    }

    private fun updateCount() {
        val ctx = requireContext()
        val stops = FavoritesManager.getFavStops(ctx)
        val lines = FavoritesManager.getFavLines(ctx)
        b.tvFavCount.text = getString(R.string.settings_fav_count, stops.size, lines.size)
    }

    private fun writeExport(uri: Uri) {
        val ctx = requireContext()
        try {
            val json = JSONObject().apply {
                // â”€â”€ Favoris â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                put("stops",      JSONArray(FavoritesManager.getFavStops(ctx).toList()))
                put("lines",      JSONArray(FavoritesManager.getFavLines(ctx).toList()))
                put("stopsOrder", JSONArray(FavoritesManager.getOrderedStops(ctx)))
                put("linesOrder", JSONArray(FavoritesManager.getOrderedLines(ctx)))
                // Bus favoris (Bus Ã  l'arrÃªt)
                put("favBuses",   JSONArray(FavoritesManager.getFavBuses(ctx)))
                // â”€â”€ Widget â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                put("widgetStops",        JSONArray(FavoritesManager.getWidgetStops(ctx)))
                put("widgetOrder",        JSONArray(WidgetOrderManager.getOrder(ctx)))
                put("widgetEnabled",      JSONArray(WidgetOrderManager.getEnabled(ctx).toList()))
                put("widgetLinesOrder",   JSONArray(WidgetLinesManager.getOrder(ctx)))
                put("widgetLinesEnabled", JSONArray(WidgetLinesManager.getEnabled(ctx).toList()))
                // â”€â”€ Alertes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                put("alerts", JSONArray(AlertManager.load(ctx).map { it.toJson() }))
                // â”€â”€ Alertes Perturbations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                put("trafficAlertsEnabled", DisruptionAlertManager.isEnabled(ctx))
                put("trafficAlertsMode", DisruptionAlertManager.getMode(ctx))
                // â”€â”€ ParamÃ¨tres â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                put("theme",      ThemeManager.get(ctx).name)
                put("navStyle",   NavStyleManager.get(ctx).name)
                put("language",   LocaleHelper.getSaved(ctx))
                put("navOrder",   JSONArray(NavConfigManager.getOrder(ctx)))
                put("navEnabled", JSONArray(NavConfigManager.getEnabled(ctx).toList()))
            }
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toString(2).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(ctx, getString(R.string.settings_export_ok), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, getString(R.string.settings_export_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun readImport(uri: Uri) {
        val ctx = requireContext()
        try {
            val raw = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return
            val json = JSONObject(raw)

            val errors  = mutableListOf<String>()
            val ignored = mutableListOf<String>()
            var countStops = 0; var countLines = 0

            val knownStopNames = AppData.busStops.map { it.name }.toSet()
            val knownLineNums  = AppData.busLines.map { it.number }.toSet()

            // â”€â”€ Favoris arrÃªts â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optJSONArray("stops")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val name = arr.getString(i)
                        if (name !in knownStopNames) ignored.add("ArrÃªt inconnu ignorÃ© : $name")
                        else if (!FavoritesManager.isStopFav(ctx, name)) { FavoritesManager.toggleStop(ctx, name); countStops++ }
                        else Unit
                    }.onFailure { ignored.add("ArrÃªt invalide Ã  l'index $i") }
                }
            }

            // â”€â”€ Favoris lignes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optJSONArray("lines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val num = arr.getString(i)
                        if (num !in knownLineNums) ignored.add("Ligne inconnue ignorÃ©e : $num")
                        else if (!FavoritesManager.isLineFav(ctx, num)) { FavoritesManager.toggleLine(ctx, num); countLines++ }
                        else Unit
                    }.onFailure { ignored.add("Ligne invalide Ã  l'index $i") }
                }
            }
            updateCount()

            // â”€â”€ Ordre favoris â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optJSONArray("stopsOrder")?.let { arr ->
                runCatching {
                    FavoritesManager.saveStopOrder(ctx, (0 until arr.length()).map { arr.getString(it) })
                }.onFailure { ignored.add("Ordre arrÃªts invalide") }
            }
            json.optJSONArray("linesOrder")?.let { arr ->
                runCatching {
                    FavoritesManager.saveLineOrder(ctx, (0 until arr.length()).map { arr.getString(it) })
                }.onFailure { ignored.add("Ordre lignes invalide") }
            }

            // â”€â”€ Bus favoris â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optJSONArray("favBuses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val key = arr.getString(i)
                        val parts = key.split("|")
                        if (parts.size >= 3 && parts[0] in knownStopNames && parts[1] in knownLineNums) {
                            if (!FavoritesManager.isBusFav(ctx, parts[0], parts[1], parts[2]))
                                FavoritesManager.toggleBus(ctx, parts[0], parts[1], parts[2])
                            else Unit
                        } else ignored.add("Bus favori ignorÃ© : $key")
                    }.onFailure { ignored.add("Bus favori invalide Ã  l'index $i") }
                }
            }

            // â”€â”€ Widget â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optJSONArray("widgetStops")?.let { arr ->
                runCatching {
                    val names = (0 until arr.length()).map { arr.getString(it) }
                        .filter { it in knownStopNames }
                    FavoritesManager.saveWidgetOrder(ctx, names)
                }.onFailure { ignored.add("Widget arrÃªts invalide") }
            }
            json.optJSONArray("widgetOrder")?.let { arr ->
                json.optJSONArray("widgetEnabled")?.let { arrE ->
                    runCatching {
                        val order   = (0 until arr.length()).map { arr.getString(it) }
                        val enabled = (0 until arrE.length()).map { arrE.getString(it) }.toSet()
                        WidgetOrderManager.save(ctx, order, enabled)
                    }.onFailure { ignored.add("Widget ordre invalide") }
                }
            }
            json.optJSONArray("widgetLinesOrder")?.let { arr ->
                json.optJSONArray("widgetLinesEnabled")?.let { arrE ->
                    runCatching {
                        val order   = (0 until arr.length()).map { arr.getString(it) }
                        val enabled = (0 until arrE.length()).map { arrE.getString(it) }.toSet()
                        WidgetLinesManager.saveOrder(ctx, order)
                        WidgetLinesManager.saveEnabled(ctx, enabled)
                    }.onFailure { ignored.add("Widget lignes invalide") }
                }
            }

            // â”€â”€ Alertes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optJSONArray("alerts")?.let { arr ->
                val existing = AlertManager.load(ctx)
                for (i in 0 until arr.length()) {
                    runCatching {
                        val a = Alert.fromJson(arr.getJSONObject(i))
                        if (a.stopName !in knownStopNames) { ignored.add("Alerte ignorÃ©e (arrÃªt inconnu : ${a.stopName})"); return@runCatching }
                        if (existing.none { it.id == a.id }) { existing.add(0, a); AlertManager.schedule(ctx, a) }
                    }.onFailure { ignored.add("Alerte invalide Ã  l'index $i") }
                }
                AlertManager.save(ctx, existing)
            }

            // â”€â”€ Alertes Perturbations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (json.has("trafficAlertsEnabled")) {
                DisruptionAlertManager.setEnabled(ctx, json.getBoolean("trafficAlertsEnabled"))
            }
            if (json.has("trafficAlertsMode")) {
                DisruptionAlertManager.setMode(ctx, json.getString("trafficAlertsMode"))
            }

            // â”€â”€ ThÃ¨me â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optString("theme").takeIf { it.isNotEmpty() }?.let { name ->
                runCatching { AppTheme.valueOf(name) }
                    .onSuccess { ThemeManager.set(ctx, it) }
                    .onFailure { ignored.add("ThÃ¨me inconnu ignorÃ© : $name") }
            }

            // â”€â”€ Style nav â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optString("navStyle").takeIf { it.isNotEmpty() }?.let { name ->
                runCatching { NavStyle.valueOf(name) }
                    .onSuccess { NavStyleManager.set(ctx, it); (activity as? MainActivity)?.applyNavStyle(it) }
                    .onFailure { ignored.add("Style navigation inconnu ignorÃ© : $name") }
            }

            // â”€â”€ Langue â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            json.optString("language").takeIf { it.isNotEmpty() }?.let { code ->
                if (LocaleHelper.languages.any { it.code == code }) LocaleHelper.save(ctx, code)
                else ignored.add("Langue inconnue ignorÃ©e : $code")
            }

            // â”€â”€ Onglets â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            val navOrderArr   = json.optJSONArray("navOrder")
            val navEnabledArr = json.optJSONArray("navEnabled")
            if (navOrderArr != null && navEnabledArr != null) {
                runCatching {
                    val knownTabIds = NavConfigManager.ALL_TABS.map { it.first }.toSet()
                    val order   = (0 until navOrderArr.length()).map { navOrderArr.getString(it) }
                    val enabled = (0 until navEnabledArr.length()).map { navEnabledArr.getString(it) }.toSet()
                    val badIds  = (order + enabled).filter { it !in knownTabIds }.distinct()
                    if (badIds.isNotEmpty()) ignored.add("Onglets inconnus ignorÃ©s : ${badIds.joinToString()}")
                    NavConfigManager.save(ctx, order.filter { it in knownTabIds }, enabled.filter { it in knownTabIds }.toSet())
                    (activity as? MainActivity)?.rebuildNav()
                }.onFailure { ignored.add("Onglets invalides ignorÃ©s") }
            }

            // â”€â”€ RÃ©sultat â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            val summary = "ImportÃ© : +$countStops arrÃªt(s), +$countLines ligne(s)"
            if (errors.isEmpty() && ignored.isEmpty()) {
                Toast.makeText(ctx, summary, Toast.LENGTH_LONG).show()
            } else {
                val detail = buildString {
                    if (ignored.isNotEmpty()) append("\n\nâ„¹ï¸ Ã‰lÃ©ments ignorÃ©s :\n${ignored.joinToString("\n")}")
                    if (errors.isNotEmpty()) append("\n\nâš ï¸ Erreurs :\n${errors.joinToString("\n")}")
                }
                ModernDialogs.showMessage(
                    context = ctx,
                    title = "Import terminÃ©",
                    message = "$summary$detail",
                    positiveText = "OK"
                )
            }
        } catch (e: Exception) {
            ModernDialogs.showMessage(
                context = ctx,
                title = "Fichier invalide",
                message = "Le fichier n'a pas pu Ãªtre lu.\n\n${e.message}",
                positiveText = "OK"
            )
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
