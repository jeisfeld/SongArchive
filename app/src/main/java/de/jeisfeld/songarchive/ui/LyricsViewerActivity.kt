package de.jeisfeld.songarchive.ui

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song

class LyricsViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen mode
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        // Prevent screen timeout
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Get lyrics and optional short lyrics from intent
        val song: Song? = intent.getParcelableExtra("SONG")
        val lyrics = song?.lyrics?.trim() ?: resources.getString(R.string.nolyrics)
        val lyricsShort = song?.lyricsShort?.trim() ?: lyrics

        setContent {
            MaterialTheme {
                LyricsViewerScreen(lyrics, lyricsShort) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricsViewerScreen(lyrics: String, lyricsShort: String, onClose: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val displayedLyrics1 = if (isLandscape) lyricsShort else lyrics
    val displayedLyrics = displayedLyrics1.lines().joinToString("\n") { it.trimEnd() }

    var textAlign by remember { mutableStateOf(if (isLandscape) TextAlign.Center else TextAlign.Left) }
    val scrollState = rememberScrollState()
    var isZooming by remember { mutableStateOf(false) }
    var startPadding = if (isLandscape) 0f else 8f

    val screenWidth = configuration.screenWidthDp - 8
    val screenHeight = configuration.screenHeightDp
    val textMeasurer = TextMeasurer()
    val longestLine = displayedLyrics.lines().maxByOrNull { textMeasurer.measureWidth(it, 24f) } ?: ""

    var fontSize by remember { mutableStateOf(24f) }
    var lineHeight by remember { mutableStateOf(1.3f) }

    LaunchedEffect(longestLine) {
        var testFontSize = 24f

        for (i in 1..3) {
            testFontSize = testFontSize * screenWidth / textMeasurer.measureWidth(longestLine, testFontSize)
        }
        if (screenWidth < textMeasurer.measureWidth(longestLine, testFontSize)) testFontSize -= 1f

        fontSize = testFontSize * 0.98f

        val totalTextHeight = textMeasurer.measureHeight(displayedLyrics, fontSize, lineHeight)
        if (totalTextHeight > screenHeight) {
            lineHeight *= (screenHeight.toFloat() / totalTextHeight)
        }

        if (lineHeight < 1.02) {
            fontSize *= lineHeight
            lineHeight = 1.02f
        }

        if (fontSize < 14) {
            fontSize = 14f
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        color = Color.Black,
                        textAlign = textAlign
                    ),
                    modifier = Modifier.padding(start = startPadding.dp)
                )
            }

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

class TextMeasurer {
    fun measureWidth(text: String, fontSize: Float): Float {
        val paint = android.graphics.Paint()
        paint.textSize = fontSize
        return paint.measureText(text)
    }

    fun measureHeight(text: String, fontSize: Float, lineHeight: Float): Float {
        val lineCount = text.lines().size
        return (lineCount * fontSize * lineHeight)
    }
}

