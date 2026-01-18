package com.example.casheye

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.casheye.utils.GeminiAnalyzer
import androidx.compose.ui.graphics.Color

@Composable
fun AppSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("casheye_prefs", Context.MODE_PRIVATE) }

    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedModel by remember {
        mutableStateOf(prefs.getString("gemini_model", "gemini-3-flash-preview") ?: "gemini-3-flash-preview")
    }
    var isModelLoading by remember { mutableStateOf(true) }

    // 画像解像度の設定状態
    var selectedQuality by remember {
        mutableStateOf(prefs.getString("image_quality", "Medium") ?: "Medium")
    }

    LaunchedEffect(Unit) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val analyzer = GeminiAnalyzer(apiKey)
        availableModels = analyzer.fetchAvailableModels()
        isModelLoading = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("システム設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
        }

        // --- 1. AI解析エンジン設定 ---
        item {
            Text("AI解析エンジン設定", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("使用するモデル：", fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))

                    if (isModelLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("モデルをロード中...", fontSize = 14.sp)
                        }
                    } else {
                        availableModels.forEach { model ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selectedModel = model
                                        prefs.edit().putString("gemini_model", model).apply()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = (selectedModel == model), onClick = null)
                                Text(model, modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        // --- 2. 画像サイズ（解像度）設定 ---
        item {
            Text("解析スピード・画質設定", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            val qualityOptions = listOf(
                "Low" to "爆速 (512px) - 通信量最小。読み取り限界に挑戦",
                "Medium" to "標準 (1024px) - バランス重視の推奨設定",
                "High" to "高画質 (1600px) - 長いレシートや細かい文字用"
            )

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(12.dp)) {
                    qualityOptions.forEach { (key, description) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selectedQuality = key
                                    prefs.edit().putString("image_quality", key).apply()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (selectedQuality == key), onClick = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(key, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(description, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            Text("※サイズを下げると送信時間が短縮されますが、精度が落ちる場合があります。", fontSize = 11.sp, color = Color.Gray)
        }

        item { Spacer(Modifier.height(32.dp)) }

        // --- 3. アプリ情報セクション ---
        item {
            Text("アプリについて", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text("バージョン") },
                supportingContent = { Text("1.5.1 (Speed Optimized)") }
            )
            ListItem(
                headlineContent = { Text("API接続状態") },
                supportingContent = { Text("Google Gemini API: 接続済み") }
            )
        }
    }
}