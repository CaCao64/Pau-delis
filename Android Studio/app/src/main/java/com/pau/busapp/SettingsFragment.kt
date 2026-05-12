package com.pau.busapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
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

        b.btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
            }
            importLauncher.launch(intent)
        }
    }

    private fun updateThemeLabel() {
        b.tvCurrentTheme.text = ThemeManager.get(requireContext()).label
    }

    private fun showThemePicker() {
        val themes  = AppTheme.values()
        val items   = themes.map { it.label }.toTypedArray()
        val current = themes.indexOf(ThemeManager.get(requireContext()))
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Thème")
            .setSingleChoiceItems(items, current) { dialog, which ->
                ThemeManager.set(requireContext(), themes[which])
                updateThemeLabel()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateNavStyleLabel() {
        val style = NavStyleManager.get(requireContext())
        b.tvCurrentNavStyle.text = style.label
    }

    private fun showNavStylePicker() {
        val styles  = NavStyle.values()
        val items   = styles.map { it.label }.toTypedArray()
        val current = styles.indexOf(NavStyleManager.get(requireContext()))
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Style de navigation")
            .setSingleChoiceItems(items, current) { dialog, which ->
                val chosen = styles[which]
                NavStyleManager.set(requireContext(), chosen)
                updateNavStyleLabel()
                (activity as? MainActivity)?.applyNavStyle(chosen)
                (activity as? MainActivity)?.setSelected(
                    (activity as? MainActivity)?.currentSelectedId ?: R.id.nav_map
                )
                dialog.dismiss()
            }
            .show()
    }

    private fun updateLanguageLabel() {
        val code = LocaleHelper.getSaved(requireContext())
        val lang = LocaleHelper.languages.find { it.code == code }
        b.tvCurrentLanguage.text = "${lang?.flag ?: "🌐"}  ${lang?.label ?: code}"
    }

    private fun showLanguagePicker() {
        val langs  = LocaleHelper.languages
        val items  = langs.map { "${it.flag}  ${it.label}" }.toTypedArray()
        val current = langs.indexOfFirst { it.code == LocaleHelper.getSaved(requireContext()) }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_choose_language))
            .setSingleChoiceItems(items, current) { dialog, which ->
                val chosen = langs[which]
                LocaleHelper.save(requireContext(), chosen.code)
                dialog.dismiss()
                requireActivity().let { act ->
                    val intent = act.intent
                    act.finish()
                    act.startActivity(intent)
                }
            }
            .show()
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
                // ── Favoris ──────────────────────────────────────────────────
                put("stops",      JSONArray(FavoritesManager.getFavStops(ctx).toList()))
                put("lines",      JSONArray(FavoritesManager.getFavLines(ctx).toList()))
                put("stopsOrder", JSONArray(FavoritesManager.getOrderedStops(ctx)))
                put("linesOrder", JSONArray(FavoritesManager.getOrderedLines(ctx)))
                // Bus favoris (Bus à l'arrêt)
                put("favBuses",   JSONArray(FavoritesManager.getFavBuses(ctx)))
                // ── Widget ───────────────────────────────────────────────────
                put("widgetStops",        JSONArray(FavoritesManager.getWidgetStops(ctx)))
                put("widgetOrder",        JSONArray(WidgetOrderManager.getOrder(ctx)))
                put("widgetEnabled",      JSONArray(WidgetOrderManager.getEnabled(ctx).toList()))
                put("widgetLinesOrder",   JSONArray(WidgetLinesManager.getOrder(ctx)))
                put("widgetLinesEnabled", JSONArray(WidgetLinesManager.getEnabled(ctx).toList()))
                // ── Alertes ──────────────────────────────────────────────────
                put("alerts", JSONArray(AlertManager.load(ctx).map { it.toJson() }))
                // ── Paramètres ───────────────────────────────────────────────
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

            // ── Favoris arrêts ────────────────────────────────────────────────
            json.optJSONArray("stops")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val name = arr.getString(i)
                        if (name !in knownStopNames) ignored.add("Arrêt inconnu ignoré : $name")
                        else if (!FavoritesManager.isStopFav(ctx, name)) { FavoritesManager.toggleStop(ctx, name); countStops++ }
                        else Unit
                    }.onFailure { ignored.add("Arrêt invalide à l'index $i") }
                }
            }

            // ── Favoris lignes ────────────────────────────────────────────────
            json.optJSONArray("lines")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val num = arr.getString(i)
                        if (num !in knownLineNums) ignored.add("Ligne inconnue ignorée : $num")
                        else if (!FavoritesManager.isLineFav(ctx, num)) { FavoritesManager.toggleLine(ctx, num); countLines++ }
                        else Unit
                    }.onFailure { ignored.add("Ligne invalide à l'index $i") }
                }
            }
            updateCount()

            // ── Ordre favoris ─────────────────────────────────────────────────
            json.optJSONArray("stopsOrder")?.let { arr ->
                runCatching {
                    FavoritesManager.saveStopOrder(ctx, (0 until arr.length()).map { arr.getString(it) })
                }.onFailure { ignored.add("Ordre arrêts invalide") }
            }
            json.optJSONArray("linesOrder")?.let { arr ->
                runCatching {
                    FavoritesManager.saveLineOrder(ctx, (0 until arr.length()).map { arr.getString(it) })
                }.onFailure { ignored.add("Ordre lignes invalide") }
            }

            // ── Bus favoris ───────────────────────────────────────────────────
            json.optJSONArray("favBuses")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching {
                        val key = arr.getString(i)
                        val parts = key.split("|")
                        if (parts.size >= 3 && parts[0] in knownStopNames && parts[1] in knownLineNums) {
                            if (!FavoritesManager.isBusFav(ctx, parts[0], parts[1], parts[2]))
                                FavoritesManager.toggleBus(ctx, parts[0], parts[1], parts[2])
                            else Unit
                        } else ignored.add("Bus favori ignoré : $key")
                    }.onFailure { ignored.add("Bus favori invalide à l'index $i") }
                }
            }

            // ── Widget ────────────────────────────────────────────────────────
            json.optJSONArray("widgetStops")?.let { arr ->
                runCatching {
                    val names = (0 until arr.length()).map { arr.getString(it) }
                        .filter { it in knownStopNames }
                    FavoritesManager.saveWidgetOrder(ctx, names)
                }.onFailure { ignored.add("Widget arrêts invalide") }
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

            // ── Alertes ───────────────────────────────────────────────────────
            json.optJSONArray("alerts")?.let { arr ->
                val existing = AlertManager.load(ctx)
                for (i in 0 until arr.length()) {
                    runCatching {
                        val a = Alert.fromJson(arr.getJSONObject(i))
                        if (a.stopName !in knownStopNames) { ignored.add("Alerte ignorée (arrêt inconnu : ${a.stopName})"); return@runCatching }
                        if (existing.none { it.id == a.id }) { existing.add(0, a); AlertManager.schedule(ctx, a) }
                    }.onFailure { ignored.add("Alerte invalide à l'index $i") }
                }
                AlertManager.save(ctx, existing)
            }

            // ── Thème ─────────────────────────────────────────────────────────
            json.optString("theme").takeIf { it.isNotEmpty() }?.let { name ->
                runCatching { AppTheme.valueOf(name) }
                    .onSuccess { ThemeManager.set(ctx, it) }
                    .onFailure { ignored.add("Thème inconnu ignoré : $name") }
            }

            // ── Style nav ─────────────────────────────────────────────────────
            json.optString("navStyle").takeIf { it.isNotEmpty() }?.let { name ->
                runCatching { NavStyle.valueOf(name) }
                    .onSuccess { NavStyleManager.set(ctx, it); (activity as? MainActivity)?.applyNavStyle(it) }
                    .onFailure { ignored.add("Style navigation inconnu ignoré : $name") }
            }

            // ── Langue ────────────────────────────────────────────────────────
            json.optString("language").takeIf { it.isNotEmpty() }?.let { code ->
                if (LocaleHelper.languages.any { it.code == code }) LocaleHelper.save(ctx, code)
                else ignored.add("Langue inconnue ignorée : $code")
            }

            // ── Onglets ───────────────────────────────────────────────────────
            val navOrderArr   = json.optJSONArray("navOrder")
            val navEnabledArr = json.optJSONArray("navEnabled")
            if (navOrderArr != null && navEnabledArr != null) {
                runCatching {
                    val knownTabIds = NavConfigManager.ALL_TABS.map { it.first }.toSet()
                    val order   = (0 until navOrderArr.length()).map { navOrderArr.getString(it) }
                    val enabled = (0 until navEnabledArr.length()).map { navEnabledArr.getString(it) }.toSet()
                    val badIds  = (order + enabled).filter { it !in knownTabIds }.distinct()
                    if (badIds.isNotEmpty()) ignored.add("Onglets inconnus ignorés : ${badIds.joinToString()}")
                    NavConfigManager.save(ctx, order.filter { it in knownTabIds }, enabled.filter { it in knownTabIds }.toSet())
                    (activity as? MainActivity)?.rebuildNav()
                }.onFailure { ignored.add("Onglets invalides ignorés") }
            }

            // ── Résultat ──────────────────────────────────────────────────────
            val summary = "Importé : +$countStops arrêt(s), +$countLines ligne(s)"
            if (errors.isEmpty() && ignored.isEmpty()) {
                Toast.makeText(ctx, summary, Toast.LENGTH_LONG).show()
            } else {
                val detail = buildString {
                    if (ignored.isNotEmpty()) append("\n\nℹ️ Éléments ignorés :\n${ignored.joinToString("\n")}")
                    if (errors.isNotEmpty()) append("\n\n⚠️ Erreurs :\n${errors.joinToString("\n")}")
                }
                AlertDialog.Builder(ctx)
                    .setTitle("Import terminé")
                    .setMessage("$summary$detail")
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            AlertDialog.Builder(ctx)
                .setTitle("Fichier invalide")
                .setMessage("Le fichier n'a pas pu être lu.\n\n${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
