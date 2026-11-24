package de.jeisfeld.songarchive.network

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyMessageCodecTest {
    @Test
    fun songAndChordMessagesCarryTheExactSongIdAndStyle() {
        assertJson(
            """{"command":"DISPLAY_SONG","params":{"songId":"0012a","style":"REMOTE_DEFAULT","lyrics":"First line\nChorus","lyricsShort":"Chorus"}}""",
            NearbyMessageCodec.encode(
                NetworkCommand.DISPLAY_SONG,
                NearbyMessageCodec.displayParams(DisplayStyle.REMOTE_DEFAULT, "0012a", "First line\nChorus", "Chorus")
            )
        )
        assertJson(
            """{"command":"DISPLAY_CHORDS","params":{"songId":"0012a","style":"REMOTE_DEFAULT"}}""",
            NearbyMessageCodec.encode(
                NetworkCommand.DISPLAY_CHORDS,
                NearbyMessageCodec.displayParams(DisplayStyle.REMOTE_DEFAULT, "0012a")
            )
        )
    }

    @Test
    fun lyricPagePreservesNewlinesAndAccentsWithoutRequiringASongId() {
        val params = NearbyMessageCodec.displayParams(
            DisplayStyle.REMOTE_DEFAULT, lyrics = "Überm Fluss\nSing \"leise\""
        )

        assertJson(
            """{"command":"DISPLAY_LYRICS","params":{"style":"REMOTE_DEFAULT","lyrics":"Überm Fluss\nSing \"leise\""}}""",
            NearbyMessageCodec.encode(NetworkCommand.DISPLAY_LYRICS, params)
        )
    }

    @Test
    fun blankDisplayAndDisconnectKeepTheirControlMessageShapes() {
        assertJson(
            """{"command":"DISPLAY_TEXT","params":{"style":"REMOTE_BLACK"}}""",
            NearbyMessageCodec.encode(
                NetworkCommand.DISPLAY_TEXT, NearbyMessageCodec.displayParams(DisplayStyle.REMOTE_BLACK)
            )
        )
        assertJson(
            """{"command":"CLIENT_DISCONNECT","params":{}}""",
            NearbyMessageCodec.encode(NetworkCommand.CLIENT_DISCONNECT, emptyMap())
        )
    }

    @Test
    fun favoriteListUsesStringsForSortFlagAndEntryJson() {
        val entries = """[{"songId":"0012a","position":0,"customTitle":"Auftakt"}]"""

        assertJson(
            """{"command":"SHARE_FAVORITE_LIST","params":{"name":"Probe","songIds":"0012a,Y001","sorted":"true","entries":"[{\"songId\":\"0012a\",\"position\":0,\"customTitle\":\"Auftakt\"}]"}}""",
            NearbyMessageCodec.encode(
                NetworkCommand.SHARE_FAVORITE_LIST,
                NearbyMessageCodec.favoriteListParams("Probe", "0012a,Y001", true, entries)
            )
        )
        assertJson(
            """{"command":"SHARE_FAVORITE_LIST","params":{"name":"Probe","songIds":"0012a,Y001","sorted":"false"}}""",
            NearbyMessageCodec.encode(
                NetworkCommand.SHARE_FAVORITE_LIST,
                NearbyMessageCodec.favoriteListParams("Probe", "0012a,Y001", false, " ")
            )
        )
    }

    @Test
    fun decoderAcceptsExistingTextAndFavoriteListPayloadsWithOptionalFieldsMissing() {
        assertEquals(
            Message("DISPLAY_TEXT", mapOf("style" to "REMOTE_DEFAULT", "lyrics" to "Grüße\nHallo")),
            NearbyMessageCodec.decode(
                """{"command":"DISPLAY_TEXT","params":{"style":"REMOTE_DEFAULT","lyrics":"Grüße\nHallo"}}"""
            )
        )
        assertEquals(
            Message("SHARE_FAVORITE_LIST", mapOf("name" to "Probe", "songIds" to "0012a,Y001")),
            NearbyMessageCodec.decode(
                """{"command":"SHARE_FAVORITE_LIST","params":{"name":"Probe","songIds":"0012a,Y001"}}"""
            )
        )
    }

    private fun assertJson(expected: String, actual: String) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual))
    }
}
