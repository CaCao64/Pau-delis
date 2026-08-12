package com.pau.busapp

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import java.util.Calendar

class AddAlertDialog : DialogFragment() {

    companion object {
        fun newInstance(stopName: String) = AddAlertDialog().apply {
            arguments = Bundle().apply { putString("stop", stopName) }
        }

        fun newInstanceEdit(alert: Alert) = AddAlertDialog().apply {
            arguments = Bundle().apply {
                putString("stop", alert.stopName)
                putLong("edit_id", alert.id)
            }
        }
    }

    private var editId: Long = -1L

    private var selectedHour   = Calendar.getInstance().also { it.add(Calendar.MINUTE, 1) }.get(Calendar.HOUR_OF_DAY)
    private var selectedMinute = Calendar.getInstance().also { it.add(Calendar.MINUTE, 1) }.get(Calendar.MINUTE)
    private val selectedDays   = mutableSetOf<Int>()
    private val selectedDates  = mutableListOf<String>()
    private val excludedDates  = mutableListOf<String>()

    // Cases récurrence
    private lateinit var cbEveryWeek:    CheckBox
    private lateinit var cbOddWeeks:     CheckBox
    private lateinit var cbEvenWeeks:    CheckBox
    private lateinit var cbNoHolidays:   CheckBox
    private lateinit var cbWeekdays:     CheckBox
    private lateinit var cbSpecific:     CheckBox
    private lateinit var cbExcluded:     CheckBox

    private lateinit var tvTime:        TextView
    private lateinit var tvDays:        TextView
    private lateinit var tvDates:       TextView
    private lateinit var tvExcluded:    TextView
    private lateinit var llDays:        LinearLayout
    private lateinit var llDates:       LinearLayout
    private lateinit var llExcluded:    LinearLayout
    private lateinit var llLine:        LinearLayout
    private lateinit var acStop:        AutoCompleteTextView
    private lateinit var spinnerLine:   Spinner
    private var stopNames = listOf<String>()

    override fun onCreateDialog(s: Bundle?): Dialog {
        editId    = arguments?.getLong("edit_id", -1L) ?: -1L
        val ctx   = requireContext()
        val editAlert = if (editId != -1L) AlertManager.load(ctx).find { it.id == editId } else null
        val stopArg = arguments?.getString("stop")?.ifEmpty { null }
            ?: editAlert?.stopName
            ?: FavoritesManager.getDefaultStop(ctx)

        val scroll = ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 30, 60, 20)
        }
        scroll.addView(layout)

        // ── Arrêt ─────────────────────────────────────────────────────────────
        stopNames = AppData.busStops.map { it.name }.sorted()
        acStop = AutoCompleteTextView(ctx).apply {
            setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, stopNames))
            threshold = 1
            setText(stopArg)
            setOnItemClickListener { _, _, pos, _ ->
                val name = adapter.getItem(pos).toString()
                updateLineSpinner(ctx, name)
                llLine.visibility = View.VISIBLE
            }
        }
        layout.addView(sectionLabel(ctx, ctx.getString(R.string.alert_stop_label)))
        layout.addView(acStop)

        // ── Ligne (masquée tant qu'aucun arrêt saisi) ──────────────────────────
        llLine = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (stopArg.isNotEmpty()) View.VISIBLE else View.GONE
        }
        spinnerLine = Spinner(ctx)
        llLine.addView(sectionLabel(ctx, ctx.getString(R.string.nav_lines), 16))
        llLine.addView(spinnerLine)
        layout.addView(llLine)
        if (stopArg.isNotEmpty()) {
            updateLineSpinner(ctx, stopArg)
            if (editAlert != null) {
                val lines = AppData.busStops.find { it.name == editAlert.stopName }?.lines ?: emptyList()
                val idx = lines.indexOf(editAlert.lineName)
                if (idx >= 0) spinnerLine.post { spinnerLine.setSelection(idx) }
            }
        }


        // ── Heure du passage ───────────────────────────────────────────────────
        layout.addView(sectionLabel(ctx, ctx.getString(R.string.next_passages), 16))
        tvTime = TextView(ctx).apply {
            text = "%02d:%02d".format(selectedHour, selectedMinute)
            textSize = 22f
            setTextColor(Color.parseColor("#00843D"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(ctx, 8), 0, dp(ctx, 8))
            setOnClickListener {
                TimePickerDialog(ctx, { _, h, m ->
                    selectedHour = h; selectedMinute = m
                    text = "%02d:%02d".format(h, m)
                }, selectedHour, selectedMinute, true).show()
            }
        }
        // Pré-remplir depuis l'alerte existante
        if (editAlert != null) {
            selectedHour   = editAlert.hourMinute.first
            selectedMinute = editAlert.hourMinute.second
            tvTime.text    = "%02d:%02d".format(selectedHour, selectedMinute)
        }
        layout.addView(tvTime)

        // ── Récurrence (checkboxes) ────────────────────────────────────────────
        val currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        val weekParity  = if (currentWeek % 2 == 1) "impaire" else "paire"
        layout.addView(sectionLabel(ctx, ctx.getString(R.string.recurrence_label), 16))
        layout.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.recurrence_week_info, currentWeek, weekParity)
            textSize = 12f
            setTextColor(Color.parseColor("#00843D"))
            setPadding(0, 0, 0, dp(ctx, 6))
        })

        cbEveryWeek  = makeCheckBox(ctx, "").also { it.visibility = View.GONE }
        cbOddWeeks   = makeCheckBox(ctx, ctx.getString(R.string.recurrence_odd_weeks))
        cbEvenWeeks  = makeCheckBox(ctx, ctx.getString(R.string.recurrence_even_weeks))
        cbNoHolidays = makeCheckBox(ctx, ctx.getString(R.string.recurrence_no_holidays))
        cbWeekdays   = makeCheckBox(ctx, ctx.getString(R.string.recurrence_weekdays))
        cbSpecific   = makeCheckBox(ctx, ctx.getString(R.string.recurrence_specific_dates))

        // Impaires et paires mutuellement exclusives
        cbOddWeeks.setOnCheckedChangeListener { _, checked ->
            if (checked) cbEvenWeeks.isChecked = false
        }
        cbEvenWeeks.setOnCheckedChangeListener { _, checked ->
            if (checked) cbOddWeeks.isChecked = false
        }

        // Hors vacances : vérifie les dates déjà sélectionnées
        cbNoHolidays.setOnCheckedChangeListener { _, checked ->
            if (checked && cbSpecific.isChecked) {
                val conflits = selectedDates.filter { date ->
                    AlertManager.isSchoolHoliday(date) ||
                    AlertManager.isFrenchHoliday(java.time.LocalDate.parse(date))
                }
                if (conflits.isNotEmpty()) {
                    val affichage = conflits.joinToString(", ") { formatDate(it) }
                    Toast.makeText(ctx,
                        "Ces dates sont en vacances scolaires ou jours fériés : $affichage — retire-les 🎒",
                        Toast.LENGTH_LONG).show()
                    cbNoHolidays.isChecked = false
                }
            }
        }

        listOf(cbEveryWeek, cbOddWeeks, cbEvenWeeks, cbNoHolidays, cbWeekdays, cbSpecific)
            .forEach { layout.addView(it) }

        // Pré-remplir depuis l'alerte existante (avant création llDays/llDates)
        if (editAlert != null) {
            editAlert.conditions.forEach { cond ->
                when (cond) {
                    AlertCondition.ODD_WEEKS          -> cbOddWeeks.isChecked   = true
                    AlertCondition.EVEN_WEEKS         -> cbEvenWeeks.isChecked  = true
                    AlertCondition.NO_SCHOOL_HOLIDAYS -> cbNoHolidays.isChecked = true
                    AlertCondition.WEEKDAYS           -> { cbWeekdays.isChecked = true; selectedDays.addAll(editAlert.weekdays) }
                    AlertCondition.SPECIFIC_DATES     -> { cbSpecific.isChecked = true; selectedDates.addAll(editAlert.specificDates) }
                }
            }
            excludedDates.addAll(editAlert.excludedDates)
        }

        // ── Jours de la semaine ────────────────────────────────────────────────
        llDays = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val dayNames = listOf(
            Calendar.MONDAY    to "Lun",
            Calendar.TUESDAY   to "Mar",
            Calendar.WEDNESDAY to "Mer",
            Calendar.THURSDAY  to "Jeu",
            Calendar.FRIDAY    to "Ven",
            Calendar.SATURDAY  to "Sam",
            Calendar.SUNDAY    to "Dim"
        )
        // Deux lignes : Lun-Mer-Jeu-Ven sur la première, Sam-Dim sur la seconde
        val dayRow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(ctx, 8), 0, 0) }
        val dayRow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(ctx, 6), 0, 0) }
        tvDays = TextView(ctx).apply {
            text = ctx.getString(R.string.recurrence_no_day); textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(ctx, 4), 0, 0)
        }
        val dayBtns = mutableListOf<Pair<Int, TextView>>()
        dayNames.forEachIndexed { idx, (calDay, label) ->
            val btn = TextView(ctx).apply {
                text = label; textSize = 12f
                setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6))
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#BBBBBB")); cornerRadius = 20f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(ctx, 6) }
                setOnClickListener {
                    if (calDay in selectedDays) {
                        selectedDays.remove(calDay)
                        (background as GradientDrawable).setColor(Color.parseColor("#BBBBBB"))
                    } else {
                        selectedDays.add(calDay)
                        (background as GradientDrawable).setColor(Color.parseColor("#00843D"))
                    }
                    updateDaysLabel()
                }
            }
            dayBtns.add(calDay to btn)
            if (idx < 5) dayRow1.addView(btn) else dayRow2.addView(btn)
        }
        llDays.addView(dayRow1)
        llDays.addView(dayRow2)
        llDays.addView(tvDays)
        layout.addView(llDays)

        cbWeekdays.setOnCheckedChangeListener { _, checked ->
            llDays.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // ── Dates spécifiques ─────────────────────────────────────────────────
        llDates = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        tvDates = TextView(ctx).apply {
            text = ctx.getString(R.string.recurrence_no_date); textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        }
        llDates.addView(Button(ctx).apply {
            text = "＋ Ajouter une date"
            setOnClickListener { showDatePicker() }
        })
        llDates.addView(tvDates)
        layout.addView(llDates)

        cbSpecific.setOnCheckedChangeListener { _, checked ->
            llDates.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked && cbNoHolidays.isChecked) {
                val conflits = selectedDates.filter { date ->
                    AlertManager.isSchoolHoliday(date) ||
                    AlertManager.isFrenchHoliday(java.time.LocalDate.parse(date))
                }
                if (conflits.isNotEmpty()) {
                    val affichage = conflits.joinToString(", ") { formatDate(it) }
                    Toast.makeText(ctx,
                        "Ces dates sont en vacances scolaires ou jours fériés : $affichage",
                        Toast.LENGTH_LONG).show()
                    cbSpecific.isChecked = false
                    llDates.visibility = View.GONE
                }
            }
        }

        // ── Dates exclues (checkbox) ──────────────────────────────────────────
        cbExcluded = makeCheckBox(ctx, ctx.getString(R.string.recurrence_excluded_dates))
        layout.addView(cbExcluded)

        llExcluded = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        tvExcluded = TextView(ctx).apply {
            text = ctx.getString(R.string.recurrence_no_excluded); textSize = 12f
            setTextColor(Color.parseColor("#888888"))
        }
        llExcluded.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.recurrence_excluded_hint)
            textSize = 11f
            setTextColor(Color.parseColor("#00843D"))
            setPadding(0, 0, 0, dp(ctx, 4))
        })
        llExcluded.addView(Button(ctx).apply {
            text = "＋ Exclure une date"
            setOnClickListener { showExcludeDatePicker() }
        })
        llExcluded.addView(tvExcluded)
        layout.addView(llExcluded)

        cbExcluded.setOnCheckedChangeListener { _, checked ->
            llExcluded.visibility = if (checked) View.VISIBLE else View.GONE
        }

        // ── Appliquer l'état initial après création de tout l'UI ──────────────
        // Obligatoire : les checkboxes sont pré-cochées AVANT que les llXxx existent,
        // donc les listeners ne se sont pas déclenchés — on applique manuellement ici.
        if (cbWeekdays.isChecked) {
            llDays.visibility = View.VISIBLE
            dayBtns.forEach { (calDay, btn) ->
                (btn.background as? GradientDrawable)?.setColor(
                    if (calDay in selectedDays) Color.parseColor("#00843D")
                    else Color.parseColor("#BBBBBB")
                )
            }
            updateDaysLabel()
        }
        if (cbSpecific.isChecked) {
            llDates.visibility = View.VISIBLE
            refreshDatesView()
        }
        // Pré-cocher "Dates exclues" si l'alerte en a (mode édition)
        if (excludedDates.isNotEmpty()) {
            cbExcluded.isChecked = true
            llExcluded.visibility = View.VISIBLE
        }
        refreshExcludedView()

        return AlertDialog.Builder(ctx)
            .setTitle(if (editId != -1L) ctx.getString(R.string.alert_edit_title) else ctx.getString(R.string.alert_dialog_title))
            .setView(scroll)
            .setPositiveButton(ctx.getString(R.string.alert_btn_add)) { _, _ -> saveAlert() }
            .setNegativeButton("Annuler", null)
            .create()
    }

    private fun buildConditions(): Set<AlertCondition> {
        val conds = mutableSetOf<AlertCondition>()
        if (cbOddWeeks.isChecked)   conds.add(AlertCondition.ODD_WEEKS)
        if (cbEvenWeeks.isChecked)  conds.add(AlertCondition.EVEN_WEEKS)
        if (cbNoHolidays.isChecked) conds.add(AlertCondition.NO_SCHOOL_HOLIDAYS)
        if (cbWeekdays.isChecked)   conds.add(AlertCondition.WEEKDAYS)
        if (cbSpecific.isChecked)   conds.add(AlertCondition.SPECIFIC_DATES)
        return conds
    }

    private fun saveAlert() {
        val ctx  = requireContext()
        val stop = acStop.text.toString().trim().ifEmpty { null } ?: run {
            Toast.makeText(ctx, ctx.getString(R.string.alert_select_stop), Toast.LENGTH_SHORT).show(); return
        }
        if (stop !in stopNames) {
            Toast.makeText(ctx, ctx.getString(R.string.alert_unknown_stop), Toast.LENGTH_SHORT).show(); return
        }
        val lineRaw = spinnerLine.selectedItem?.toString() ?: run {
            Toast.makeText(ctx, ctx.getString(R.string.alert_select_line), Toast.LENGTH_SHORT).show(); return
        }
                val line = lineRaw.substringBefore(" →").trim()
        val destination = lineRaw.substringAfter(" →", "").trim()
        val direction = if (destination.isNotEmpty()) {
            val busLine = AppData.busLines.find { it.number == line }
            if (busLine != null) {
                if (destination.contains(busLine.terminus1, ignoreCase = true)) "1" else "2"
            } else ""
        } else ""

        if (cbWeekdays.isChecked && selectedDays.isEmpty()) {
            Toast.makeText(ctx, ctx.getString(R.string.alert_select_day), Toast.LENGTH_SHORT).show(); return
        }
        if (cbSpecific.isChecked && selectedDates.isEmpty()) {
            Toast.makeText(ctx, ctx.getString(R.string.alert_select_date), Toast.LENGTH_SHORT).show(); return
        }

        val conditions = buildConditions()
        // Aucune condition cochée = aujourd'hui seulement
        val isToday = conditions.isEmpty()

        val existing = AlertManager.load(ctx).toMutableList()
        if (editId != -1L) {
            existing.find { it.id == editId }?.let { AlertManager.cancel(ctx, it) }
            existing.removeAll { it.id == editId }
        }

        val duplicate = existing.any {
            it.stopName == stop && it.lineName == line &&
            it.hourMinute.first == selectedHour && it.hourMinute.second == selectedMinute
        }
        if (duplicate) {
            Toast.makeText(ctx, ctx.getString(R.string.alert_duplicate), Toast.LENGTH_LONG).show()
            return
        }

        val alert = Alert(
            stopName      = stop,
            lineName      = line,
            destination   = destination,
            hourMinute    = Pair(selectedHour, selectedMinute),
            minutesBefore = 0,
            conditions    = conditions,
            isToday       = isToday,
            weekdays      = if (AlertCondition.WEEKDAYS in conditions) selectedDays.toSet() else emptySet(),
            specificDates = if (AlertCondition.SPECIFIC_DATES in conditions) selectedDates.toList() else emptyList(),
            excludedDates = excludedDates.toList()
        )
        existing.add(0, alert)
        AlertManager.schedule(ctx, alert)
        AlertManager.save(ctx, existing)
        AppData.alerts.clear()
        AppData.alerts.addAll(existing)
        (activity as? MainActivity)?.refreshAlerts()
        Toast.makeText(ctx, ctx.getString(R.string.alert_added), Toast.LENGTH_SHORT).show()
    }

    private fun updateLineSpinner(ctx: android.content.Context, stopName: String) {
        val stop = AppData.busStops.find { it.name == stopName }
        val lines = stop?.lines ?: emptyList()
        if (lines.isEmpty()) {
            spinnerLine.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, listOf(ctx.getString(R.string.line_all)))
            return
        }

        val display = mutableListOf<String>()
        lines.forEach { lineNum ->
            val busLine = AppData.busLines.find { it.number == lineNum }
            if (busLine != null) {
                if (stopName in busLine.stopsDir1) display.add("$lineNum → ${busLine.terminus1}")
                if (stopName in busLine.stopsDir2) display.add("$lineNum → ${busLine.terminus2}")
            } else {
                display.add(lineNum)
            }
        }
        spinnerLine.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, display)
    }

    private fun updateDaysLabel() {
        val c = requireContext()
        val names = mapOf(
            Calendar.MONDAY    to c.getString(R.string.day_mon),
            Calendar.TUESDAY   to c.getString(R.string.day_tue),
            Calendar.WEDNESDAY to c.getString(R.string.day_wed),
            Calendar.THURSDAY  to c.getString(R.string.day_thu),
            Calendar.FRIDAY    to c.getString(R.string.day_fri),
            Calendar.SATURDAY  to c.getString(R.string.day_sat),
            Calendar.SUNDAY    to c.getString(R.string.day_sun)
        )
        tvDays.text = if (selectedDays.isEmpty()) c.getString(R.string.recurrence_no_day)
        else selectedDays.sorted().mapNotNull { names[it] }.joinToString(", ")
    }

    private fun showDatePicker() {
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        val dialog = android.app.DatePickerDialog(
            ctx, { _, y, m, d ->
                val date = "%04d-%02d-%02d".format(y, m + 1, d)

                val localDate = java.time.LocalDate.of(y, m + 1, d)
                if (cbNoHolidays.isChecked && (AlertManager.isSchoolHoliday(date) || AlertManager.isFrenchHoliday(localDate))) {
                    Toast.makeText(ctx,
                        "Cette date est en vacances scolaires ou jour férié — incompatible 🎒",
                        Toast.LENGTH_LONG).show()
                    return@DatePickerDialog
                }

                if (date !in selectedDates) { selectedDates.add(date); selectedDates.sort() }
                refreshDatesView()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.minDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun refreshDatesView() {
        val ctx = requireContext()
        // Toujours supprimer les lignes dynamiques (index >= 2 : après btn + tvDates)
        while (llDates.childCount > 2) llDates.removeViewAt(llDates.childCount - 1)

        if (selectedDates.isEmpty()) {
            tvDates.text = ctx.getString(R.string.recurrence_no_date)
            tvDates.visibility = View.VISIBLE
            return
        }

        tvDates.visibility = View.GONE

        selectedDates.toList().forEach { date ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
            }
            row.addView(TextView(ctx).apply {
                text = formatDate(date); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(ctx).apply {
                text = "✕"; textSize = 14f; setTextColor(Color.parseColor("#C62828"))
                setPadding(dp(ctx, 12), 0, 0, 0)
                setOnClickListener {
                    selectedDates.remove(date)
                    refreshDatesView()
                }
            })
            llDates.addView(row)
        }
    }

    private fun formatDate(iso: String): String {
        val p = iso.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
    }

    private fun makeCheckBox(ctx: android.content.Context, label: String) =
        CheckBox(ctx).apply { text = label; textSize = 14f }

    private fun sectionLabel(ctx: android.content.Context, text: String, topPad: Int = 0) =
        TextView(ctx).apply {
            this.text = text; textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(0, dp(ctx, topPad), 0, dp(ctx, 4))
        }

    private fun showExcludeDatePicker() {
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        val dlg = android.app.DatePickerDialog(
            ctx, { _, y, m, d ->
                val date = "%04d-%02d-%02d".format(y, m + 1, d)
                if (date in selectedDates) {
                    Toast.makeText(ctx,
                        "Cette date est déjà dans les dates spécifiques — impossible de l'exclure",
                        Toast.LENGTH_LONG).show()
                    return@DatePickerDialog
                }
                if (date !in excludedDates) { excludedDates.add(date); excludedDates.sort() }
                refreshExcludedView()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dlg.datePicker.minDate = System.currentTimeMillis()
        dlg.show()
    }

    private fun refreshExcludedView() {
        val ctx = requireContext()
        // Garder bouton (index 0) + tvExcluded (index 1), supprimer le reste
        while (llExcluded.childCount > 2) llExcluded.removeViewAt(llExcluded.childCount - 1)

        if (excludedDates.isEmpty()) {
            tvExcluded.text = ctx.getString(R.string.recurrence_no_excluded)
            tvExcluded.visibility = View.VISIBLE
            return
        }
        tvExcluded.visibility = View.GONE
        excludedDates.toList().forEach { date ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
            }
            row.addView(TextView(ctx).apply {
                text = formatDate(date); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(ctx).apply {
                text = "✕"; textSize = 14f; setTextColor(Color.parseColor("#C62828"))
                setPadding(dp(ctx, 12), 0, 0, 0)
                setOnClickListener { excludedDates.remove(date); refreshExcludedView() }
            })
            llExcluded.addView(row)
        }
    }

    private fun dp(ctx: android.content.Context, v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()
}
