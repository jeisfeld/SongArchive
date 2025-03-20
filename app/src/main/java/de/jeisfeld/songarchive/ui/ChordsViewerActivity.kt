package de.jeisfeld.songarchive.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Meaning
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.ui.theme.AppTheme
import de.jeisfeld.songarchive.wifi.WiFiDirectService
import de.jeisfeld.songarchive.wifi.WifiViewModel

class ChordsViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen mode (hide status & navigation bars)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        }

        // Prevent screen timeout
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Get the image path from intent
        val song: Song? = intent.getParcelableExtra("SONG")
        val imagePath = intent.getStringExtra("IMAGE_PATH") ?: return
        val meanings: List<Meaning> = intent.getParcelableArrayListExtra("MEANINGS") ?: emptyList()

        setContent {
            AppTheme {
                ChordsViewerScreen(song, imagePath, meanings) { finish() }
            }
        }
    }

}

@Composable
fun ChordsViewerScreen(song: Song?, imagePath: String, meanings: List<Meaning>, onClose: () -> Unit) {
    var showPopup by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val bitmap = remember(imagePath) {
        val originalBitmap = BitmapFactory.decodeFile(imagePath)
        if (configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            rotateBitmap(originalBitmap, 90f)
        } else {
            originalBitmap
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val maxScale = 5f
    val minScale = 1f
    val imageWidth = bitmap.width.toFloat()
    val imageHeight = bitmap.height.toFloat()
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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

            // Close button (Top-Right)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row {
                    if (WifiViewModel.wifiTransferMode == 1 && WifiViewModel.connectedDevices > 0) {
                        IconButton(
                            onClick = {
                                song?.let {
                                    val serviceIntent = Intent(context, WiFiDirectService::class.java).apply {
                                        putExtra("SONG_ID", song.id)
                                        putExtra("MODE", 10)
                                    }
                                    context.startForegroundService(serviceIntent)
                                }
                            },
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
                                painter = painterResource(id = R.drawable.ic_send),
                                contentDescription = "Send",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(dimensionResource(id = R.dimen.icon_size_small))
                            )
                        }
                        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                    }
                    if (!meanings.isEmpty()) {
                        IconButton(
                            onClick = { showPopup = true },
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
                                painter = painterResource(id = R.drawable.ic_info),
                                contentDescription = "Info",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(dimensionResource(id = R.dimen.icon_size_small))
                            )
                        }
                        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                    }

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
            if (showPopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()  // Ensures full-screen grey overlay
                        .background(Color.Black.copy(alpha = 0.3f))  // Properly covers full screen
                        .clickable { showPopup = false },  // Click outside to close
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
