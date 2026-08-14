package com.pau.busapp

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.Calendar
import java.time.Instant
import java.time.ZoneId

class WidgetStopConfigDialog : DialogFragment() {

    companion object {
        fun newInstance(stopName: String) = WidgetStopConfigDialog().apply {
            arguments = Bundle().apply { putString("stop", stopName) }
        }
    }

    var onSaved: (() -> Unit)? = null

    private lateinit var stopName: String
    private lateinit var config: WidgetStopConfig

    private lateinit var sbTextSize: SeekBar
    private lateinit var tvTextSizeLabel: TextView
    private lateinit var cbOddWeeks: CheckBox
    private lateinit var cbEvenWeeks: CheckBox
    private lateinit var cbWeekdays: CheckBox
    private lateinit var cbNoHolidays: CheckBox
    private lateinit var cbSpecific: CheckBox
    private lateinit var llDays: LinearLayout
    private lateinit var llDates: LinearLayout
    private lateinit var tvDays: TextView
    private lateinit var tvDates: TextView

    private val selectedDays  = mutableSetOf<Int>()
    private val selectedDates = mutableListOf<String>()

    override fun onCreateDialog(s: Bundle?): Dialog {
        stopName = arguments?.getString("stop") ?: ""
        config = WidgetStopConfigManager.get(requireContext(), stopName)
        selectedDays.addAll(config.weekdays)
        selectedDates.addAll(config.specificDates)

        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val card = MaterialCardView(ctx).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(12).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
        }
        root.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val shell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        card.addView(shell)
        shell.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
            )
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.green_primary))
        })
        shell.addView(TextView(ctx).apply {
            text = "Configuration du widget"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setPadding(dp(20), dp(18), dp(20), dp(10))
        })
        val scroll = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        scroll.addView(layout)
        shell.addView(scroll)

        // ── Taille de texte ───────────────────────────────────────────────────
        layout.addView(sectionLabel("Taille du texte", 0))
        val sizeRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(12))
        }
        sbTextSize = SeekBar(ctx).apply {
            max = 8; progress = config.textSize - 10
            val tint = android.content.res.ColorStateList.valueOf(Color.parseColor("#00843D"))
            progressTintList = tint; thumbTintList = tint
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvTextSizeLabel = TextView(ctx).apply {
            text = "${config.textSize}sp"; textSize = 13f
            setTextColor(Color.parseColor("#00843D"))
            layoutParams = LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.marginStart = dp(12) }
        }
        sbTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) { tvTextSizeLabel.text = "${p + 10}sp" }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        sizeRow.addView(sbTextSize); sizeRow.addView(tvTextSizeLabel)
        layout.addView(sizeRow)

        // ── Récurrence (checkboxes combinables) ───────────────────────────────
        val currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        val weekParity  = if (currentWeek % 2 == 1) "impaire" else "paire"
        layout.addView(sectionLabel("Afficher (combinable)", 4))
        layout.addView(TextView(ctx).apply {
            text = "Semaine actuelle : n°$currentWeek ($weekParity)\nAucune condition = tous les jours"
            textSize = 11f; setTextColor(Color.parseColor("#00843D"))
            setPadding(0, 0, 0, dp(6))
        })

        cbOddWeeks   = makeCb("Semaines impaires")
        cbEvenWeeks  = makeCb("Semaines paires")
        cbWeekdays   = makeCb("Jours de la semaine")
        cbNoHolidays = makeCb("Hors vacances scolaires")
        cbSpecific   = makeCb("Dates spécifiques")

        // Impaires et paires mutuellement exclusives
        cbOddWeeks.setOnCheckedChangeListener { _, c -> if (c) { cbEvenWeeks.setOnCheckedChangeListener(null); cbEvenWeeks.isChecked = false; cbEvenWeeks.setOnCheckedChangeListener { _, c2 -> if (c2) { cbOddWeeks.isChecked = false } } } }
        cbEvenWeeks.setOnCheckedChangeListener { _, c -> if (c) { cbOddWeeks.setOnCheckedChangeListener(null); cbOddWeeks.isChecked = false; cbOddWeeks.setOnCheckedChangeListener { _, c2 -> if (c2) { cbEvenWeeks.isChecked = false } } } }

        listOf(cbOddWeeks, cbEvenWeeks, cbWeekdays, cbNoHolidays, cbSpecific).forEach { layout.addView(it) }

        // Pré-cocher depuis config
        cbOddWeeks.isChecked   = WidgetCondition.ODD_WEEKS      in config.conditions
        cbEvenWeeks.isChecked  = WidgetCondition.EVEN_WEEKS     in config.conditions
        cbWeekdays.isChecked   = WidgetCondition.WEEKDAYS        in config.conditions
        cbNoHolidays.isChecked = WidgetCondition.NO_HOLIDAYS     in config.conditions
        cbSpecific.isChecked   = WidgetCondition.SPECIFIC_DATES  in config.conditions

        // ── Sélecteur jours ───────────────────────────────────────────────────
        llDays = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (cbWeekdays.isChecked) View.VISIBLE else View.GONE
        }
        val dayNames = listOf(
            Calendar.MONDAY to "Lun", Calendar.TUESDAY to "Mar", Calendar.WEDNESDAY to "Mer",
            Calendar.THURSDAY to "Jeu", Calendar.FRIDAY to "Ven",
            Calendar.SATURDAY to "Sam", Calendar.SUNDAY to "Dim"
        )
        val drow1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val drow2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        tvDays = TextView(ctx).apply {
            text = "Aucun jour"; textSize = 12f; setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            setPadding(0, dp(4), 0, 0)
        }
        dayNames.forEachIndexed { idx, (calDay, label) ->
            val btn = TextView(ctx).apply {
                text = label; textSize = 12f
                setPadding(dp(10), dp(6), dp(10), dp(6)); setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (calDay in selectedDays) Color.parseColor("#00843D") else Color.parseColor("#BBBBBB"))
                    cornerRadius = 20f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(6) }
                setOnClickListener {
                    if (calDay in selectedDays) { selectedDays.remove(calDay); (background as GradientDrawable).setColor(Color.parseColor("#BBBBBB")) }
                    else { selectedDays.add(calDay); (background as GradientDrawable).setColor(Color.parseColor("#00843D")) }
                    updateDaysLabel()
                }
            }
            if (idx < 5) drow1.addView(btn) else drow2.addView(btn)
        }
        llDays.addView(drow1); llDays.addView(drow2); llDays.addView(tvDays)
        layout.addView(llDays)
        updateDaysLabel()

        cbWeekdays.setOnCheckedChangeListener { _, c ->
            llDays.visibility = if (c) View.VISIBLE else View.GONE
        }

        // ── Sélecteur dates ───────────────────────────────────────────────────
        llDates = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (cbSpecific.isChecked) View.VISIBLE else View.GONE
        }
        tvDates = TextView(ctx).apply { text = "Aucune date"; textSize = 12f; setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary)) }
        llDates.addView(Button(ctx).apply { text = "＋ Ajouter une date"; setOnClickListener { showDatePicker() } })
        llDates.addView(tvDates)
        layout.addView(llDates)
        refreshDatesView()

        cbSpecific.setOnCheckedChangeListener { _, c ->
            llDates.visibility = if (c) View.VISIBLE else View.GONE
        }

        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        actions.addView(MaterialButton(ctx).apply {
            text = "Annuler"
            minWidth = 0
            cornerRadius = dp(18)
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.green_dark)
            setOnClickListener { dismiss() }
        })
        actions.addView(MaterialButton(ctx).apply {
            text = "Enregistrer"
            minWidth = 0
            cornerRadius = dp(18)
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.green_primary)
            setOnClickListener { save(); dismiss() }
        })
        shell.addView(actions)

        val dialog = AlertDialog.Builder(ctx)
            .setView(root)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.94f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        return dialog
    }

    private fun save() {
        val ctx = requireContext()
        val conds = mutableSetOf<WidgetCondition>()
        if (cbOddWeeks.isChecked)   conds.add(WidgetCondition.ODD_WEEKS)
        if (cbEvenWeeks.isChecked)  conds.add(WidgetCondition.EVEN_WEEKS)
        if (cbWeekdays.isChecked)   conds.add(WidgetCondition.WEEKDAYS)
        if (cbNoHolidays.isChecked) conds.add(WidgetCondition.NO_HOLIDAYS)
        if (cbSpecific.isChecked)   conds.add(WidgetCondition.SPECIFIC_DATES)

        if (WidgetCondition.WEEKDAYS in conds && selectedDays.isEmpty()) {
            Toast.makeText(ctx, "Sélectionne au moins un jour", Toast.LENGTH_SHORT).show(); return
        }
        if (WidgetCondition.SPECIFIC_DATES in conds && selectedDates.isEmpty()) {
            Toast.makeText(ctx, "Sélectionne au moins une date", Toast.LENGTH_SHORT).show(); return
        }
        val newConfig = WidgetStopConfig(
            textSize      = sbTextSize.progress + 10,
            conditions    = conds,
            weekdays      = if (WidgetCondition.WEEKDAYS in conds) selectedDays.toSet() else emptySet(),
            specificDates = if (WidgetCondition.SPECIFIC_DATES in conds) selectedDates.toList() else emptyList()
        )
        WidgetStopConfigManager.save(ctx, stopName, newConfig)
        StopsWidgetProvider.requestUpdate(ctx)
        onSaved?.invoke()
        Toast.makeText(ctx, "Config enregistrée", Toast.LENGTH_SHORT).show()
    }

    private fun showDatePicker() {
        val ctx = requireContext()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Choisir une date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            if (date !in selectedDates) { selectedDates.add(date); selectedDates.sort() }
            refreshDatesView()
        }
        picker.show(parentFragmentManager, "widget_config_date")
    }

    private fun refreshDatesView() {
        while (llDates.childCount > 2) llDates.removeViewAt(llDates.childCount - 1)
        if (selectedDates.isEmpty()) { tvDates.text = "Aucune date"; tvDates.visibility = View.VISIBLE; return }
        tvDates.visibility = View.GONE
        val ctx = requireContext()
        selectedDates.toList().forEach { date ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            row.addView(TextView(ctx).apply {
                text = formatDate(date); textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(ctx).apply {
                text = "✕"; textSize = 14f; setTextColor(Color.parseColor("#C62828"))
                setPadding(dp(12), 0, 0, 0)
                setOnClickListener { selectedDates.remove(date); refreshDatesView() }
            })
            llDates.addView(row)
        }
    }

    private fun updateDaysLabel() {
        val names = mapOf(
            Calendar.MONDAY to "Lun", Calendar.TUESDAY to "Mar", Calendar.WEDNESDAY to "Mer",
            Calendar.THURSDAY to "Jeu", Calendar.FRIDAY to "Ven",
            Calendar.SATURDAY to "Sam", Calendar.SUNDAY to "Dim"
        )
        tvDays.text = if (selectedDays.isEmpty()) "Aucun jour"
        else selectedDays.sorted().mapNotNull { names[it] }.joinToString(", ")
    }

    private fun makeCb(label: String) = CheckBox(requireContext()).apply {
        val c = requireContext()
        text = label; textSize = 14f
        setTextColor(ContextCompat.getColor(c, R.color.text_primary))
        buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#00843D"))
    }

    private fun sectionLabel(text: String, topPad: Int) = TextView(requireContext()).apply {
        val c = requireContext()
        this.text = text; textSize = 13f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(ContextCompat.getColor(c, R.color.text_primary))
        setPadding(0, dp(topPad), 0, dp(4))
    }

    private fun formatDate(iso: String): String {
        val p = iso.split("-"); return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
    }

    private fun dp(v: Int) = (v * requireContext().resources.displayMetrics.density).toInt()
}
