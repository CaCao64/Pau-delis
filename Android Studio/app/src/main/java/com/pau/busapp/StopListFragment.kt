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

class StopListFragment : Fragment() {
    private var _b: FragmentStopListBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentStopListBinding.inflate(i, c, false); return b.root
    }

    private val stops = AppData.busStops.sortedBy { it.name }
    private val jobs  = mutableMapOf<Int, Job>()
    private var autoRefreshJob: Job? = null

    private lateinit var dtPicker: DateTimePickerHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) { refreshAll() }
        b.btnRefreshStops.setOnClickListener { refreshAll() }
        setupAdapter()
        view.post { (activity as? MainActivity)?.refreshApiStatusViews() }
    }

    private fun setupAdapter() {
        val adapter = object : ArrayAdapter<BusStop>(requireContext(), 0, stops) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val row = cv ?: LayoutInflater.from(context).inflate(R.layout.item_stop, parent, false)
                val s = getItem(pos)!!
                row.findViewById<TextView>(R.id.tv_name).text = s.name
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
                val dest = info.destination.split(" ").take(2).joinToString(" ")
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
                val dest = info.destination.split(" ").take(2).joinToString(" ")
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
            this.text = text; textSize = 12f
            setTextColor(color)
            gravity = android.view.Gravity.END
        }

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
