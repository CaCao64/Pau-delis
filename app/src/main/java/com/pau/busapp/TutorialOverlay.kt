package com.pau.busapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.view.MotionEvent
import android.widget.*
import androidx.core.content.ContextCompat

class TutorialOverlay(
    private val ctx: Context,
    private val rootView: ViewGroup,
    private val onFinished: () -> Unit
) {

    data class Step(val title: String, val body: String, val emoji: String = "")

    private val steps = listOf(
        Step(ctx.getString(R.string.tuto_welcome_title), ctx.getString(R.string.tuto_welcome_body)),
        Step(ctx.getString(R.string.tuto_map_title), ctx.getString(R.string.tuto_map_body), "🗺️"),
        Step(ctx.getString(R.string.tuto_locate_title), ctx.getString(R.string.tuto_locate_body)),
        Step(ctx.getString(R.string.tuto_nearest_title), ctx.getString(R.string.tuto_nearest_body), "🚏"),
        Step(ctx.getString(R.string.tuto_favs_title), ctx.getString(R.string.tuto_favs_body), "⭐"),
        Step(ctx.getString(R.string.tuto_colors_title), ctx.getString(R.string.tuto_colors_body)),
        Step(ctx.getString(R.string.tuto_search_title), ctx.getString(R.string.tuto_search_body), "🔍"),
        Step(ctx.getString(R.string.tuto_alerts_title), ctx.getString(R.string.tuto_alerts_body), "🔔"),
        Step(ctx.getString(R.string.tuto_customize_alert_title), ctx.getString(R.string.tuto_customize_alert_body)),
        Step(ctx.getString(R.string.tuto_tabs_title), ctx.getString(R.string.tuto_tabs_body)),
        Step(ctx.getString(R.string.tuto_settings_title), ctx.getString(R.string.tuto_settings_body), "⚙️"),
        Step(ctx.getString(R.string.tuto_widget_title), ctx.getString(R.string.tuto_widget_body), "📱"),
        Step(ctx.getString(R.string.tuto_go_title), ctx.getString(R.string.tuto_go_body), "🚀")
    )

    private var currentStep = 0
    private var overlay: View? = null

    // Views mis à jour à chaque étape
    private lateinit var tvStep: TextView
    private lateinit var tvEmoji: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvBody: TextView
    private lateinit var btnNext: TextView
    private lateinit var btnSkip: TextView
    private lateinit var progressDots: LinearLayout

    fun setCurrentStep(index: Int) {
        if (index in steps.indices) {
            currentStep = index
        }
    }

    fun show() {
        if (overlay != null) return
        buildOverlay()
        bindStep()
        overlay?.alpha = 0f
        rootView.addView(overlay)
        overlay?.animate()?.alpha(1f)?.setDuration(300)?.start()
    }

    private fun buildOverlay() {
        val density = ctx.resources.displayMetrics.density

        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xBB000000.toInt())
            elevation = 9999f
            isClickable = true
            isFocusable = true
        }

        // Card centrale
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.surface))
                cornerRadius = 20 * density
            }
            elevation = 10000f
            setPadding(
                (24 * density).toInt(), (28 * density).toInt(),
                (24 * density).toInt(), (20 * density).toInt()
            )
            layoutParams = FrameLayout.LayoutParams(
                (320 * density).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER }
        }

        // Indicateur étape (1/13)
        tvStep = TextView(ctx).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (4 * density).toInt() }
        }
        card.addView(tvStep)

        // Barre de progression
        val progressBar = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (4 * density).toInt()
            ).also { it.bottomMargin = (16 * density).toInt() }
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.divider))
                cornerRadius = 2 * density
            }
        }
        val progressFill = View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(ctx, R.color.green_primary))
                cornerRadius = 2 * density
            }
            tag = "progress_fill"
        }
        progressBar.addView(progressFill)
        card.addView(progressBar)

        // Emoji
        tvEmoji = TextView(ctx).apply {
            textSize = 36f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * density).toInt() }
        }
        card.addView(tvEmoji)

        // Titre
        tvTitle = TextView(ctx).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (12 * density).toInt() }
        }
        card.addView(tvTitle)

        // Corps
        tvBody = TextView(ctx).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (24 * density).toInt() }
        }
        card.addView(tvBody)

        // Dots de progression
        progressDots = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (16 * density).toInt() }
        }
        card.addView(progressDots)

        // Boutons
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        btnSkip = TextView(ctx).apply {
            text = ctx.getString(R.string.btn_skip)
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnSkip.setOnClickListener { dismiss() }

        btnNext = TextView(ctx).apply {
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.green_primary))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding((8 * density).toInt(), (12 * density).toInt(), 0, (12 * density).toInt())
        }
        btnNext.setOnClickListener { nextStep(forward = true) }

        btnRow.addView(btnSkip)
        btnRow.addView(btnNext)
        card.addView(btnRow)

        // Swipe horizontal pour naviguer entre étapes
        var swipeStartX = 0f
        card.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { swipeStartX = ev.rawX; false }
                MotionEvent.ACTION_UP -> {
                    val dx = ev.rawX - swipeStartX
                    when {
                        dx < -80f -> { nextStep(forward = true);  true }
                        dx >  80f -> { nextStep(forward = false); true }
                        else -> false
                    }
                }
                else -> false
            }
        }

        root.addView(card)
        overlay = root
    }

    private fun bindStep() {
        val density = ctx.resources.displayMetrics.density
        val step = steps[currentStep]
        val total = steps.size

        tvStep.text = "${currentStep + 1}/$total"
        tvEmoji.text = step.emoji
        tvEmoji.visibility = if (step.emoji.isEmpty()) View.GONE else View.VISIBLE
        tvTitle.text = step.title
        
        if (currentStep == 5) { // Étape des couleurs
            val fullText = ctx.getString(R.string.tuto_colors_body, 
                ctx.getString(R.string.tuto_color_green), 
                ctx.getString(R.string.tuto_color_orange), 
                ctx.getString(R.string.tuto_color_blue), 
                ctx.getString(R.string.tuto_color_red_strike))
            val spannable = android.text.SpannableString(fullText)
            
            fun colorWord(word: String, colorRes: Int, strike: Boolean = false) {
                val start = fullText.indexOf(word)
                if (start != -1) {
                    spannable.setSpan(android.text.style.ForegroundColorSpan(ContextCompat.getColor(ctx, colorRes)), start, start + word.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, start + word.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (strike) spannable.setSpan(android.text.style.StrikethroughSpan(), start, start + word.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            
            colorWord(ctx.getString(R.string.tuto_color_green), R.color.green_primary)
            colorWord(ctx.getString(R.string.tuto_color_orange), R.color.orange_primary)
            colorWord(ctx.getString(R.string.tuto_color_blue), R.color.blue_primary)
            colorWord(ctx.getString(R.string.tuto_color_red_strike), R.color.red_primary, true)
            
            tvBody.text = spannable
        } else {
            tvBody.text = step.body
        }

        // Bouton suivant / terminer
        val isLast = currentStep == total - 1
        btnNext.text = if (isLast) ctx.getString(R.string.btn_finish) else ctx.getString(R.string.btn_next)
        btnSkip.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

        // Barre de progression
        val fill = overlay?.findViewWithTag<View>("progress_fill")
        fill?.post {
            val parent = fill.parent as? FrameLayout ?: return@post
            val fraction = (currentStep + 1).toFloat() / total.toFloat()
            fill.layoutParams = FrameLayout.LayoutParams(
                (parent.width * fraction).toInt(),
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            fill.requestLayout()
        }

        // Dots
        progressDots.removeAllViews()
        for (i in 0 until total) {
            val dot = View(ctx).apply {
                val size = (if (i == currentStep) 10 else 6) * density
                layoutParams = LinearLayout.LayoutParams(size.toInt(), size.toInt()).also {
                    it.marginEnd = (4 * density).toInt()
                    it.gravity = Gravity.CENTER_VERTICAL
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (i == currentStep) ContextCompat.getColor(ctx, R.color.green_primary)
                        else ContextCompat.getColor(ctx, R.color.divider)
                    )
                }
            }
            progressDots.addView(dot)
        }
    }

    private fun nextStep(forward: Boolean = true) {
        if (forward && currentStep >= steps.size - 1) { dismiss(); return }
        if (!forward && currentStep <= 0) return

        val card = (overlay as? FrameLayout)?.getChildAt(0) ?: run {
            if (forward) currentStep++ else currentStep--
            bindStep(); return
        }
        val cardW = card.width.toFloat().takeIf { it > 0f } ?: 900f
        val exitX  = if (forward) -cardW else  cardW
        val enterX = if (forward)  cardW else -cardW

        // Slide out
        card.animate()
            .translationX(exitX)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(android.view.animation.AccelerateInterpolator(1.2f))
            .withEndAction {
                if (forward) currentStep++ else currentStep--
                bindStep()
                // Positionner hors écran côté opposé, puis slide in
                card.translationX = enterX
                card.alpha = 0f
                card.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                    .start()
            }
            .start()
    }

    private fun dismiss() {
        TutorialManager.markDone(ctx)
        overlay?.animate()?.alpha(0f)?.setDuration(250)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rootView.removeView(overlay)
                    overlay = null
                    onFinished()
                }
            })?.start()
    }
}
