// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.transition.MaterialSharedAxis
import org.yuzu.yuzu_emu.HomeNavigationDirections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.adapters.DriverAdapter
import org.yuzu.yuzu_emu.databinding.FragmentDriverManagerBinding
import org.yuzu.yuzu_emu.features.settings.model.Settings
import org.yuzu.yuzu_emu.features.settings.model.StringSetting
import org.yuzu.yuzu_emu.features.settings.ui.SettingsSubscreen
import org.yuzu.yuzu_emu.model.DriverViewModel
import org.yuzu.yuzu_emu.model.HomeViewModel
import org.yuzu.yuzu_emu.utils.FileUtil
import org.yuzu.yuzu_emu.utils.GpuDriverHelper
import org.yuzu.yuzu_emu.utils.NativeConfig
import org.yuzu.yuzu_emu.utils.Log
import org.yuzu.yuzu_emu.utils.RecommendedDriver
import org.yuzu.yuzu_emu.utils.RecommendedDriver.canUseTurnip
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.yuzu.yuzu_emu.databinding.DialogProgressBinding
import org.yuzu.yuzu_emu.utils.ViewUtils.updateMargins
import org.yuzu.yuzu_emu.utils.collect
import java.io.File
import java.io.IOException

class DriverManagerFragment : Fragment() {
    private var _binding: FragmentDriverManagerBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val driverViewModel: DriverViewModel by activityViewModels()

    private val args by navArgs<DriverManagerFragmentArgs>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriverManagerBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel.setStatusBarShadeVisibility(visible = false)

        driverViewModel.onOpenDriverManager(args.game)
        if (NativeConfig.isPerGameConfigLoaded()) {
            binding.toolbarDrivers.inflateMenu(R.menu.menu_driver_manager)
            driverViewModel.showClearButton(!StringSetting.DRIVER_PATH.global)
            binding.toolbarDrivers.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_driver_use_global -> {
                        StringSetting.DRIVER_PATH.global = true
                        driverViewModel.updateDriverList()
                        (binding.listDrivers.adapter as DriverAdapter)
                            .replaceList(driverViewModel.driverList.value)
                        driverViewModel.showClearButton(false)
                        true
                    }

                    else -> false
                }
            }

            driverViewModel.showClearButton.collect(viewLifecycleOwner) {
                binding.toolbarDrivers.menu.findItem(R.id.menu_driver_use_global).isVisible = it
            }
        }

        driverViewModel.shouldShowDriverShaderDialog.collect(viewLifecycleOwner) { shouldShow ->
            if (shouldShow) {
                showDriverShaderWipeDialog()
            }
        }

        if (!driverViewModel.isInteractionAllowed.value) {
            DriversLoadingDialogFragment().show(
                childFragmentManager,
                DriversLoadingDialogFragment.TAG
            )
        }

        binding.toolbarDrivers.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.buttonInstall.setOnClickListener {
            getDriver.launch(arrayOf("application/zip"))
        }

        binding.buttonFetch.setOnClickListener {
            val action = HomeNavigationDirections.actionGlobalSettingsSubscreenActivity(
                SettingsSubscreen.DRIVER_FETCHER,
                null
            )
            binding.root.findNavController().navigate(action)
        }

        binding.listDrivers.apply {
            layoutManager = GridLayoutManager(
                requireContext(),
                resources.getInteger(R.integer.grid_columns)
            )
            adapter = DriverAdapter(driverViewModel)
        }

        setInsets()
        setUpRecommendedDriver()

        if (!GpuDriverHelper.supportsCustomDriverLoading()) {
            showDriverWarningDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        driverViewModel.onCloseDriverManager(args.game)
    }

    override fun onResume() {
        super.onResume()
        refreshDriverList()
    }

    private fun refreshDriverList() {
        driverViewModel.reloadDriverData()
        (binding.listDrivers.adapter as? DriverAdapter)
            ?.replaceList(driverViewModel.driverList.value)
        updateRecommendedDriverInUse()
    }

    // RetroForge: "Recommended for your GPU" card
    private var gpuModel: String? = null
    private var recommendedText = ""

    private fun setUpRecommendedDriver() {
        gpuModel = runCatching {
            GpuDriverHelper.hookLibPath?.let { GpuDriverHelper.getGpuModel(hookLibPath = it) }
        }.getOrNull()
        val family = RecommendedDriver.family(gpuModel)
        val gpuName = gpuModel ?: getString(R.string.unknown)
        Log.info("[RecommendedDriver] GPU: $gpuName, family: $family")
        binding.cardRecommendedDriver.isVisible = true
        if (!family.canUseTurnip || !GpuDriverHelper.supportsCustomDriverLoading()) {
            recommendedText = getString(R.string.recommended_driver_not_adreno, gpuName)
            binding.rowRecommendedButtons.isVisible = false
            binding.buttonFetch.isVisible = false
            binding.buttonInstall.isVisible = false
        } else {
            recommendedText = getString(
                if (family == RecommendedDriver.Family.ADRENO_8XX) {
                    R.string.recommended_driver_turnip_8xx
                } else {
                    R.string.recommended_driver_turnip
                },
                gpuName
            )
            binding.buttonUseRecommended.setOnClickListener { installRecommended(family) }
            binding.buttonUseSystemDriver.setOnClickListener {
                driverViewModel.onDriverSelected(0)
                NativeConfig.saveGlobalConfig()
                refreshDriverList()
                Toast.makeText(
                    requireContext(),
                    R.string.recommended_driver_phone_selected,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        updateRecommendedDriverInUse()
    }

    private fun updateRecommendedDriverInUse() {
        val b = _binding ?: return
        if (!b.cardRecommendedDriver.isVisible) return
        val selected = GpuDriverHelper.customDriverSettingData
        val inUse = selected.name?.let { "$it ${selected.version ?: ""}".trim() }
            ?: getString(R.string.system_gpu_driver)
        b.textRecommendedBody.text =
            recommendedText + "\n\n" + getString(R.string.recommended_driver_in_use, inUse)
    }

    private fun installRecommended(family: RecommendedDriver.Family) {
        val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
        dialogBinding.progressBar.isIndeterminate = true
        dialogBinding.title.text = getString(R.string.installing_driver)
        dialogBinding.status.text = getString(R.string.recommended_driver_looking)
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        progressDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val download = withContext(Dispatchers.IO) { RecommendedDriver.find(family) }
                    ?: throw IOException(getString(R.string.recommended_driver_none_found))
                Log.info("[RecommendedDriver] ${download.release}: ${download.url}")
                val driverPath = "${GpuDriverHelper.driverStoragePath}${download.fileName}"
                if (!GpuDriverHelper.isDriverZipInstalledByName(download.fileName)) {
                    dialogBinding.status.text = getString(R.string.downloading)
                    val file = withContext(Dispatchers.IO) {
                        RecommendedDriver.fetch(requireContext(), download)
                    }
                    dialogBinding.status.text = getString(R.string.installing)
                    val copied = withContext(Dispatchers.IO) {
                        GpuDriverHelper.copyDriverToInternalStorage(file.toUri())
                    }
                    file.delete()
                    if (!copied) {
                        throw IOException(getString(R.string.failed_install_driver, download.fileName))
                    }
                }
                val metadata = GpuDriverHelper.getMetadataFromZip(File(driverPath))
                driverViewModel.onDriverAdded(Pair(driverPath, metadata))
                NativeConfig.saveGlobalConfig()
                metadata.name ?: download.fileName
            }
            progressDialog.dismiss()
            if (_binding == null) return@launch
            refreshDriverList()
            result.onSuccess {
                Log.info("[RecommendedDriver] selected $it")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.recommended_driver_installed, it),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Log.warning("[RecommendedDriver] failed: $it")
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error_during_fetch)
                    .setMessage(it.message ?: it.toString())
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }

    private fun setInsets() =
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _: View, windowInsets: WindowInsetsCompat ->
            val barInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val leftInsets = barInsets.left + cutoutInsets.left
            val rightInsets = barInsets.right + cutoutInsets.right

            binding.toolbarDrivers.updateMargins(left = leftInsets, right = rightInsets)
            binding.listDrivers.updateMargins(left = leftInsets, right = rightInsets)

            val fabSpacing = resources.getDimensionPixelSize(R.dimen.spacing_fab)
            binding.buttonInstall.updateMargins(
                left = leftInsets + fabSpacing,
                right = rightInsets + fabSpacing,
                bottom = barInsets.bottom + fabSpacing
            )

            binding.buttonFetch.updateMargins(
                left = leftInsets + fabSpacing,
                right = rightInsets + fabSpacing,
                bottom = barInsets.bottom + fabSpacing
            )

            binding.listDrivers.updatePadding(
                bottom = barInsets.bottom +
                    resources.getDimensionPixelSize(R.dimen.spacing_bottom_list_fab)
            )

            windowInsets
        }

    private val getDriver =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
            if (result == null) {
                return@registerForActivityResult
            }

            ProgressDialogFragment.newInstance(
                requireActivity(),
                R.string.installing_driver,
                false
            ) { _, _ ->
                val driverPath =
                    "${GpuDriverHelper.driverStoragePath}${FileUtil.getFilename(result)}"
                val driverFile = File(driverPath)

                // Ignore file exceptions when a user selects an invalid zip
                try {
                    if (!GpuDriverHelper.copyDriverToInternalStorage(result)) {
                        throw IOException("Driver failed validation!")
                    }
                } catch (_: IOException) {
                    if (driverFile.exists()) {
                        driverFile.delete()
                    }
                    return@newInstance getString(R.string.select_gpu_driver_error)
                }

                val driverData = GpuDriverHelper.getMetadataFromZip(driverFile)
                val driverInList =
                    driverViewModel.driverData.firstOrNull {
                        it.first == driverPath || it.second == driverData
                    }
                if (driverInList != null) {
                    return@newInstance getString(R.string.driver_already_installed)
                } else {
                    driverViewModel.onDriverAdded(Pair(driverPath, driverData))
                    withContext(Dispatchers.Main) {
                        if (_binding != null) {
                            refreshDriverList()
                            val adapter = binding.listDrivers.adapter as DriverAdapter
                            val selectedPosition = adapter.currentList
                                .indexOfFirst { it.selected }
                                .let { if (it == -1) 0 else it }
                            driverViewModel.showClearButton(!StringSetting.DRIVER_PATH.global)
                            binding.listDrivers
                                .smoothScrollToPosition(selectedPosition)
                        }
                    }
                }
                return@newInstance Any()
            }.show(childFragmentManager, ProgressDialogFragment.TAG)
        }

    fun showDriverWarningDialog() {
        val shouldDisplayGpuWarning =
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(Settings.PREF_SHOULD_SHOW_DRIVER_WARNING, true)
        if (shouldDisplayGpuWarning) {
            MessageDialogFragment.newInstance(
                activity,
                titleId = R.string.unsupported_gpu,
                descriptionId = R.string.unsupported_gpu_warning,
                positiveButtonTitleId = R.string.dont_show_again,
                negativeButtonTitleId = R.string.close,
                showNegativeButton = true,
                positiveAction = {
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                        .edit() {
                            putBoolean(Settings.PREF_SHOULD_SHOW_DRIVER_WARNING, false)
                        }
                }
            ).show(requireActivity().supportFragmentManager, MessageDialogFragment.TAG)
        }
    }

    private fun showDriverShaderWipeDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.driver_shader_wipe_dialog_title)
            .setMessage(R.string.driver_shader_wipe_dialog_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                driverViewModel.onDriverShaderDialogDismissed(dontShowAgain = false)
            }
            .setNegativeButton(R.string.dont_show_again) { _, _ ->
                driverViewModel.onDriverShaderDialogDismissed(dontShowAgain = true)
            }
            .setCancelable(false)
            .show()
    }
}
