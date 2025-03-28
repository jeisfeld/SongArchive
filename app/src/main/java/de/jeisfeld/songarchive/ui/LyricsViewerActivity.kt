package de.jeisfeld.songarchive.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.DisplayStyle
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        val displayStyle = (intent.getSerializableExtra("STYLE") as DisplayStyle?) ?: DisplayStyle.STANDARD

        // allow to turn off screen
        if (displayStyle != DisplayStyle.REMOTE_BLACK) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (displayStyle == DisplayStyle.REMOTE_DEFAULT || displayStyle == DisplayStyle.REMOTE_BLACK) {
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

        // Get lyrics and optional short lyrics from intent
        val song: Song? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("SONG", Song::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("SONG")
        }
        if (song == null) {
            val songId: String? = intent.getStringExtra("SONG_ID")
            val lyrics: String? = intent.getStringExtra("LYRICS")
            val lyricsShort: String? = intent.getStringExtra("LYRICS_SHORT")
            if (songId != null) {
                val songDao = AppDatabase.getDatabase(application).songDao()
                lifecycleScope.launch {
                    val fetchedSong = withContext(Dispatchers.IO) { songDao.getSongById(songId) }
                    updateUI(fetchedSong, lyrics, lyricsShort, displayStyle)
                }
            }
            else {
                updateUI(null, lyrics, lyricsShort, displayStyle)
            }
        } else {
            updateUI(song, null, null, displayStyle)
        }
    }

    private fun updateUI(song: Song?, lyrics: String?, lyricsShort: String?, displayStyle: DisplayStyle) {
        val displayLyrics = song?.lyrics?.trim() ?: lyrics ?: " "
        val displayLyricsShort = song?.lyricsShort?.trim() ?: lyricsShort ?: displayLyrics
        setContent {
            MaterialTheme {
                LyricsViewerScreen(displayLyrics, displayLyricsShort, displayStyle) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricsViewerScreen(lyrics: String, lyricsShort: String, displayStyle: DisplayStyle, onClose: () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val displayedLyrics1 = if (isLandscape) lyricsShort else lyrics
    val displayedLyrics = displayedLyrics1.lines().joinToString("\n") { it.trimEnd() }

    var textAlign by remember { mutableStateOf(if (isLandscape) TextAlign.Center else TextAlign.Left) }
    val scrollState = rememberScrollState()
    var isZooming by remember { mutableStateOf(false) }
    var startPadding = if (isLandscape) 0f else 8f

    val textMeasurer = TextMeasurer()
    val longestLine = displayedLyrics.lines().maxByOrNull { textMeasurer.measureWidth(it, 24f) } ?: ""

    var fontSize by remember { mutableStateOf(24f) }
    var lineHeight by remember { mutableStateOf(1.3f) }

    val localView = LocalView.current
    var screenWidth by remember { mutableStateOf(with(density) { localView.width.toDp().value }) }
    var screenHeight by remember { mutableStateOf(with(density) { localView.height.toDp().value }) }

    LaunchedEffect(screenWidth, screenHeight) {
        var testFontSize = 24f

        for (i in 1..3) {
            testFontSize = testFontSize * (screenWidth - 8) / textMeasurer.measureWidth(longestLine, testFontSize)
        }
        if (screenWidth - 8 < textMeasurer.measureWidth(longestLine, testFontSize)) {
            testFontSize -= 1f
        }

        fontSize = testFontSize * 0.98f

        val totalTextHeight = textMeasurer.measureHeight(displayedLyrics, fontSize, lineHeight)
        if (totalTextHeight > screenHeight * 0.95f) {
            lineHeight *= (screenHeight * 0.95f / totalTextHeight)
        }

        if (lineHeight < 1.02) {
            fontSize *= lineHeight / 1.02f
            lineHeight = 1.02f
        }

        if (fontSize < 14) {
            fontSize = 14f
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = when (displayStyle) {
            DisplayStyle.STANDARD -> Color.White
            DisplayStyle.REMOTE_BLACK -> Color.Black
            DisplayStyle.REMOTE_DEFAULT -> when (PeerConnectionViewModel.clientMode) {
                ClientMode.LYRICS_WB -> Color.Black
                ClientMode.LYRICS_BW -> Color.White
                ClientMode.LYRICS_BS -> AppColors.Background
                ClientMode.CHORDS -> Color.White
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        screenWidth = with(density) { coordinates.size.width.toDp().value }
                        screenHeight = with(density) { coordinates.size.height.toDp().value }
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (zoom != 1f) { // Two-finger gesture detected
                                isZooming = true
                                if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                    // Horizontal pinch → Adjust font size
                                    fontSize = (fontSize * zoom).coerceIn(8f, 96f)
                                } else {
                                    // Vertical pinch → Adjust line spacing
                                    lineHeight = (lineHeight * zoom).coerceIn(1f, 3f)
                                }
                            }
                        }
                    }
                    .pointerInteropFilter { event ->
                        isZooming = event.pointerCount > 1
                        false
                    }
                    .verticalScroll(if (isZooming) rememberScrollState() else scrollState) // Enable scrolling only if not zooming
                    .clickable {
                        textAlign = if (textAlign == TextAlign.Left) TextAlign.Center else TextAlign.Left
                        startPadding = if (textAlign == TextAlign.Left) 8f else 0f
                    },
                verticalArrangement = if (scrollState.maxValue == 0) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayedLyrics,
                    style = TextStyle(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeight).sp,
                        fontWeight = FontWeight.Normal,
                        color = when (displayStyle) {
                            DisplayStyle.STANDARD -> Color.Black
                            DisplayStyle.REMOTE_BLACK -> Color.Black
                            DisplayStyle.REMOTE_DEFAULT -> when (PeerConnectionViewModel.clientMode) {
                                ClientMode.LYRICS_BS -> AppColors.TextColor
                                ClientMode.LYRICS_BW -> Color.Black
                                ClientMode.LYRICS_WB -> Color.White
                                ClientMode.CHORDS -> Color.Black
                            }
                        },
                        textAlign = textAlign
                    ),
                    modifier = Modifier
                        .padding(start = startPadding.dp)
                )
            }

            if (displayStyle == DisplayStyle.STANDARD) {
                // Close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, end = 0.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .size(dimensionResource(id = R.dimen.icon_size_large))
                            .clip(RoundedCornerShape(50))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = Color.Black,
                            modifier = Modifier.padding(8.dp).size(dimensionResource(id = R.dimen.icon_size_small))
                        )
                    }
                }
            }
        }
    }
}

class TextMeasurer {
    fun measureWidth(text: String, fontSize: Float): Float {
        val paint = android.graphics.Paint()
        paint.textSize = fontSize
        return paint.measureText(text).coerceIn(0.1f, 1e10f)
    }

    fun measureHeight(text: String, fontSize: Float, lineHeight: Float): Float {
        val lineCount = text.lines().size
        return (lineCount * fontSize * lineHeight)
    }
}

