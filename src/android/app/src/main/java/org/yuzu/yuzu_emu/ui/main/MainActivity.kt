// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.activities.EmulationActivity
import org.yuzu.yuzu_emu.databinding.ActivityMainBinding
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.FullscreenHelper
import org.yuzu.yuzu_emu.utils.ThemeHelper

/**
 * RetroForge: the player's launcher entry. It hosts only the no-game screen
 * (PlayerHomeFragment) and its keys, firmware, GPU driver and About pages; games are
 * opened from RetroForge straight into EmulationActivity.
 */
class MainActivity : AppCompatActivity(), ThemeProvider {
    private lateinit var binding: ActivityMainBinding

    override var themeId: Int = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(YuzuApplication.applyLanguage(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getBooleanExtra(EXTRA_CHECK, false)) {
            super.onCreate(savedInstanceState)
            reportSetupState()
            finish()
            return
        }
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !DirectoryInitialization.areDirectoriesReady }

        ThemeHelper.ThemeChangeListener(this)
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        NativeLibrary.initMultiplayer()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window.statusBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)
        window.navigationBarColor =
            ContextCompat.getColor(applicationContext, android.R.color.transparent)

        // Dismiss previous notifications (should not happen unless a crash occurred)
        EmulationActivity.stopForegroundService(this)
        applyFullscreenPreference()
    }

    override fun onResume() {
        ThemeHelper.setCorrectTheme(this)
        super.onResume()
        applyFullscreenPreference()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreenPreference()
        }
    }

    private fun applyFullscreenPreference() {
        FullscreenHelper.applyToActivity(this)
    }

    /** Result for RetroForge: OK once keys and firmware are both installed, with the details as extras. */
    fun reportSetupState() {
        val keys = NativeLibrary.areKeysPresent()
        val firmware = NativeLibrary.isFirmwareAvailable()
        setResult(
            if (keys && firmware) RESULT_OK else RESULT_CANCELED,
            Intent()
                .putExtra(RESULT_KEYS, keys)
                .putExtra(RESULT_FIRMWARE, firmware)
                .putExtra(RESULT_FIRMWARE_VERSION, if (firmware) NativeLibrary.firmwareVersion() else "")
        )
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)
        themeId = resId
    }

    override fun onDestroy() {
        EmulationActivity.stopForegroundService(this)
        super.onDestroy()
    }

    companion object {
        /** RetroForge's first-run step: Keys and Firmware only, with a Done button. */
        const val EXTRA_SETUP = "setup"

        /** Answer at once (result code and extras below) without showing anything. */
        const val EXTRA_CHECK = "check"

        /** Opened from a running game's pause menu: changes apply the next time a game starts. */
        const val EXTRA_FROM_GAME = "from_game"

        /** A game was started without keys or firmware. */
        const val EXTRA_MISSING = "missing"

        const val RESULT_KEYS = "keys"
        const val RESULT_FIRMWARE = "firmware"
        const val RESULT_FIRMWARE_VERSION = "firmware_version"
    }
}
