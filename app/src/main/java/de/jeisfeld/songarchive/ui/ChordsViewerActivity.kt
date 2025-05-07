package de.jeisfeld.songarchive.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.lifecycle.lifecycleScope
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.db.Meaning
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.network.DisplayStyle
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChordsViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen mode (hide status & navigation bars)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        // Prevent screen timeout
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val displayStyle = (intent.getSerializableExtra("STYLE") as DisplayStyle?) ?: DisplayStyle.STANDARD

        if (displayStyle == DisplayStyle.REMOTE_DEFAULT) {
            PeerConnectionViewModel.stopRemoteActivity.observe(this) {
                if (it) {
                    finish()
                    PeerConnectionViewModel.stopRemoteActivity.postValue(false)
                }
            }
        }

        // wakeup if required
        if (displayStyle == DisplayStyle.REMOTE_DEFAULT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(true)
                setShowWhenLocked(true)
            } else {
                val window = window
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }

        // Get the image path from intent
        @Suppress("DEPRECATION")
        val song: Song? = intent.getParcelableExtra("SONG")
        val songId: String? = intent.getStringExtra("SONG_ID")
        if (song == null && songId != null) {
            val songDao = AppDatabase.getDatabase(application).songDao()
            lifecycleScope.launch {
                val fetchedSong = withContext(Dispatchers.IO) { songDao.getSongById(songId) }
                updateUI(this@ChordsViewerActivity, fetchedSong, displayStyle)
            }
        } else {
            updateUI(this, song, displayStyle)
        }
    }

    private fun updateUI(context: Context, song: Song?, displayStyle: DisplayStyle) {
        if (song == null) {
            finish()
        }

        val imageFile = File(context.filesDir, "chords/${song?.tabfilename}")

        @Suppress("DEPRECATION")
        val meanings: List<Meaning> = intent.getParcelableArrayListExtra("MEANINGS") ?: emptyList()
        setContent {
            AppTheme {
                ChordsViewerScreen(song, imageFile.absolutePath, displayStyle, meanings) { finish() }
            }
        }
    }
}

@Composable
fun ChordsViewerScreen(song: Song?, imagePath: String, displayStyle: DisplayStyle, meanings: List<Meaning>, onClose: () -> Unit) {
    var showMeaningsPopup by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(displayStyle == DisplayStyle.STANDARD) }

    val configuration = LocalConfiguration.current
    val bitmap = remember(imagePath) {
        val originalBitmap = BitmapFactory.decodeFile(imagePath)

        if (originalBitmap != null && configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            rotateBitmap(originalBitmap, 90f)
        } else {
            originalBitmap
        }
    }

    if (bitmap == null) {
        return
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val maxScale = 5f
    val minScale = 1f
    val imageWidth = bitmap.width.toFloat()
    val imageHeight = bitmap.height.toFloat()


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    showButtons = !showButtons
                }
            }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                            val maxTranslationX = (imageWidth * (newScale - 1)* 1.3f)
                            val maxTranslationY = (imageHeight * (newScale - 1))

                            scale = newScale
                            offsetX = (offsetX + pan.x).coerceIn(-maxTranslationX, maxTranslationX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxTranslationY, maxTranslationY)
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )

                AnimatedVisibility(
                    visible = showButtons,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ViewerControlButtons(
                        showButtons = showButtons,
                        song = song,
                        displayStyle = displayStyle,
                        meanings = meanings,
                        onShowMeaningChange = { showMeaningsPopup = it },
                        onClose = onClose,
                    )
                }
            if (showMeaningsPopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()  // Ensures full-screen grey overlay
                        .background(Color.Black.copy(alpha = 0.3f))  // Properly covers full screen
                        .clickable { showMeaningsPopup = false },  // Click outside to close
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)  // 90% of screen width
                            .wrapContentHeight()  // Allows shrinking for small content
                            .heightIn(min = 100.dp, max = 0.9f * LocalConfiguration.current.screenHeightDp.dp) // Limits max height to 90% of screen
                            .background(Color.White, shape = RoundedCornerShape(16.dp))
                            .border(1.dp, Color.Gray, shape = RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimensionResource(id = R.dimen.spacing_medium), vertical = dimensionResource(id = R.dimen.spacing_heading_vertical))
                                .verticalScroll(rememberScrollState()) // Scroll if content is too long
                        ) {


                            // Display meanings
                            meanings.forEach { meaning ->
                                Text(
                                    text = meaning.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Text(
                                    text = meaning.meaning,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// Function to rotate a bitmap
fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(angle)
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
