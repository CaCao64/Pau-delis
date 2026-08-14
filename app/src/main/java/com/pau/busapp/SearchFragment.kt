package com.pau.busapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pau.busapp.databinding.FragmentSearchBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchFragment : Fragment() {
    private var _b: FragmentSearchBinding? = null
    private val b get() = _b!!
    private lateinit var dtPicker: DateTimePickerHelper

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSearchBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) {}

        b.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, co: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, co: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                if (q.isEmpty()) showHistory() else showResults(q)
            }
        })

        b.etSearch.setOnEditorActionListener { _, _, _ ->
            val q = b.etSearch.text.toString().trim()
            if (q.isNotEmpty()) {
                SearchHistoryManager.addQuery(requireContext(), q)
                AnalyticsTracker.search(requireContext(), q)
            }
            false
        }

        b.btnClearSearch.setOnClickListener {
            AnalyticsTracker.trackAction(requireContext(), "clear", "search_field", "Recherche")
            b.etSearch.setText("")
            showHistory()
        }

        val initialQuery = arguments?.getString("query")
        if (initialQuery != null) {
            b.etSearch.setText(initialQuery)
        } else {
            showHistory()
        }
    }

    private fun showHistory() {
        b.tvSectionTitle.text = getString(R.string.search_recent)
        b.resultsContainer.removeAllViews()
        val history = SearchHistoryManager.getHistory(requireContext())
        if (history.isEmpty()) {
            b.tvEmpty.visibility = View.VISIBLE
            b.tvEmpty.text = getString(R.string.search_empty_hint)
        } else {
            b.tvEmpty.visibility = View.GONE
            history.forEach { q -> b.resultsContainer.addView(historyRow(q)) }
        }
    }

    @Suppress("DEPRECATION")
    private fun showResults(query: String) {
        b.tvEmpty.visibility = View.GONE
        b.resultsContainer.removeAllViews()
        val q = SearchTextUtils.normalize(query)

        val matchedLines = AppData.busLines.filter {
            SearchTextUtils.normalize(it.number).contains(q) ||
            SearchTextUtils.normalize(it.terminus1).contains(q) ||
            SearchTextUtils.normalize(it.terminus2).contains(q) ||
            it.stopsDir1.any { s -> SearchTextUtils.normalize(s).contains(q) } ||
            it.stopsDir2.any { s -> SearchTextUtils.normalize(s).contains(q) }
        }
        val matchedStops = AppData.quaiStops.filter {
            SearchTextUtils.normalize(it.stop.name).contains(q) || SearchTextUtils.normalize(it.direction).contains(q)
        }.distinctBy { it.stop.name }.take(30)

        val total = matchedLines.size + matchedStops.size
        b.tvSectionTitle.text = if (total == 0)
            getString(R.string.search_no_result)
        else
            getString(R.string.search_results_count, total)

        if (matchedStops.isNotEmpty()) {
            b.resultsContainer.addView(sectionHeader(getString(R.string.search_section_stops)))
            matchedStops.forEach { q -> b.resultsContainer.addView(quaiRow(q)) }
        }
        if (matchedLines.isNotEmpty()) {
            b.resultsContainer.addView(sectionHeader(getString(R.string.search_section_lines)))
            matchedLines.forEach { line -> b.resultsContainer.addView(lineRow(line)) }
        }

        // Recherche d'adresse à Pau
        val geocoder = android.location.Geocoder(requireContext())
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (query.length >= 4) {
                    val addresses = geocoder.getFromLocationName("$query, Pau, France", 3)
                    if (!addresses.isNullOrEmpty()) {
                        val firstAddr = addresses.first()
                        val addrLat = firstAddr.latitude
                        val addrLon = firstAddr.longitude
                        val addrName = firstAddr.getAddressLine(0) ?: query

                        // Trouver les arrêts les plus proches
                        val nearby = AppData.busStops.map { stop ->
                            val distResults = FloatArray(1)
                            android.location.Location.distanceBetween(addrLat, addrLon, stop.lat, stop.lon, distResults)
                            stop to distResults[0].toDouble()
                        }.sortedBy { it.second }.take(5)

                        withContext(Dispatchers.Main) {
                            if (_b != null && b.etSearch.text.toString().trim() == query) {
                                val headerView = sectionHeader("Arrêts proches de : $addrName")
                                b.resultsContainer.addView(headerView, 0)
                                nearby.forEachIndexed { index, (stop, dist) ->
                                    val row = LayoutInflater.from(requireContext())
                                        .inflate(R.layout.item_stop, b.resultsContainer, false)
                                    row.findViewById<TextView>(R.id.tv_name).text = stop.name
                                    row.findViewById<TextView>(R.id.tv_info).text = "Distance : ${dist.toInt()}m  •  Lignes : ${stop.lines.joinToString(", ")}"
                                    row.setOnClickListener {
                                        SearchHistoryManager.addQuery(requireContext(), query)
                                        AnalyticsTracker.trackAction(requireContext(), "open", "search_address_stop", "Recherche", mapOf("stop_name" to stop.name))
                                        (activity as? MainActivity)?.openDetails(stop)
                                    }
                                    b.resultsContainer.addView(row, index + 1)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun historyRow(query: String): View {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_search_history, b.resultsContainer, false)
        row.findViewById<TextView>(R.id.tv_history_query).text = query
        row.setOnClickListener {
            AnalyticsTracker.trackAction(requireContext(), "select_history", "search_history", "Recherche", mapOf("query" to query))
            b.etSearch.setText(query)
            b.etSearch.setSelection(query.length)
        }
        row.findViewById<ImageButton>(R.id.btn_remove_history).setOnClickListener {
            AnalyticsTracker.trackAction(requireContext(), "delete", "search_history_entry", "Recherche", mapOf("query" to query))
            SearchHistoryManager.removeQuery(requireContext(), query)
            showHistory()
        }
        return row
    }

    private fun sectionHeader(title: String): View {
        val tv = TextView(requireContext())
        tv.text = title
        tv.setPadding(16, 24, 16, 8)
        tv.setTextColor(android.graphics.Color.parseColor("#00843D"))
        tv.textSize = 13f
        tv.setTypeface(null, android.graphics.Typeface.BOLD)
        return tv
    }

    private fun lineRow(line: BusLine): View {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_line, b.resultsContainer, false)
        val tvNum  = row.findViewById<TextView>(R.id.tv_line_number)
        val tvDir  = row.findViewById<TextView>(R.id.tv_line_direction)
        val tvDesc = row.findViewById<TextView>(R.id.tv_line_desc)
        tvNum.text = line.number
        tvDir.text = "${line.terminus1}  ↔  ${line.terminus2}"
        tvDesc.text = line.description
        val badge = android.graphics.drawable.GradientDrawable().apply {
            setColor(line.color); cornerRadius = 12f }
        tvNum.background = badge
        tvNum.setTextColor(line.textColor)
        row.setOnClickListener {
            SearchHistoryManager.addQuery(requireContext(), line.number)
            AnalyticsTracker.trackAction(requireContext(), "open", "search_line_result", "Recherche", mapOf("line" to line.number))
            (activity as? MainActivity)?.openLineDetail(line)
        }
        return row
    }

    private fun quaiRow(q: AppData.QuaiStop): View {
        val row = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_stop, b.resultsContainer, false)
        row.findViewById<TextView>(R.id.tv_name).text = q.stop.name
        row.findViewById<TextView>(R.id.tv_info).text = buildString {
            if (q.direction.isNotEmpty()) append("→ ${q.direction}")
            if (q.stop.lines.isNotEmpty()) append(getString(R.string.stops_lines_label, q.stop.lines.joinToString(", ")))
        }
        row.setOnClickListener {
            SearchHistoryManager.addQuery(requireContext(), q.stop.name)
            AnalyticsTracker.trackAction(requireContext(), "open", "search_stop_result", "Recherche", mapOf("stop_name" to q.stop.name))
            (activity as? MainActivity)?.openDetails(q.stop)
        }
        return row
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object {
        fun newInstance(query: String): SearchFragment {
            return SearchFragment().apply {
                arguments = Bundle().apply {
                    putString("query", query)
                }
            }
        }
    }
}
