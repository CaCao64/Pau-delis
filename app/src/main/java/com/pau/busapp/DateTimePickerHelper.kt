package com.pau.busapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

object DateTimeState {
    var calendar: Calendar = Calendar.getInstance()
    var active = false

    fun reset() {
        active = false
        calendar = Calendar.getInstance()
    }
}

class DateTimePickerHelper(
    private val fragment: Fragment,
    private val bar: View,
    private val onChanged: () -> Unit
) {
    private val btnDate = bar.findViewById<TextView>(R.id.btn_date)
    private val btnTime = bar.findViewById<TextView>(R.id.btn_time)
    private val btnReset = bar.findViewById<ImageView>(R.id.btn_reset_datetime)

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    val calendar: Calendar get() = DateTimeState.calendar
    val isNow: Boolean get() = !DateTimeState.active

    init {
        updateUI()
        btnDate.setOnClickListener { pickDate() }
        btnTime.setOnClickListener { pickTime() }
        btnReset.setOnClickListener { deactivate() }
    }

    fun refreshUI() { updateUI() }

    private fun activate() {
        DateTimeState.active = true
        updateUI()
    }

    private fun deactivate() {
        DateTimeState.reset()
        updateUI()
        onChanged()
    }

    private fun pickDate() {
        val ctx = fragment.requireContext()
        val cal = DateTimeState.calendar
        DatePickerDialog(ctx, { _, y, m, d ->
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
            activate()
            onChanged()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        val ctx = fragment.requireContext()
        val cal = DateTimeState.calendar
        TimePickerDialog(ctx, { _, h, m ->
            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            activate()
            onChanged()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun updateUI() {
        val cal = DateTimeState.calendar
        val now = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        btnDate.text = if (isToday) "Aujourd'hui" else dateFmt.format(cal.time)
        btnTime.text = timeFmt.format(cal.time)

        val ctx = fragment.requireContext()
        if (DateTimeState.active) {
            btnDate.setBackgroundResource(R.drawable.bg_datetime_btn_active)
            btnTime.setBackgroundResource(R.drawable.bg_datetime_btn_active)
            btnReset.visibility = View.VISIBLE
            btnDate.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            btnTime.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
        } else {
            btnDate.setBackgroundResource(R.drawable.bg_datetime_btn)
            btnTime.setBackgroundResource(R.drawable.bg_datetime_btn)
            btnReset.visibility = View.GONE
            btnDate.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            btnTime.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
    }
}
