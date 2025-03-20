package de.jeisfeld.songarchive.wifi

import androidx.lifecycle.ViewModel

object WifiViewModel : ViewModel() {
    var wifiTransferMode = 0
    var connectedDevices = 0
}