package com.pau.busapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentDetailsBinding
import kotlinx.coroutines.launch

class DetailsFragment : Fragment() {
    private var _b: FragmentDetailsBinding? = null
    private val b get() = _b!!

    companion object {
        fun newInstance(stop: BusStop, highlightLine: String? = null) = DetailsFragment().apply {
            arguments = Bundle().apply {
                putString("name", stop.name)
                putString("lines", stop.lines.joinToString(","))
                putStringArrayList("codes", ArrayList(stop.codes))
                putDouble("lat", stop.lat)
                putDouble("lon", stop.lon)
                putString("highlight_line", highlightLine)
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDetailsBinding.inflate(i, c, false); return b.root
    }

    private lateinit var dtPicker: DateTimePickerHelper
    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val name  = arguments?.getString("name") ?: return
        val lines = arguments?.getString("lines")?.takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()
        val codes = arguments?.getStringArrayList("codes") ?: arrayListOf()

        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) { loadPassages(codes) }

        b.tvStopName.text = name
        b.tvInfo.text =
            if (lines.isNotEmpty()) getString(R.string.lines_label, lines.joinToString(", "))
            else getString(R.string.no_line)

        // Appliquer un dégradé au bandeau avec les couleurs des lignes y passant
        val sortedLines = lines.sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
        val topLines = sortedLines.take(4)
        val headerColors = topLines.mapNotNull { num -> AppData.busLines.find { it.number == num }?.color }.distinct()
        
        if (headerColors.isNotEmpty()) {
            val gd = if (headerColors.size == 1) {
                GradientDrawable().apply { setColor(headerColors[0]) }
            } else {
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, headerColors.toIntArray())
            }
            b.headerLayout.background = gd
            
            // Adapter les couleurs du texte et des icônes pour la lisibilité
            b.tvStopName.setTextColor(Color.WHITE)
            b.btnRetour.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            b.btnRefresh.setTextColor(Color.WHITE)
            b.btnLocate.setTextColor(Color.WHITE)
            b.btnSeeLine.setTextColor(Color.WHITE)
        }

        updateStar()
        b.btnFav.setOnClickListener {
            FavoritesManager.toggleStop(requireContext(), name)
            updateStar()
            val msg = if (FavoritesManager.isStopFav(requireContext(), name))
                getString(R.string.added_to_favs) else getString(R.string.removed_from_favs)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        b.btnRetour.setOnClickListener { parentFragmentManager.popBackStack() }
        b.btnAddAlert.setOnClickListener { (activity as? MainActivity)?.requestNotifThenAddAlert(name) }
        b.btnSeeLine.setOnClickListener {
            val matchedLines = lines.mapNotNull { num -> AppData.busLines.find { it.number == num } }
            when {
                matchedLines.isEmpty() ->
                    Toast.makeText(requireContext(), getString(R.string.no_line), Toast.LENGTH_SHORT).show()
                matchedLines.size == 1 ->
                    (activity as? MainActivity)?.showFragment(LineMapFragment.newInstance(matchedLines[0], name), true)
                else -> {
                    val names = matchedLines.map { "Ligne ${it.number}  –  ${it.terminus1} ↔ ${it.terminus2}" }.toTypedArray()
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.choose_line))
                        .setItems(names) { _, idx ->
                            (activity as? MainActivity)?.showFragment(LineMapFragment.newInstance(matchedLines[idx], name), true)
                        }
                        .show()
                }
            }
        }
        b.btnLocate.setOnClickListener {
            val lat = arguments?.getDouble("lat") ?: return@setOnClickListener
            val lon = arguments?.getDouble("lon") ?: return@setOnClickListener
            (activity as? MainActivity)?.locateOnMap(lat, lon)
        }
        b.btnRefresh.setOnClickListener { loadPassages(codes) }

        loadPassages(codes)
    }

    override fun onResume() {
        super.onResume()
        val codes = arguments?.getStringArrayList("codes") ?: return
        if (dtPicker.isNow) {
            loadPassages(codes)
        }
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(40_000)
                if (_b != null && dtPicker.isNow) loadPassages(codes)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun addPassageRows(list: List<StopInfo>, highlightLine: String?) {
        list.forEach { info ->
            val row = passageRow(info)
            if (info.ligne == highlightLine) {
                row.tag = "highlight"
            }
            b.passagesContainer.addView(row)
        }
    }

    private fun scrollAndHighlight(savedScroll: Int, highlightLine: String?) {
        b.scrollDetails.post {
            if (highlightLine != null) {
                var targetView: View? = null
                for (i in 0 until b.passagesContainer.childCount) {
                    val child = b.passagesContainer.getChildAt(i)
                    if (child.tag == "highlight") {
                        targetView = child
                        break
                    }
                }
                if (targetView != null) {
                    b.scrollDetails.scrollTo(0, targetView.top)
                    val originalBg = targetView.background
                    targetView.setBackgroundColor(Color.parseColor("#FFE082"))
                    targetView.postDelayed({
                        targetView.background = originalBg
                    }, 2500)
                } else {
                    b.scrollDetails.scrollTo(0, savedScroll)
                }
            } else {
                b.scrollDetails.scrollTo(0, savedScroll)
            }
        }
    }

    private fun loadPassages(codes: List<String>) {
        val savedScroll = b.scrollDetails.scrollY
        if (codes.isEmpty()) {
            b.tvPassagesStatus.text = getString(R.string.no_stop_code)
            b.passagesContainer.removeAllViews()
            return
        }
        b.tvPassagesStatus.text = getString(R.string.loading)
        b.passagesContainer.removeAllViews()

        viewLifecycleOwner.lifecycleScope.launch {
            val name = arguments?.getString("name") ?: ""
            val highlightLine = arguments?.getString("highlight_line")
            if (!dtPicker.isNow) {
                loadTheoreticalAtTime(codes, highlightLine)
                scrollAndHighlight(savedScroll, highlightLine)
                return@launch
            }

            // Vérifier dimanche/férié avant appel réseau
            val cal = java.util.Calendar.getInstance()
            val isSunday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
            val isFerie = isFrenchHoliday(cal)
            val stopLines = arguments?.getString("lines")?.takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()
            val hasSundayLine = stopLines.any { ln ->
                val line = AppData.busLines.find { it.number == ln }
                line?.type == LineType.FEBUS || line?.type == LineType.DIMANCHE || line?.type == LineType.SPECIAL
            }

            val allInfos = mutableListOf<StopInfo>()
            var serverReachable = false
            var lastError: String? = null
            val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val isNightTime = currentHour >= 22 || currentHour < 6
            val isOffSchedule = isSunday || isFerie || isNightTime

            for (code in codes) {
                try {
                    val infos = IdelisApi.getStopMonitoring(code, 5)
                    allInfos.addAll(infos)
                    serverReachable = true
                    if (FavoritesManager.isStopFav(requireContext(), name)) {
                        SchedulesCacheManager.saveCache(requireContext(), code, infos)
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("500") && isOffSchedule) {
                        serverReachable = true
                    } else {
                        lastError = if (msg.contains("500")) "HTTP 500 (API Indisponible)" else "${e.javaClass.simpleName}: ${msg.take(60)}"
                        android.util.Log.e("IdelisApi", "Erreur code=$code : $lastError")
                    }
                }
            }
            (activity as? MainActivity)?.setApiOnline(serverReachable, if (!serverReachable) lastError else null)
            if (_b == null) return@launch

            if (serverReachable) {
                val merged = allInfos
                    .groupBy { Pair(it.ligne, it.destination) }
                    .values
                    .map { group -> group.maxByOrNull { it.passages.size } ?: group.first() }
                    .sortedWith(compareBy({ lineSortKey(it.ligne) }, { it.destination }))
                if (merged.isEmpty()) {
                    // Si dimanche/férié sans bus → afficher message directement sans appel GTFS
                    if ((isSunday || isFerie) && !hasSundayLine) {
                        val noServiceMsg = if (isFerie) getString(R.string.stop_no_bus_today_holiday) else getString(R.string.stop_no_bus_today_sunday)
                        b.tvPassagesStatus.text = noServiceMsg
                        b.tvPassagesStatus.setTypeface(null, android.graphics.Typeface.BOLD)
                        b.tvPassagesStatus.setTextColor(android.graphics.Color.WHITE)
                        b.scrollDetails.post { b.scrollDetails.scrollTo(0, savedScroll) }
                    } else {
                    b.tvPassagesStatus.text = getString(R.string.offline_theoretical)
                    try {
                        val theoretical = GtfsReader.getTheoreticalPassages(requireContext(), codes)
                        if (_b == null) return@launch
                        if (theoretical.isEmpty()) {
                            b.tvPassagesStatus.text = getString(R.string.no_passage_today)
                            b.tvPassagesStatus.setTypeface(null, android.graphics.Typeface.BOLD)
                            b.tvPassagesStatus.setTextColor(android.graphics.Color.WHITE)
                        } else {
                            b.tvPassagesStatus.text = getString(R.string.offline_theoretical_count, theoretical.size)
                            addPassageRows(theoretical, highlightLine)
                        }
                        scrollAndHighlight(savedScroll, highlightLine)
                    } catch (e: Exception) {
                        if (_b == null) return@launch
                        b.tvPassagesStatus.text = getString(R.string.no_passage_today)
                        b.tvPassagesStatus.setTypeface(null, android.graphics.Typeface.BOLD)
                        b.tvPassagesStatus.setTextColor(android.graphics.Color.WHITE)
                        b.scrollDetails.post { b.scrollDetails.scrollTo(0, savedScroll) }
                    }
                    }
                } else {
                    b.tvPassagesStatus.text = getString(R.string.passages_count, merged.size)
                    addPassageRows(merged, highlightLine)
                    scrollAndHighlight(savedScroll, highlightLine)
                }
            } else {
                // Tenter de charger depuis le cache
                var loadedFromCache = false
                val cachedInfos = mutableListOf<StopInfo>()
                var cacheTimestamp = 0L
                for (code in codes) {
                    val cache = SchedulesCacheManager.getCache(requireContext(), code)
                    if (cache != null) {
                        cachedInfos.addAll(cache.second)
                        cacheTimestamp = maxOf(cacheTimestamp, cache.first)
                    }
                }
                if (cachedInfos.isNotEmpty()) {
                    loadedFromCache = true
                }

                if (loadedFromCache) {
                    val formattedTime = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(cacheTimestamp))
                    b.tvPassagesStatus.text = "Données réelles en cache du $formattedTime"
                    val merged = cachedInfos
                        .groupBy { Pair(it.ligne, it.destination) }
                        .values
                        .map { group -> group.maxByOrNull { it.passages.size } ?: group.first() }
                        .sortedWith(compareBy({ lineSortKey(it.ligne) }, { it.destination }))
                    addPassageRows(merged, highlightLine)
                    scrollAndHighlight(savedScroll, highlightLine)
                } else if ((isSunday || isFerie) && !hasSundayLine) {
                    val noServiceMsg = if (isFerie) "Aucun bus aujourd'hui\n(Jour férié)" else "Aucun bus aujourd'hui\n(Dimanche)"
                    b.tvPassagesStatus.text = noServiceMsg
                    b.tvPassagesStatus.setTypeface(null, android.graphics.Typeface.BOLD)
                    b.tvPassagesStatus.setTextColor(android.graphics.Color.WHITE)
                    b.scrollDetails.post { b.scrollDetails.scrollTo(0, savedScroll) }
                } else {
                    b.tvPassagesStatus.text = getString(R.string.offline_theoretical)
                    try {
                        val theoretical = GtfsReader.getTheoreticalPassages(requireContext(), codes)
                        if (_b == null) return@launch
                        if (theoretical.isEmpty()) {
                            b.tvPassagesStatus.text = getString(R.string.no_passage_today)
                            b.tvPassagesStatus.setTypeface(null, android.graphics.Typeface.BOLD)
                            b.tvPassagesStatus.setTextColor(android.graphics.Color.WHITE)
                        } else {
                            b.tvPassagesStatus.text = getString(R.string.offline_theoretical_count, theoretical.size)
                            addPassageRows(theoretical, highlightLine)
                        }
                        scrollAndHighlight(savedScroll, highlightLine)
                    } catch (e: Exception) {
                        if (_b == null) return@launch
                        b.tvPassagesStatus.text = getString(R.string.cannot_load_schedules)
                        b.tvPassagesStatus.setTypeface(null, android.graphics.Typeface.BOLD)
                        b.tvPassagesStatus.setTextColor(android.graphics.Color.WHITE)
                        b.scrollDetails.post { b.scrollDetails.scrollTo(0, savedScroll) }
                    }
                }
            }

        }
    }

    private suspend fun loadTheoreticalAtTime(codes: List<String>, highlightLine: String? = null) {
        try {
            val cal = dtPicker.calendar
            val date = java.time.LocalDate.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            val time = java.time.LocalTime.of(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            val theoretical = GtfsReader.getTheoreticalPassages(requireContext(), codes, time, date)
            if (_b == null) return
            if (theoretical.isEmpty()) {
                b.tvPassagesStatus.text = getString(R.string.no_passage_today)
            } else {
                b.tvPassagesStatus.text = getString(R.string.offline_theoretical_count, theoretical.size)
                addPassageRows(theoretical, highlightLine)
            }
        } catch (e: Exception) {
            if (_b == null) return
            b.tvPassagesStatus.text = getString(R.string.no_passage_today)
        }
    }

    private fun lineSortKey(ligne: String): Int {
        val order = listOf("F", "T1", "T2", "T3", "T4",
            "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "16", "17",
            "A", "B", "C", "D", "COXI", "EMMA")
        val idx = order.indexOf(ligne)
        return if (idx == -1) 999 else idx
    }

    private fun passageRow(info: StopInfo): View {
        val ctx = requireContext()
        val codes = arguments?.getStringArrayList("codes") ?: arrayListOf()
        val stopName = arguments?.getString("name") ?: ""

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val busLine = AppData.busLines.find { it.number == info.ligne }
        val lineColor = busLine?.color ?: Color.parseColor("#00843D")
        val lineTextColor = busLine?.textColor ?: android.graphics.Color.WHITE

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Badge ligne
        header.addView(TextView(ctx).apply {
            text = info.ligne
            setTextColor(lineTextColor)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply { setColor(lineColor); cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                val line = AppData.busLines.find { it.number == info.ligne }
                if (line != null)
                    (activity as? MainActivity)?.showFragment(
                        LineDetailFragment.newInstance(line, stopName, info.destination), true)
            }
        })

        // Direction (flex)
        header.addView(TextView(ctx).apply {
            val quaiSuffix = if (info.quaiCode.isNotEmpty()) " (Quai ${info.quaiCode.uppercase()})" else ""
            text = " → ${info.destination}$quaiSuffix"
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(dp(4), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // ⭐ favori
        val btnFavItem = TextView(ctx).apply {
            val isFav = FavoritesManager.isBusFav(ctx, stopName, info.ligne, info.destination)
            text = if (isFav) "⭐" else "☆"
            textSize = 20f
            setTextColor(if (isFav) Color.parseColor("#F7C100") else ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener {
                val added = FavoritesManager.toggleBus(ctx, stopName, info.ligne, info.destination)
                text = if (added) "⭐" else "☆"
                setTextColor(if (added) Color.parseColor("#F7C100") else ContextCompat.getColor(ctx, R.color.text_secondary))
                Toast.makeText(ctx, if (added) getString(R.string.fav_added) else getString(R.string.fav_bus_removed), Toast.LENGTH_SHORT).show()
            }
        }
        header.addView(btnFavItem)

        // ⟳ refresh
        val btnRefreshItem = TextView(ctx).apply {
            text = "⟳"
            textSize = 22f
            setTextColor(ContextCompat.getColor(ctx, R.color.green_primary))
            setPadding(dp(6), 0, dp(4), 0)
        }
        header.addView(btnRefreshItem)
        root.addView(header)

        // ── Zone horaires ─────────────────────────────────────────────────────
        val llPassages = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        fillPassages(llPassages, info)
        root.addView(llPassages)

        // Action refresh
        btnRefreshItem.setOnClickListener {
            llPassages.removeAllViews()
            llPassages.addView(android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(4) }
                indeterminateTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.green_primary))
            })
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val allInfos = mutableListOf<StopInfo>()
                    for (code in codes) {
                        try { allInfos.addAll(IdelisApi.getStopMonitoring(code, 5)) } catch (_: Exception) {}
                    }
                    if (_b == null) return@launch
                    val updated = allInfos.find { it.ligne == info.ligne }
                    llPassages.removeAllViews()
                    fillPassages(llPassages, updated ?: info)
                } catch (_: Exception) {
                    if (_b == null) return@launch
                    llPassages.removeAllViews()
                    fillPassages(llPassages, info)
                }
            }
        }

        // ── Séparateur ────────────────────────────────────────────────────────
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { topMargin = dp(10) }
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider))
        })

        return root
    }

    private fun fillPassages(ll: LinearLayout, info: StopInfo) {
        val ctx = requireContext()
        if (info.passages.isEmpty()) {
            ll.addView(TextView(ctx).apply {
                text = "  ${getString(R.string.no_passage)}"
                textSize = 13f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(dp(4), dp(4), 0, 0)
            })
            return
        }
        val theoMinutes = info.passages
            .filter { it.type == "theorique" }
            .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
            .map { it.hour * 60 + it.minute }

        info.passages.forEach { p ->
            val statut = if (p.type != "reel") PassageStatut.THEORIQUE
            else PassageHelper.toStatut(PassageHelper.computeEcart(p, theoMinutes.ifEmpty { null }))

            val (iconLeft, iconRight, timeText, color, bold, strike) = when (statut) {
                PassageStatut.THEORIQUE -> PassageStyle("",      "",   "${p.arrivee}*", "theorique",                  false, false)
                PassageStatut.A_LHEURE  -> PassageStyle("SIGNAL","",   p.arrivee,       statusHexColor(statut, ctx),  true,  false)
                PassageStatut.RETARD    -> PassageStyle("🕐",    "",   p.arrivee,       statusHexColor(statut, ctx),  true,  false)
                PassageStatut.AVANCE    -> PassageStyle("⚡",    "",   p.arrivee,       statusHexColor(statut, ctx),  true,  false)
                PassageStatut.ANNULE    -> PassageStyle("",      "❌", p.arrivee,       statusHexColor(statut, ctx),  true,  true)
            }

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(4), 0, 0)
            }
            if (iconLeft.isNotEmpty()) row.addView(makeIconView(ctx, iconLeft))
            row.addView(TextView(ctx).apply {
                text = timeText; textSize = 14f
                setTextColor(if (color == "theorique") ContextCompat.getColor(ctx, R.color.theorique_text) else Color.parseColor(color))
                setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                if (strike) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            })
            if (iconRight.isNotEmpty()) row.addView(TextView(ctx).apply {
                text = iconRight; textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginStart = dp(6) }
            })
            ll.addView(row)
        }
    }

    private data class PassageStyle(
        val iconLeft: String, val iconRight: String,
        val timeText: String, val color: String,
        val bold: Boolean, val strike: Boolean
    )

    private fun makeIconView(ctx: android.content.Context, icon: String): View {
        if (icon == "SIGNAL") {
            return android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.ic_signal_bars)
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).also { it.marginEnd = dp(4) }
            }
        }
        return TextView(ctx).apply {
            text = icon; textSize = 14f
            setTextColor(when {
                icon.contains("🕐") || icon.contains("ðŸ•") -> Color.parseColor("#E65100")
                icon.contains("⚡") || icon.contains("âš¡") -> Color.parseColor("#1565C0")
                icon.contains("❌") || icon.contains("âŒ") -> Color.parseColor("#C62828")
                else -> ContextCompat.getColor(ctx, R.color.text_primary)
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dp(6) }
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun updateStar() {
        val isFav = FavoritesManager.isStopFav(requireContext(),
            arguments?.getString("name") ?: "")
        b.btnFav.text = if (isFav) "⭐" else "☆"
        
        val lines = arguments?.getString("lines")?.takeIf { it.isNotEmpty() }?.split(",") ?: emptyList()
        val hasGradient = lines.isNotEmpty()
        
        b.btnFav.setTextColor(
            if (isFav) Color.parseColor("#F7C100")
            else if (hasGradient) Color.WHITE
            else Color.GRAY
        )
    }

    private fun isFrenchHoliday(cal: java.util.Calendar): Boolean {
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        if (month == 1 && day == 1)   return true
        if (month == 5 && day == 1)   return true
        if (month == 5 && day == 8)   return true
        if (month == 7 && day == 14)  return true
        if (month == 8 && day == 15)  return true
        if (month == 11 && day == 1)  return true
        if (month == 11 && day == 11) return true
        if (month == 12 && day == 25) return true
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25
        val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val em = (h + l - 7 * m + 114) / 31; val ed = ((h + l - 7 * m + 114) % 31) + 1
        val easter = java.util.Calendar.getInstance().also { it.set(year, em - 1, ed) }
        fun offset(n: Int): Pair<Int,Int> { val c2 = easter.clone() as java.util.Calendar; c2.add(java.util.Calendar.DAY_OF_YEAR, n); return Pair(c2.get(java.util.Calendar.MONTH)+1, c2.get(java.util.Calendar.DAY_OF_MONTH)) }
        val (lmM, lmD) = offset(1); val (aM, aD) = offset(39); val (pM, pD) = offset(50)
        return (month == lmM && day == lmD) || (month == aM && day == aD) || (month == pM && day == pD)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
