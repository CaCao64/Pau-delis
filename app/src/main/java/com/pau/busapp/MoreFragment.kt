package com.pau.busapp

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pau.busapp.databinding.FragmentMoreBinding

class MoreFragment : Fragment() {
    private var _b: FragmentMoreBinding? = null
    private val b get() = _b!!

    private data class MoreItem(val id: String, val iconRes: Int, val label: String, val onClick: () -> Unit)

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentMoreBinding.inflate(i, c, false); return b.root
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
            MoreItem("favs",    R.drawable.ic_star,     getString(R.string.nav_favs)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_favorites", "Plus")
                AnalyticsTracker.screenView(ctx, "Favoris", "FavoritesFragment")
                (activity as? MainActivity)?.showFragment(FavoritesFragment(), true)
            },
            MoreItem("search",  R.drawable.ic_search,   getString(R.string.nav_search)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_search", "Plus")
                AnalyticsTracker.screenView(ctx, "Recherche", "SearchFragment")
                (activity as? MainActivity)?.showFragment(SearchFragment(), true)
            },
            MoreItem("alerts",  R.drawable.ic_bell,     getString(R.string.nav_alerts)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_alerts", "Plus")
                (activity as? MainActivity)?.checkNotifPermissionThenOpenAlerts()
            },
            MoreItem("stops",   R.drawable.ic_list,     getString(R.string.nav_stops)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_stops", "Plus")
                AnalyticsTracker.screenView(ctx, "Arrêts", "StopListFragment")
                (activity as? MainActivity)?.showFragment(StopListFragment(), true)
            },
            MoreItem("lines",   R.drawable.ic_route,    getString(R.string.nav_lines)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_lines", "Plus")
                AnalyticsTracker.screenView(ctx, "Lignes", "LinesFragment")
                (activity as? MainActivity)?.showFragment(LinesFragment(), true)
            },
            MoreItem("settings",R.drawable.ic_settings, getString(R.string.nav_settings)) {
                AnalyticsTracker.trackAction(ctx, "open", "more_settings", "Plus")
                AnalyticsTracker.screenView(ctx, "Paramètres", "SettingsFragment")
                (activity as? MainActivity)?.showFragment(SettingsFragment(), true)
            },
            MoreItem("tutorial",R.drawable.ic_help,     "Tutoriel") {
                AnalyticsTracker.trackAction(ctx, "open", "more_tutorial", "Plus")
                (activity as? MainActivity)?.showTutorial()
            },
            MoreItem("donate",  R.drawable.ic_ticket,   getString(R.string.nav_donate)) {
                AnalyticsTracker.trackAction(ctx, "open_external", "donate_button", "Plus")
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://donate.stripe.com/5kQaEXe7ea138GL9AhgjC00"))
                    startActivity(intent)
                }
            }
        )

        val items = allItems.filter { it.id !in visibleTabs || it.id == "settings" || it.id == "tutorial" || it.id == "donate" }

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

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (56 * resources.displayMetrics.density).toInt()
                )
                setBackgroundColor(ContextCompat.getColor(ctx, R.color.surface))
                setPadding((20 * resources.displayMetrics.density).toInt(), 0, (20 * resources.displayMetrics.density).toInt(), 0)
                setOnClickListener { item.onClick() }
            }

            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (24 * resources.displayMetrics.density).toInt(),
                    (24 * resources.displayMetrics.density).toInt()
                )
                setImageResource(item.iconRes)
                setColorFilter(ContextCompat.getColor(ctx, R.color.text_primary))
            }
            row.addView(icon)

            val label = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
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

        // Vibe coded footer
        val footerContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = (32 * resources.displayMetrics.density).toInt()
                it.bottomMargin = (16 * resources.displayMetrics.density).toInt()
            }
        }

        val footerText1 = TextView(ctx).apply {
            text = "© 2026 Pau'delis · Projet commencé à 14 ans"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
        }
        footerContainer.addView(footerText1)

        val footerText2 = TextView(ctx).apply {
            text = "✨ Entièrement vibe codé ✨"
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (6 * resources.displayMetrics.density).toInt() }
        }
        footerContainer.addView(footerText2)
        applyVibeCodedGradient(footerText2)

        container.addView(footerContainer)
    }

    private fun applyShinyGoldGradient(textView: TextView, iconView: ImageView) {
        iconView.setColorFilter(0xFFFFD700.toInt())

        textView.post {
            if (_b == null) return@post
            val width = textView.paint.measureText(textView.text.toString())
            if (width <= 0f) return@post

            val colors = intArrayOf(
                0xFFFFD700.toInt(), // Gold
                0xFFFFF8DC.toInt(), // White Gold Reflect
                0xFFFFB700.toInt(), // Amber Gold
                0xFFFFE57F.toInt(), // Neon Gold
                0xFFFFD700.toInt()  // Gold
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
                if (_b == null) {
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
                0xFFA855F7.toInt(), // #a855f7
                0xFFEC4899.toInt(), // #ec4899
                0xFFF472B6.toInt(), // #f472b6
                0xFFEC4899.toInt(), // #ec4899
                0xFFA855F7.toInt()  // #a855f7
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
                if (_b == null) {
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

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
