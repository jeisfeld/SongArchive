package de.jeisfeld.songarchive.network

interface PeerConnectionHandler {
    fun startServer()
    fun startClient()
    fun stopEndpoint()
    fun sendCommandToClients(command: NetworkCommand, params: Map<String, String?> = emptyMap())
}

enum class NetworkCommand {
    DISPLAY_SONG,
    CLIENT_DISCONNECT
}

enum class DisplayStyle {
    STANDARD,
    REMOTE_DEFAULT,
    REMOTE_BLACK
}

data class Message(
    val command: String,
    val params: Map<String, String>? = null
)
