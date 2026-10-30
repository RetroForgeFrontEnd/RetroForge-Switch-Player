// SPDX-FileCopyrightText: Copyright 2026 RetroForge
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.overlay

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

/**
 * RetroForge's pause menu, the same one RetroForge's own players show: a dimmed game, a dark card
 * with Back and ✕ that stay in reach, a title, and a column of outlined buttons. Sub-pages keep the
 * main page short. Opened by swiping in from either screen edge, or with Back.
 */
class PauseMenu(private val container: FrameLayout, private val onClosed: () -> Unit) {
    data class Item(val label: String, val action: () -> Unit)

    data class Page(
        val title: String,
        val subtitle: String? = null,
        val isMain: Boolean = false,
        val items: List<Item>
    )

    private val context get() = container.context
    private var build: (() -> Page)? = null
    private var onBack: (() -> Unit)? = null

    /** A short message under the title (e.g. "Resolution changes on the next start"). */
    var notice: String? = null

    val isShowing get() = container.visibility == View.VISIBLE

    private var firstButton: View? = null

    /** Puts the controller's focus on the page's first button unless something in the menu has it. */
    fun focusForController(): Boolean {
        if (!isShowing || container.findFocus() != null) return false
        return firstButton?.requestFocusFromTouch() == true
    }

    /** Shows the page [page] builds; [back] runs for the Back button on sub-pages. */
    fun show(back: () -> Unit, page: () -> Page) {
        onBack = back
        build = page
        render()
        container.visibility = View.VISIBLE
    }

    /** Builds the current page again, after a choice changed one of its labels. */
    fun refresh() {
        if (isShowing) render()
    }

    fun close() {
        if (!isShowing) return
        container.visibility = View.GONE
        container.removeAllViews()
        notice = null
        onClosed()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun render() {
        val page = build?.invoke() ?: return
        container.removeAllViews()
        container.setBackgroundColor(BACKDROP)
        // Taps on the dimmed game close the menu, as in RetroForge.
        container.setOnClickListener { close() }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(16f)
            }
            isClickable = true // keeps taps on the card from closing the menu
        }

        val header = FrameLayout(context).apply {
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        if (!page.isMain) {
            header.addView(
                textButton("Back") { onBack?.invoke() },
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL)
            )
        }
        header.addView(
            textButton("✕", 20f) { close() },
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL)
        )
        card.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        column.addView(text(page.title, Color.WHITE, 16f, bold = true))
        page.subtitle?.let { column.addView(text(it, SECONDARY, 13f), spaced()) }
        notice?.let { column.addView(text(it, NOTICE, 13f), spaced()) }

        var first: View? = null
        page.items.forEach { item ->
            val button = menuButton(item.label, item.action)
            column.addView(button, spaced())
            if (first == null) first = button
        }

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(column, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        card.addView(scroll, LinearLayout.LayoutParams(MATCH, WRAP))

        val maxHeight = (container.height.takeIf { it > 0 } ?: dp(600)) - dp(32)
        container.addView(
            card,
            FrameLayout.LayoutParams(dp(320), WRAP, Gravity.CENTER)
        )
        card.post {
            if (card.height > maxHeight) {
                card.layoutParams = (card.layoutParams as FrameLayout.LayoutParams).apply {
                    height = maxHeight.coerceAtMost(dp(560))
                }
            }
        }
        firstButton = first
        if (!container.isInTouchMode) first?.requestFocus()
    }

    private fun menuButton(label: String, action: () -> Unit): MaterialButton =
        MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            isAllCaps = false
            setTextColor(LABEL)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            cornerRadius = dp(20)
            minHeight = dp(40)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            rippleColor = ColorStateList.valueOf(0x33D0BCFF)
            strokeColor = ColorStateList.valueOf(OUTLINE)
            strokeWidth = dp(1)
            isFocusable = true
            setOnFocusChangeListener { _, focused ->
                strokeColor = ColorStateList.valueOf(if (focused) FOCUS else OUTLINE)
                strokeWidth = dp(if (focused) 3 else 1)
            }
            setOnClickListener { action() }
        }

    private fun textButton(label: String, size: Float = 14f, action: () -> Unit): MaterialButton =
        MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.borderlessButtonStyle
        ).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            setOnClickListener { action() }
        }

    private fun text(value: String, color: Int, size: Float, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun spaced() = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }

    private fun dp(value: Int): Int = dp(value.toFloat()).roundToInt()
    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        // RetroForge's pause menu colours
        private const val BACKDROP = 0xCC000000.toInt()
        private const val CARD = 0xFF1C1C22.toInt()
        private const val OUTLINE = 0xFF55555F.toInt()
        private const val FOCUS = 0xFFFF8A1F.toInt()
        private const val LABEL = 0xFFD0BCFF.toInt()
        private const val SECONDARY = 0xFFAAAAB4.toInt()
        private const val NOTICE = 0xFF9FE3B0.toInt()

        /**
         * Makes [edge] (a thin strip at the left or right of the screen) open the menu when the
         * player swipes in from it, the way RetroForge's players do.
         */
        @SuppressLint("ClickableViewAccessibility")
        fun attachEdgeSwipe(edge: View, fromLeft: Boolean, open: () -> Unit) {
            var startX = 0f
            var fired = false
            edge.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        fired = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val travelled = if (fromLeft) event.rawX - startX else startX - event.rawX
                        if (!fired && travelled > 48 * view.resources.displayMetrics.density) {
                            fired = true
                            open()
                        }
                    }
                }
                true
            }
        }
    }
}
