package com.pau.busapp

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object ModernDialogs {

    fun showMessage(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        onPositive: (() -> Unit)? = null,
        onNegative: (() -> Unit)? = null
    ) {
        val content = buildShell(context, title)

        val messageView = TextView(context).apply {
            text = message
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(context, 10), 0, dp(context, 20))
        }
        content.body.addView(messageView)

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        negativeText?.let {
            actions.addView(makeButton(context, it, false).apply {
                setOnClickListener { onNegative?.invoke(); content.dialog.dismiss() }
            })
        }
        actions.addView(makeButton(context, positiveText, true).apply {
            setOnClickListener { onPositive?.invoke(); content.dialog.dismiss() }
        })
        content.body.addView(actions)

        show(context, content.dialog)
    }

    fun showChoice(
        context: Context,
        title: String,
        items: List<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val content = buildShell(context, title)
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(list)

        items.forEachIndexed { index, label ->
            val card = MaterialCardView(context).apply {
                radius = dp(context, 16).toFloat()
                cardElevation = 0f
                strokeWidth = dp(context, if (index == selectedIndex) 2 else 1)
                setStrokeColor(
                    ContextCompat.getColorStateList(
                        context,
                        if (index == selectedIndex) R.color.green_primary else R.color.divider
                    )
                )
                setCardBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (index == selectedIndex) R.color.green_light else R.color.surface
                    )
                )
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 0 else dp(context, 10)
                }
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 14), dp(context, 12), dp(context, 16), dp(context, 12))
            }

            row.addView(RadioButton(context).apply {
                isChecked = index == selectedIndex
                isClickable = false
                buttonTintList = ContextCompat.getColorStateList(context, R.color.green_primary)
            })

            row.addView(TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(context, 12)
                }
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            })

            card.addView(row)
            card.setOnClickListener {
                onSelected(index)
                content.dialog.dismiss()
            }
            list.addView(card)
        }

        content.body.addView(scroll)
        show(context, content.dialog)
    }

    private fun buildShell(context: Context, title: String): Shell {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20))
        }
        val card = MaterialCardView(context).apply {
            radius = dp(context, 20).toFloat()
            cardElevation = dp(context, 12).toFloat()
            strokeWidth = 0
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        outer.addView(card)

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(context, 4))
        }
        card.addView(body)

        body.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 6)
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.green_primary))
        })

        body.addView(TextView(context).apply {
            text = title
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 10))
        })

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), 0, dp(context, 20), dp(context, 20))
        }
        body.addView(inner)

        val dialog = AlertDialog.Builder(context).setView(outer).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setLayout(
                (context.resources.displayMetrics.widthPixels * 0.92f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return Shell(dialog, inner)
    }

    private fun show(context: Context, dialog: AlertDialog) {
        dialog.show()
    }

    private fun makeButton(context: Context, text: String, primary: Boolean): MaterialButton {
        return MaterialButton(context).apply {
            this.text = text
            minWidth = 0
            isAllCaps = true
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
            cornerRadius = dp(context, 18)
            if (primary) {
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.green_primary)
            } else {
                backgroundTintList = ContextCompat.getColorStateList(context, R.color.green_dark)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(context, 10)
            }
        }
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    private data class Shell(val dialog: AlertDialog, val body: LinearLayout)
}
