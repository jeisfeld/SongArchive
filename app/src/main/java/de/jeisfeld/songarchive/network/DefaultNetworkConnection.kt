package de.jeisfeld.songarchive.network

enum class DefaultNetworkConnection(val id: Int) {
    NONE(0),
    SERVER(1),
    CLIENT_LYRICS_BS(2),
    CLIENT_LYRICS_BW(3),
    CLIENT_LYRICS_WB(4),
    CLIENT_CHORDS(5);

    companion object {
        fun fromId(id: Int): DefaultNetworkConnection {
            return values().find { it.id == id } ?: NONE
        }
    }
}
