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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeisfeld.songarchive.R

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

        // Get lyrics from intent
        val lyrics = intent.getStringExtra("LYRICS") ?: "No lyrics available."

        setContent {
            MaterialTheme {
                LyricsViewerScreen(lyrics) { finish() }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LyricsViewerScreen(lyrics: String, onClose: () -> Unit) {
    var fontSize by remember { mutableStateOf(18f) } // Default font size
    var lineHeight by remember { mutableStateOf(1.5f) } // Default line spacing
    var textAlign by remember { mutableStateOf(TextAlign.Left) } // Default alignment
    val scrollState = rememberScrollState()
    var isZooming by remember { mutableStateOf(false) } // Track if zoom is happening

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White // White background
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
                                    fontSize = (fontSize * zoom).coerceIn(12f, 48f)
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
                        // Tap gesture to switch between left and center alignment
                        textAlign = if (textAlign == TextAlign.Left) TextAlign.Center else TextAlign.Left
                    }
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = if (scrollState.maxValue == 0) Arrangement.Center else Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = lyrics,
                    style = TextStyle(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeight).sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Black, // Black text on white background
                        textAlign = textAlign
                    )
                )
            }

            // Close button (Top-Right, with semi-transparent background)
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
                            shape = RoundedCornerShape(50) // Rounded background
                        )
                        .clip(RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}
