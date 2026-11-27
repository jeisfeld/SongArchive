package de.jeisfeld.songarchive.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SongChordsButton(song: Song, viewModel: SongViewModel, iconSize: Dp, chordRefreshKey: Int = 0) {
    val context = LocalContext.current
    val hasChords = remember(song.chordmarkup, song.tabfilename, context, chordRefreshKey) {
        findChordsImage(context, song.tabfilename) != null || ChordSheet.parse(song.chordmarkup) != null
    }
    if (!hasChords) return

    val scope = rememberCoroutineScope()
    Image(
        painter = painterResource(R.drawable.chords2),
        contentDescription = stringResource(R.string.view_chords),
        modifier = Modifier.size(iconSize).clickable {
            scope.launch {
                val meanings = withContext(Dispatchers.IO) { viewModel.getMeaningsForSong(song.id) }
                context.startActivity(Intent(context, ChordsViewerActivity::class.java).apply {
                    putExtra("SONG", song)
                    putExtra("MEANINGS", meanings)
                })
            }
        }
    )
}
