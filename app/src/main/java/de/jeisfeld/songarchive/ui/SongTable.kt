package de.jeisfeld.songarchive.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song
import java.io.File

@Composable
fun SongTable(songs: List<Song>, isWideScreen: Boolean) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Table Header (Outside LazyColumn)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                text = stringResource(id = R.string.column_id),
                modifier = Modifier.width(50.dp),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.column_title),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            if (isWideScreen) {
                Text(
                    text = stringResource(id = R.string.column_author),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(id = R.string.column_actions),
                modifier = Modifier.width(80.dp),
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider()

        // Scrollable List of Songs
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = song.id, modifier = Modifier.width(50.dp))
                    Text(text = song.title, modifier = Modifier.weight(1f))
                    if (isWideScreen) {
                        Text(text = song.author, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.width(80.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.text),
                            contentDescription = stringResource(id = R.string.view_lyrics),
                            modifier = Modifier.size(24.dp).clickable {
                                val intent = Intent(context, LyricsViewerActivity::class.java)
                                intent.putExtra("LYRICS", song.lyrics)
                                context.startActivity(intent)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        song.tabfilename?.takeIf { it.isNotBlank() }?.let {
                            Image(
                                painter = painterResource(id = R.drawable.chords),
                                contentDescription = stringResource(id = R.string.view_chords),
                                modifier = Modifier.size(24.dp).clickable {
                                    val imageFile = File(context.filesDir, "chords/$it")
                                    if (imageFile.exists()) {
                                        val intent = Intent(context, ChordsViewerActivity::class.java)
                                        intent.putExtra("IMAGE_PATH", imageFile.absolutePath)
                                        context.startActivity(intent)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        song.mp3filename?.takeIf { it.isNotBlank() }?.let {
                            Image(
                                painter = painterResource(id = R.drawable.ic_play),
                                contentDescription = stringResource(id = R.string.play_song),
                                modifier = Modifier.size(24.dp).clickable {
                                    val url = "https://jeisfeld.de/audio/songs/$it"
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(Uri.parse(url), "audio/*")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
