package de.jeisfeld.songarchive.network

import com.google.gson.Gson

internal object NearbyMessageCodec {
    private val gson = Gson()

    fun displayParams(
        style: DisplayStyle,
        songId: String? = null,
        lyrics: String? = null,
        lyricsShort: String? = null
    ): Map<String, String?> = mapOf(
        "songId" to songId,
        "style" to style.toString(),
        "lyrics" to lyrics,
        "lyricsShort" to lyricsShort
    )

    fun favoriteListParams(name: String, songIds: String, sorted: Boolean, entries: String?): Map<String, String> =
        buildMap {
            put("name", name)
            put("songIds", songIds)
            put("sorted", sorted.toString())
            entries?.takeIf { it.isNotBlank() }?.let { put("entries", it) }
        }

    fun encode(command: NetworkCommand, params: Map<String, String?>): String =
        gson.toJson(Message(command.name, params.filterValues { it != null }.mapValues { it.value!! }))

    fun decode(json: String): Message = gson.fromJson(json, Message::class.java)
}
