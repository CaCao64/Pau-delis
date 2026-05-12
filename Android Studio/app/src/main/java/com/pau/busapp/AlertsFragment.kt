package com.pau.busapp

import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentAlertsBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

class AlertsFragment : Fragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private val alerts get() = AppData.alerts
    private val passageJobs = mutableMapOf<Long, Job>()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val loaded = AlertManager.load(requireContext())
        AppData.alerts.clear()
        AppData.alerts.addAll(loaded)
        renderList()
        binding.fabAddAlert.setOnClickListener { (activity as? MainActivity)?.requestNotifThenAddAlert("") }
        binding.btnRefreshAlerts.setOnClickListener { refreshAlerts() }
    }

    private fun refreshAlerts() {
        val ctx = requireContext()
        val firstVisible = binding.listAlerts.firstVisiblePosition
        val offsetY = binding.listAlerts.getChildAt(0)?.top ?: 0
        AlertManager.cleanupPastTodayAlerts(ctx)
        val loaded = AlertManager.load(ctx)
        AppData.alerts.clear()
        AppData.alerts.addAll(loaded)
        renderList()
        binding.listAlerts.post { binding.listAlerts.setSelectionFromTop(firstVisible, offsetY) }
    }

    fun refresh() {
        if (_binding == null) return
        refreshAlerts()
    }

    private fun renderList() {
        val ctx = requireContext()
        if (alerts.isEmpty()) {
            binding.tvEmpty.visibility    = View.VISIBLE
            binding.listAlerts.visibility = View.GONE
            return
        }
        binding.tvEmpty.visibility    = View.GONE
        binding.listAlerts.visibility = View.VISIBLE

        binding.listAlerts.adapter = object : BaseAdapter() {
            override fun getCount()        = alerts.size
            override fun getItem(p: Int)   = alerts[p]
            override fun getItemId(p: Int) = alerts[p].id

            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val row = cv ?: LayoutInflater.from(ctx).inflate(R.layout.item_alert, parent, false)
                val a = alerts[pos]

                row.findViewById<TextView>(R.id.tv_alert_stop).text = "📍 ${a.stopName}  •  Ligne ${a.lineName}"
                row.findViewById<TextView>(R.id.tv_alert_message).text = buildSummary(a)

                val tvPassage = row.findViewById<TextView>(R.id.tv_alert_passage)
                val pbLoading = row.findViewById<android.widget.ProgressBar>(R.id.pb_alert_loading)
                tvPassage.visibility = View.GONE
                pbLoading.visibility = View.VISIBLE
                tvPassage.paintFlags = tvPassage.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

                passageJobs[a.id]?.cancel()
                passageJobs[a.id] = viewLifecycleOwner.lifecycleScope.launch {
                    val theo = fetchTheoreticalAtAlertTime(a)
                    val result = fetchPassage(a)
                    if (_binding == null) return@launch

                    pbLoading.visibility = View.GONE
                    tvPassage.visibility = View.VISIBLE

                    val parts = mutableListOf<String>()
                    if (theo != null) parts.add("${theo.second} ${theo.first}*")
                    if (result != null) {
                        val rt = when (result.statut) {
                            PassageStatut.THEORIQUE -> "${result.arrivee}*"
                            PassageStatut.A_LHEURE  -> "🟢 ${result.arrivee}"
                            PassageStatut.RETARD    -> "🕐 ${result.arrivee}"
                            PassageStatut.AVANCE    -> "⚡ ${result.arrivee}"
                            PassageStatut.ANNULE    -> "❌ ${result.arrivee}"
                        }
                        parts.add(rt)
                    }

                    if (parts.isEmpty()) {
                        tvPassage.text = "—"
                    } else {
                        tvPassage.text = parts.joinToString("  |  ")
                        if (a.enabled) {
                            tvPassage.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        } else {
                            tvPassage.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        }
                        if (result?.statut == PassageStatut.ANNULE) tvPassage.paintFlags = tvPassage.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    }
                }

                val btnToggle = row.findViewById<TextView>(R.id.btn_toggle)
                val btnEdit   = row.findViewById<TextView>(R.id.btn_edit)
                val btnDelete = row.findViewById<TextView>(R.id.btn_delete)

                btnToggle.text = if (a.enabled) "⏸" else "▶"
                btnToggle.setTextColor(if (a.enabled) ContextCompat.getColor(ctx, R.color.text_secondary) else Color.parseColor("#F7C100"))
                btnToggle.setOnClickListener {
                    val updated = alerts.toMutableList()
                    updated[pos] = a.copy(enabled = !a.enabled)
                    if (updated[pos].enabled) AlertManager.schedule(ctx, updated[pos])
                    else AlertManager.cancel(ctx, a)
                    AlertManager.save(ctx, updated)
                    AppData.alerts.clear(); AppData.alerts.addAll(updated)
                    notifyDataSetChanged()
                }
                btnEdit.setOnClickListener {
                    AddAlertDialog.newInstanceEdit(a).show(parentFragmentManager, "edit_alert")
                }
                btnDelete.setOnClickListener {
                    AlertManager.cancel(ctx, a)
                    val updated = alerts.toMutableList().also { it.removeAt(pos) }
                    AlertManager.save(ctx, updated)
                    AppData.alerts.clear(); AppData.alerts.addAll(updated)
                    renderList()
                    Toast.makeText(ctx, getString(R.string.alert_deleted), Toast.LENGTH_SHORT).show()
                }
                val isNight = (requireContext().resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                row.setBackgroundColor(when {
                    a.enabled && isNight  -> Color.parseColor("#3D2E00")
                    a.enabled && !isNight -> Color.parseColor("#FFF8E1")
                    else -> ContextCompat.getColor(requireContext(), R.color.bg_light)
                })
                return row
            }
        }
    }

    private data class PassageResult(val arrivee: String, val statut: PassageStatut)

    private suspend fun fetchPassage(alert: Alert): PassageResult? {
        val stop = AppData.busStops.find { it.name == alert.stopName } ?: return null
        if (stop.codes.isEmpty()) return null
        return try {
            val infos = IdelisApi.getStopMonitoring(stop.codes.first(), 5)
            val info = infos.find { it.ligne == alert.lineName } ?: return null
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

    private suspend fun fetchTheoreticalAtAlertTime(alert: Alert): Pair<String, String>? {
        val stop = AppData.busStops.find { it.name == alert.stopName } ?: return null
        if (stop.codes.isEmpty()) return null
        return try {
            val time = java.time.LocalTime.of(alert.hourMinute.first, alert.hourMinute.second)
            var date = java.time.LocalDate.now()
            for (attempt in 0..6) {
                val infos = GtfsReader.getTheoreticalPassages(requireContext(), stop.codes, time, date)
                val info = infos.find { it.ligne == alert.lineName }
                val passage = info?.passages?.firstOrNull()?.arrivee
                if (passage != null) {
                    val dayLabel = when (attempt) {
                        0 -> getString(R.string.day_today_short)
                        1 -> getString(R.string.day_tomorrow_short)
                        else -> {
                            val dayNames = arrayOf(getString(R.string.day_mon), getString(R.string.day_tue), getString(R.string.day_wed), getString(R.string.day_thu), getString(R.string.day_fri), getString(R.string.day_sat), getString(R.string.day_sun))
                            dayNames[date.dayOfWeek.value - 1]
                        }
                    }
                    return Pair(passage, dayLabel)
                }
                date = date.plusDays(1)
            }
            null
        } catch (_: Exception) { null }
    }

    private fun buildSummary(a: Alert): String {
        val time = "%02d:%02d".format(a.hourMinute.first, a.hourMinute.second)
        val dayNames = mapOf(
            Calendar.MONDAY to getString(R.string.day_mon), Calendar.TUESDAY to getString(R.string.day_tue),
            Calendar.WEDNESDAY to getString(R.string.day_wed), Calendar.THURSDAY to getString(R.string.day_thu),
            Calendar.FRIDAY to getString(R.string.day_fri), Calendar.SATURDAY to getString(R.string.day_sat),
            Calendar.SUNDAY to getString(R.string.day_sun)
        )
        val rec = when {
            a.isToday -> getString(R.string.recurrence_today_only)
            a.conditions.isEmpty() -> getString(R.string.recurrence_every_day)
            else -> a.conditions.joinToString(" + ") { cond ->
                when (cond) {
                    AlertCondition.ODD_WEEKS          -> getString(R.string.recurrence_odd_weeks)
                    AlertCondition.EVEN_WEEKS         -> getString(R.string.recurrence_even_weeks)
                    AlertCondition.NO_SCHOOL_HOLIDAYS -> getString(R.string.recurrence_no_holidays_short)
                    AlertCondition.WEEKDAYS           -> a.weekdays.sorted().mapNotNull { dayNames[it] }.joinToString(", ")
                    AlertCondition.SPECIFIC_DATES     -> a.specificDates.joinToString(", ") { d ->
                        val p = d.split("-"); if (p.size == 3) "${p[2]}/${p[1]}" else d
                    }
                }
            }
        }
        return "🕐 $time\n$rec"
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
