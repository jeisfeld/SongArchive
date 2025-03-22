package de.jeisfeld.songarchive

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