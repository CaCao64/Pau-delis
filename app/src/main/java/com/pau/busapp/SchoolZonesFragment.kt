package com.pau.busapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class SchoolZonesFragment : Fragment() {
    private lateinit var contentContainer: LinearLayout
    private lateinit var loadingView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var cachedView: TextView
    private lateinit var selectedDateView: TextView
    private lateinit var selectedPeriodView: TextView
    private lateinit var selectedDateDetails: LinearLayout
    private lateinit var allZonesContainer: LinearLayout
    private lateinit var monthTitleView: TextView
    private lateinit var calendarDaysContainer: LinearLayout

    private var snapshot: SchoolZonesSnapshot? = null
    private var selectedDate: LocalDate = LocalDate.now()
    private var currentMonth: YearMonth = YearMonth.from(selectedDate)

    private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val monthFormatter by lazy {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }
    private val locale: Locale
        get() = resources.configuration.locales[0] ?: Locale.getDefault()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_light))
        }
        contentContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scroll.addView(contentContainer)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildUi()
        renderCalendar()
        updateSelectedDatePanel()
        loadData()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && snapshot == null) {
            loadData()
        }
    }

    private fun buildUi() {
        val ctx = requireContext()
        contentContainer.removeAllViews()
        contentContainer.addView(headerCard(ctx))
        contentContainer.addView(calendarCard(ctx))
        contentContainer.addView(legendCard(ctx))
        contentContainer.addView(selectedDateCard(ctx))
        contentContainer.addView(allZonesCard(ctx))
    }

    private fun headerCard(ctx: android.content.Context): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(10).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        card.addView(layout)

        val title = TextView(ctx).apply {
            text = getString(R.string.school_zones_title)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }
        subtitleView = TextView(ctx).apply {
            text = getString(R.string.school_zones_subtitle)
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(0, dp(6), 0, 0)
        }
        cachedView = TextView(ctx).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.green_primary))
            setPadding(0, dp(8), 0, 0)
        }
        loadingView = TextView(ctx).apply {
            text = getString(R.string.school_zones_loading)
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(0, dp(8), 0, 0)
        }

        layout.addView(title)
        layout.addView(subtitleView)
        layout.addView(cachedView)
        layout.addView(loadingView)
        return card
    }

    private fun calendarCard(ctx: android.content.Context): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(layout)

        val label = TextView(ctx).apply {
            text = getString(R.string.school_zones_calendar_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(dp(4), 0, 0, dp(10))
        }

        val monthRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val prevButton = TextView(ctx).apply {
            text = "‹"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            background = circleBackground(ContextCompat.getColor(ctx, R.color.divider))
            setOnClickListener {
                currentMonth = currentMonth.minusMonths(1)
                renderCalendar()
            }
        }
        monthTitleView = TextView(ctx).apply {
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nextButton = TextView(ctx).apply {
            text = "›"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            background = circleBackground(ContextCompat.getColor(ctx, R.color.divider))
            setOnClickListener {
                currentMonth = currentMonth.plusMonths(1)
                renderCalendar()
            }
        }

        monthRow.addView(prevButton)
        monthRow.addView(monthTitleView)
        monthRow.addView(nextButton)

        val weekHeader = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 7f
            setPadding(0, dp(6), 0, dp(8))
        }
        listOf(
            R.string.day_mon,
            R.string.day_tue,
            R.string.day_wed,
            R.string.day_thu,
            R.string.day_fri,
            R.string.day_sat,
            R.string.day_sun
        ).forEach { resId ->
            weekHeader.addView(TextView(ctx).apply {
                text = getString(resId)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        calendarDaysContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        layout.addView(label)
        layout.addView(monthRow)
        layout.addView(weekHeader)
        layout.addView(calendarDaysContainer)
        return card
    }

    private fun legendCard(ctx: android.content.Context): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        card.addView(layout)

        layout.addView(TextView(ctx).apply {
            text = getString(R.string.school_zones_legend_title)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(0, 0, 0, dp(10))
        })

        listOf(
            R.string.school_zones_legend_school to R.color.school_period_school,
            R.string.school_zones_legend_short_holidays to R.color.school_period_short_holidays,
            R.string.school_zones_legend_summer_holidays to R.color.school_period_summer_holidays,
            R.string.school_zones_legend_saturday to R.color.school_period_saturday,
            R.string.school_zones_legend_sunday_holidays to R.color.school_period_sunday_holidays,
            R.string.school_zones_legend_no_service to R.color.school_period_no_service
        ).forEach { (labelRes, colorRes) ->
            layout.addView(
                legendRow(
                    ctx = ctx,
                    label = getString(labelRes),
                    color = ContextCompat.getColor(ctx, colorRes)
                )
            )
        }

        return card
    }

    private fun selectedDateCard(ctx: android.content.Context): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        card.addView(layout)

        selectedDateView = TextView(ctx).apply {
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }
        selectedPeriodView = TextView(ctx).apply {
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setTextColor(ContextCompat.getColor(ctx, R.color.green_primary))
            background = chipBackground(ContextCompat.getColor(ctx, R.color.green_primary))
        }
        selectedDateDetails = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        layout.addView(selectedDateView)
        layout.addView(selectedPeriodView)
        layout.addView(selectedDateDetails)
        return card
    }

    private fun allZonesCard(ctx: android.content.Context): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        card.addView(layout)

        val title = TextView(ctx).apply {
            text = getString(R.string.school_zones_detail_title)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(0, 0, 0, dp(8))
        }
        allZonesContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        layout.addView(title)
        layout.addView(allZonesContainer)
        return card
    }

    private fun loadData() {
        loadingView.visibility = View.VISIBLE
        loadingView.text = getString(R.string.school_zones_loading)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = SchoolZonesRepository.load(requireContext())
            snapshot = result
            loadingView.visibility = View.GONE
            if (result == null) {
                subtitleView.text = getString(R.string.school_zones_error)
                cachedView.text = ""
                selectedDateView.text = getString(
                    R.string.school_zones_selected_date,
                    selectedDate.format(displayFormatter)
                )
                bindSelectedPeriod(resolveCalendarPeriod(selectedDate))
                selectedDateDetails.removeAllViews()
                selectedDateDetails.addView(infoText(getString(R.string.school_zones_error)))
                allZonesContainer.removeAllViews()
                renderCalendar()
                return@launch
            }

            subtitleView.text = getString(R.string.school_zones_subtitle)
            cachedView.text = if (result.fromCache) {
                getString(R.string.school_zones_cached)
            } else {
                getString(R.string.school_zones_updated_on, LocalDate.now().format(displayFormatter))
            }
            currentMonth = YearMonth.from(selectedDate)
            renderCalendar()
            updateSelectedDatePanel()
            renderZones(result)
        }
    }

    private fun updateSelectedDatePanel() {
        val period = resolveCalendarPeriod(selectedDate)
        selectedDateView.text = getString(
            R.string.school_zones_selected_date,
            selectedDate.format(displayFormatter)
        )
        bindSelectedPeriod(period)
        selectedDateDetails.removeAllViews()

        val result = snapshot
        if (result == null) {
            selectedDateDetails.addView(infoText(getString(R.string.school_zones_loading)))
            return
        }

        result.zones.forEach { zone ->
            val periodForZone = zone.periods.firstOrNull { selectedDate.toString() in it.startDate..it.endDate }
            selectedDateDetails.addView(zoneStatusRow(zone, periodForZone))
        }
    }

    private fun renderZones(snapshot: SchoolZonesSnapshot) {
        allZonesContainer.removeAllViews()
        snapshot.zones.forEach { zone ->
            allZonesContainer.addView(zoneCard(zone))
        }
    }

    private fun renderCalendar() {
        if (!::monthTitleView.isInitialized || !::calendarDaysContainer.isInitialized) return
        val ctx = requireContext()
        monthTitleView.text = currentMonth
            .atDay(1)
            .format(monthFormatter)
            .uppercase(locale)

        calendarDaysContainer.removeAllViews()
        val firstDay = currentMonth.atDay(1)
        val startOffset = firstDay.dayOfWeek.value - 1
        val daysInMonth = currentMonth.lengthOfMonth()
        var dayNumber = 1

        repeat(6) { weekIndex ->
            val weekRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 7f
            }
            repeat(7) { columnIndex ->
                val cellIndex = weekIndex * 7 + columnIndex
                if (cellIndex < startOffset || dayNumber > daysInMonth) {
                    weekRow.addView(spacerCell(ctx))
                } else {
                    val date = currentMonth.atDay(dayNumber++)
                    weekRow.addView(dayCell(ctx, date))
                }
            }
            calendarDaysContainer.addView(weekRow)
        }
    }

    private fun dayCell(ctx: android.content.Context, date: LocalDate): View {
        val period = resolveCalendarPeriod(date)
        val selected = date == selectedDate
        val cell = TextView(ctx).apply {
            text = date.dayOfMonth.toString()
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            background = dayBackground(period.color, selected)
            setOnClickListener {
                selectedDate = date
                currentMonth = YearMonth.from(date)
                updateSelectedDatePanel()
                renderCalendar()
            }
        }
        return cell
    }

    private fun spacerCell(ctx: android.content.Context): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun zoneCard(zone: SchoolZoneInfo): View {
        val ctx = requireContext()
        val zoneColor = zoneColor(zone.zoneCode)
        val card = MaterialCardView(ctx).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(zoneColor)
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.green_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        card.addView(layout)

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(colorDot(ctx, zoneColor))
        header.addView(TextView(ctx).apply {
            text = "${zone.zoneLabel} (${zone.zoneCode.removePrefix("Zone ")})"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(dp(10), 0, 0, 0)
        })
        layout.addView(header)

        if (zone.periods.isEmpty()) {
            layout.addView(TextView(ctx).apply {
                text = getString(R.string.school_zones_no_periods)
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(0, dp(10), 0, 0)
            })
        } else {
            zone.periods.forEach { period ->
                layout.addView(periodRow(period))
            }
        }

        return card
    }

    private fun periodRow(period: SchoolHolidayPeriod): View {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val title = TextView(ctx).apply {
            text = period.description
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        }
        val range = TextView(ctx).apply {
            text = "${formatDate(period.startDate)} -> ${formatDate(period.endDate)}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(0, dp(2), 0, 0)
        }
        container.addView(title)
        container.addView(range)
        return container
    }

    private fun zoneStatusRow(zone: SchoolZoneInfo, period: SchoolHolidayPeriod?): View {
        val ctx = requireContext()
        val zoneColor = zoneColor(zone.zoneCode)
        val card = MaterialCardView(ctx).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            setStrokeColor(ContextCompat.getColor(ctx, R.color.divider))
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        card.addView(row)

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(colorDot(ctx, zoneColor))
        header.addView(TextView(ctx).apply {
            text = zone.zoneLabel
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(dp(10), 0, 0, 0)
        })

        val status = TextView(ctx).apply {
            text = if (period != null) {
                getString(R.string.school_zones_status_in_period)
            } else {
                getString(R.string.school_zones_status_outside_period)
            }
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(999).toFloat()
                setColor(if (period != null) zoneColor else ContextCompat.getColor(ctx, R.color.text_secondary))
            }
        }
        header.addView(
            status,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(12)
            }
        )

        row.addView(header)
        if (period != null) {
            row.addView(TextView(ctx).apply {
                text = period.description
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(dp(22), dp(6), 0, 0)
            })
            row.addView(TextView(ctx).apply {
                text = "${formatDate(period.startDate)} -> ${formatDate(period.endDate)}"
                textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                setPadding(dp(22), dp(2), 0, 0)
            })
        }
        return card
    }

    private fun legendRow(ctx: android.content.Context, label: String, color: Int): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(color)
            }
        })
        row.addView(TextView(ctx).apply {
            text = label
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(dp(10), 0, 0, 0)
        })
        return row
    }

    private fun colorDot(ctx: android.content.Context, color: Int): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
        }
    }

    private fun bindSelectedPeriod(period: CalendarPeriod) {
        selectedPeriodView.text = getString(R.string.school_zones_selected_period, period.label)
        selectedPeriodView.setTextColor(period.color)
        selectedPeriodView.background = chipBackground(period.color)
    }

    private fun resolveCalendarPeriod(date: LocalDate): CalendarPeriod {
        val color = when {
            isNoServiceDate(date) -> ContextCompat.getColor(requireContext(), R.color.school_period_no_service)
            date.dayOfWeek == DayOfWeek.SATURDAY -> ContextCompat.getColor(requireContext(), R.color.school_period_saturday)
            date.dayOfWeek == DayOfWeek.SUNDAY || isFrenchPublicHoliday(date) ->
                ContextCompat.getColor(requireContext(), R.color.school_period_sunday_holidays)
            isSummerHoliday(date) -> ContextCompat.getColor(requireContext(), R.color.school_period_summer_holidays)
            isSchoolHoliday(date) -> ContextCompat.getColor(requireContext(), R.color.school_period_short_holidays)
            else -> ContextCompat.getColor(requireContext(), R.color.school_period_school)
        }
        val type = when (color) {
            ContextCompat.getColor(requireContext(), R.color.school_period_no_service) -> CalendarPeriodType.NO_SERVICE
            ContextCompat.getColor(requireContext(), R.color.school_period_saturday) -> CalendarPeriodType.SATURDAY
            ContextCompat.getColor(requireContext(), R.color.school_period_sunday_holidays) -> CalendarPeriodType.SUNDAY_HOLIDAYS
            ContextCompat.getColor(requireContext(), R.color.school_period_summer_holidays) -> CalendarPeriodType.SUMMER_HOLIDAYS
            ContextCompat.getColor(requireContext(), R.color.school_period_short_holidays) -> CalendarPeriodType.SHORT_HOLIDAYS
            else -> CalendarPeriodType.SCHOOL
        }
        return CalendarPeriod(type, periodLabel(type), color)
    }

    private fun isSchoolHoliday(date: LocalDate): Boolean {
        val periods = snapshot?.zones.orEmpty().flatMap { zone ->
            zone.periods.filter { date.toString() in it.startDate..it.endDate }
        }
        return periods.isNotEmpty()
    }

    private fun isSummerHoliday(date: LocalDate): Boolean {
        val periods = snapshot?.zones.orEmpty().flatMap { zone ->
            zone.periods.filter { date.toString() in it.startDate..it.endDate }
        }
        return periods.any { period ->
            val normalized = SearchTextUtils.normalize(period.description)
            normalized.contains("ete") || normalized.contains("summer") || normalized.contains("vacances d'")
        }
    }

    private fun isNoServiceDate(date: LocalDate): Boolean {
        val monthDay = MonthDay.from(date)
        return monthDay == MonthDay.of(1, 1) || monthDay == MonthDay.of(5, 1)
    }

    private fun isFrenchPublicHoliday(date: LocalDate): Boolean {
        val md = MonthDay.from(date)
        if (md in fixedFrenchHolidays) return true

        val easter = easterSunday(date.year)
        return date == easter.plusDays(1) ||
            date == easter.plusDays(39) ||
            date == easter.plusDays(50)
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private fun periodLabel(type: CalendarPeriodType): String = when (type) {
        CalendarPeriodType.SCHOOL -> getString(R.string.school_zones_legend_school)
        CalendarPeriodType.SHORT_HOLIDAYS -> getString(R.string.school_zones_legend_short_holidays)
        CalendarPeriodType.SUMMER_HOLIDAYS -> getString(R.string.school_zones_legend_summer_holidays)
        CalendarPeriodType.SATURDAY -> getString(R.string.school_zones_legend_saturday)
        CalendarPeriodType.SUNDAY_HOLIDAYS -> getString(R.string.school_zones_legend_sunday_holidays)
        CalendarPeriodType.NO_SERVICE -> getString(R.string.school_zones_legend_no_service)
    }

    private fun dayBackground(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(adjustAlpha(color, if (selected) 0x66 else 0x33))
            setStroke(dp(if (selected) 2 else 1), if (selected) color else adjustAlpha(color, 0x88))
        }
    }

    private fun chipBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(999).toFloat()
            setColor(adjustAlpha(color, 0x22))
            setStroke(dp(1), color)
        }
    }

    private fun circleBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun infoText(text: String): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
    }

    private fun formatDate(value: String): String {
        return runCatching { LocalDate.parse(value).format(displayFormatter) }.getOrElse { value }
    }

    private fun zoneColor(zoneCode: String): Int = when (zoneCode) {
        "Zone A" -> ContextCompat.getColor(requireContext(), R.color.orange_primary)
        "Zone B" -> ContextCompat.getColor(requireContext(), R.color.blue_primary)
        else -> ContextCompat.getColor(requireContext(), R.color.school_zone_gray)
    }

    private fun dp(value: Int): Int =
        (value * requireContext().resources.displayMetrics.density).toInt()

    private enum class CalendarPeriodType {
        SCHOOL,
        SHORT_HOLIDAYS,
        SUMMER_HOLIDAYS,
        SATURDAY,
        SUNDAY_HOLIDAYS,
        NO_SERVICE
    }

    private data class CalendarPeriod(
        val type: CalendarPeriodType,
        val label: String,
        val color: Int
    )

    private companion object {
        val fixedFrenchHolidays = setOf(
            MonthDay.of(1, 1),
            MonthDay.of(5, 1),
            MonthDay.of(5, 8),
            MonthDay.of(7, 14),
            MonthDay.of(8, 15),
            MonthDay.of(11, 1),
            MonthDay.of(11, 11),
            MonthDay.of(12, 25)
        )
    }
}
