// SPDX-FileCopyrightText: Copyright 2026 RetroForge
// SPDX-License-Identifier: GPL-3.0-or-later

package org.yuzu.yuzu_emu.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import android.widget.Toast
import org.yuzu.yuzu_emu.NativeLibrary
import org.yuzu.yuzu_emu.R

/**
 * RetroForge's online play: a multiplayer room, which games see as the Switch's local wireless play. RetroForge picks
 * host or join on the game's page and passes it in the launch extras; this player joins (or hosts) the room as the game
 * starts, and the pause menu's Online page shows who's in it and leaves it.
 */
object OnlineRoom {
    const val EXTRA_MODE = "room_mode" // "host" or "join"
    const val EXTRA_ADDRESS = "room_address"
    const val EXTRA_PORT = "room_port"
    const val EXTRA_NICKNAME = "room_nickname"
    const val EXTRA_PASSWORD = "room_password"
    const val EXTRA_NAME = "room_name"
    const val EXTRA_MAX_PLAYERS = "room_max_players"
    /** The game and its title, given back to RetroForges asking who's hosting on this Wi-Fi (RetroForge's Netplay.kt). */
    const val EXTRA_GAME_ID = "room_game_id"
    const val EXTRA_TITLE = "room_title"

    const val DEFAULT_PORT = 24872

    /** Joins or hosts the room [intent] names, if it names one, off the main thread; the outcome shows as a toast. */
    fun startFrom(activity: Activity, intent: Intent) {
        val mode = intent.getStringExtra(EXTRA_MODE) ?: return
        Thread { start(activity, intent, mode) }.start()
    }

    private fun start(activity: Activity, intent: Intent, mode: String) {
        NativeLibrary.initMultiplayer()
        if (NetPlayManager.netPlayIsJoined()) return
        val nickname = intent.getStringExtra(EXTRA_NICKNAME)?.trim()?.takeIf { it.isNotEmpty() }?.take(20) ?: "Player"
        val port = intent.getStringExtra(EXTRA_PORT)?.toIntOrNull() ?: DEFAULT_PORT
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
        val result = if (mode == "host") {
            val address = intent.getStringExtra(EXTRA_ADDRESS)?.takeIf { it.isNotBlank() }
                ?: NetPlayManager.getIpAddressByWifi(activity)
            // The room's name must be 3 to 20 characters.
            val name = (intent.getStringExtra(EXTRA_NAME)?.trim()?.takeIf { it.length >= 3 } ?: "$nickname's room").take(20).padEnd(3, '.')
            val players = intent.getStringExtra(EXTRA_MAX_PLAYERS)?.toIntOrNull()?.coerceIn(2, 16) ?: 4
            NetPlayManager.netPlayCreateRoom(address, port, nickname, "", 0L, password, name, players, false)
        } else {
            val address = intent.getStringExtra(EXTRA_ADDRESS)?.trim().orEmpty()
            if (address.isEmpty()) {
                toast(activity, activity.getString(R.string.player_room_no_address))
                return
            }
            NetPlayManager.netPlayJoinRoom(address, port, nickname, password)
        }
        if (result == 0 && mode == "host") {
            announce(activity, port, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_GAME_ID).orEmpty(), nickname)
        }
        toast(
            activity,
            when {
                result != 0 -> activity.getString(R.string.player_room_failed, problem(activity, result))
                mode == "host" -> activity.getString(R.string.player_room_hosting, NetPlayManager.getIpAddressByWifi(activity), port)
                else -> activity.getString(R.string.player_room_joined, roomName().orEmpty())
            }
        )
    }

    /** The people in the room, for the pause menu; empty when not in one. */
    fun members(): List<String> =
        if (!NetPlayManager.netPlayIsJoined()) emptyList()
        else NetPlayManager.netPlayRoomInfo().drop(1).map { it.substringBefore('|') }

    /** The room's name, or null when not in one. */
    fun roomName(): String? =
        if (!NetPlayManager.netPlayIsJoined()) null
        else NetPlayManager.netPlayRoomInfo().firstOrNull()?.substringBefore('|')

    fun leave() {
        stopAnnouncing()
        NetPlayManager.netPlayLeaveRoom()
    }

    // RetroForge's "who's hosting?" on this Wi-Fi: the same question and answer as its own netplay, so a hosted room
    // shows in friends' RetroForge lists to tap and join.
    private const val ASK_PORT = 55451
    private const val ASK = "RFNP?"
    private const val ANSWER = "RFNP!"
    @Volatile private var announcer: DatagramSocket? = null
    private var multicast: WifiManager.MulticastLock? = null

    private fun announce(context: Context, port: Int, title: String, gameId: String, nickname: String) {
        stopAnnouncing()
        multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-room")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        val socket = runCatching {
            DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(ASK_PORT)) }
        }.getOrNull() ?: return
        announcer = socket
        Thread {
            val buffer = ByteArray(256)
            runCatching {
                while (announcer === socket) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    if (String(packet.data, 0, packet.length) != ASK || !NetPlayManager.netPlayIsJoined()) continue
                    val answer = listOf(ANSWER, port.toString(), title, gameId, nickname).joinToString("\n").toByteArray()
                    socket.send(DatagramPacket(answer, answer.size, packet.address, packet.port))
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stopAnnouncing() {
        announcer?.let { runCatching { it.close() } }
        announcer = null
        multicast?.let { runCatching { it.release() } }
        multicast = null
    }

    private fun problem(activity: Activity, code: Int): String = activity.getString(
        when (code) {
            7 -> R.string.player_room_wrong_password
            8 -> R.string.player_room_could_not_connect
            9 -> R.string.player_room_full
            3 -> R.string.player_room_name_taken
            6 -> R.string.player_room_wrong_version
            14 -> R.string.player_room_create_failed
            else -> R.string.player_room_network_error
        }
    )

    private fun toast(activity: Activity, text: String) =
        activity.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
}
