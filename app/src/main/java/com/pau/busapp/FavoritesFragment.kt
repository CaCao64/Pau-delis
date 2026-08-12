package com.pau.busapp

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.*
import android.widget.*
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentFavoritesBinding
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {
    private var _b: FragmentFavoritesBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentFavoritesBinding.inflate(i, c, false); return b.root
    }

    private val defaultBtns = mutableListOf<Pair<String, TextView>>()
    private val stopOrder = mutableListOf<String>()
    private val renderJobs = mutableListOf<kotlinx.coroutines.Job>()
    private var renderToken = 0
    private var autoRefreshJob: kotlinx.coroutines.Job? = null
    private val lineOrder = mutableListOf<String>()

    private lateinit var dtPicker: DateTimePickerHelper

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) { render() }
        b.btnRefreshFavs.setOnClickListener { render() }

        // Le drag est géré directement dans les handles (voir setOnTouchListener dans render())

        render()
        view.post { (activity as? MainActivity)?.refreshApiStatusViews() }

        // Rafraîchir quand on revient du backstack (retour depuis fiche arrêt/ligne)
        parentFragmentManager.addOnBackStackChangedListener {
            if (isVisible) render()
        }
    }

    override fun onResume() { super.onResume(); if (::dtPicker.isInitialized) dtPicker.refreshUI(); render() }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            if (::dtPicker.isInitialized) dtPicker.refreshUI()
            render()
            view?.post { (activity as? MainActivity)?.refreshApiStatusViews() }
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(40_000)
            while (true) {
                if (_b != null && dtPicker.isNow) render()
                kotlinx.coroutines.delay(40_000)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun render() {
        val ctx = requireContext()
        val favLines = FavoritesManager.getFavLines(ctx)
        val favStops = FavoritesManager.getFavStops(ctx)
        val favBuses = FavoritesManager.getFavBuses(ctx)
        val savedScroll = view?.findViewById<android.widget.ScrollView>(R.id.scroll_favs)?.scrollY ?: 0
        renderJobs.forEach { it.cancel() }
        renderJobs.clear()
        renderToken++
        val myToken = renderToken
        b.container.removeAllViews()
        val inflater = LayoutInflater.from(ctx)

        if (favLines.isEmpty() && favStops.isEmpty() && favBuses.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            return
        }
        b.tvEmpty.visibility = View.GONE

        if (favBuses.isNotEmpty()) {
            b.container.addView(sectionHeader(getString(R.string.fav_bus_section)))
            favBuses.forEach { key ->
                val parts = key.split("|")
                if (parts.size < 3) return@forEach
                val stopName = parts[0]; val ligne = parts[1]; val destination = parts[2]
                val stop = AppData.busStops.find { it.name == stopName }
                val busLine0 = AppData.busLines.find { it.number == ligne }
                val lineColor = busLine0?.color ?: 0xFF00843D.toInt()
                val lineTextColor0 = busLine0?.textColor ?: android.graphics.Color.WHITE
                val dp4 = (4 * ctx.resources.displayMetrics.density).toInt()

                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp4 * 4, dp4 * 3, dp4 * 4, dp4 * 3)
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
                    setOnClickListener { if (stop != null) (activity as? MainActivity)?.openDetails(stop) }
                }

                // Badge ligne coloré
                val badge = TextView(ctx).apply {
                    text = ligne; textSize = 13f
                    setTextColor(lineTextColor0)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(dp4 * 3, dp4, dp4 * 3, dp4)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(lineColor); cornerRadius = dp4 * 3f }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp4 * 3 }
                }
                row.addView(badge)

                // Infos : arrêt en grand + direction en petit
                val info = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(ctx).apply {
                    text = stopName; textSize = 15f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                info.addView(TextView(ctx).apply {
                    text = "→ $destination"; textSize = 12f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                })
                row.addView(info)

                // Temps de passage
                val llTime = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                llTime.addView(makePlaceholder(ctx))
                row.addView(llTime)

                // ⭐ supprimer ce bus des favoris
                row.addView(TextView(ctx).apply {
                    text = "⭐"
                    textSize = 20f
                    setPadding(dp4 * 2, 0, 0, 0)
                    setOnClickListener {
                        FavoritesManager.toggleBus(ctx, stopName, ligne, destination)
                        Toast.makeText(ctx, getString(R.string.fav_bus_removed), Toast.LENGTH_SHORT).show()
                        render()
                    }
                })

                b.container.addView(row)
                if (stop != null) {
                    val capturedToken = myToken
                    renderJobs += viewLifecycleOwner.lifecycleScope.launch {
                        // Filtrer par ligne ET destination pour n'avoir qu'un seul résultat
                        val raw = if (!dtPicker.isNow) gtfsFallbackAtTime(stop)
                                  else fetchPassagesPerLine(stop)
                        // it.first peut être "B|LONS Perlic" ou juste "B"
                        val results = raw.filter { it.first.substringBefore("|") == ligne }
                            .distinctBy { it.first.substringBefore("|") }
                            .take(1)
                        if (_b == null || renderToken != capturedToken || !llTime.isAttachedToWindow) return@launch
                        llTime.removeAllViews()
                        if (!llTime.isAttachedToWindow) return@launch
                        if (results.isEmpty()) {
                            llTime.addView(TextView(ctx).apply {
                                text = getString(R.string.fav_no_info); textSize = 11f
                                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                            })
                        } else {
                            results.forEach { (_, result) ->
                                if (!llTime.isAttachedToWindow) return@launch
                                // Pas de badge ligne ici : déjà affiché dans la rangée
                                llTime.addView(makePassageRow(ctx, "", result))
                            }
                        }
                    }
                }
            }
        }

        if (favStops.isNotEmpty()) {
            defaultBtns.clear()
            stopOrder.clear()
            stopOrder.addAll(FavoritesManager.getOrderedStops(ctx))
            b.container.addView(sectionHeader(getString(R.string.fav_stops_section)))
            stopOrder.mapNotNull { name -> AppData.busStops.find { it.name == name } }.forEach { stop ->
                val row = inflater.inflate(R.layout.item_stop_fav, b.container, false)
                val tvName     = row.findViewById<TextView>(R.id.tv_name)
                val tvInfo     = row.findViewById<TextView>(R.id.tv_info)
                val llPassages = row.findViewById<LinearLayout>(R.id.ll_passages)
                val btnDefault   = row.findViewById<TextView>(R.id.btn_default)
                val btnRemoveFav = row.findViewById<TextView>(R.id.btn_remove_fav)

                row.findViewById<ImageView>(R.id.iv_drag_handle).visibility = View.GONE

                tvName.text = stop.name
                tvInfo.text = if (stop.lines.isNotEmpty())
                    getString(R.string.lines_label, stop.lines.joinToString(", "))
                else ""

                defaultBtns.add(stop.name to btnDefault)
                btnDefault.alpha = if (FavoritesManager.isDefaultStop(ctx, stop.name)) 1f else 0.3f
                btnDefault.setOnClickListener {
                    FavoritesManager.setDefaultStop(ctx, stop.name)
                    defaultBtns.forEach { (name, btn) ->
                        btn.alpha = if (FavoritesManager.isDefaultStop(ctx, name)) 1f else 0.3f
                    }
                }

                btnRemoveFav.setOnClickListener {
                    FavoritesManager.toggleStop(ctx, stop.name)
                    Toast.makeText(ctx, getString(R.string.removed_from_favs), Toast.LENGTH_SHORT).show()
                    render()
                }

                llPassages.addView(makePlaceholder(ctx))

                row.tag = "stop_row"
                row.setOnClickListener { (activity as? MainActivity)?.openDetails(stop) }
                b.container.addView(row)

                val capturedTokenStop = myToken
                renderJobs += viewLifecycleOwner.lifecycleScope.launch {
                    val results = fetchPassagesPerLine(stop)
                    if (_b == null || renderToken != capturedTokenStop || !llPassages.isAttachedToWindow) return@launch
                    llPassages.removeAllViews()
                    if (!llPassages.isAttachedToWindow) return@launch
                    if (results.isEmpty()) {
                        if (!llPassages.isAttachedToWindow) return@launch
                        llPassages.addView(TextView(ctx).apply {
                            text = getString(R.string.fav_no_info_short)
                            textSize = 11f
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        })
                    } else {
                        results.distinctBy { it.first.substringBefore("|") }.forEach { (ligne, result) ->
                            if (!llPassages.isAttachedToWindow) return@launch
                            llPassages.addView(makePassageRow(ctx, ligne, result))
                        }
                    }
                }
            }
        }

        if (favLines.isNotEmpty()) {
            lineOrder.clear()
            lineOrder.addAll(FavoritesManager.getOrderedLines(ctx))
            b.container.addView(sectionHeader(getString(R.string.fav_lines_section)))
            lineOrder.mapNotNull { num -> AppData.busLines.find { it.number == num } }.forEach { line ->
                val row = inflater.inflate(R.layout.item_line, b.container, false) as LinearLayout
                row.findViewById<TextView>(R.id.tv_line_number).apply {
                    text = line.number
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(line.color); cornerRadius = 12f }
                    setTextColor(line.textColor)
                }
                row.findViewById<TextView>(R.id.tv_line_direction).text =
                    "${line.terminus1}  ↔  ${line.terminus2}"
                row.findViewById<TextView>(R.id.tv_line_desc).text = line.description

                // Retrait de l'icône d'épingle (Point 12) - On ne fait rien car elle n'est pas dans item_line
                // Mais on s'assure qu'aucun autre élément ne ressemble à une épingle ici.

                // ⭐ supprimer la ligne des favoris
                val dp8 = (8 * ctx.resources.displayMetrics.density).toInt()
                row.addView(TextView(ctx).apply {
                    text = "⭐"
                    textSize = 20f
                    setPadding(dp8, 0, dp8, 0)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.gravity = android.view.Gravity.CENTER_VERTICAL }
                    setOnClickListener {
                        FavoritesManager.toggleLine(ctx, line.number)
                        Toast.makeText(ctx, getString(R.string.line_removed_from_favs), Toast.LENGTH_SHORT).show()
                        render()
                    }
                })

                row.tag = "line_row"
                row.setOnClickListener { (activity as? MainActivity)?.openLineDetail(line) }
                b.container.addView(row)
            }
        }
        val sv = view?.findViewById<android.widget.ScrollView>(R.id.scroll_favs)
        sv?.post { sv.scrollTo(0, savedScroll) }
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private data class PassageResult(
        val arrivee: String,
        val statut: PassageStatut,
        val ecartMin: Int = 0
    )

    private suspend fun fetchPassagesPerLine(stop: BusStop): List<Pair<String, PassageResult>> {
        if (stop.codes.isEmpty()) return emptyList()
        if (!dtPicker.isNow) return gtfsFallbackAtTime(stop)
        try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 5)
            (activity as? MainActivity)?.setApiOnline(true)
            val realTime = infos.mapNotNull { info ->
                val first = info.passages.firstOrNull() ?: return@mapNotNull null
                val statut: PassageStatut
                val ecartMin: Int
                if (first.type != "reel") {
                    statut = PassageStatut.THEORIQUE
                    ecartMin = 0
                } else {
                    val theoMinutes = info.passages
                        .filter { it.type == "theorique" }
                        .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                        .map { it.hour * 60 + it.minute }
                    val ecart = PassageHelper.computeEcart(first, theoMinutes.ifEmpty { null })
                    statut = PassageHelper.toStatut(ecart)
                    ecartMin = ecart ?: 0
                }
                // Clé = "ligne|destination" pour afficher la direction
                val dest = info.destination.split(" ").take(2).joinToString(" ")
                "${info.ligne}|$dest" to PassageResult(first.arrivee, statut, ecartMin)
            }
            return realTime
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (!msg.contains("500")) (activity as? MainActivity)?.setApiOnline(false, msg.take(60))
            return gtfsFallback(stop)
        }
    }

    private suspend fun gtfsFallbackAtTime(stop: BusStop): List<Pair<String, PassageResult>> {
        return try {
            val cal = dtPicker.calendar
            val date = java.time.LocalDate.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            val time = java.time.LocalTime.of(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            val theoretical = GtfsReader.getTheoreticalPassages(requireContext(), stop.codes, time, date)
            theoretical.mapNotNull { info ->
                val first = info.passages.firstOrNull() ?: return@mapNotNull null
                val dest = info.destination.split(" ").take(2).joinToString(" ")
                "${info.ligne}|$dest" to PassageResult(first.arrivee, PassageStatut.THEORIQUE, 0)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun gtfsFallback(stop: BusStop): List<Pair<String, PassageResult>> {
        return try {
            val theoretical = GtfsReader.getTheoreticalPassages(requireContext(), stop.codes)
            theoretical.mapNotNull { info ->
                val first = info.passages.firstOrNull() ?: return@mapNotNull null
                val dest = info.destination.split(" ").take(2).joinToString(" ")
                "${info.ligne}|$dest" to PassageResult(first.arrivee, PassageStatut.THEORIQUE, 0)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { emptyList() }
    }

    // ── Vues dynamiques ───────────────────────────────────────────────────────

    private fun makePlaceholder(ctx: android.content.Context): android.widget.ProgressBar =
        android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.END }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.green_primary))
        }

    private data class PassageStyle(
        val timeText: String,
        val color: String,
        val bold: Boolean,
        val strike: Boolean,
        val iconLeft: String,   // emoji affiché avant le temps
        val iconRight: String   // emoji affiché après le temps
    )

    private fun styleFor(result: PassageResult, ctx: android.content.Context): PassageStyle = when (result.statut) {
        PassageStatut.THEORIQUE -> PassageStyle("${result.arrivee}*", "theorique",                false, false, "",       "")
        PassageStatut.A_LHEURE  -> PassageStyle(result.arrivee,       statusHexColor(result.statut, ctx), true, false, "SIGNAL", "")
        PassageStatut.RETARD    -> PassageStyle(result.arrivee,        statusHexColor(result.statut, ctx), true, false, "🕐",   "")
        PassageStatut.AVANCE    -> PassageStyle(result.arrivee,        statusHexColor(result.statut, ctx), true, false, "⚡",   "")
        PassageStatut.ANNULE    -> PassageStyle(result.arrivee,        statusHexColor(result.statut, ctx), true, true,  "",     "❌")
    }

    private fun makePassageRow(ctx: android.content.Context, ligneKey: String, result: PassageResult): View {
        val lineNum = ligneKey.substringBefore("|")
        val destShort = ligneKey.substringAfter("|", "").let { if (it == ligneKey) "" else it }

        val style = styleFor(result, ctx)
        val color = if (style.color == "theorique") ContextCompat.getColor(ctx, R.color.theorique_text) else Color.parseColor(style.color)
        val busLineObj = AppData.busLines.find { it.number == lineNum }
        val lineColor = busLineObj?.color ?: Color.GRAY
        val lineTextColor = busLineObj?.textColor ?: android.graphics.Color.WHITE

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 4 }
        }

        // Ligne 1 : badge [T3 → LONS Pe]
        if (lineNum.isNotEmpty()) {
            wrapper.addView(TextView(ctx).apply {
                text = if (destShort.isNotEmpty()) "$lineNum → $destShort" else lineNum
                textSize = 11f
                setTextColor(lineTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(6, 1, 6, 1)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(lineColor); cornerRadius = 8f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }

        // Ligne 2 : icône + heure
        val timeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 2 }
        }
        if (style.iconLeft.isNotEmpty()) timeRow.addView(makeIconView(ctx, style.iconLeft))
        timeRow.addView(TextView(ctx).apply {
            text = style.timeText
            textSize = 13f
            setTextColor(color)
            setTypeface(null, if (style.bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            if (style.strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        if (style.iconRight.isNotEmpty()) {
            timeRow.addView(TextView(ctx).apply {
                text = style.iconRight
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = 3 }
            })
        }
        wrapper.addView(timeRow)

        return wrapper
    }

    private fun makeTimeRow(
        ctx: android.content.Context,
        ligne: String, time: String, color: Int,
        bold: Boolean, strike: Boolean = false
    ): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END
        }
        if (ligne.isNotEmpty()) {
            row.addView(TextView(ctx).apply {
                text = ligne
                textSize = 11f
                val busLineTmp = AppData.busLines.find { it.number == ligne }
                val lineColor = busLineTmp?.color ?: Color.GRAY
                setTextColor(busLineTmp?.textColor ?: android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(6, 1, 6, 1)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(lineColor); cornerRadius = 8f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 6 }
            })
        }
        row.addView(TextView(ctx).apply {
            text = time
            textSize = 13f
            setTextColor(color)
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            gravity = android.view.Gravity.END
        })
        return row
    }

    private fun makeIconView(ctx: android.content.Context, icon: String): View {
        if (icon == "SIGNAL") {
            return android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.ic_signal_bars)
                layoutParams = LinearLayout.LayoutParams(
                    (16 * ctx.resources.displayMetrics.density).toInt(),
                    (16 * ctx.resources.displayMetrics.density).toInt()
                ).also { it.marginEnd = 3 }
            }
        }
        return TextView(ctx).apply {
            text = icon; textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 3 }
        }
    }

    private fun sectionHeader(title: String): View {
        val tv = TextView(requireContext())
        tv.text = title
        tv.setPadding(16, 20, 16, 8)
        tv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider))
        tv.textSize = 14f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        return tv
    }

    override fun onDestroyView() {
        renderJobs.forEach { it.cancel() }
        renderJobs.clear()
        super.onDestroyView()
        _b = null
    }
}
