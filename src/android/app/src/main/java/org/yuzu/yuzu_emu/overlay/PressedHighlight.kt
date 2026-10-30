// SPDX-FileCopyrightText: Copyright 2026 RetroForge
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.LightingColorFilter
import android.graphics.drawable.BitmapDrawable

/**
 * RetroForge Switch Player: a pressed touch control is its normal picture lit up in RetroForge orange, so it never
 * turns into a faint dark circle under the finger. The grey body turns logo orange and the label stays dark enough to read.
 */
object PressedHighlight {
    private val filter = LightingColorFilter(0xFFB070, 0x602000)

    fun of(res: Resources, normal: Bitmap): BitmapDrawable =
        BitmapDrawable(res, normal).apply { colorFilter = filter }
}
