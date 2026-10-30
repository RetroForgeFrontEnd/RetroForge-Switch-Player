// SPDX-FileCopyrightText: Copyright 2026 RetroForge
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.utils

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.yuzu.yuzu_emu.fragments.DriverFetcherFragment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * RetroForge Switch Player: the one Mesa Turnip build to suggest for this device's Adreno GPU, found in its
 * builder's GitHub releases and downloaded only when the user asks. Same families as RetroForge's own GPU driver
 * advice (pc/DriverRecommendations.kt): Adreno 6xx and 7xx take the newest regular Turnip build, Adreno 8xx a build
 * made for a8xx, everything else (Mali, Xclipse, PowerVR, old Adrenos) keeps the phone's own driver.
 * Only open-source Turnip builds are suggested, never Qualcomm's own drivers repackaged by third parties.
 */
object RecommendedDriver {
    enum class Family { ADRENO_6XX, ADRENO_7XX, ADRENO_8XX, ADRENO_OLD, NOT_ADRENO }

    /** A builder's GitHub repo, and which of its release files is the one to use. */
    private data class Source(val repo: String, val matches: (String) -> Boolean)

    private val purpleTurnip = Source("MrPurple666/purple-turnip") { name ->
        name.endsWith(".zip", true) && name.contains("turnip", true) &&
            !name.contains("8xx", true) && !name.contains("ayaneo", true)
    }
    private val kimchiTurnip = Source("K11MCH1/AdrenoToolsDrivers") { name ->
        Regex("""Turnip_v[\d.]+_R\d+\.zip""", RegexOption.IGNORE_CASE).matches(name)
    }
    private val kimchiA8xx = Source("K11MCH1/AdrenoToolsDrivers") { name ->
        name.endsWith(".zip", true) && name.contains("turnip", true) && name.contains("a8xx", true)
    }

    data class Download(val url: String, val fileName: String, val release: String)

    /** "Adreno (TM) 619" → 619; Axx GPUs (AYANEO) → their number; null when it isn't an Adreno. */
    fun adrenoModel(gpuModel: String?): Int? {
        val parts = gpuModel?.split(" ") ?: return null
        if (parts.size < 3 || parts[0] != "Adreno") return null
        return parts[2].removePrefix("A").toIntOrNull()
    }

    fun family(gpuModel: String?): Family {
        val model = adrenoModel(gpuModel) ?: return Family.NOT_ADRENO
        return when (model) {
            in 800..899 -> Family.ADRENO_8XX
            in 700..799 -> Family.ADRENO_7XX
            in 600..699 -> Family.ADRENO_6XX
            else -> Family.ADRENO_OLD
        }
    }

    val Family.canUseTurnip get() = this == Family.ADRENO_6XX || this == Family.ADRENO_7XX || this == Family.ADRENO_8XX

    private fun sources(family: Family): List<Source> = when (family) {
        Family.ADRENO_6XX, Family.ADRENO_7XX -> listOf(purpleTurnip, kimchiTurnip)
        Family.ADRENO_8XX -> listOf(kimchiA8xx)
        else -> emptyList()
    }

    private val client = OkHttpClient()

    /** The newest suitable Turnip build for [family] (network; call off the main thread), or null. */
    fun find(family: Family): Download? {
        for (source in sources(family)) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${source.repo}/releases")
                .build()
            val releases = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    DriverFetcherFragment.Release.fromJsonArray(
                        response.body?.string() ?: "",
                        false,
                        DriverFetcherFragment.SortMode.PublishTime
                    )
                }
            } catch (e: Exception) {
                Log.warning("[RecommendedDriver] ${source.repo}: $e")
                continue
            }
            releases.filterNot { it.prerelease }.forEach { release ->
                release.artifacts.firstOrNull { source.matches(it.name) }?.let {
                    return Download(it.url.toString(), it.name, release.title.ifEmpty { release.tagName })
                }
            }
        }
        return null
    }

    /** Downloads [download] into the app's cache (network; call off the main thread). */
    fun fetch(context: Context, download: Download): File {
        val dir = context.externalCacheDir ?: context.cacheDir
        dir.mkdirs()
        val file = File(dir, download.fileName)
        val request = Request.Builder()
            .url(download.url)
            .header("Accept", "application/octet-stream")
            .build()
        client.newBuilder().followRedirects(true).followSslRedirects(true).build()
            .newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty response")
                body.byteStream().use { input -> FileOutputStream(file).use { input.copyTo(it) } }
            }
        if (file.length() == 0L) throw IOException("empty file")
        return file
    }
}
