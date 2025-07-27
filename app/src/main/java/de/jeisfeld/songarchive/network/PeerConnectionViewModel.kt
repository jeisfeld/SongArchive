package de.jeisfeld.songarchive.network

import android.content.Context
import android.content.Intent
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

object PeerConnectionViewModel : ViewModel() {
    var peerConnectionMode = PeerConnectionMode.DISABLED
    var clientMode = ClientMode.LYRICS_BS
    var connectedDevices = 0

    val stopRemoteActivity = MutableLiveData(false)

    fun startPeerConnectionService(context: Context) {
        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
            val action = when (peerConnectionMode) {
                PeerConnectionMode.DISABLED -> PeerConnectionAction.CONNECTION_DISABLE
                PeerConnectionMode.SERVER -> PeerConnectionAction.START_SERVER
                PeerConnectionMode.CLIENT -> PeerConnectionAction.START_CLIENT
            }
            setAction(action.toString())
            putExtra("ACTION", action)
        }
        context.startForegroundService(serviceIntent)
    }
}


enum class PeerConnectionMode {
    DISABLED,
    SERVER,
    CLIENT
}

enum class ClientMode {
    LYRICS_BW,
    LYRICS_BS,
    LYRICS_WB,
    CHORDS
}

enum class PeerConnectionAction {
    CONNECTION_DISABLE,
    START_SERVER,
    START_CLIENT,
    DISPLAY_SONG,
    DISPLAY_LYRICS,
    DISPLAY_CHORDS,
    DISPLAY_TEXT,
    SHARE_FAVORITE_LIST,
    CLIENTS_CONNECTED,
    CLIENT_CONNECTED,
    CLIENT_DISCONNECTED
}
