package com.example.casheye.utils

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreviewScreen(
    onCapture: (android.graphics.Bitmap) -> Unit, // 沼回避のため明示的指定
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraHelper = remember { CameraHelper(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    cameraHelper.startCamera(lifecycleOwner, this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 閉じるボタン
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart).padding(32.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
        }

        // シャッターボタン
        FloatingActionButton(
            onClick = {
                cameraHelper.takePicture { bitmap: android.graphics.Bitmap ->
                    onCapture(bitmap)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(72.dp),
            shape = CircleShape,
            containerColor = Color.White
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "撮影", tint = Color.Black)
        }
    }
}