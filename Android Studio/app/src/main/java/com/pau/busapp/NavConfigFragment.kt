package com.pau.busapp

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pau.busapp.databinding.FragmentNavConfigBinding

class NavConfigFragment : Fragment() {

    private var _b: FragmentNavConfigBinding? = null
    private val b get() = _b!!

    private val order   = mutableListOf<String>()
    private val enabled = mutableSetOf<String>()

    private var dragActive = false
    private var draggedIndex = -1
    private var containerTopY = 0f
    private var rowHeightPx = 0

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentNavConfigBinding.inflate(i, c, false); return b.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        order.clear();   order.addAll(NavConfigManager.getOrder(ctx))
        enabled.clear(); enabled.addAll(NavConfigManager.getEnabled(ctx))

        b.btnRetour.setOnClickListener { parentFragmentManager.popBackStack() }
        b.btnSave.setOnClickListener { saveAndApply() }
        b.btnReset.setOnClickListener { resetToDefault() }

        renderList()
        renderPreview()
    }

    private fun resetToDefault() {
        order.clear()
        order.addAll(NavConfigManager.ALL_TABS.map { it.first })
        enabled.clear()
        enabled.addAll(setOf("map", "favs", "search", "alerts"))
        renderList()
        renderPreview()
        Toast.makeText(requireContext(), getString(R.string.nav_default_restored), Toast.LENGTH_SHORT).show()
    }

    // ── Drag ──────────────────────────────────────────────────────────────────

    private fun startDrag(index: Int) {
        dragActive = true
        draggedIndex = index
        val loc = IntArray(2)
        b.llTabs.getLocationOnScreen(loc)
        containerTopY = loc[1].toFloat()
        rowHeightPx = b.llTabs.getChildAt(0)?.height ?: 0
        findScrollView()?.requestDisallowInterceptTouchEvent(true)
        b.llTabs.getChildAt(index)?.alpha = 0.5f
    }

    private fun moveDrag(rawY: Float) {
        val n = b.llTabs.childCount
        if (n == 0 || rowHeightPx == 0) return
        val target = ((rawY - containerTopY) / rowHeightPx).toInt().coerceIn(0, n - 1)
        if (target == draggedIndex) return

        // Swap data in order list
        val item = order.removeAt(draggedIndex)
        order.add(target, item)

        // Swap contents of the two affected rows — views stay in place
        val range = if (target > draggedIndex) draggedIndex..target else target..draggedIndex
        for (i in range) bindRowContent(b.llTabs.getChildAt(i) ?: continue, i)

        b.llTabs.getChildAt(draggedIndex)?.alpha = 1f
        draggedIndex = target
        b.llTabs.getChildAt(draggedIndex)?.alpha = 0.5f
        renderPreview()
    }

    private fun endDrag() {
        b.llTabs.getChildAt(draggedIndex)?.alpha = 1f
        dragActive = false
        draggedIndex = -1
        findScrollView()?.requestDisallowInterceptTouchEvent(false)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun renderList() {
        val ctx = requireContext()
        b.llTabs.removeAllViews()
        order.indices.forEach { index ->
            val row = LayoutInflater.from(ctx).inflate(R.layout.item_nav_config, b.llTabs, false)
            bindRowContent(row, index)
            row.findViewById<ImageView>(R.id.iv_drag_handle).setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { startDrag(b.llTabs.indexOfChild(row)); true }
                    MotionEvent.ACTION_MOVE -> { moveDrag(event.rawY); true }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { endDrag(); true }
                    else -> false
                }
            }
            b.llTabs.addView(row)
        }
    }

    private fun bindRowContent(row: View, index: Int) {
        val ctx = requireContext()
        val id  = order[index]
        val tab = NavConfigManager.ALL_TABS.find { it.first == id } ?: return

        row.findViewById<TextView>(R.id.tv_tab_label).text = tab.third
        row.findViewById<ImageView>(R.id.iv_tab_icon).apply {
            setImageResource(tab.second)
            setColorFilter(ContextCompat.getColor(ctx, R.color.green_primary))
        }
        val cb = row.findViewById<CheckBox>(R.id.cb_tab_enabled)
        cb.setOnCheckedChangeListener(null)
        cb.isChecked = id in enabled
        cb.isEnabled = id != "map"
        cb.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (enabled.size >= 4) {
                    cb.setOnCheckedChangeListener(null)
                    cb.isChecked = false
                    Toast.makeText(ctx, "Maximum 4 onglets - desactivez-en un d'abord", Toast.LENGTH_SHORT).show()
                    bindRowContent(row, index)
                    return@setOnCheckedChangeListener
                }
                enabled.add(id)
            } else {
                enabled.remove(id)
                if (enabled.isEmpty()) enabled.add("map")
            }
            renderPreview()
        }
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    private fun renderPreview() {
        val ctx          = requireContext()
        val visible      = order.filter { it in enabled }.take(4)
        val greenColor   = ContextCompat.getColor(ctx, R.color.green_primary)
        val inactiveColor = Color.parseColor("#9E9E9E")
        b.llPreview.removeAllViews()
        visible.forEach { id ->
            val tab = NavConfigManager.ALL_TABS.find { it.first == id } ?: return@forEach
            b.llPreview.addView(makePreviewCell(ctx, tab.second, tab.third, greenColor))
        }
        b.llPreview.addView(makePreviewCell(ctx, R.drawable.ic_more, "Plus", inactiveColor))
    }

    private fun makePreviewCell(ctx: android.content.Context, iconRes: Int, label: String, color: Int): View {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 8, 0, 8)
        }
        cell.addView(ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(20.dp(ctx), 20.dp(ctx))
            setImageResource(iconRes)
            setColorFilter(color)
        })
        cell.addView(TextView(ctx).apply {
            text = label; textSize = 9f
            setTextColor(color); gravity = Gravity.CENTER
        })
        return cell
    }

    private fun Int.dp(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findScrollView(): ScrollView? {
        var v: View? = b.llTabs
        while (v != null) { if (v is ScrollView) return v; v = v.parent as? View }
        return null
    }

    private fun saveAndApply() {
        val ctx = requireContext()
        if (enabled.isEmpty()) enabled.add("map")
        NavConfigManager.save(ctx, order, enabled)
        (activity as? MainActivity)?.rebuildNav()
        (activity as? MainActivity)?.applyNavStyle(NavStyleManager.get(ctx))
        parentFragmentManager.popBackStack()
        Toast.makeText(ctx, "Navigation mise a jour", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
