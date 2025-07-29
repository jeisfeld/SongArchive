package de.jeisfeld.songarchive.network

enum class DefaultConnectionType(val value: Int) {
    NONE(0),
    SERVER(1),
    CLIENT_LYRICS_BS(2),
    CLIENT_LYRICS_BW(3),
    CLIENT_LYRICS_WB(4),
    CLIENT_CHORDS(5);

    companion object {
        fun fromInt(value: Int): DefaultConnectionType = values().firstOrNull { it.value == value } ?: NONE
    }

    fun toModes(): Pair<PeerConnectionMode, ClientMode> = when(this) {
        SERVER -> PeerConnectionMode.SERVER to PeerConnectionViewModel.clientMode
        CLIENT_LYRICS_BS -> PeerConnectionMode.CLIENT to ClientMode.LYRICS_BS
        CLIENT_LYRICS_BW -> PeerConnectionMode.CLIENT to ClientMode.LYRICS_BW
        CLIENT_LYRICS_WB -> PeerConnectionMode.CLIENT to ClientMode.LYRICS_WB
        CLIENT_CHORDS -> PeerConnectionMode.CLIENT to ClientMode.CHORDS
        NONE -> PeerConnectionMode.DISABLED to PeerConnectionViewModel.clientMode
    }
}

