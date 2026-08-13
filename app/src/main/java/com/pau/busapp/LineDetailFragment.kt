package com.pau.busapp

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentLineDetailBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class LineDetailFragment : Fragment() {
    private var _b: FragmentLineDetailBinding? = null
    private val b get() = _b!!
    private var reversed = false

    companion object {
        fun newInstance(line: BusLine, highlightStop: String = "", destination: String = "") = LineDetailFragment().apply {
            arguments = Bundle().apply {
                putString("num", line.number)
                putString("highlight", highlightStop)
                putString("destination", destination)
            }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentLineDetailBinding.inflate(i, c, false); return b.root
    }

    private lateinit var dtPicker: DateTimePickerHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val line = AppData.busLines.find { it.number == arguments?.getString("num") } ?: return
        val highlight = arguments?.getString("highlight") ?: ""
        val destination = arguments?.getString("destination") ?: ""

        // Déduire la direction depuis la destination fournie par l'API
        // destination = terminus vers lequel le bus se dirige
        // Si destination ~ terminus2 → stopsDir1 → reversed=false
        // Si destination ~ terminus1 → stopsDir2 → reversed=true
        if (destination.isNotEmpty()) {
            val words = { s: String -> s.uppercase().split(" ", "-").filter { it.length > 2 }.toSet() }
            val wd = words(destination)
            val s1 = wd.intersect(words(line.terminus1)).size
            val s2 = wd.intersect(words(line.terminus2)).size
            if (s1 > s2) reversed = true  // destination ~ terminus1 → bus va vers terminus1 = direction 2
        }

        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) { renderDirection(line, highlight) }

        val typeLabel = when (line.type) {
            LineType.FEBUS     -> getString(R.string.type_febus)
            LineType.TEMPORIS  -> getString(R.string.type_temporis)
            LineType.PROXIMITE -> getString(R.string.type_proximite)
            LineType.DIMANCHE  -> getString(R.string.type_dimanche)
            LineType.SPECIAL   -> getString(R.string.type_special)
        }

        val badge = GradientDrawable().apply { setColor(line.color); cornerRadius = 14f }
        b.tvLineNumber.background = badge
        b.tvLineNumber.setTextColor(line.textColor)
        b.tvLineNumber.text = line.number
        b.tvLineType.text = typeLabel
        b.tvLineDescription.text = line.description
        b.headerLayout.setBackgroundColor(line.color)
        val headerTextColor = line.textColor
        b.tvLineType.setTextColor(headerTextColor)
        b.tvLineDescription.setTextColor(headerTextColor)
        b.btnRetour.setColorFilter(headerTextColor)

        updateLineStar(line)
        b.btnFav.setOnClickListener {
            FavoritesManager.toggleLine(requireContext(), line.number)
            updateLineStar(line)
            val msg = if (FavoritesManager.isLineFav(requireContext(), line.number))
                getString(R.string.line_added_to_favs) else getString(R.string.line_removed_from_favs)
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        renderDirection(line, highlight)
        b.btnInvert.setOnClickListener { reversed = !reversed; renderDirection(line, highlight) }
        b.btnRetour.setOnClickListener { parentFragmentManager.popBackStack() }
        b.btnMap.setOnClickListener {
            (activity as? MainActivity)?.showFragment(LineMapFragment.newInstance(line, highlight), true)
        }
    }

    private fun updateLineStar(line: BusLine) {
        val isFav = FavoritesManager.isLineFav(requireContext(), line.number)
        b.btnFav.text = if (isFav) "⭐" else "☆"
        b.btnFav.setTextColor(if (isFav) Color.parseColor("#F7C100") else ContextCompat.getColor(requireContext(), R.color.surface))
    }

    private val passageJobs = mutableMapOf<String, Job>()
    private var autoRefreshJob: Job? = null

    private fun renderDirection(line: BusLine, highlight: String) {
        val savedScroll = b.scrollStops.scrollY
        passageJobs.values.forEach { it.cancel() }
        passageJobs.clear()

        val stops = if (!reversed) line.stopsDir1 else line.stopsDir2
        val t1 = if (!reversed) line.terminus1 else line.terminus2
        val t2 = if (!reversed) line.terminus2 else line.terminus1
        b.tvTerminus1.text = "🚏 $t1"
        b.tvTerminus2.text = "🚏 $t2"
        b.tvStopsTitle.text = getString(R.string.stops_in_direction, stops.size)
        b.listStops.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        stops.forEachIndexed { index, stopName ->
            val row = inflater.inflate(R.layout.item_stop_simple, b.listStops, false)
            val tvOrd  = row.findViewById<TextView>(R.id.tv_order)
            val tvNm   = row.findViewById<TextView>(R.id.tv_stop_name)
            val llPass = row.findViewById<LinearLayout>(R.id.ll_passages)
            tvOrd.text = "${index + 1}"
            tvNm.text  = stopName
            val isEnd = index == 0 || index == stops.size - 1
            val ordColor = if (isEnd) line.color else 0xFFBBBBBB.toInt()
            tvOrd.background = GradientDrawable().apply {
                setColor(ordColor); cornerRadius = 20f }
            tvOrd.setTextColor(contrastTextColor(ordColor))

            val isHighlighted = highlight.isNotEmpty() && (
                stopName.equals(highlight, ignoreCase = true) ||
                stopName.contains(highlight, ignoreCase = true) ||
                highlight.contains(stopName, ignoreCase = true)
            )
            tvNm.setTypeface(null, if (isHighlighted) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            tvNm.setTextColor(if (isHighlighted) line.color else ContextCompat.getColor(requireContext(), R.color.text_primary))
            tvNm.textSize = if (isHighlighted) 15f else 14f

            val stop = AppData.busStops.find { s ->
                s.name.equals(stopName, ignoreCase = true) ||
                s.name.contains(stopName, ignoreCase = true) ||
                stopName.contains(s.name, ignoreCase = true)
            }
            row.setOnClickListener { if (stop != null) (activity as? MainActivity)?.openDetails(stop) }

            val isSunday = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
            val runsOnSunday = line.type == LineType.FEBUS || line.type == LineType.DIMANCHE || line.type == LineType.SPECIAL
            if (isSunday && !runsOnSunday) {
                llPass.removeAllViews()
                llPass.addView(makeText(requireContext(), "Ne passe pas le dimanche", "#AAAAAA", false))
            } else {
                llPass.addView(makePlaceholder(requireContext()))
                if (stop != null) {
                    passageJobs[stopName] = viewLifecycleOwner.lifecycleScope.launch {
                        val results = fetchPassagesForLine(stop, line.number)
                        if (_b == null) return@launch
                        llPass.removeAllViews()
                        if (results == null) {
                            llPass.addView(makeText(requireContext(), "Pas d'info", "#AAAAAA", false))
                        } else {
                            llPass.addView(makePassageRow(requireContext(), results))
                        }
                    }
                }
            }

            b.listStops.addView(row)
        }

        // Scroller jusqu'à l'arrêt highlighté — attendre que les hauteurs soient mesurées
        val scrollView = view?.findViewById<ScrollView>(R.id.scroll_stops)
        if (savedScroll > 0) {
            scrollView?.post { scrollView.scrollTo(0, savedScroll) }
        } else if (highlight.isNotEmpty()) {
            scrollView?.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    var offsetY = 0
                    var found = false
                    for (i in 0 until b.listStops.childCount) {
                        val row = b.listStops.getChildAt(i) ?: continue
                        val tv = row.findViewById<TextView>(R.id.tv_stop_name)
                        if (tv != null && (tv.text.toString().equals(highlight, ignoreCase = true) ||
                                tv.text.toString().contains(highlight, ignoreCase = true) ||
                                highlight.contains(tv.text.toString(), ignoreCase = true))) {
                            found = true
                            break
                        }
                        offsetY += row.height
                    }
                    if (found) scrollView.smoothScrollTo(0, (offsetY - scrollView.height / 3).coerceAtLeast(0))
                }
            })
        }
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    private data class PassageResult(val arrivee: String, val statut: PassageStatut)

    private suspend fun fetchPassagesForLine(stop: BusStop, lineNumber: String): PassageResult? {
        if (stop.codes.isEmpty()) return null
        if (!dtPicker.isNow) return fetchTheoreticalForLine(stop, lineNumber)
        return try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 5)
            val info = infos.find { it.ligne == lineNumber } ?: return null
            val first = info.passages.firstOrNull() ?: return null
            val statut = if (first.type != "reel") {
                PassageStatut.THEORIQUE
            } else {
                val theoMinutes = info.passages
                    .filter { it.type == "theorique" }
                    .mapNotNull { PassageHelper.parseArrivee(it.arrivee) }
                    .map { it.hour * 60 + it.minute }
                PassageHelper.toStatut(PassageHelper.computeEcart(first, theoMinutes.ifEmpty { null }))
            }
            PassageResult(first.arrivee, statut)
        } catch (_: Exception) { null }
    }

    private suspend fun fetchTheoreticalForLine(stop: BusStop, lineNumber: String): PassageResult? {
        return try {
            val cal = dtPicker.calendar
            val date = java.time.LocalDate.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            val time = java.time.LocalTime.of(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            val infos = GtfsReader.getTheoreticalPassages(requireContext(), stop.codes, time, date)
            val info = infos.find { it.ligne == lineNumber } ?: return null
            val first = info.passages.firstOrNull() ?: return null
            PassageResult(first.arrivee, PassageStatut.THEORIQUE)
        } catch (_: Exception) { null }
    }

    // ── Vues ─────────────────────────────────────────────────────────────────

    private fun makePlaceholder(ctx: android.content.Context) =
        android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = android.view.Gravity.END }
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.green_primary))
        }

    private fun makeText(ctx: android.content.Context, text: String, color: String, bold: Boolean) =
        TextView(ctx).apply {
            this.text = text; textSize = 12f
            setTextColor(Color.parseColor(color))
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

    private fun makePassageRow(ctx: android.content.Context, result: PassageResult): View {
        val (iconLeft, iconRight, timeText, color, bold, strike) = when (result.statut) {
            PassageStatut.THEORIQUE -> Style("",      "",   "${result.arrivee}*", "theorique",                          false, false)
            PassageStatut.A_LHEURE  -> Style("SIGNAL","",   result.arrivee,       statusHexColor(result.statut, ctx),  true,  false)
            PassageStatut.RETARD    -> Style("🕐",   "",   result.arrivee,       statusHexColor(result.statut, ctx),  true,  false)
            PassageStatut.AVANCE    -> Style("⚡",   "",   result.arrivee,       statusHexColor(result.statut, ctx),  true,  false)
            PassageStatut.ANNULE    -> Style("",      "❌", result.arrivee,       statusHexColor(result.statut, ctx),  true,  true)
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        if (iconLeft.isNotEmpty()) row.addView(makeIconView(ctx, iconLeft))
        row.addView(TextView(ctx).apply {
            text = timeText; textSize = 12f
            setTextColor(if (color == "theorique") ContextCompat.getColor(ctx, R.color.theorique_text) else Color.parseColor(color))
            setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            if (strike) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        if (iconRight.isNotEmpty()) row.addView(TextView(ctx).apply {
            text = iconRight; textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = 3 }
        })
        return row
    }

    private data class Style(
        val iconLeft: String, val iconRight: String,
        val timeText: String, val color: String,
        val bold: Boolean, val strike: Boolean
    )

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

    override fun onResume() {
        super.onResume()
        val line = AppData.busLines.find { it.number == arguments?.getString("num") } ?: return
        val highlight = arguments?.getString("highlight") ?: ""
        autoRefreshJob?.cancel()
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(40_000)
            while (true) {
                if (_b != null && dtPicker.isNow) renderDirection(line, highlight)
                kotlinx.coroutines.delay(40_000)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
