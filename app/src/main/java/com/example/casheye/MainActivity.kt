@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.casheye

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.casheye.ui.theme.CasheyeTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import java.time.format.DateTimeFormatter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import com.example.casheye.utils.GeminiAnalyzer
import com.example.casheye.utils.CsvParser
import androidx.compose.material.icons.filled.PhotoCamera
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.casheye.ReceiptItem
import kotlin.plus
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.ExperimentalFoundationApi

import android.widget.Button

import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import android.graphics.Bitmap
import com.example.casheye.ResultActivity




val categoryMap = mapOf(
    "食費" to listOf(
        "精肉", "魚介", "野菜", "果物", "パン",
        "惣菜", "菓子", "飲料", "酒類", "調味料",
        "インスタント食品", "乳製品", "冷凍食品", "加工食品","その他食品"
    ),
    "日用品" to listOf(
        "洗剤", "紙類", "消耗品", "文房具",
        "キッチン用品", "バス・トイレ用品", "衛生用品","その他"
    ),
    "車両費" to listOf(
        "ガソリン", "駐車場代", "メンテナンス","その他"
    ),
    "交通" to listOf(
        "電車", "バス", "タクシー","その他"
    ),
    "外食費" to listOf(
        "昼食", "夕食", "カフェ", "テイクアウト","その他"
    ),
    "医療費" to listOf(
        "診療代", "薬代", "検査代","その他"
    ),
    "保険代" to listOf(
        "生命保険", "損害保険", "自動車保険","その他"
    ),
    "収入" to listOf(
        "給与", "副収入", "還付金","その他"
    )
)



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★★★ CategoryRepositoryの初期化（重要） ★★★
        CategoryRepository.initialize(this)

        // --- 権限リクエストを追加 ---
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "カメラ権限が必要です", Toast.LENGTH_SHORT).show()
            }
        }

        // アプリ起動時にチェック
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        // ------------------------

        setContent {
            CasheyeTheme {
                CashEyeApp()
            }
        }
    }

    private fun convertJsonToCsv(jsonString: String): String {
        val sb = StringBuilder("購入日,購入店舗,商品名,分類大分類,分類中分類,税抜価格,税込価格\n")
        try {
            val root = org.json.JSONObject(jsonString)
            val receipts = root.getJSONArray("receipts")
            for (i in 0 until receipts.length()) {
                val receipt = receipts.getJSONObject(i)
                val date = receipt.getString("date")
                val store = receipt.getString("store")
                val items = receipt.getJSONArray("items")

                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    sb.append("$date,")
                    sb.append("$store,")
                    sb.append("${item.getString("name")},")
                    sb.append("${item.getString("major_category")},")
                    sb.append("${item.getString("minor_category")},")
                    sb.append("${item.getInt("price_excl_tax")},")
                    sb.append("${item.getInt("price_incl_tax")}\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString()
    }



}



// --- CSVエクスポート ---
fun exportReceiptItemsToCSV(context: Context, ReceiptItems: List<ReceiptItem>) {
    try {
        val csvHeader = "購入日,購入店舗,商品名,大分類,中分類,税抜価格,税込価格\n"
        val csvBody = ReceiptItems.joinToString("\n") {
            "${it.date},${it.store},${it.name},${it.majorCategory},${it.minorCategory},${it.priceNet},${it.priceIncludeTax}"
        }
        val file = File(context.cacheDir, "casheye_data.csv")
        file.writeText(csvHeader + csvBody, Charsets.UTF_8)
        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "CSVを共有")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Log.e("CashEye", "共有エラー: ${e.message}")
    }
}

@Composable
fun CsvImportSection(
    onImport: (String) -> Unit
) {
    var csvText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = csvText,
                onValueChange = { csvText = it },
                label = { Text("ここにCSVデータを貼り付け") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (csvText.isNotBlank()) {
                        onImport(csvText)
                        csvText = ""
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("データ取り込み")
            }
        }
    }
}

@Composable
fun CashEyeApp(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // --- 状態管理 ---
    var ReceiptItems by remember { mutableStateOf<List<ReceiptItem>>(emptyList()) }
    var recurringTransactions by remember { mutableStateOf<List<RecurringTransaction>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingReceiptItem by remember { mutableStateOf<ReceiptItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var capturedBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var showCamera by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("casheye_prefs", Context.MODE_PRIVATE)
    var startYear by remember { mutableIntStateOf(prefs.getInt("start_year", 2025)) }
    var startMonth by remember { mutableIntStateOf(prefs.getInt("start_month", 1)) }
    val today = LocalDate.now()
    var baseDate by remember { mutableStateOf(today) }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    // --- CSV解析ロジック ---
    fun processCsvResults(csvText: String) {
        android.util.Log.d("GeminiData", "Received CSV: $csvText")
        isAnalyzing = false
        val lines = csvText.trim().split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("購入日") }

        if (lines.isEmpty()) return

        val newItems = lines.mapNotNull { line ->
            val cols = line.split(",")
            if (cols.size >= 7) {
                try {
                    val cleanNet = cols[5].replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                    val cleanTaxIn = cols[6].replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

                    ReceiptItem(
                        date = cols[0].trim(),
                        store = cols[1].trim(),
                        name = cols[2].trim(),
                        majorCategory = cols[3].trim(),
                        minorCategory = cols[4].trim(),
                        priceNet = cleanNet,
                        priceIncludeTax = cleanTaxIn
                    )
                } catch (e: Exception) { null }
            } else { null }
        }

        if (newItems.isNotEmpty()) {
            val intent = Intent(context, ResultActivity::class.java).apply {
                putExtra("RECEIPT_ITEMS", ArrayList(newItems))
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "有効な商品データが見つかりませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    // ON_RESUMEでリストをリロード
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                ReceiptItems = loadReceiptItems(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        ReceiptItems = loadReceiptItems(context)
        recurringTransactions = loadRecurringTransactions(context)
        // Repositoryの初期化もここで行う
        CategoryRepository.initialize(context)
    }

    val onUpdate: (ReceiptItem, ReceiptItem) -> Unit = { old, new ->
        ReceiptItems = ReceiptItems.map { if (it == old) new else it }
        saveReceiptItems(context, ReceiptItems)
        editingReceiptItem = null
    }

    val onDelete: (ReceiptItem) -> Unit = {
        ReceiptItems = ReceiptItems - it
        saveReceiptItems(context, ReceiptItems)
    }

    val importFromClipboard = {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboardManager?.primaryClip
        val clipboardText = clipData?.let { if (it.itemCount > 0) it.getItemAt(0).text?.toString() else "" } ?: ""
        processCsvResults(clipboardText)
    }

    // --- UI構成 ---
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp)
                .pointerInput(selectedTab) {
                    detectHorizontalDragGestures(onDragEnd = { dragTotal = 0f }) { _, dragAmount ->
                        dragTotal += dragAmount
                        if (dragTotal > 120f) {
                            baseDate = if (selectedTab == 2) baseDate.plusYears(1) else baseDate.plusMonths(1)
                            dragTotal = 0f
                        } else if (dragTotal < -120f) {
                            baseDate = if (selectedTab == 2) baseDate.minusYears(1) else baseDate.minusMonths(1)
                            dragTotal = 0f
                        }
                    }
                }
        ) {
            /* ======= 1. 上部サマリー ======== */
            val baseMonth = YearMonth.from(baseDate)
            val baseYear = baseDate.year
            val filteredReceiptItems = when (selectedTab) {
                0, 1 -> ReceiptItems.filter { it.date.startsWith(baseMonth.toString()) }
                2 -> ReceiptItems.filter { it.date.startsWith(baseYear.toString()) }
                else -> ReceiptItems
            }
            val totalInc = filteredReceiptItems.filter { it.isIncome }.sumOf { it.priceIncludeTax }
            val totalExp = filteredReceiptItems.filter { !it.isIncome }.sumOf { it.priceIncludeTax }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(if (selectedTab == 2) "${baseYear}年の累計" else "${baseMonth.year}年${baseMonth.monthValue}月の累計",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("¥%,d".format(totalInc - totalExp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showExportMenu = true }) { Icon(Icons.Default.Share, "共有") }
                    }
                }
            }

            /* ======== 2. ボタンエリア ======== */
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = importFromClipboard, modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("貼り付け", fontSize = 12.sp)
                }
                Button(onClick = { showCamera = true }, modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("レシート撮影", fontSize = 12.sp)
                }
            }

            /* ======== 3. タブ ======== */
            // ★「分類」を追加
            val tabs = listOf("日別", "月別", "年別", "明細", "分析", "グラフ", "分類", "定期", "設定")
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                        Text(title, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            /* ======== 4. 各画面切り替え ======== */
// --- MainActivity.kt の tabs 定義も更新を確認してください ---
// val tabs = listOf("日別", "月別", "年別", "明細", "分析", "グラフ", "分類", "定期", "設定")

            Box(modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                when (selectedTab) {
                    0 -> DailyScreen(ReceiptItems, { processCsvResults(it) }, onDelete, { editingReceiptItem = it })
                    1 -> HierarchicalReceiptItemList(ReceiptItems, "month", onDelete) { editingReceiptItem = it }
                    2 -> HierarchicalReceiptItemList(ReceiptItems, "year", onDelete) { editingReceiptItem = it }
                    3 -> FullHistoryDatabaseScreen(ReceiptItems, onDelete) { editingReceiptItem = it }
                    4 -> AnalysisScreen(ReceiptItems)
                    5 -> ChartScreen(ReceiptItems)
                    6 -> CategoryManagementScreen() // 分類管理

                    // 7: 定期収支の登録ページ（旧 SettingsScreen）
                    7 -> RecurringTransactionsScreen(
                        recurringTransactions = recurringTransactions,
                        onAdd = { newItem ->
                            recurringTransactions = recurringTransactions + newItem
                            saveRecurringTransactions(context, recurringTransactions)
                        },
                        onUpdate = { old, new ->
                            recurringTransactions = recurringTransactions.map { if (it == old) new else it }
                            saveRecurringTransactions(context, recurringTransactions)
                        },
                        onDelete = { item ->
                            recurringTransactions = recurringTransactions - item
                            saveRecurringTransactions(context, recurringTransactions)
                        }
                    )

                    // 8: システム設定ページ（モデル選択など）
                    8 -> AppSettingsScreen()
                }
            }
        }

        /* ======== 5. カメラ・解析オーバーレイ ======== */
        if (showCamera) {
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.casheye.utils.CameraPreviewScreen(
                    onCapture = { capturedBitmap -> capturedBitmaps = capturedBitmaps + capturedBitmap },
                    onDismiss = { showCamera = false }
                )
                if (capturedBitmaps.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            if (isAnalyzing) return@FloatingActionButton

                            // --- 1. 設定からモデル名と解像度を読み込む ---
                            val modelName = prefs.getString("gemini_model", "gemini-3-flash-preview") ?: "gemini-3-flash-preview"
                            val targetSize = when (prefs.getString("image_quality", "Medium")) {
                                "Low" -> 512
                                "Medium" -> 1024
                                "High" -> 1600
                                else -> 1024
                            }

                            showCamera = false
                            isAnalyzing = true

                            scope.launch {
                                try {
                                    val apiKey = BuildConfig.GEMINI_API_KEY
                                    val analyzer = com.example.casheye.utils.GeminiAnalyzer(apiKey, modelName)

                                    // --- 2. 送信前にリサイズを実行して軽量化！ ---
                                    val resizedBitmaps = capturedBitmaps.map {
                                        resizeBitmapForAnalysis(it, targetSize)
                                    }

                                    // リサイズ後の画像を解析に回す
                                    val analyzedItems = analyzer.analyzeMultipleReceiptImages(resizedBitmaps)

                                    isAnalyzing = false
                                    capturedBitmaps = emptyList()

                                    if (analyzedItems.isNotEmpty()) {
                                        context.startActivity(Intent(context, ResultActivity::class.java).apply {
                                            putExtra("RECEIPT_ITEMS", ArrayList(analyzedItems))
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }
                                } catch (e: Exception) {
                                    isAnalyzing = false
                                    android.util.Log.e("Gemini", "解析エラー", e)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Share, null)
                            Text("${capturedBitmaps.size}枚送信", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

// --- 3. 解析中表示 (モデル名を反映) ---
        if (isAnalyzing) {
            val currentModel = prefs.getString("gemini_model", "gemini-3-flash-preview") ?: "gemini-3-flash-preview"
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Gemini ($currentModel) が解析中...", color = Color.White)
                    Text("リサイズ処理で高速化しています", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }

        /* ======== FAB ======== */
        if (selectedTab != 7 && !showCamera) { // 設定タブ以外で表示
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding()
            ) { Icon(Icons.Default.Add, "追加") }
        }
    }

    // --- ダイアログ群 (省略せず維持) ---
    if (showExportMenu) {
        AlertDialog(
            onDismissRequest = { showExportMenu = false },
            title = { Text("CSV出力形式の選択") },
            confirmButton = {
                TextButton(onClick = { /* 分析用共有処理 */ }) { Text("分析用 (Excel等)") }
            },
            dismissButton = {
                TextButton(onClick = { /* バックアップ共有処理 */ }) { Text("復元用バックアップ") }
            }
        )
    }

    editingReceiptItem?.let { item ->
        EditReceiptItemDialog(item, { editingReceiptItem = null }) { updated -> onUpdate(item, updated) }
    }

    if (showAddDialog) {
        AddReceiptItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newItem ->
                ReceiptItems = ReceiptItems + newItem
                saveReceiptItems(context, ReceiptItems)
                showAddDialog = false
            }
        )
    }
}
@Composable
fun DailyScreen(
    ReceiptItems: List<ReceiptItem>,
    onImportCsv: (String) -> Unit,
    onDelete: (ReceiptItem) -> Unit,
    onEdit: (ReceiptItem) -> Unit
) {
    Column {
        // ✅ CSV取り込みは日別だけ
        CsvImportSection(onImport = onImportCsv)

        HierarchicalReceiptItemList(
            ReceiptItems = ReceiptItems,
            type = "date",
            onDelete = onDelete,
            onEdit = onEdit
        )
    }
}


@Composable
fun HierarchicalReceiptItemList(
    ReceiptItems: List<ReceiptItem>,
    type: String,
    onDelete: (ReceiptItem) -> Unit,
    onEdit: (ReceiptItem) -> Unit
) {
    var expandedHeaders by remember { mutableStateOf(setOf<String>()) }
    var expandedMajors by remember { mutableStateOf(setOf<String>()) }
    var expandedMinors by remember { mutableStateOf(setOf<String>()) }

    val grouped = when (type) {
        // 1. 日付ごとのグループ化（そのままの文字列を使用）
        "date" -> ReceiptItems.groupBy { it.date }

        // 2. 月ごとのグループ化（先頭 7 文字 "YYYY-MM" を抽出） [cite: 2026-01-05]
        "month" -> ReceiptItems.groupBy { it.date.take(7) }

        // 3. 年度（年）ごとのグループ化（先頭 4 文字 "YYYY" を抽出） [cite: 2026-01-05]
        else -> ReceiptItems.groupBy { "${it.date.take(4)}年度" }

    }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize()) {

        grouped.forEach { (header, listForHeader) ->

            // ===== ヘッダ =====
            item {
                val (inc, exp) = calculateBalance(listForHeader)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedHeaders =
                                if (expandedHeaders.contains(header))
                                    expandedHeaders - header
                                else
                                    expandedHeaders + header
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(header, fontWeight = FontWeight.Bold)
                        Row {
                            Text("入: ¥%,d".format(inc), color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Text("出: ¥%,d".format(exp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (expandedHeaders.contains(header)) {

                // ===== 大分類 =====
                listForHeader.groupBy { it.majorCategory }.forEach { (major, majorList) ->
                    val majorKey = "$header-$major"

                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedMajors =
                                        if (expandedMajors.contains(majorKey))
                                            expandedMajors - majorKey
                                        else
                                            expandedMajors + majorKey
                                }
                        ) {
                            Row(
                                Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("▶ $major", fontWeight = FontWeight.SemiBold)
                                Text("¥%,d".format(majorList.sumOf { it.priceIncludeTax }))
                            }
                        }
                    }

                    if (expandedMajors.contains(majorKey)) {

                        // ===== 中分類 =====
                        majorList.groupBy { it.minorCategory }.forEach { (minor, minorList) ->
                            val minorKey = "$majorKey-$minor"

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedMinors =
                                                if (expandedMinors.contains(minorKey))
                                                    expandedMinors - minorKey
                                                else
                                                    expandedMinors + minorKey
                                        }
                                        .padding(start = 40.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Text("・$minor", Modifier.weight(1f))
                                    Text("¥%,d".format(minorList.sumOf { it.priceIncludeTax }))
                                }
                            }

                            if (expandedMinors.contains(minorKey)) {
                                items(minorList) { ReceiptItem ->
                                    ReceiptItemItemRow(
                                        ReceiptItem,
                                        onDelete,
                                        onEdit,
                                        paddingStart = 56.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun FullHistoryDatabaseScreen(ReceiptItems: List<ReceiptItem>, onDelete: (ReceiptItem) -> Unit, onEdit: (ReceiptItem) -> Unit) {
    val sortedReceiptItems = ReceiptItems.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize()) {
        items(sortedReceiptItems) { ReceiptItem ->
            ReceiptItemItemRow(ReceiptItem, onDelete, onEdit, paddingStart = 16.dp, showDate = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun ReceiptItemItemRow(ReceiptItem: ReceiptItem, onDelete: (ReceiptItem) -> Unit, onEdit: (ReceiptItem) -> Unit, paddingStart: androidx.compose.ui.unit.Dp, showDate: Boolean = false) {
    var showOptions by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { showOptions = !showOptions }.padding(start = paddingStart, top = 8.dp, bottom = 8.dp, end = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (showDate) Text(ReceiptItem.date.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                Text(ReceiptItem.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${ReceiptItem.store} | ${ReceiptItem.minorCategory}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            Text("¥%,d".format(ReceiptItem.priceIncludeTax), color = if (ReceiptItem.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        if (showOptions) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(ReceiptItem) }) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Text("修正", fontSize = 12.sp) }
                TextButton(onClick = { onDelete(ReceiptItem) }) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Text("削除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReceiptItemDialog(
    ReceiptItem: ReceiptItem,
    onDismiss: () -> Unit,
    onSave: (ReceiptItem) -> Unit
) {
    var name by remember(ReceiptItem) { mutableStateOf(ReceiptItem.name) }
    var store by remember(ReceiptItem) { mutableStateOf(ReceiptItem.store) }
    var priceStr by remember(ReceiptItem) { mutableStateOf(ReceiptItem.priceIncludeTax.toString()) }
    var major by remember(ReceiptItem) { mutableStateOf(ReceiptItem.majorCategory) }
    var minor by remember(ReceiptItem) { mutableStateOf(ReceiptItem.minorCategory) }

    var majorExpanded by remember { mutableStateOf(false) }
    var minorExpanded by remember { mutableStateOf(false) }

    val minorList = categoryMap[major] ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("明細を修正") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("商品名") }
                )

                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text("店舗名") }
                )

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("税込価格") }
                )

                // ▼ 大分類ドロップダウン
                ExposedDropdownMenuBox(
                    expanded = majorExpanded,
                    onExpandedChange = { majorExpanded = !majorExpanded }
                ) {
                    OutlinedTextField(
                        value = major,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("大分類") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(majorExpanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = majorExpanded,
                        onDismissRequest = { majorExpanded = false }
                    ) {
                        categoryMap.keys.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    major = category
                                    minor = ""   // ★ 大分類変更時は中分類リセット
                                    majorExpanded = false
                                }
                            )
                        }
                    }
                }

                // ▼ 中分類ドロップダウン
                ExposedDropdownMenuBox(
                    expanded = minorExpanded,
                    onExpandedChange = {
                        if (minorList.isNotEmpty()) {
                            minorExpanded = !minorExpanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = minor,
                        onValueChange = {},
                        readOnly = true,
                        enabled = minorList.isNotEmpty(),
                        label = { Text("中分類") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(minorExpanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = minorExpanded,
                        onDismissRequest = { minorExpanded = false }
                    ) {
                        minorList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    minor = m
                                    minorExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newPrice = priceStr.toIntOrNull() ?: ReceiptItem.priceIncludeTax

                onSave(
                    ReceiptItem.copy(
                        name = name,
                        store = store,
                        priceIncludeTax = newPrice,
                        majorCategory = major,
                        minorCategory = minor
                    )
                )
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
fun SettingsScreen(
    recurringTransactions: List<RecurringTransaction>,
    startYear: Int,
    startMonth: Int,
    onStartSettingsChange: (Int, Int) -> Unit,
    onAdd: (RecurringTransaction) -> Unit,
    onDelete: (RecurringTransaction) -> Unit,
    onUpdateRecurring: (RecurringTransaction) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var minor by remember { mutableStateOf("") }
    var isIncome by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RecurringTransaction?>(null) }

    LazyColumn(Modifier.padding(16.dp)) {
        item {
            Text("基本設定", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("家計簿 開始年度:")
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = startYear.toString(),
                    onValueChange = { onStartSettingsChange(it.toIntOrNull() ?: startYear, startMonth) },
                    modifier = Modifier.width(100.dp),
                    label = { Text("年") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("家計簿 開始月:")
                Spacer(Modifier.width(24.dp))
                OutlinedTextField(
                    value = startMonth.toString(),
                    onValueChange = { onStartSettingsChange(startYear, it.toIntOrNull()?.coerceIn(1, 12) ?: startMonth) },
                    modifier = Modifier.width(100.dp),
                    label = { Text("月") }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
        }

        item {
            Text("定期収支の新規登録", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("項目名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金額") }, modifier = Modifier.fillMaxWidth())
            var majorExpanded by remember { mutableStateOf(false) }
            var minorExpanded by remember { mutableStateOf(false) }

            val minorList = categoryMap[major] ?: emptyList()

// ▼ 大分類
            ExposedDropdownMenuBox(
                expanded = majorExpanded,
                onExpandedChange = { majorExpanded = !majorExpanded }
            ) {
                OutlinedTextField(
                    value = major,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("大分類") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(majorExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = majorExpanded,
                    onDismissRequest = { majorExpanded = false }
                ) {
                    categoryMap.keys.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                major = category
                                minor = ""   // ★ 重要
                                majorExpanded = false
                            }
                        )
                    }
                }
            }

// ▼ 中分類
            ExposedDropdownMenuBox(
                expanded = minorExpanded,
                onExpandedChange = {
                    if (minorList.isNotEmpty()) {
                        minorExpanded = !minorExpanded
                    }
                }
            ) {
                OutlinedTextField(
                    value = minor,
                    onValueChange = {},
                    readOnly = true,
                    enabled = minorList.isNotEmpty(),
                    label = { Text("中分類") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(minorExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = minorExpanded,
                    onDismissRequest = { minorExpanded = false }
                ) {
                    minorList.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                minor = m
                                minorExpanded = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isIncome) "種別：収入" else "種別：支出")
                Switch(checked = isIncome, onCheckedChange = { isIncome = it })
            }
            Button(onClick = {
                if (title.isNotEmpty()) {
                    onAdd(RecurringTransaction(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        amount = amount.toIntOrNull() ?: 0,
                        majorCategory = if (isIncome) "収入" else major,
                        minorCategory = minor,
                        dayOfMonth = 25,
                        startYearMonth = YearMonth.of(startYear, startMonth),
                        isIncome = isIncome
                    ))
                    title = ""; amount = ""; major = ""; minor = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("登録する") }
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("登録済みリスト", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        items(recurringTransactions) { item ->
            ListItem(
                headlineContent = { Text(item.title) },
                supportingContent = { Text("¥%,d (${item.majorCategory})".format(item.amount)) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { editingItem = item }) {
                            Icon(Icons.Default.Edit, contentDescription = "修正")
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    }

    // 修正用ダイアログ（開始年月・分類・種別すべて対応）
    editingItem?.let { item ->
        var editTitle by remember { mutableStateOf(item.title) }
        var editAmount by remember { mutableStateOf(item.amount.toString()) }
        var editMajor by remember { mutableStateOf(item.majorCategory) }
        var editMinor by remember { mutableStateOf(item.minorCategory) }
        var editIsIncome by remember { mutableStateOf(item.isIncome) }
        // 開始年月の編集用
        var editYear by remember { mutableIntStateOf(item.startYearMonth.year) }
        var editMonth by remember { mutableIntStateOf(item.startYearMonth.monthValue) }

        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("定期収支の修正") },
            text = {
                // 項目が多いので、画面からはみ出さないよう縦スクロール可能にします
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("項目名") })
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("金額") })

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editYear.toString(),
                            onValueChange = { editYear = it.toIntOrNull() ?: editYear },
                            label = { Text("開始年") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editMonth.toString(),
                            onValueChange = { editMonth = it.toIntOrNull()?.coerceIn(1, 12) ?: editMonth },
                            label = { Text("開始月") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    var editMajorExpanded by remember { mutableStateOf(false) }
                    var editMinorExpanded by remember { mutableStateOf(false) }

                    val editMinorList = categoryMap[editMajor] ?: emptyList()

// ▼ 大分類
                    ExposedDropdownMenuBox(
                        expanded = editMajorExpanded,
                        onExpandedChange = { editMajorExpanded = !editMajorExpanded }
                    ) {
                        OutlinedTextField(
                            value = editMajor,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("大分類") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(editMajorExpanded)
                            },
                            modifier = Modifier.menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = editMajorExpanded,
                            onDismissRequest = { editMajorExpanded = false }
                        ) {
                            categoryMap.keys.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        editMajor = category
                                        editMinor = ""
                                        editMajorExpanded = false
                                    }
                                )
                            }
                        }
                    }

// ▼ 中分類
                    ExposedDropdownMenuBox(
                        expanded = editMinorExpanded,
                        onExpandedChange = {
                            if (editMinorList.isNotEmpty()) {
                                editMinorExpanded = !editMinorExpanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = editMinor,
                            onValueChange = {},
                            readOnly = true,
                            enabled = editMinorList.isNotEmpty(),
                            label = { Text("中分類") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(editMinorExpanded)
                            },
                            modifier = Modifier.menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = editMinorExpanded,
                            onDismissRequest = { editMinorExpanded = false }
                        ) {
                            editMinorList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        editMinor = m
                                        editMinorExpanded = false
                                    }
                                )
                            }
                        }
                    }


                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (editIsIncome) "種別：収入" else "種別：支出")
                        Switch(checked = editIsIncome, onCheckedChange = { editIsIncome = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateRecurring(item.copy(
                        title = editTitle,
                        amount = editAmount.toIntOrNull() ?: 0,
                        majorCategory = if (editIsIncome) "収入" else editMajor,
                        minorCategory = editMinor,
                        isIncome = editIsIncome,
                        startYearMonth = YearMonth.of(editYear, editMonth) // ここで開始年月を更新
                    ))
                    editingItem = null
                }) { Text("更新") }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) { Text("キャンセル") }
            }
        )
    }
}


@Composable
fun ChartScreen(ReceiptItems: List<ReceiptItem>) {
    // 表示対象の年月を保持する状態
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }

    // 選択された月の支出だけを抽出
    val monthlyReceiptItems = ReceiptItems.filter {
        // String(YYYY-MM-DD) の先頭7文字を解析して YearMonth に変換し比較する [cite: 2026-01-05]
        java.time.YearMonth.parse(it.date.take(7)) == displayMonth && !it.isIncome
    }
    // 大分類ごとに集計
    val categoryTotals = monthlyReceiptItems.groupBy { it.majorCategory }
        .mapValues { entry -> entry.value.sumOf { it.priceIncludeTax } }

    val totalAmount = categoryTotals.values.sum().toFloat()

    Column(Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // --- 月選択セレクター ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                Text("＜", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "${displayMonth.year}年${displayMonth.monthValue}月",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                Text("＞", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text("の支出内訳", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(24.dp))

        if (totalAmount > 0) {
            // 円グラフ
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    var startAngle = -90f
                    // 見やすい配色リスト
                    val colors = listOf(
                        Color(0xFF80DEEA), Color(0xFFCE93D8), Color(0xFFFFF59D),
                        Color(0xFFA5D6A7), Color(0xFFEF9A9A), Color(0xFF90CAF9),
                        Color(0xFFFFCC80), Color(0xFFBCAAA4)
                    )

                    categoryTotals.entries.forEachIndexed { index, entry ->
                        val sweepAngle = (entry.value / totalAmount) * 360f
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                }
                // 真ん中に合計金額を表示（ドーナツグラフ風にする場合はここも使えます）
            }

            Spacer(Modifier.height(24.dp))

            // 凡例リスト
            LazyColumn(Modifier.fillMaxWidth()) {
                val colors = listOf(
                    Color(0xFF80DEEA), Color(0xFFCE93D8), Color(0xFFFFF59D),
                    Color(0xFFA5D6A7), Color(0xFFEF9A9A), Color(0xFF90CAF9),
                    Color(0xFFFFCC80), Color(0xFFBCAAA4)
                )
                categoryTotals.entries.forEachIndexed { index, entry ->
                    item {
                        Row(Modifier.padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(14.dp), color = colors[index % colors.size], shape = MaterialTheme.shapes.small) {}
                            Spacer(Modifier.width(12.dp))
                            Text(entry.key, Modifier.weight(1f), fontSize = 14.sp)
                            Text("¥%,d".format(entry.value), fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            Text("${(entry.value / totalAmount * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.width(40.dp))
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        } else {
            Spacer(Modifier.height(50.dp))
            Text("この月の支出データはありません", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthlyTableScreen(ReceiptItems: List<ReceiptItem>) {
    val context = LocalContext.current
    val horizontalScrollState = rememberScrollState()
    var expandedMajor by remember { mutableStateOf<String?>(null) }

    // データ準備
    val summaries = remember(ReceiptItems) { buildMonthlySummaries(ReceiptItems) }
    val months = remember(summaries) { summaries.map { it.monthStr }.distinct().sorted() }
    val majorCategories = remember(ReceiptItems) {
        ReceiptItems.map { it.majorCategory.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // --- A. CSVボタン ---
        item {
            Button(modifier = Modifier.padding(8.dp), onClick = { exportMonthlyAnalysisToCsv(context, ReceiptItems) }) {
                Text("分析CSV（サマリー＋分類）")
            }
        }

        // --- B. 月ヘッダー (ここが黒い帯の部分) ---
        stickyHeader {
            Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.8f)).padding(vertical = 8.dp)) {
                Spacer(modifier = Modifier.width(120.dp)) // 左端の固定幅
                Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    months.forEach { label ->
                        Text(text = label, modifier = Modifier.width(100.dp), color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- C. サマリー段 (収入・支出・残高) ---
        listOf("収入", "支出", "残高").forEach { label ->
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(text = label, modifier = Modifier.width(120.dp).padding(start = 12.dp), fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        months.forEach { mStr ->
                            val s = summaries.firstOrNull { it.monthStr == mStr }
                            val amt = when(label) {
                                "収入" -> s?.incomeTotal ?: 0
                                "支出" -> s?.ReceiptItemTotal ?: 0
                                "残高" -> s?.balance ?: 0
                                else -> 0
                            }
                            Text(text = if(amt == 0) "–" else "¥%,d".format(amt), modifier = Modifier.width(100.dp).padding(end = 8.dp), textAlign = TextAlign.End)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp)
            }
        }

        // --- D. 分類別段 ---
        majorCategories.forEach { major ->
            // --- 大分類 ---
            item {
                val isExpanded = expandedMajor == major
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expandedMajor = if (isExpanded) null else major }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isExpanded) "▼ $major" else "▶ $major", modifier = Modifier.width(120.dp).padding(start = 12.dp), fontWeight = FontWeight.Medium)
                    Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        months.forEach { mStr ->
                            val total = ReceiptItems.filter { it.majorCategory.trim() == major && it.date.startsWith(mStr) }.sumOf { it.priceIncludeTax }
                            Text(text = if (total == 0) "–" else "¥%,d".format(total), modifier = Modifier.width(100.dp).padding(end = 8.dp), textAlign = TextAlign.End)
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
            }

            // --- 中分類 (展開時) ---
            if (expandedMajor == major) {
                val subCategories = ReceiptItems.filter { it.majorCategory.trim() == major }.map { it.minorCategory.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                subCategories.forEach { sub ->
                    item {
                        Row(modifier = Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.05f)).padding(vertical = 8.dp)) {
                            Text(text = "  ・$sub", modifier = Modifier.width(120.dp).padding(start = 24.dp), fontSize = 13.sp, color = Color.Gray)
                            Row(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                                months.forEach { mStr ->
                                    val total = ReceiptItems.filter { it.majorCategory.trim() == major && it.minorCategory.trim() == sub && it.date.startsWith(mStr) }.sumOf { it.priceIncludeTax }
                                    Text(text = if (total == 0) "–" else "¥%,d".format(total), modifier = Modifier.width(100.dp).padding(end = 8.dp), textAlign = TextAlign.End, fontSize = 13.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
fun buildMonthlySummaries(
    ReceiptItems: List<ReceiptItem>
): List<MonthlySummary> {

    return ReceiptItems
        .filter { it.date.length >= 7 } // YYYY-MM までの長さがあるかチェック
        .groupBy { it.date.take(7) }   // 先頭7文字 "YYYY-MM" でグループ化 [cite: 2026-01-05]
        .map { (month, list) ->

            val income = list
                .filter { it.isIncome }
                .sumOf { it.priceIncludeTax }

            val totalExpense = list
                .filter { !it.isIncome }
                .sumOf { it.priceIncludeTax }

            val majorTotals = list
                .filter { !it.isIncome }
                .groupBy { it.majorCategory }
                .mapValues { entry ->
                    entry.value.sumOf { it.priceIncludeTax }
                }

            MonthlySummary(
                monthStr = month,             // String型として保持
                incomeTotal = income,
                ReceiptItemTotal = totalExpense,
                balance = income - totalExpense,
                majorCategoryTotals = majorTotals
            )
        }
        .sortedBy { it.monthStr }
}


@Composable
fun AnalysisScreen(ReceiptItems: List<ReceiptItem>) {
    // 整理：これ一行だけにします。
    // 描画ロジックはすべて MonthlyTableScreen の中にあるため、これで十分です。
    MonthlyTableScreen(ReceiptItems = ReceiptItems)
}



// 大分類別の月次集計 [cite: 2026-01-05]
fun buildAnalysisResult(items: List<ReceiptItem>): Map<String, Map<String, Int>> {
    return items
        .groupBy { it.majorCategory.trim() } // 空白を除去
        .mapValues { (_, majorList) ->
            majorList
                .filter { it.date.length >= 7 }
                .groupBy { it.date.take(7) } // "YYYY-MM" [cite: 2026-01-05]
                .mapValues { it.value.sumOf { item -> item.priceIncludeTax } }
        }
}

// 中分類別の月次集計 [cite: 2026-01-05]
fun buildSubCategoryAnalysis(items: List<ReceiptItem>, major: String): Map<String, Map<String, Int>> {
    return items
        .filter { it.majorCategory.trim() == major.trim() }
        .groupBy { it.minorCategory.trim() }
        .mapValues { (_, minorList) ->
            minorList
                .filter { it.date.length >= 7 }
                .groupBy { it.date.take(7) } // "YYYY-MM" [cite: 2026-01-05]
                .mapValues { it.value.sumOf { item -> item.priceIncludeTax } }
        }
}



@Composable
fun MajorCategoryAnalysisTable(ReceiptItems: List<ReceiptItem>) {
    // ReceiptItems → 月別 × 大分類 集計
    // AnalysisTableSkeleton に流す
}

fun buildMajorCategoryMonthlyTable(
    ReceiptItems: List<ReceiptItem>
): Pair<List<String>, Map<String, Map<String, Int>>> {

    val monthKeys = ReceiptItems
        .map { java.time.YearMonth.from(java.time.LocalDate.parse(it.date)) }
        .distinct()
        .sorted()
        .map { "${it.year}/${it.monthValue}" }

    val table = ReceiptItems
        .filter { !it.isIncome }
        .groupBy { it.majorCategory }
        .mapValues { (_, list) ->
            list
                .groupBy { java.time.YearMonth.from(java.time.LocalDate.parse(it.date)) }
                .mapValues { (_, monthList) ->
                    monthList.sumOf { it.priceIncludeTax }
                }
                .mapKeys { (ym, _) ->
                    "${ym.year}/${ym.monthValue}"
                }
        }

    return monthKeys to table
}



fun parseCsvToReceiptItems(csv: String): List<ReceiptItem> {

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    return csv
        .replace("\uFEFF", "")
        .replace("\r", "")
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }   // ← これが決定打
        .drop(1)                      // ← ここで初めてヘッダーを捨てられる
        .mapNotNull { line ->

            val cols = line.split(",")

            if (cols.size < 7) return@mapNotNull null

            try {
                ReceiptItem(
                    date = cols[0].trim(),                     // 購入日
                    store = cols[1].trim(),                    // 購入店舗
                    name = cols[2].trim(),                     // 商品名
                    majorCategory = cols[3].trim(),            // 分類大分類
                    minorCategory = cols[4].trim(),            // 分類中分類
                    priceNet = cols[5].trim().toDoubleOrNull()?.toInt() ?: 0,      // 税抜価格
                    priceIncludeTax = cols[6].trim().toDoubleOrNull()?.toInt() ?: 0 // 税込価格
                )
            } catch (e: Exception) {
                null
            }
        }
}
@Composable
fun SummaryCard(
    ReceiptItems: List<ReceiptItem>,
    selectedTab: Int,
    baseMonth: YearMonth,
    baseYear: Int
) {

    val filteredReceiptItems = when (selectedTab) {
        // 日別・月別 → 表示中の月
        0, 1 -> ReceiptItems.filter {
            java.time.YearMonth.from(java.time.LocalDate.parse(it.date)) == baseMonth
        }

        // 年別 → 表示中の年（1月〜12月）
        2 -> ReceiptItems.filter {
            java.time.LocalDate.parse(it.date).year == baseYear
        }

        else -> ReceiptItems
    }

    val income = filteredReceiptItems
        .filter { it.isIncome }
        .sumOf { it.priceIncludeTax }

    val ReceiptItem = filteredReceiptItems
        .filter { !it.isIncome }
        .sumOf { it.priceIncludeTax }

    val title = when (selectedTab) {
        0, 1 -> "${baseMonth.year}年${baseMonth.monthValue}月の累計"
        2 -> "${baseYear}年の累計"
        else -> "累計"
    }

    SummaryCardUI(
        title = title,
        amount = income - ReceiptItem
    )
}

@Composable
fun SummaryCardUI(
    title: String,
    amount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "¥${"%,d".format(amount)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}




@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptItemDialog(
    onDismiss: () -> Unit,
    onAdd: (ReceiptItem) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var major by remember { mutableStateOf("") }
    var minor by remember { mutableStateOf("") }

    var majorExpanded by remember { mutableStateOf(false) }
    var minorExpanded by remember { mutableStateOf(false) }

    val minorList = categoryMap[major] ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手入力で追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // 日付
                OutlinedTextField(
                    value = date.toString(),
                    onValueChange = {
                        runCatching { LocalDate.parse(it) }
                            .onSuccess { date = it }
                    },
                    label = { Text("日付 (yyyy-MM-dd)") }
                )

                // 商品名
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("商品名") }
                )

                // 金額
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金額（税込）") }
                )

                // ▼ 大分類ドロップダウン
                ExposedDropdownMenuBox(
                    expanded = majorExpanded,
                    onExpandedChange = { majorExpanded = !majorExpanded }
                ) {
                    OutlinedTextField(
                        value = major,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("大分類") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(majorExpanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = majorExpanded,
                        onDismissRequest = { majorExpanded = false }
                    ) {
                        categoryMap.keys.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    major = category
                                    minor = ""        // ★ 大分類変更時は中分類リセット
                                    majorExpanded = false
                                }
                            )
                        }
                    }
                }

                // ▼ 中分類ドロップダウン
                ExposedDropdownMenuBox(
                    expanded = minorExpanded,
                    onExpandedChange = {
                        if (minorList.isNotEmpty()) {
                            minorExpanded = !minorExpanded
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = minor,
                        onValueChange = {},
                        readOnly = true,
                        enabled = minorList.isNotEmpty(),
                        label = { Text("中分類") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(minorExpanded)
                        },
                        modifier = Modifier.menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = minorExpanded,
                        onDismissRequest = { minorExpanded = false }
                    ) {
                        minorList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    minor = m
                                    minorExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = amount.toIntOrNull() ?: return@Button

                    onAdd(
                        ReceiptItem(
                            date = date.toString(),
                            store = "手入力",
                            name = name,
                            majorCategory = major,
                            minorCategory = minor,
                            priceNet = price,
                            priceIncludeTax = price
                        )
                    )
                }
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MajorCategoryDropdown(
    selected: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("大分類") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categoryMap.keys.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onSelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MinorCategoryDropdown(
    majorCategory: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    val minors = categoryMap[majorCategory] ?: emptyList()
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = minors.isNotEmpty(),
            label = { Text("中分類") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            minors.forEach { minor ->
                DropdownMenuItem(
                    text = { Text(minor) },
                    onClick = {
                        onSelected(minor)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun calculateBalance(list: List<ReceiptItem>): Pair<Int, Int> {
    val inc = list.filter { it.isIncome }.sumOf { it.priceIncludeTax }
    val exp = list.filter { !it.isIncome }.sumOf { it.priceIncludeTax }
    return inc to exp
}


// クリップボードから文字列を取得し、リストに変換する処理


@Composable
fun ReceiptInputSection(onReceiptItemsParsed: (List<ReceiptItem>) -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        Button(
            onClick = {
                val results = processClipboard(context)
                if (results.isNotEmpty()) {
                    onReceiptItemsParsed(results)
                    Toast.makeText(context, "${results.size}件読み込みました", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "有効なCSVデータが見つかりません", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("GeminiのCSVを貼り付け")
        }
    }
}

fun processClipboard(context: Context): List<ReceiptItem> {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = clipboard.primaryClip

    // if文全体の結果を返却するようにします
    return if (clipData != null && clipData.itemCount > 0) {
        val pastedText = clipData.getItemAt(0).text.toString()
        // [cite: 2026-01-05] 仕様に基づき、CSVを解析したリストを直接返します
        com.example.casheye.utils.CsvParser.parse(pastedText)
    } else {
        emptyList()
    }
}

// --- クラスの内部に配置してください ---


private fun startReceiptAnalysis(
    bitmaps: List<android.graphics.Bitmap>, // 複数画像を受け取るように変更
    currentReceiptItems: List<ReceiptItem>,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onResult: (List<ReceiptItem>) -> Unit,
    onFinished: () -> Unit
) {
    scope.launch {
        try {
            val apiKey = com.example.casheye.BuildConfig.GEMINI_API_KEY
            val analyzer = com.example.casheye.utils.GeminiAnalyzer(apiKey)

            // Geminiに「複数枚の画像リスト」を渡して解析（先ほどAnalyzerに追加した関数を呼ぶ）
            val results: List<ReceiptItem> = analyzer.analyzeMultipleReceiptImages(bitmaps)

            if (results.isNotEmpty()) {
                // 1. 解析結果を呼び出し元（MainActivity）に返す
                // これにより「〇件解析しました」というトーストやUI更新が走ります
                onResult(results)

                // 2. Intentを作成して結果画面(ResultActivity)へ飛ばす
                val intent = android.content.Intent(context, ResultActivity::class.java).apply {
                    val arrayList = ArrayList<ReceiptItem>(results)
                    val bundle = android.os.Bundle()
                    bundle.putSerializable("RECEIPT_ITEMS", arrayList)
                    putExtras(bundle)

                    // すでに起動している場合は再利用し、新しいタスクとして起動
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }

                context.startActivity(intent)
            } else {
                // 結果が空の場合もフラグを下ろす必要がある
                onFinished()
                android.widget.Toast.makeText(context, "解析結果が空でした。もう一度撮影してください。", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            onFinished()
            android.util.Log.e("GeminiError", "Analysis failed", e)
            android.widget.Toast.makeText(context, "解析エラー: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        } finally {
            // 成功・失敗に関わらず解析中表示を消す
            onFinished()
        }
    }
}


// --- MainActivityクラスの最後の } の直前に配置 ---

private fun exportMonthlyAnalysisToCsv(context: android.content.Context, items: List<ReceiptItem>) {
    // 1. データ準備（画面の表と同じロジックを使用） [cite: 2026-01-05]
    val summaries = buildMonthlySummaries(items)
    val months = summaries.map { it.monthStr }.distinct().sortedDescending()
    val majorCategories = items.map { it.majorCategory.trim() }.filter { it.isNotBlank() }.distinct().sorted()

    val csvContent = StringBuilder().apply {
        // --- A. サマリーセクション ---
        append("サマリー,${months.joinToString(",")}\n")
        listOf("収入", "支出", "残高").forEach { label ->
            append(label)
            months.forEach { mStr ->
                val s = summaries.firstOrNull { it.monthStr == mStr }
                val amt = when(label) {
                    "収入" -> s?.incomeTotal ?: 0
                    "支出" -> s?.ReceiptItemTotal ?: 0
                    "残高" -> s?.balance ?: 0
                    else -> 0
                }
                append(",$amt")
            }
            append("\n")
        }
        append("\n") // 空行

        // --- B. 大分類セクション ---
        append("大分類,${months.joinToString(",")}\n")
        majorCategories.forEach { major ->
            append(major)
            months.forEach { mStr ->
                val total = items.filter { it.majorCategory.trim() == major && it.date.startsWith(mStr) }
                    .sumOf { it.priceIncludeTax }
                append(",$total")
            }
            append("\n")
        }
        append("\n") // 空行

        // --- C. 中分類セクション ---
        append("中分類,${months.joinToString(",")}\n")
        majorCategories.forEach { major ->
            val subCategories = items.filter { it.majorCategory.trim() == major }
                .map { it.minorCategory.trim() }.filter { it.isNotBlank() }.distinct().sorted()
            subCategories.forEach { sub ->
                append("$sub（$major）")
                months.forEach { mStr ->
                    val total = items.filter { it.majorCategory.trim() == major && it.minorCategory.trim() == sub && it.date.startsWith(mStr) }
                        .sumOf { it.priceIncludeTax }
                    append(",$total")
                }
                append("\n")
            }
        }
    }.toString()

    // --- 2. 共有処理（ここは変更なし） ---
    try {
        val fileName = "analysis_matrix_${System.currentTimeMillis()}.csv"
        val file = java.io.File(context.cacheDir, fileName)
        file.writeText(csvContent, charset("Shift-JIS")) // Excelで開くならShift-JISが安全ですが、文字化けするならUTF-8に

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(shareIntent, "分析CSVを共有"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "共有失敗: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }


    @Composable
    fun CategoryManagementScreen() {
        val context = LocalContext.current
        var categoryMap by remember { mutableStateOf(CategoryRepository.getCategoryMap()) }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            categoryMap.forEach { (major, minors) ->
                item {
                    Text(
                        text = major,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(minors) { minor ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "・$minor")
                        // 基本分類以外の「追加分」だけ削除ボタンを出すことも可能
                        if (minor != "その他") {
                            IconButton(onClick = {
                                CategoryRepository.removeMinorCategory(context, major, minor)
                                categoryMap = CategoryRepository.getCategoryMap() // 表示更新
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color.Gray)
                            }
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }
        }
    }


} // ← ここが MainActivity クラスを閉じる最後のカッコです


@Composable
fun RecurringTransactionsScreen(
    recurringTransactions: List<RecurringTransaction>,
    onDelete: (RecurringTransaction) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "登録済みの定期収支",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (recurringTransactions.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "定期収支は登録されていません。\n設定タブの「定期収支設定」から追加できます。",
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                // items に id を key として渡すことで、リストの動作が高速・安定になります
                items(recurringTransactions, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        ListItem(
                            headlineContent = { Text(item.title, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text("${item.majorCategory} > ${item.minorCategory} | 毎月 ${item.dayOfMonth} 日")
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "¥%,d".format(item.amount),
                                        color = if (item.isIncome) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { onDelete(item) }) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                            contentDescription = "削除",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

fun resizeBitmap(bitmap: Bitmap, targetLongSide: Int): Bitmap {
    if (targetLongSide <= 0) return bitmap // 0（無制限）ならそのまま

    val width = bitmap.width
    val height = bitmap.height
    val longSide = if (width > height) width else height

    if (longSide <= targetLongSide) return bitmap

    val scale = targetLongSide.toFloat() / longSide
    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

/**
 * 画像を指定された長辺サイズにリサイズする関数
 */
fun resizeBitmapForAnalysis(bitmap: Bitmap, targetLongSide: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    val longSide = if (width > height) width else height

    // 指定サイズより小さい場合はそのまま返す
    if (longSide <= targetLongSide) return bitmap

    val scale = targetLongSide.toFloat() / longSide
    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()

    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}