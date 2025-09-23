package de.jeisfeld.songarchive.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.network.ClientMode
import de.jeisfeld.songarchive.network.DisplayStyle
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsViewerActivity : AppCompatActivity() {
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
            val lyrics: String? = intent.getStringExtra("LYRICS")?.replace("|", "")
            val lyricsShort: String? = intent.getStringExtra("LYRICS_SHORT")?.replace("|", "")
            if (songId != null) {
                val songDao = AppDatabase.getDatabase(application).songDao()
                lifecycleScope.launch {
                    val fetchedSong = withContext(Dispatchers.IO) { songDao.getSongById(songId) }
                    updateUI(fetchedSong, lyrics, lyricsShort, displayStyle)
                }
            } else {
                updateUI(null, lyrics, lyricsShort, displayStyle)
            }
        } else {
            updateUI(song, null, null, displayStyle)
        }
    }

    private fun updateUI(song: Song?, lyrics: String?, lyricsShort: String?, displayStyle: DisplayStyle) {
        val displayLyrics = song?.lyrics?.replace("|", "")?.trim() ?: lyrics ?: " "
        val displayLyricsShort = song?.lyricsShort?.replace("|", "")?.trim() ?: lyricsShort ?: displayLyrics
        var setLyricsPaged: ((String?) -> Unit)? = null
        val songViewModel = ViewModelProvider(this)[SongViewModel::class.java]

        setContent {
            MaterialTheme {
                LyricsViewerScreen(
                    song,
                    displayLyrics,
                    displayLyricsShort,
                    displayStyle,
                    onClose = { finish() },
                    setLyricsPagedCallback = { setLyricsPaged = it },
                    viewModel = songViewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricsViewerScreen(
    song: Song?,
    lyrics: String,
    lyricsShort: String,
    displayStyle: DisplayStyle,
    onClose: () -> Unit,
    setLyricsPagedCallback: ((String?) -> Unit) -> Unit = {},
    viewModel: SongViewModel? = null
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var currentSong by remember { mutableStateOf(song) }
    var lyricsState by remember { mutableStateOf(lyrics) }
    var lyricsShortState by remember { mutableStateOf(lyricsShort) }
    var lyricsPageOverride by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(song) {
        currentSong = song
        lyricsState = lyrics
        lyricsShortState = lyricsShort
        lyricsPageOverride = null
    }

    val baseLyrics = lyricsPageOverride ?: if (isLandscape) lyricsShortState else lyricsState
    val displayedLyrics = baseLyrics.lines().joinToString("\n") { it.trimEnd() }

    LaunchedEffect(Unit) {
        setLyricsPagedCallback { newLyrics -> lyricsPageOverride = newLyrics }
    }

    var textAlign by remember { mutableStateOf(if (isLandscape) TextAlign.Center else TextAlign.Left) }
    val scrollState = rememberScrollState()
    var isZooming by remember { mutableStateOf(false) }
    var startPadding = if (isLandscape) 0f else 8f

    val textMeasurer = TextMeasurer()

    var fontSize by remember { mutableStateOf(24f) }
    var lineHeight by remember { mutableStateOf(1.3f) }

    val localView = LocalView.current
    var screenWidth by remember { mutableStateOf(with(density) { localView.width.toDp().value }) }
    var screenHeight by remember { mutableStateOf(with(density) { localView.height.toDp().value }) }
    var showButtons by remember { mutableStateOf(displayStyle == DisplayStyle.STANDARD || displayStyle == DisplayStyle.LOCAL_PREVIEW) }
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(
        lyricsPageOverride,
        screenWidth,
        screenHeight,
        lyricsState,
        lyricsShortState,
        isLandscape
    ) {
        val currentLyrics = lyricsPageOverride ?: if (isLandscape) lyricsShortState else lyricsState
        val cleanLyrics = currentLyrics.lines().joinToString("\n") { it.trimEnd() }
        val longestLine = cleanLyrics.lines().maxByOrNull { textMeasurer.measureWidth(it, 24f) } ?: ""

        var testFontSize = 24f
        for (i in 1..3) {
            testFontSize *= (screenWidth - 8) / textMeasurer.measureWidth(longestLine, testFontSize)
        }
        while (screenWidth - 8 < textMeasurer.measureWidth(longestLine, testFontSize)) {
            testFontSize -= 1f
        }

        var adjustedFontSize = testFontSize * 0.96f
        var adjustedLineHeight = 1.3f

        val totalTextHeight = textMeasurer.measureHeight(cleanLyrics, adjustedFontSize, adjustedLineHeight)
        if (totalTextHeight > screenHeight * 0.95f) {
            adjustedLineHeight *= (screenHeight * 0.95f / totalTextHeight)
        }

        if (adjustedLineHeight < 1.02f) {
            adjustedFontSize *= adjustedLineHeight / 1.02f
            adjustedLineHeight = 1.02f
        }

        fontSize = adjustedFontSize.coerceAtLeast(14f)
        lineHeight = adjustedLineHeight
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = when (displayStyle) {
            DisplayStyle.STANDARD, DisplayStyle.LOCAL_PREVIEW -> Color.White
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
                            if (zoom != 1f) {
                                isZooming = true
                                if (kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y)) {
                                    fontSize = (fontSize * zoom).coerceIn(8f, 96f)
                                } else {
                                    lineHeight = (lineHeight * zoom).coerceIn(1f, 3f)
                                }
                            }
                        }
                    }
                    .pointerInteropFilter { event ->
                        isZooming = event.pointerCount > 1
                        false
                    }
                    .verticalScroll(if (isZooming) rememberScrollState() else scrollState)
                    .clickable {
                        showButtons = if (textAlign == TextAlign.Center && displayStyle != DisplayStyle.LOCAL_PREVIEW) !showButtons else showButtons
                        textAlign = if (textAlign == TextAlign.Left) TextAlign.Center else TextAlign.Left
                        startPadding = if (textAlign == TextAlign.Left) 8f else 0f
                    },
                verticalArrangement = if (scrollState.maxValue == 0) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SelectionContainer {
                    Text(
                        text = displayedLyrics,
                        style = TextStyle(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineHeight).sp,
                            fontWeight = FontWeight.Normal,
                            color = when (displayStyle) {
                                DisplayStyle.STANDARD, DisplayStyle.LOCAL_PREVIEW -> Color.Black
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
            }

            ViewerControlButtons(
                showButtons = showButtons,
                isShowingLyrics = true,
                song = currentSong,
                displayStyle = displayStyle,
                meanings = emptyList(),
                onShowMeaningChange = { },
                onDisplayLyricsPage = { lyricsPageOverride = it },
                onClose = onClose,
                onEditLocalSong = if (currentSong?.id?.startsWith("Y") == true && viewModel != null) {
                    { _ -> showEditDialog = true }
                } else {
                    null
                }
            )
        }
    }

    if (showEditDialog && currentSong?.id?.startsWith("Y") == true && viewModel != null) {
        LocalSongDialog(
            isEditing = true,
            initialTitle = currentSong!!.title,
            initialLyrics = currentSong!!.lyrics,
            initialLyricsPaged = currentSong!!.lyricsPaged ?: "",
            initialTabFilename = currentSong!!.tabfilename,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedTitle, updatedLyrics, updatedLyricsPaged, updatedTabUri ->
                viewModel.updateLocalSong(
                    currentSong!!.id,
                    updatedTitle,
                    updatedLyrics,
                    updatedLyricsPaged,
                    updatedTabUri
                ) { updatedSong ->
                    currentSong = updatedSong
                    val sanitizedLyrics = updatedSong.lyrics.replace("|", "").trim()
                    val sanitizedShort = updatedSong.lyricsShort?.replace("|", "")?.trim()
                    lyricsState = sanitizedLyrics.ifBlank { " " }
                    lyricsShortState = sanitizedShort?.takeIf { it.isNotEmpty() } ?: sanitizedLyrics.ifBlank { " " }
                    lyricsPageOverride = null
                    showEditDialog = false
                }
            },
            onDelete = {
                viewModel.deleteLocalSong(currentSong!!.id) {
                    showEditDialog = false
                    onClose()
                }
            }
        )
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

