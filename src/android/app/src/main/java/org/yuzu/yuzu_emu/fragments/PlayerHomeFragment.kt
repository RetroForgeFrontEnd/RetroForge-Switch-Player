// SPDX-FileCopyrightText: Copyright 2026 RetroForge
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.databinding.CardPlayerOptionBinding
import org.yuzu.yuzu_emu.databinding.FragmentPlayerHomeBinding
import org.yuzu.yuzu_emu.features.settings.model.BooleanSetting
import org.yuzu.yuzu_emu.ui.main.MainActivity
import org.yuzu.yuzu_emu.utils.InstallableActions
import org.yuzu.yuzu_emu.utils.NativeConfig

/**
 * RetroForge: "Switch settings". The player has no game list; RetroForge opens this screen
 * (as a first-run step with [MainActivity.EXTRA_SETUP], or from its settings), and so does the
 * pause menu. It offers only what the player itself needs: keys, firmware, GPU driver and About.
 */
class PlayerHomeFragment : Fragment() {
    private var _binding: FragmentPlayerHomeBinding? = null
    private val binding get() = _binding!!

    private val extras get() = requireActivity().intent
    private val setupOnly get() = extras.getBooleanExtra(MainActivity.EXTRA_SETUP, false)
    private val fromGame get() = extras.getBooleanExtra(MainActivity.EXTRA_FROM_GAME, false)

    private val getKeys =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { results ->
            // prod.keys, and title.keys when it was picked too
            results.forEach { result ->
                InstallableActions.processKey(
                    activity = requireActivity(),
                    fragmentManager = parentFragmentManager,
                    result = result,
                    onInstalled = { if (_binding != null) refreshStatus() }
                )
            }
        }

    private val getFirmwareZip =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result != null) {
                InstallableActions.processFirmware(
                    activity = requireActivity(),
                    fragmentManager = parentFragmentManager,
                    result = result,
                    onComplete = { if (_binding != null) refreshStatus() }
                )
            }
        }

    private val getFirmwareFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { result ->
            if (result != null) {
                InstallableActions.processFirmwareFolder(
                    activity = requireActivity(),
                    fragmentManager = parentFragmentManager,
                    result = result,
                    onComplete = { if (_binding != null) refreshStatus() }
                )
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerHomeBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back and the red X close it: to RetroForge, or to the paused game
        binding.buttonClose.setOnClickListener { requireActivity().finish() }
        binding.buttonDone.isVisible = setupOnly
        binding.buttonDone.setOnClickListener { requireActivity().finish() }

        val description = when {
            extras.getBooleanExtra(MainActivity.EXTRA_MISSING, false) -> R.string.player_missing_description
            setupOnly -> R.string.player_setup_description
            fromGame -> R.string.player_from_game_description
            else -> null
        }
        binding.textDescription.isVisible = description != null
        description?.let { binding.textDescription.setText(it) }

        binding.cardKeys.setUp(R.string.player_install_keys, "") {
            getKeys.launch(arrayOf("*/*"))
        }
        binding.cardFirmware.setUp(R.string.player_install_firmware, "") {
            chooseFirmwareSource()
        }
        binding.cardDriver.setUp(
            R.string.gpu_driver_manager,
            getString(R.string.player_gpu_driver_description)
        ) {
            findNavController().navigate(R.id.driverManagerFragment)
        }
        binding.cardAbout.setUp(R.string.about, getString(R.string.player_about_description)) {
            findNavController().navigate(R.id.aboutFragment)
        }
        binding.cardPerfOverlay.setUp(R.string.player_perf_overlay, "") {
            // One switch for both of the game's stats overlays, as in the pause menu
            val show = !isPerfOverlayShown()
            BooleanSetting.SHOW_PERFORMANCE_OVERLAY.setBoolean(show)
            BooleanSetting.SHOW_SOC_OVERLAY.setBoolean(show)
            NativeConfig.saveGlobalConfig()
            refreshStatus()
        }
        binding.cardPerfOverlay.root.isVisible = !setupOnly
        binding.cardDriver.root.isVisible = !setupOnly
        binding.cardAbout.root.isVisible = !setupOnly

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val gutter = (16 * resources.displayMetrics.density).toInt()
            binding.contentPlayerHome.updatePadding(
                left = insets.left + gutter,
                right = insets.right + gutter,
                top = insets.top + gutter,
                bottom = insets.bottom + gutter
            )
            windowInsets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun chooseFirmwareSource() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.player_install_firmware)
            .setItems(
                arrayOf(
                    getString(R.string.player_firmware_from_folder),
                    getString(R.string.player_firmware_from_zip)
                )
            ) { _, which ->
                if (which == 0) getFirmwareFolder.launch(null) else getFirmwareZip.launch(arrayOf("application/zip"))
            }
            .show()
    }

    private fun refreshStatus() {
        val keys = NativeLibrary.areKeysPresent()
        binding.cardKeys.optionDescription.text = getString(
            if (keys) R.string.player_keys_installed else R.string.player_keys_pick
        ) + if (keys) "" else "\n" + getString(R.string.player_keys_missing)
        binding.cardFirmware.optionDescription.text =
            if (NativeLibrary.isFirmwareAvailable()) {
                getString(R.string.player_firmware_installed, NativeLibrary.firmwareVersion())
            } else {
                getString(R.string.player_firmware_missing)
            }
        binding.cardPerfOverlay.optionDescription.text = getString(
            R.string.player_perf_overlay_description,
            getString(if (isPerfOverlayShown()) R.string.player_menu_on else R.string.player_menu_off)
        )
        (activity as? MainActivity)?.reportSetupState()
    }

    private fun isPerfOverlayShown() =
        BooleanSetting.SHOW_PERFORMANCE_OVERLAY.getBoolean() || BooleanSetting.SHOW_SOC_OVERLAY.getBoolean()

    private fun CardPlayerOptionBinding.setUp(
        titleId: Int,
        description: String,
        onClick: () -> Unit
    ) {
        optionTitle.setText(titleId)
        optionDescription.text = description
        root.setOnClickListener { onClick() }
    }
}
