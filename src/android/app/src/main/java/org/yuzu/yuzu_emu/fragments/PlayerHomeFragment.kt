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
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.databinding.CardPlayerOptionBinding
import org.yuzu.yuzu_emu.databinding.FragmentPlayerHomeBinding
import org.yuzu.yuzu_emu.utils.InstallableActions

/**
 * RetroForge: the player has no game list. Opened with no game, it points to RetroForge and
 * offers only what the player itself needs: keys, firmware, GPU driver and the GPL credit.
 */
class PlayerHomeFragment : Fragment() {
    private var _binding: FragmentPlayerHomeBinding? = null
    private val binding get() = _binding!!

    private val getProdKey =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result != null) {
                InstallableActions.processKey(
                    activity = requireActivity(),
                    fragmentManager = parentFragmentManager,
                    result = result,
                    onInstalled = { refreshStatus() }
                )
            }
        }

    private val getFirmware =
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

        val launchRetroForge =
            requireContext().packageManager.getLaunchIntentForPackage(RETROFORGE_PACKAGE)
        binding.buttonOpenRetroforge.apply {
            isEnabled = launchRetroForge != null
            if (launchRetroForge == null) text = getString(R.string.player_retroforge_missing)
            setOnClickListener {
                launchRetroForge?.let {
                    startActivity(it)
                    requireActivity().finish()
                }
            }
        }

        binding.cardKeys.setUp(R.string.player_keys, "") {
            getProdKey.launch(arrayOf("*/*"))
        }
        binding.cardFirmware.setUp(R.string.firmware, "") {
            getFirmware.launch(arrayOf("application/zip"))
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.contentPlayerHome.updatePadding(
                left = insets.left,
                right = insets.right,
                top = insets.top,
                bottom = insets.bottom
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

    private fun refreshStatus() {
        binding.cardKeys.optionDescription.text = getString(
            if (NativeLibrary.areKeysPresent()) {
                R.string.player_keys_installed
            } else {
                R.string.player_keys_missing
            }
        )
        binding.cardFirmware.optionDescription.text =
            if (NativeLibrary.isFirmwareAvailable()) {
                getString(R.string.player_firmware_installed, NativeLibrary.firmwareVersion())
            } else {
                getString(R.string.player_firmware_missing)
            }
    }

    private fun CardPlayerOptionBinding.setUp(
        titleId: Int,
        description: String,
        onClick: () -> Unit
    ) {
        optionTitle.setText(titleId)
        optionDescription.text = description
        root.setOnClickListener { onClick() }
    }

    companion object {
        const val RETROFORGE_PACKAGE = "com.retroforge.app"
    }
}
