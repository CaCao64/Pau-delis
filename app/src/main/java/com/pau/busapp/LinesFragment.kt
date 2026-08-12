package com.pau.busapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.pau.busapp.databinding.FragmentLinesBinding

class LinesFragment : Fragment() {
    private var _b: FragmentLinesBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentLinesBinding.inflate(i, c, false); return b.root
    }

    private lateinit var dtPicker: DateTimePickerHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dtPicker = DateTimePickerHelper(this, view.findViewById(R.id.datetime_bar)) {}
        // AppData.busLines est déjà dans l'ordre officiel F,T1..T4,5..17,A..D,Coxitis,Emmaüs
        val adapter = object : ArrayAdapter<BusLine>(requireContext(), 0, AppData.busLines) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val row = cv ?: LayoutInflater.from(context).inflate(R.layout.item_line, parent, false)
                val line = getItem(pos)!!
                val tvNum = row.findViewById<TextView>(R.id.tv_line_number)
                // Supprimer ancien ImageView logo si présent (recyclage de vue)
                (row.tag as? ImageView)?.let { (it.parent as? ViewGroup)?.removeView(it) }
                row.tag = null

                when (line.number) {
                    "COXI", "EMMA" -> {
                        tvNum.visibility = View.GONE
                        val resId = if (line.number == "COXI") R.drawable.ic_coxi else R.drawable.ic_emma
                        val iv = ImageView(context).apply {
                            layoutParams = tvNum.layoutParams
                            setImageResource(resId)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                        row.tag = iv
                        (tvNum.parent as ViewGroup).addView(iv, 0)
                    }
                    else -> {
                        tvNum.visibility = View.VISIBLE
                        val bg = GradientDrawable().apply { setColor(line.color); cornerRadius = 12f }
                        tvNum.background = bg
                        tvNum.text = line.number
                        tvNum.setTextColor(line.textColor)
                    }
                }
                row.findViewById<TextView>(R.id.tv_line_direction).text =
                    "${line.terminus1}  ↔  ${line.terminus2}"
                row.findViewById<TextView>(R.id.tv_line_desc).visibility = View.GONE
                return row
            }
        }
        b.listLines.adapter = adapter
        b.listLines.setOnItemClickListener { _, _, pos, _ ->
            (activity as? MainActivity)?.openLineDetail(AppData.busLines[pos])
        }
        view.post { (activity as? MainActivity)?.refreshApiStatusViews() }
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            if (::dtPicker.isInitialized) dtPicker.refreshUI()
            view?.post { (activity as? MainActivity)?.refreshApiStatusViews() }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
