package com.pau.busapp

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.pau.busapp.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {
    private var _b: FragmentMoreBinding? = null
    private val b get() = _b!!

    private data class MoreItem(val id: String, val iconRes: Int, val label: String, val onClick: () -> Unit)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMoreBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildItems()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) buildItems()
    }

    private fun buildItems() {
        val ctx = requireContext()
        val visibleTabs = NavConfigManager.getVisibleTabs(ctx).toSet()
        val container = b.llMoreItems
        container.removeAllViews()

        val allItems = listOf(
            MoreItem("favs", R.drawable.ic_star, getString(R.string.nav_favs)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_favorites", "Plus")
                AnalyticsTracker.screenView(ctx, "Favoris", "FavoritesFragment")
                (activity as? MainActivity)?.showFragment(FavoritesFragment(), true)
            },
            MoreItem("search", R.drawable.ic_search, getString(R.string.nav_search)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_search", "Plus")
                AnalyticsTracker.screenView(ctx, "Recherche", "SearchFragment")
                (activity as? MainActivity)?.showFragment(SearchFragment(), true)
            },
            MoreItem("alerts", R.drawable.ic_bell, getString(R.string.nav_alerts)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_alerts", "Plus")
                (activity as? MainActivity)?.checkNotifPermissionThenOpenAlerts()
            },
            MoreItem("stops", R.drawable.ic_list, getString(R.string.nav_stops)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_stops", "Plus")
                AnalyticsTracker.screenView(ctx, "Arrêts", "StopListFragment")
                (activity as? MainActivity)?.showFragment(StopListFragment(), true)
            },
            MoreItem("lines", R.drawable.ic_map, getString(R.string.nav_lines)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_lines", "Plus")
                AnalyticsTracker.screenView(ctx, "Lignes", "LinesFragment")
                (activity as? MainActivity)?.showFragment(LinesFragment(), true)
            },
            MoreItem("school_zones", R.drawable.ic_calendar, getString(R.string.nav_school_zones)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_school_zones", "Plus")
                AnalyticsTracker.screenView(ctx, getString(R.string.school_zones_title), "SchoolZonesFragment")
                (activity as? MainActivity)?.showFragment(SchoolZonesFragment(), true)
            },
            MoreItem("settings", R.drawable.ic_settings, getString(R.string.nav_settings)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_settings", "Plus")
                AnalyticsTracker.screenView(ctx, "Paramètres", "SettingsFragment")
                (activity as? MainActivity)?.showFragment(SettingsFragment(), true)
            },
            MoreItem("tutorial", R.drawable.ic_help, "Tutoriel") {
                AnalyticsTracker.trackAction(ctx, "open", "more_tutorial", "Plus")
                (activity as? MainActivity)?.showTutorial()
            },
            MoreItem("about", R.drawable.ic_info, "À propos") {
                AnalyticsTracker.trackAction(ctx, "open", "more_about", "Plus")
                showAboutDialog(ctx)
            },
            MoreItem("donate", R.drawable.ic_ticket, getString(R.string.nav_donate)) {
                AnalyticsTracker.trackAction(ctx, "open_external", "donate_button", "Plus")
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://donate.stripe.com/5kQaEXe7ea138GL9AhgjC00"))
                    startActivity(intent)
                }
            }
        )

        val items = allItems.filter {
            it.id !in visibleTabs ||
            it.id == "school_zones" ||
            it.id == "settings" ||
            it.id == "tutorial" ||
            it.id == "donate" ||
            it.id == "about"
        }

        items.forEachIndexed { index, item ->
            if (index > 0) {
                val divider = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.marginStart = (56 * resources.displayMetrics.density).toInt() }
                    setBackgroundColor(ContextCompat.getColor(ctx, R.color.divider))
                }
                container.addView(divider)
            }

            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    (56 * resources.displayMetrics.density).toInt()
                )
                setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
                setPadding((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
                setOnClickListener { item.onClick() }
            }

            val icon = ImageView(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    (24 * resources.displayMetrics.density).toInt(),
                    (24 * resources.displayMetrics.density).toInt()
                )
                setImageResource(item.iconRes)
                setColorFilter(ContextCompat.getColor(ctx, R.color.text_primary))
            }
            row.addView(icon)

            val label = TextView(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                    it.marginStart = (16 * resources.displayMetrics.density).toInt()
                }
                text = item.label
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            }
            row.addView(label)

            if (item.id == "donate") {
                applyShinyGoldGradient(label, icon)
            }

            container.addView(row)
        }
    }

    private fun showAboutDialog(ctx: Context) {
        val content = LayoutInflater.from(ctx).inflate(R.layout.dialog_about_modern, null, false)

        content.findViewById<TextView>(R.id.tvAboutTitle).text = getString(R.string.about_title)
        content.findViewById<TextView>(R.id.tvAboutSubtitle).text = getString(R.string.about_subtitle)
        content.findViewById<TextView>(R.id.tvAboutCreator).apply {
            text = getString(R.string.about_creator)
            applySilverAnimatedGradient(this)
        }
        content.findViewById<TextView>(R.id.tvAboutTagline).apply {
            text = getString(R.string.about_tagline)
            textSize = 15f
            applyVibeCodedGradient(this)
        }

        val dialog = android.app.AlertDialog.Builder(ctx)
            .setView(content)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.92f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        content.findViewById<MaterialButton>(R.id.btnAboutClose).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyShinyGoldGradient(textView: TextView, iconView: ImageView) {
        iconView.setColorFilter(0xFFFFD700.toInt())

        textView.post {
            if (_b == null) return@post
            val width = textView.paint.measureText(textView.text.toString())
            if (width <= 0f) return@post

            val colors = intArrayOf(
                0xFFFFD700.toInt(),
                0xFFFFF8DC.toInt(),
                0xFFFFB700.toInt(),
                0xFFFFE57F.toInt(),
                0xFFFFD700.toInt()
            )

            val shader = LinearGradient(
                0f, 0f, width * 1.5f, 0f,
                colors,
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.REPEAT
            )
            textView.paint.shader = shader

            val matrix = Matrix()
            val animator = ValueAnimator.ofFloat(0f, width * 1.5f)
            animator.duration = 2500L
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.RESTART
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener { anim ->
                if (_b == null || !textView.isAttachedToWindow) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val translate = anim.animatedValue as Float
                matrix.setTranslate(translate, 0f)
                shader.setLocalMatrix(matrix)
                textView.invalidate()
            }
            animator.start()
        }
    }

    private fun applySilverAnimatedGradient(textView: TextView) {
        textView.post {
            if (_b == null) return@post
            val width = textView.paint.measureText(textView.text.toString())
            if (width <= 0f) return@post

            val colors = intArrayOf(
                0xFF9EA7B3.toInt(),
                0xFFF5F7FA.toInt(),
                0xFFD7DDE8.toInt(),
                0xFFFFFFFF.toInt(),
                0xFF9EA7B3.toInt()
            )

            val shader = LinearGradient(
                0f, 0f, width * 1.5f, 0f,
                colors,
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.REPEAT
            )
            textView.paint.shader = shader

            val matrix = Matrix()
            val animator = ValueAnimator.ofFloat(0f, width * 1.5f)
            animator.duration = 2800L
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.RESTART
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener { anim ->
                if (_b == null || !textView.isAttachedToWindow) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val translate = anim.animatedValue as Float
                matrix.setTranslate(translate, 0f)
                shader.setLocalMatrix(matrix)
                textView.invalidate()
            }
            animator.start()
        }
    }

    private fun applyVibeCodedGradient(textView: TextView) {
        textView.post {
            if (_b == null) return@post
            val width = textView.paint.measureText(textView.text.toString())
            if (width <= 0f) return@post

            val colors = intArrayOf(
                0xFFA855F7.toInt(),
                0xFFEC4899.toInt(),
                0xFFF472B6.toInt(),
                0xFFEC4899.toInt(),
                0xFFA855F7.toInt()
            )

            val shader = LinearGradient(
                0f, 0f, width * 1.5f, 0f,
                colors,
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.REPEAT
            )
            textView.paint.shader = shader

            val matrix = Matrix()
            val animator = ValueAnimator.ofFloat(0f, width * 1.5f)
            animator.duration = 3000L
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.RESTART
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener { anim ->
                if (_b == null || !textView.isAttachedToWindow) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val translate = anim.animatedValue as Float
                matrix.setTranslate(translate, 0f)
                shader.setLocalMatrix(matrix)
                textView.invalidate()
            }
            animator.start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
