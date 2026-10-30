// SPDX-FileCopyrightText: Copyright 2026 Eden Emulator Project
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.fragments.MessageDialogFragment
import org.yuzu.yuzu_emu.fragments.ProgressDialogFragment
import java.io.File
import java.io.FilenameFilter

// RetroForge: only the one-time keys and firmware import is left; games, updates, DLC and
// save data are RetroForge's business.
object InstallableActions {
    fun processKey(
        activity: FragmentActivity,
        fragmentManager: FragmentManager,
        result: Uri,
        extension: String = "keys",
        onInstalled: () -> Unit = {}
    ) {
        activity.contentResolver.takePersistableUriPermission(
            result,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val resultCode = NativeLibrary.installKeys(result.toString(), extension)
        if (resultCode == 0) {
            Toast.makeText(
                activity.applicationContext,
                R.string.keys_install_success,
                Toast.LENGTH_SHORT
            ).show()
            onInstalled()
            return
        }

        val resultString = activity.resources.getStringArray(R.array.installKeysResults)[resultCode]
        MessageDialogFragment.newInstance(
            titleId = R.string.keys_failed,
            descriptionString = resultString,
            helpLinkId = R.string.keys_missing_help
        ).show(fragmentManager, MessageDialogFragment.TAG)
    }

    fun processFirmware(
        activity: FragmentActivity,
        fragmentManager: FragmentManager,
        result: Uri,
        onComplete: (() -> Unit)? = null
    ) {
        val filterNCA = FilenameFilter { _, dirName -> dirName.endsWith(".nca") }
        val firmwarePath = File(NativeConfig.getNandDir() + "/system/Contents/registered/")
        val cacheFirmwareDir = File("${activity.cacheDir.path}/registered/")

        ProgressDialogFragment.newInstance(
            activity,
            R.string.firmware_installing
        ) { progressCallback, _ ->
            var messageToShow: Any
            try {
                FileUtil.unzipToInternalStorage(
                    result.toString(),
                    cacheFirmwareDir,
                    progressCallback
                )
                val unfilteredNumOfFiles = cacheFirmwareDir.list()?.size ?: -1
                val filteredNumOfFiles = cacheFirmwareDir.list(filterNCA)?.size ?: -2
                messageToShow = if (unfilteredNumOfFiles != filteredNumOfFiles) {
                    MessageDialogFragment.newInstance(
                        activity,
                        titleId = R.string.firmware_installed_failure,
                        descriptionId = R.string.firmware_installed_failure_description
                    )
                } else {
                    firmwarePath.deleteRecursively()
                    cacheFirmwareDir.copyRecursively(firmwarePath, overwrite = true)
                    NativeLibrary.initializeSystem(true)
                    activity.getString(R.string.save_file_imported_success)
                }
            } catch (_: Exception) {
                messageToShow = activity.getString(R.string.fatal_error)
            } finally {
                cacheFirmwareDir.deleteRecursively()
            }
            messageToShow
        }.apply {
            onDialogComplete = onComplete
        }.show(fragmentManager, ProgressDialogFragment.TAG)
    }
}
