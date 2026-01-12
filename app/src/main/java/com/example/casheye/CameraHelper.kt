package com.example.casheye.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.casheye.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaType


class CameraHelper(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /** カメラ起動 */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraHelper", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 写真撮影 → Gemini 解析 → CSV 取得
     * onResult: CSV 文字列が返る
     */
    fun takePictureAndAnalyze(onResult: (String?) -> Unit) {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    image.close()

                    // Coroutine で Gemini 解析
                    CoroutineScope(Dispatchers.IO).launch {
                        // ← ここが重要
                        val csv = this@CameraHelper.sendToGemini(bitmap)
                        CoroutineScope(Dispatchers.Main).launch {
                            onResult(csv)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("CameraHelper", "Capture processing failed", e)
                    onResult(null)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraHelper", "Capture failed: ${exception.message}", exception)
                onResult(null)
            }
        })
    }

    /** Bitmap → Gemini に送信 → CSV を取得 */
    private fun sendToGemini(bitmap: Bitmap): String? {
        val base64Image = bitmapToBase64(bitmap)
        val json = JSONObject()
        json.put("image", base64Image)
        json.put("prompt", completeGeminiPrompt())

        val body = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(),
            json.toString()
        )

        return try {
            OkHttpClient().newCall(
                Request.Builder()
                    .url("https://api.gemini.example.com/analyze") // 実際のURLに置換
                    .addHeader("Authorization", "Bearer ${BuildConfig.GEMINI_API_KEY}")
                    .post(body)
                    .build()
            ).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: IOException) {
            Log.e("CameraHelper", "Gemini request failed", e)
            null
        }
    }

    /** Bitmap → Base64 */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    /** 完全版プロンプト */
    private fun completeGeminiPrompt(): String {
        return """
            【目的】
            添付されたレシート画像、またはアップロードされた音声データを解析し、
            家計簿・店舗調査用のCSVデータを作成してください。
            【基本ルール】
            日付表記: YYYY-MM-DD
            ヘッダー: 購入日,購入店舗,商品名,分類大分類,分類中分類,税抜価格,税込価格
            1商品1行、割引・ポイント・合計金額行は無視
            税計算: 食品は8%、その他10%
            分類マップ: 食費/日用品/車両費/交通/外食費/医療費/保険代/収入
            CSV形式のみで出力
        """.trimIndent()
    }
}
