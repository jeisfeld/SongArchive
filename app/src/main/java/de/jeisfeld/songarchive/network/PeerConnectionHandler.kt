package de.jeisfeld.songarchive.network

import de.jeisfeld.songarchive.ui.LyricsDisplayStyle

interface PeerConnectionHandler {
    fun registerReceiver()
    fun unregisterReceiver()
    fun startServer()
    fun startClient()
    fun stopServer()
    fun stopClient()
    fun sendCommandToClients(songId: String, style: LyricsDisplayStyle)
}

enum class NetworkCommand {
    START_ACTIVITY,
    CLIENT_DISCONNECT
}