package com.pau.busapp

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout

class SafeLinearLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        try { super.onInitializeAccessibilityNodeInfo(info) } catch (_: Exception) {}
    }

    override fun addChildrenForAccessibility(outChildren: ArrayList<View>) {
        try {
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                outChildren.add(child)
            }
        } catch (_: Exception) {}
    }
}
