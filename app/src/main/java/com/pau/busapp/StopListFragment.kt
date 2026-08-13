package com.pau.busapp

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentStopListBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class StopListFragment : Fragment() {
    private var _b: FragmentStopListBinding? = null
    private val b get() = _b!!

    private enum class SortMode { NAME, DISTANCE, NEXT_PASSAGE }
    private var currentSortMode = SortMode.NAME
    private val stops = mutableListOf<BusStop>()
    private val jobs  = mutableMapOf<Int, Job>()
    private var autoRefreshJob: Job? = null

    private lateinit var dtPicker: DateTimePickerHelper

    private var searchQuery = ""

    private fun normalizeString(s: String): String {
        val normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(normalized).replaceAll("").lowercase()
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStopListBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) { refreshAll() }
        b.btnRefreshStops.setOnClickListener { refreshAll() }
        b.btnSortStops.setOnClickListener { v ->
            val popup = PopupMenu(requireContext(), v)
            popup.menu.add(0, 1, 0, getString(R.string.sort_name))
            popup.menu.add(0, 2, 1, getString(R.string.sort_distance))
            popup.menu.add(0, 3, 2, getString(R.string.sort_next_passage))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        currentSortMode = SortMode.NAME
                        updateList()
                        true
                    }
                    2 -> {
                        if (AppData.userLocation == null) {
                            Toast.makeText(requireContext(), "Position GPS inconnue 📍", Toast.LENGTH_SHORT).show()
                        } else {
                            currentSortMode = SortMode.DISTANCE
                            updateList()
                        }
                        true
                    }
                    3 -> {
                        currentSortMode = SortMode.NEXT_PASSAGE
                        updateList()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        b.etSearchStops.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                updateList()
            }
        })

        updateList()
        view.post { (activity as? MainActivity)?.refreshApiStatusViews() }
    }

    private fun updateList() {
        stops.clear()
        var raw = AppData.busStops.toList()

        if (searchQuery.isNotEmpty()) {
            val q = normalizeString(searchQuery)
            raw = raw.filter { normalizeString(it.name).contains(q) }
        }

        when (currentSortMode) {
            SortMode.DISTANCE -> {
                if (AppData.userLocation != null) {
                    val user = AppData.userLocation!!
                    stops.addAll(raw.sortedBy { s ->
                        val res = FloatArray(1)
                        android.location.Location.distanceBetween(user.first, user.second, s.lat, s.lon, res)
                        res[0]
                    })
                } else {
                    stops.addAll(raw.sortedBy { it.name })
                }
                b.btnSortStops.text = "📍"
                setupAdapter()
            }
            SortMode.NEXT_PASSAGE -> {
                b.btnSortStops.text = "🕒"
                val ctx = requireContext()
                viewLifecycleOwner.lifecycleScope.launch {
                    val sortedList = withContext(Dispatchers.Default) {
                        val now = java.time.LocalTime.now()
                        val date = java.time.LocalDate.now()
                        val stopTimes = raw.associateWith { stop ->
                            val passages = GtfsReader.getTheoreticalPassages(ctx, stop.codes, now, date)
                            passages.flatMap { it.passages }
                                .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                                .map { it.hour * 60 + it.minute }
                                .minOrNull() ?: Int.MAX_VALUE
                        }
                        raw.sortedWith(compareBy({ stopTimes[it] ?: Int.MAX_VALUE }, { it.name }))
                    }
                    stops.clear()
                    stops.addAll(sortedList)
                    setupAdapter()
                }
            }
            SortMode.NAME -> {
                stops.addAll(raw.sortedBy { it.name })
                b.btnSortStops.text = "Az"
                setupAdapter()
            }
        }
    }

    private fun setupAdapter() {
        val adapter = object : ArrayAdapter<BusStop>(requireContext(), 0, stops) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val row = cv ?: LayoutInflater.from(context).inflate(R.layout.item_stop, parent, false)
                val s = getItem(pos)!!
                row.findViewById<TextView>(R.id.tv_name).text = s.name

                val user = AppData.userLocation
                if (user != null) {
                    val res = FloatArray(1)
                    android.location.Location.distanceBetween(user.first, user.second, s.lat, s.lon, res)
                    val d = res[0]
                    val txt = if (d < 1000) "${d.toInt()}m" else "%.1fkm".format(d / 1000f)
                    row.findViewById<TextView>(R.id.tv_dist).apply {
                        text = txt
                        visibility = View.VISIBLE
                    }
                } else {
                    row.findViewById<TextView>(R.id.tv_dist).visibility = View.GONE
                }

                row.findViewById<TextView>(R.id.tv_info).text =
                    if (s.lines.isNotEmpty()) context.getString(R.string.stops_lines_label, s.lines.joinToString(", "))
                    else ""

                val ll = row.findViewById<LinearLayout>(R.id.ll_passages)
                ll.removeAllViews()
                ll.addView(makePlaceholder(context))
                ll.tag = s.codes.firstOrNull() ?: s.name

                jobs[pos]?.cancel()
                val stopTag = ll.tag
                jobs[pos] = viewLifecycleOwner.lifecycleScope.launch {
                    val results = fetchPassagesPerLine(s)
                    if (_b == null || ll.tag != stopTag) return@launch
                    ll.removeAllViews()
                    if (results.isEmpty()) {
                        val isSunday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
                        val hasSundayLine = s.lines.any { ln ->
                            val line = AppData.busLines.find { it.number == ln }
                            line?.type == LineType.FEBUS || line?.type == LineType.DIMANCHE || line?.type == LineType.SPECIAL
                        }
                        val msg = if (isSunday && !hasSundayLine) "Ne passe pas le dimanche" else getString(R.string.no_info)
                        ll.addView(makeTimeText(context, msg,
                            ContextCompat.getColor(context, R.color.text_secondary)))
                    } else {
                        results.forEach { (ligne, result) ->
                            ll.addView(makePassageRow(context, ligne, result))
                        }
                    }
                }
                return row
            }
        }
        b.listView.adapter = adapter
        b.listView.setOnItemClickListener { _, _, pos, _ ->
            (activity as? MainActivity)?.openDetails(stops[pos])
        }

        val pendingScroll = (activity as? MainActivity)?.pendingScrollStopName
        if (pendingScroll != null) {
            val idx = stops.indexOfFirst { it.name == pendingScroll }
            if (idx >= 0) {
                b.listView.post {
                    b.listView.setSelection(idx)
                }
                (activity as? MainActivity)?.pendingScrollStopName = null
            }
        }
    }

    private fun refreshAll() {
        val firstVisible = b.listView.firstVisiblePosition
        val offsetY = b.listView.getChildAt(0)?.top ?: 0
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        (b.listView.adapter as? ArrayAdapter<*>)?.notifyDataSetChanged()
        b.listView.post { b.listView.setSelectionFromTop(firstVisible, offsetY) }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            if (::dtPicker.isInitialized) dtPicker.refreshUI()
            view?.post { (activity as? MainActivity)?.refreshApiStatusViews() }
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isVisible) {
            if (::dtPicker.isInitialized) dtPicker.refreshUI()
            refreshAll()
            startAutoRefresh()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(40_000)
            while (true) {
                if (_b != null && dtPicker.isNow) refreshAll()
                kotlinx.coroutines.delay(40_000)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private data class PassageResult(val arrivee: String, val statut: PassageStatut)

    private suspend fun fetchPassagesPerLine(stop: BusStop): List<Pair<String, PassageResult>> {
        if (stop.codes.isEmpty()) return emptyList()
        if (!dtPicker.isNow) return gtfsFallbackAtTime(stop)
        return try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 5)
            (activity as? MainActivity)?.setApiOnline(true)
            infos.mapNotNull { info ->
                val first = info.passages.firstOrNull() ?: return@mapNotNull null
                val statut = if (first.type != "reel") {
                    PassageStatut.THEORIQUE
                } else {
                    val theoMinutes = info.passages
                        .filter { it.type == "theorique" }
                        .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                        .map { it.hour * 60 + it.minute }
                    PassageHelper.toStatut(PassageHelper.computeEcart(first, theoMinutes.ifEmpty { null }))
                }
                val dest = info.destination.let { d ->
                    val parts = d.split(" ")
                    if (parts.size > 3 && d.length > 20) parts.take(3).joinToString(" ") else d
                }
                "${info.ligne}|$dest" to PassageResult(first.arrivee, statut)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (!msg.contains("500")) (activity as? MainActivity)?.setApiOnline(false, msg.take(60))
            gtfsFallbackAtTime(stop)
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
                val dest = info.destination.let { d ->
                    val parts = d.split(" ")
                    if (parts.size > 3 && d.length > 20) parts.take(3).joinToString(" ") else d
                }
                "${info.ligne}|$dest" to PassageResult(first.arrivee, PassageStatut.THEORIQUE)
            }
        } catch (e: Exception) { emptyList() }
    }

    // ── Vues ─────────────────────────────────────────────────────────────────

    private fun makePlaceholder(ctx: android.content.Context): android.widget.ProgressBar =
        android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.END }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.green_primary))
        }

    private fun makeTimeText(ctx: android.content.Context, text: String, color: Int) =
        TextView(ctx).apply {
            this.text = text; textSize = sp11(ctx)
            setTextColor(color)
            gravity = android.view.Gravity.END
            setTypeface(null, android.graphics.Typeface.ITALIC)
        }

    private fun sp11(ctx: android.content.Context) = 11f

    private fun makePassageRow(ctx: android.content.Context, ligneKey: String, result: PassageResult): View {
        val lineNum   = ligneKey.substringBefore("|")
        val destShort = ligneKey.substringAfter("|", "").let { if (it == ligneKey) "" else it }

        val (iconLeft, iconRight, timeText, color, bold, strike) = when (result.statut) {
            PassageStatut.THEORIQUE -> Style("",      "",   "${result.arrivee}*", "theorique",                          false, false)
            PassageStatut.A_LHEURE  -> Style("SIGNAL","",   result.arrivee,       statusHexColor(result.statut, ctx),  true,  false)
            PassageStatut.RETARD    -> Style("🕐",    "",   result.arrivee,       statusHexColor(result.statut, ctx),  true,  false)
            PassageStatut.AVANCE    -> Style("⚡",    "",   result.arrivee,       statusHexColor(result.statut, ctx),  true,  false)
            PassageStatut.ANNULE    -> Style("",      "❌", result.arrivee,       statusHexColor(result.statut, ctx),  true,  true)
        }
        val timeColor = if (color == "theorique")
            ContextCompat.getColor(ctx, R.color.theorique_text) else Color.parseColor(color)
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

        // Ligne 1 : badge [T3 → DEST]
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
        if (iconLeft.isNotEmpty()) timeRow.addView(makeIconView(ctx, iconLeft))
        timeRow.addView(TextView(ctx).apply {
            text = timeText; textSize = 12f
            setTextColor(timeColor)
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        if (iconRight.isNotEmpty()) timeRow.addView(TextView(ctx).apply {
            text = iconRight; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = 3 }
        })
        wrapper.addView(timeRow)

        return wrapper
    }

    private fun makeIconView(ctx: android.content.Context, icon: String): View {
        if (icon == "SIGNAL") {
            return android.widget.ImageView(ctx).apply {
                setImageResource(R.drawable.ic_signal_bars)
                layoutParams = LinearLayout.LayoutParams(
                    (14 * ctx.resources.displayMetrics.density).toInt(),
                    (14 * ctx.resources.displayMetrics.density).toInt()
                ).also { it.marginEnd = 3 }
            }
        }
        return TextView(ctx).apply {
            text = icon; textSize = 12f
            setTextColor(when {
                icon.contains("🕐") || icon.contains("ðŸ•") -> Color.parseColor("#E65100")
                icon.contains("⚡") || icon.contains("âš¡") -> Color.parseColor("#1565C0")
                icon.contains("❌") || icon.contains("âŒ") -> Color.parseColor("#C62828")
                else -> ContextCompat.getColor(ctx, R.color.text_primary)
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = 3 }
        }
    }

    private data class Style(
        val iconLeft: String, val iconRight: String,
        val timeText: String, val color: String,
        val bold: Boolean, val strike: Boolean
    )
}
