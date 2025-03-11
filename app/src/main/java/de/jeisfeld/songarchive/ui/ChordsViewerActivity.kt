package de.jeisfeld.songarchive.ui

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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Meaning

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
        val imagePath = intent.getStringExtra("IMAGE_PATH") ?: return
        val meanings: List<Meaning> = intent.getParcelableArrayListExtra("MEANINGS") ?: emptyList()

        setContent {
            MaterialTheme {
                ChordsViewerScreen(imagePath, meanings) { finish() }
            }
        }
    }

}

@Composable
fun ChordsViewerScreen(imagePath: String, meanings: List<Meaning>, onClose: () -> Unit) {
    val context = LocalContext.current
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
                    if (!meanings.isEmpty()) {
                        IconButton(
                            onClick = { /* TODO: Open meanings display */ },
                            modifier = Modifier.size(24.dp)
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
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = Color.Black,
                            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                        )
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
