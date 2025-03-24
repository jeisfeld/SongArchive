package de.jeisfeld.songarchive.network

interface PeerConnectionHandler {
    fun startServer()
    fun startClient()
    fun stopEndpoint()
    fun sendCommandToClients(command: NetworkCommand, vararg params: String)
}

enum class NetworkCommand {
    DISPLAY_LYRICS,
    CLIENT_DISCONNECT
}

enum class DisplayStyle {
    STANDARD,
    REMOTE_DEFAULT,
    REMOTE_BLACK
}
