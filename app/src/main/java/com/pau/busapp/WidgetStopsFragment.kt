package com.pau.busapp

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class WidgetStopsFragment : Fragment() {

    private var rootView: View? = null

    private val order   = mutableListOf<String>()
    private val enabled = mutableSetOf<String>()

    private var dragActive    = false
    private var draggedIdx    = -1
    private var containerTopY = 0f
    private var rowHeightPx   = 0

    private lateinit var llEntries: LinearLayout

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        rootView = i.inflate(R.layout.fragment_widget_stops, c, false)
        return rootView!!
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        llEntries = view.findViewById(R.id.ll_widget_entries)

        WidgetOrderManager.migrateIfNeeded(ctx)

        val savedOrder   = WidgetOrderManager.getOrder(ctx).toMutableList()
        val savedEnabled = WidgetOrderManager.getEnabled(ctx).toMutableSet()

        // S'assurer que tous les favoris apparaissent
        FavoritesManager.getOrderedStops(ctx).forEach { name ->
            val key = "${WidgetOrderManager.PREFIX_STOP}$name"
            if (key !in savedOrder) savedOrder.add(key)
        }
        FavoritesManager.getFavBuses(ctx).forEach { busKey ->
            val key = "${WidgetOrderManager.PREFIX_BUS}$busKey"
            if (key !in savedOrder) savedOrder.add(key)
        }
        WidgetLinesManager.getOrder(ctx).forEach { num ->
            val key = "${WidgetOrderManager.PREFIX_LINE}$num"
            if (key !in savedOrder) savedOrder.add(key)
        }

        order.clear(); order.addAll(savedOrder)
        enabled.clear(); enabled.addAll(savedEnabled)

        view.findViewById<ImageButton>(R.id.btn_retour_widget).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        view.findViewById<Button>(R.id.btn_save_widget).setOnClickListener { saveAndClose() }
        view.findViewById<Button>(R.id.btn_add_line).setOnClickListener { showLineChooser() }

        renderList()
    }

    // ── Drag ──────────────────────────────────────────────────────────────────

    private fun startDrag(index: Int) {
        dragActive = true; draggedIdx = index
        val loc = IntArray(2); llEntries.getLocationOnScreen(loc)
        containerTopY = loc[1].toFloat()
        rowHeightPx = llEntries.getChildAt(0)?.height ?: 0
        findScrollView()?.requestDisallowInterceptTouchEvent(true)
        llEntries.getChildAt(index)?.alpha = 0.5f
    }

    private fun moveDrag(rawY: Float) {
        val n = llEntries.childCount; if (n == 0 || rowHeightPx == 0) return
        val target = ((rawY - containerTopY) / rowHeightPx).toInt().coerceIn(0, n - 1)
        if (target == draggedIdx) return

        val item = order.removeAt(draggedIdx); order.add(target, item)

        // Échanger le contenu des vues sans déplacer les vues → handle reste attaché
        val range = if (target > draggedIdx) draggedIdx..target else target..draggedIdx
        for (i in range) bindRow(llEntries.getChildAt(i) ?: continue, i)

        llEntries.getChildAt(draggedIdx)?.alpha = 1f
        draggedIdx = target
        llEntries.getChildAt(draggedIdx)?.alpha = 0.5f
    }

    private fun endDrag() {
        for (i in 0 until llEntries.childCount) llEntries.getChildAt(i)?.alpha = 1f
        dragActive = false; draggedIdx = -1
        findScrollView()?.requestDisallowInterceptTouchEvent(false)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun renderList() {
        val ctx = requireContext(); llEntries.removeAllViews()
        order.indices.forEach { idx ->
            val row = LayoutInflater.from(ctx).inflate(R.layout.item_widget_entry, llEntries, false)
            bindRow(row, idx)
            row.findViewById<ImageView>(R.id.iv_entry_drag).setOnTouchListener { _, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN   -> { startDrag(llEntries.indexOfChild(row)); true }
                    MotionEvent.ACTION_MOVE   -> { moveDrag(ev.rawY); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { endDrag(); true }
                    else -> false
                }
            }
            llEntries.addView(row)
        }
    }

    private fun bindRow(row: View, index: Int) {
        val ctx      = requireContext()
        val key      = order[index]
        val isChecked = key in enabled

        val badge   = row.findViewById<TextView>(R.id.tv_entry_badge)
        val tvName  = row.findViewById<TextView>(R.id.tv_entry_name)
        val tvSub   = row.findViewById<TextView>(R.id.tv_entry_sub)
        val btnCfg  = row.findViewById<TextView>(R.id.btn_entry_config)
        val cb      = row.findViewById<CheckBox>(R.id.cb_entry)

        // Remplir selon le type
        when {
            key.startsWith(WidgetOrderManager.PREFIX_STOP) -> {
                val stopName = key.removePrefix(WidgetOrderManager.PREFIX_STOP)
                badge.visibility = View.GONE
                tvName.text = stopName
                tvSub.visibility = View.GONE
            }
            key.startsWith(WidgetOrderManager.PREFIX_LINE) -> {
                val num  = key.removePrefix(WidgetOrderManager.PREFIX_LINE)
                val line = AppData.busLines.find { it.number == num }
                badge.visibility = View.VISIBLE
                badge.text = num
                if (line != null) {
                    badge.background = GradientDrawable().apply { setColor(line.color); cornerRadius = 14f }
                    badge.setTextColor(line?.textColor ?: android.graphics.Color.WHITE)
                }
                tvName.text = line?.let { "${it.terminus1} ↔ ${it.terminus2}" } ?: num
                tvSub.visibility = View.GONE
            }
            key.startsWith(WidgetOrderManager.PREFIX_BUS) -> {
                val parts    = key.removePrefix(WidgetOrderManager.PREFIX_BUS).split("|")
                val stopName = parts.getOrElse(0) { "" }
                val lineNum  = parts.getOrElse(1) { "" }
                val dest     = parts.getOrElse(2) { "" }
                val line = AppData.busLines.find { it.number == lineNum }
                badge.visibility = View.VISIBLE
                badge.text = lineNum
                if (line != null) {
                    badge.background = GradientDrawable().apply { setColor(line.color); cornerRadius = 12f }
                    badge.setTextColor(line?.textColor ?: android.graphics.Color.WHITE)
                }
                tvName.text = stopName
                tvSub.visibility = View.VISIBLE
                tvSub.text = "→ $dest"
            }
        }

        // ⚙ config
        btnCfg.alpha = if (isChecked) 1f else 0.3f
        btnCfg.isEnabled = isChecked
        btnCfg.setOnClickListener {
            if (key !in enabled) return@setOnClickListener
            val d = WidgetStopConfigDialog.newInstance(key)
            d.onSaved = { bindRow(row, llEntries.indexOfChild(row)) }
            d.show(parentFragmentManager, "wcfg_$index")
        }

        // Checkbox
        cb.setOnCheckedChangeListener(null)
        cb.isChecked = isChecked
        cb.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                val maxTotal = FavoritesManager.MAX_WIDGET + WidgetLinesManager.MAX_LINES
                if (enabled.size >= maxTotal) {
                    cb.setOnCheckedChangeListener(null); cb.isChecked = false
                    Toast.makeText(ctx, "Maximum $maxTotal entrées dans le widget", Toast.LENGTH_SHORT).show()
                    bindRow(row, index); return@setOnCheckedChangeListener
                }
                enabled.add(key)
            } else enabled.remove(key)
            btnCfg.alpha = if (key in enabled) 1f else 0.3f
            btnCfg.isEnabled = key in enabled
        }
    }

    private fun showLineChooser() {
        val ctx = requireContext()
        val existing = order.filter { it.startsWith(WidgetOrderManager.PREFIX_LINE) }
            .map { it.removePrefix(WidgetOrderManager.PREFIX_LINE) }.toSet()
        val available = AppData.busLines.filter { it.number !in existing }
        if (available.isEmpty()) {
            Toast.makeText(ctx, "Toutes les lignes déjà ajoutées", Toast.LENGTH_SHORT).show(); return
        }
        val items = available.map { "Ligne ${it.number}  –  ${it.terminus1} ↔ ${it.terminus2}" }.toTypedArray()
        ModernDialogs.showChoice(
            context = ctx,
            title = "Choisir une ligne",
            items = items.toList(),
            selectedIndex = -1
        ) { i ->
            val key = "${WidgetOrderManager.PREFIX_LINE}${available[i].number}"
            order.add(key)
            renderList()
        }
    }

    private fun saveAndClose() {
        val ctx = requireContext()
        WidgetOrderManager.save(ctx, order, enabled)

        val enabledStops = enabled.filter { it.startsWith(WidgetOrderManager.PREFIX_STOP) }
            .map { it.removePrefix(WidgetOrderManager.PREFIX_STOP) }
        FavoritesManager.saveWidgetOrder(ctx, enabledStops)

        val lineOrder = order.filter { it.startsWith(WidgetOrderManager.PREFIX_LINE) }
            .map { it.removePrefix(WidgetOrderManager.PREFIX_LINE) }
        val enabledLines = enabled.filter { it.startsWith(WidgetOrderManager.PREFIX_LINE) }
            .map { it.removePrefix(WidgetOrderManager.PREFIX_LINE) }.toSet()
        WidgetLinesManager.saveOrder(ctx, lineOrder)
        WidgetLinesManager.saveEnabled(ctx, enabledLines)

        StopsWidgetProvider.requestUpdate(ctx)
        parentFragmentManager.popBackStack()
        Toast.makeText(ctx, "Widget mis à jour", Toast.LENGTH_SHORT).show()
    }

    private fun findScrollView(): ScrollView? {
        var v: View? = llEntries
        while (v != null) { if (v is ScrollView) return v; v = v.parent as? View }
        return null
    }

    override fun onDestroyView() { super.onDestroyView(); rootView = null }
}
