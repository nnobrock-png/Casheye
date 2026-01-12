package com.example.casheye.utils

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun CameraPreviewScreen(
    onCapture: (android.graphics.Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ★ imageCapture をここで定義して保持します
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { androidx.camera.view.PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    LaunchedEffect(Unit) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture // ここで紐付けます
            )
        } catch (e: Exception) {
            Log.e("Camera", "Use case binding failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ★ <androidx.camera.view.PreviewView> を明示的に指定します
        AndroidView<androidx.camera.view.PreviewView>(
            factory = { ctx ->
                // すでに remember で定義した previewView をここで返します
                previewView
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // 必要に応じてここに更新処理を書きますが、空でも大丈夫です
            }
        )

        // シャッターボタン
        FloatingActionButton(
            onClick = {
                val executor = ContextCompat.getMainExecutor(context)
                imageCapture.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            // ImageProxy を Bitmap に変換
                            val bitmap = image.toBitmap()
                            onCapture(bitmap)
                            image.close()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("Camera", "撮影失敗", exception)
                        }
                    }
                )
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

        // 閉じるボタン
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "戻る", tint = Color.White)
        }
    }
}