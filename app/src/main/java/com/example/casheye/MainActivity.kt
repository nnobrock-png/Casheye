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


val categoryMap = mapOf(
    "食費" to listOf(
        "精肉", "魚介", "野菜", "果物", "パン",
        "惣菜", "菓子", "飲料", "酒類", "調味料",
        "インスタント食品", "乳製品", "冷凍食品", "その他食品"
    ),
    "日用品" to listOf(
        "洗剤", "紙類", "消耗品", "文房具",
        "キッチン用品", "バス・トイレ用品", "衛生用品"
    ),
    "車両費" to listOf(
        "ガソリン", "駐車場代", "メンテナンス"
    ),
    "交通" to listOf(
        "電車", "バス", "タクシー"
    ),
    "外食費" to listOf(
        "昼食", "夕食", "カフェ", "テイクアウト"
    ),
    "医療費" to listOf(
        "診療代", "薬代", "検査代"
    ),
    "保険代" to listOf(
        "生命保険", "損害保険", "自動車保険"
    ),
    "収入" to listOf(
        "給与", "副収入", "還付金"
    )
)



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CasheyeTheme {
                Surface {
                    CashEyeApp()
                }
            }
        }
    }
}



// --- CSVエクスポート ---
fun exportExpensesToCSV(context: Context, expenses: List<Expense>) {
    try {
        val csvHeader = "購入日,購入店舗,商品名,大分類,中分類,税抜価格,税込価格\n"
        val csvBody = expenses.joinToString("\n") {
            "${it.date},${it.store},${it.name},${it.majorCategory},${it.minorCategory},${it.priceExcludeTax},${it.priceIncludeTax}"
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

    val context = LocalContext.current

    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var recurringTransactions by remember { mutableStateOf<List<RecurringTransaction>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    // ★ 手入力ダイアログ制御
    var showAddDialog by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("casheye_prefs", Context.MODE_PRIVATE)
    var startYear by remember { mutableIntStateOf(prefs.getInt("start_year", 2025)) }
    var startMonth by remember { mutableIntStateOf(prefs.getInt("start_month", 1)) }

    val today = LocalDate.now()
    var baseDate by remember { mutableStateOf(today) }

    // ★ スワイプ量蓄積
    var dragTotal by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        expenses = loadExpenses(context)
        recurringTransactions = loadRecurringTransactions(context)
    }

    val onUpdate: (Expense, Expense) -> Unit = { old, new ->
        expenses = expenses.map { if (it == old) new else it }
        saveExpenses(context, expenses)
        editingExpense = null
    }

    val onDelete: (Expense) -> Unit = {
        expenses = expenses - it
        saveExpenses(context, expenses)
    }

    // =============================
    // ★★★ 全体を Box で包む ★★★
    // =============================
    Box(
        modifier = modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .pointerInput(selectedTab) {
                    detectHorizontalDragGestures(
                        onDragEnd = { dragTotal = 0f }
                    ) { _, dragAmount ->
                        dragTotal += dragAmount
                        val threshold = 120f

                        if (dragTotal > threshold) {
                            // 👉 右スワイプ → 未来
                            baseDate = if (selectedTab == 2)
                                baseDate.plusYears(1)
                            else
                                baseDate.plusMonths(1)
                            dragTotal = 0f
                        } else if (dragTotal < -threshold) {
                            // 👈 左スワイプ → 過去
                            baseDate = if (selectedTab == 2)
                                baseDate.minusYears(1)
                            else
                                baseDate.minusMonths(1)
                            dragTotal = 0f
                        }
                    }
                }
        ) {

            /* ======== 上部サマリー ======== */

            val baseMonth = YearMonth.from(baseDate)
            val baseYear = baseDate.year

            val filteredExpenses = when (selectedTab) {
                0, 1 -> expenses.filter { YearMonth.from(it.date) == baseMonth }
                2 -> expenses.filter { it.date.year == baseYear }
                else -> expenses
            }

            val totalInc = filteredExpenses.filter { it.isIncome }.sumOf { it.priceIncludeTax }
            val totalExp = filteredExpenses.filter { !it.isIncome }.sumOf { it.priceIncludeTax }

            val title = when (selectedTab) {
                0, 1 -> "${baseMonth.year}年${baseMonth.monthValue}月の累計"
                2 -> "${baseYear}年の累計"
                else -> "累計"
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {

                    Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "¥%,d".format(totalInc - totalExp),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val csv = exportExpensesToCsv(expenses)
                                val file = saveCsvToCache(context, csv)
                                shareCsv(context, file)
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "共有")
                        }

                    }

                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("収入合計", fontSize = 10.sp)
                            Text("¥%,d".format(totalInc), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("支出合計", fontSize = 10.sp)
                            Text("¥%,d".format(totalExp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            /* ======== タブ ======== */

            val tabs = listOf("日別", "月別", "年別", "明細", "分析", "グラフ", "設定")

            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                        Text(title, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            /* ======== 中身 ======== */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
            ) {
                when (selectedTab) {
                    0 -> DailyScreen(
                        expenses = expenses,
                        onImportCsv = { csvText ->
                            val imported = parseCsvToExpenses(csvText)
                            if (imported.isNotEmpty()) {
                                expenses = imported + expenses
                                saveExpenses(context, expenses)
                            }
                        },
                        onDelete = onDelete,
                        onEdit = { editingExpense = it }
                    )

                    1 -> HierarchicalExpenseList(expenses, "month", onDelete) { editingExpense = it }
                    2 -> HierarchicalExpenseList(expenses, "year", onDelete) { editingExpense = it }
                    3 -> FullHistoryDatabaseScreen(expenses, onDelete) { editingExpense = it }
                    4 -> AnalysisScreen(expenses)
                    5 -> ChartScreen(expenses)
                    6 -> SettingsScreen(recurringTransactions, startYear, startMonth, { _, _ -> }, {}, {}, {})
                }
            }
        }

        /* ======== ★ FAB（右下固定） ======== */

        if (selectedTab != 6) { // 設定タブでは非表示なども可能
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "手入力で追加")
            }
        }
    }

    /* ======== ダイアログ群 ======== */

    editingExpense?.let {
        EditExpenseDialog(it, { editingExpense = null }) { updated ->
            onUpdate(it, updated)
        }
    }

    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { expense ->
                expenses = expenses + expense
                saveExpenses(context, expenses)
                showAddDialog = false
            }
        )
    }
}


@Composable
fun DailyScreen(
    expenses: List<Expense>,
    onImportCsv: (String) -> Unit,
    onDelete: (Expense) -> Unit,
    onEdit: (Expense) -> Unit
) {
    Column {
        // ✅ CSV取り込みは日別だけ
        CsvImportSection(onImport = onImportCsv)

        HierarchicalExpenseList(
            expenses = expenses,
            type = "date",
            onDelete = onDelete,
            onEdit = onEdit
        )
    }
}


@Composable
fun HierarchicalExpenseList(
    expenses: List<Expense>,
    type: String,
    onDelete: (Expense) -> Unit,
    onEdit: (Expense) -> Unit
) {
    var expandedHeaders by remember { mutableStateOf(setOf<String>()) }
    var expandedMajors by remember { mutableStateOf(setOf<String>()) }
    var expandedMinors by remember { mutableStateOf(setOf<String>()) }

    val grouped = when (type) {
        "date" -> expenses.groupBy { it.date.toString() }
        "month" -> expenses.groupBy { YearMonth.from(it.date).toString() }
        else -> expenses.groupBy { "${it.date.year}年度" }
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
                                items(minorList) { expense ->
                                    ExpenseItemRow(
                                        expense,
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
fun FullHistoryDatabaseScreen(expenses: List<Expense>, onDelete: (Expense) -> Unit, onEdit: (Expense) -> Unit) {
    val sortedExpenses = expenses.sortedByDescending { it.date }
    LazyColumn(Modifier.fillMaxSize()) {
        items(sortedExpenses) { expense ->
            ExpenseItemRow(expense, onDelete, onEdit, paddingStart = 16.dp, showDate = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun ExpenseItemRow(expense: Expense, onDelete: (Expense) -> Unit, onEdit: (Expense) -> Unit, paddingStart: androidx.compose.ui.unit.Dp, showDate: Boolean = false) {
    var showOptions by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { showOptions = !showOptions }.padding(start = paddingStart, top = 8.dp, bottom = 8.dp, end = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (showDate) Text(expense.date.toString(), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                Text(expense.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${expense.store} | ${expense.minorCategory}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
            Text("¥%,d".format(expense.priceIncludeTax), color = if (expense.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        if (showOptions) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEdit(expense) }) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Text("修正", fontSize = 12.sp) }
                TextButton(onClick = { onDelete(expense) }) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Text("削除", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var name by remember(expense) { mutableStateOf(expense.name) }
    var store by remember(expense) { mutableStateOf(expense.store) }
    var priceStr by remember(expense) { mutableStateOf(expense.priceIncludeTax.toString()) }
    var major by remember(expense) { mutableStateOf(expense.majorCategory) }
    var minor by remember(expense) { mutableStateOf(expense.minorCategory) }

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
                val newPrice = priceStr.toIntOrNull() ?: expense.priceIncludeTax

                onSave(
                    expense.copy(
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
fun ChartScreen(expenses: List<Expense>) {
    // 表示対象の年月を保持する状態
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }

    // 選択された月の支出だけを抽出
    val monthlyExpenses = expenses.filter {
        YearMonth.from(it.date) == displayMonth && !it.isIncome
    }

    // 大分類ごとに集計
    val categoryTotals = monthlyExpenses.groupBy { it.majorCategory }
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


@Composable
fun MonthlyTableScreen(
    expenses: List<Expense>
) {
    // --- Context ---
    val context = LocalContext.current

    // --- 横スクロール（1本に統一）---
    val horizontalScrollState = rememberScrollState()

    // --- 大分類の展開状態 ---
    var expandedMajor by remember { mutableStateOf<String?>(null) }

    // --- 月次サマリー ---
    val summaries = remember(expenses) {
        buildMonthlySummaries(expenses)
    }

    val months = remember(summaries) {
        summaries.map { it.yearMonth }
    }

    val monthLabels = remember(months) {
        months.map { "${it.year}/${it.monthValue}" }
    }

    // --- 大分類一覧（支出のみ）---
    val majorCategories = remember(expenses) {
        expenses
            .filter { !it.isIncome }
            .map { it.majorCategory }
            .distinct()
            .sorted()
    }

    // ===== 縦スクロールは LazyColumn 1本 =====
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {

        // ===== CSV書き出しボタン =====
        item {
            Button(
                modifier = Modifier.padding(8.dp),
                onClick = {
                    val csv = exportMonthlyAnalysisToCsv(
                        months = months,
                        expenses = expenses
                    )
                    val file = saveCsvToCache(context, csv)
                    shareCsv(context, file)
                }
            ) {
                Text("月次CSVを書き出し")
            }
        }

        // ===== ヘッダー（月）=====
        item {
            Row {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(48.dp)
                )

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState)
                ) {
                    monthLabels.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .width(100.dp)
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item { Divider(thickness = 2.dp) }

        // ===== 収入・支出・残高 =====
        listOf("収入", "支出", "残高").forEach { rowLabel ->
            item {
                Row {

                    Text(
                        text = rowLabel,
                        modifier = Modifier
                            .width(120.dp)
                            .padding(8.dp)
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(horizontalScrollState)
                    ) {
                        months.forEach { ym ->
                            val summary = summaries.firstOrNull {
                                it.yearMonth == ym
                            }

                            val value = when (rowLabel) {
                                "収入" -> summary?.incomeTotal ?: 0
                                "支出" -> summary?.expenseTotal ?: 0
                                "残高" -> summary?.balance ?: 0
                                else -> 0
                            }

                            Text(
                                text = if (value == 0) "–" else "¥%,d".format(value),
                                modifier = Modifier
                                    .width(100.dp)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Divider(thickness = 2.dp) }

        // ===== 大分類・中分類 =====
        items(majorCategories) { major ->

            val isExpanded = expandedMajor == major

            // --- 大分類行 ---
            Row {
                Text(
                    text = if (isExpanded) "▼ $major" else "▶ $major",
                    modifier = Modifier
                        .width(120.dp)
                        .padding(8.dp)
                        .clickable {
                            expandedMajor = if (isExpanded) null else major
                        }
                )

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState)
                ) {
                    months.forEach { ym ->
                        val total = expenses
                            .filter {
                                !it.isIncome &&
                                        it.majorCategory == major &&
                                        YearMonth.from(it.date) == ym
                            }
                            .sumOf { it.priceIncludeTax }

                        Text(
                            text = if (total == 0) "–" else "¥%,d".format(total),
                            modifier = Modifier
                                .width(100.dp)
                                .padding(8.dp)
                        )
                    }
                }
            }

            // --- 中分類 ---
            if (isExpanded) {

                val subCategories = remember(major, expenses) {
                    expenses
                        .filter { !it.isIncome && it.majorCategory == major }
                        .map { it.minorCategory }
                        .distinct()
                        .sorted()
                }

                subCategories.forEach { sub ->
                    Row {
                        Text(
                            text = "  ・$sub",
                            modifier = Modifier
                                .width(120.dp)
                                .padding(8.dp)
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(horizontalScrollState)
                        ) {
                            months.forEach { ym ->
                                val total = expenses
                                    .filter {
                                        !it.isIncome &&
                                                it.majorCategory == major &&
                                                it.minorCategory == sub &&
                                                YearMonth.from(it.date) == ym
                                    }
                                    .sumOf { it.priceIncludeTax }

                                Text(
                                    text = if (total == 0) "–" else "¥%,d".format(total),
                                    modifier = Modifier
                                        .width(100.dp)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


fun buildMonthlySummaries(
    expenses: List<Expense>
): List<MonthlySummary> {

    return expenses
        .groupBy { YearMonth.from(it.date) }
        .map { (ym, list) ->

            val income = list
                .filter { it.isIncome }
                .sumOf { it.priceIncludeTax }

            val expense = list
                .filter { !it.isIncome }
                .sumOf { it.priceIncludeTax }

            val majorTotals = list
                .filter { !it.isIncome }
                .groupBy { it.majorCategory }
                .mapValues { entry ->
                    entry.value.sumOf { it.priceIncludeTax }
                }

            MonthlySummary(
                yearMonth = ym,
                incomeTotal = income,
                expenseTotal = expense,
                balance = income - expense,
                majorCategoryTotals = majorTotals
            )
        }
        .sortedBy { it.yearMonth }
}


@Composable
fun AnalysisTableSkeleton(
    rows: List<String>,
    columns: List<String>,
    horizontalScrollState: ScrollState, // ← 追加
    onRowClick: ((String) -> Unit)? = null,
    valueAt: (row: String, column: String) -> Int
) {
    val labelWidth = 120.dp
    val cellWidth = 100.dp


    Column {



        // ===== 本体（← LazyColumn禁止）=====
        rows.forEach { rowLabel ->

            Row {

                Text(
                    text = rowLabel,
                    modifier = Modifier
                        .width(labelWidth)
                        .padding(8.dp)
                        .clickable(enabled = onRowClick != null) {
                            onRowClick?.invoke(rowLabel)
                        }
                )

                Row(
                    modifier = Modifier.horizontalScroll(horizontalScrollState)
                ) {
                    columns.forEach { column ->
                        val value = valueAt(rowLabel, column)

                        Text(
                            text = if (value == 0) "–" else "¥%,d".format(value),
                            modifier = Modifier
                                .width(cellWidth)
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun AnalysisScreen(expenses: List<Expense>) {

    // --- Context ---
    val context = LocalContext.current

    // ★ 横スクロールはこれ1つだけ
    val horizontalScrollState = rememberScrollState()

    // ---- 状態 ----
    var selectedMajor by remember { mutableStateOf<String?>(null) }

    // ---- 月次サマリー ----
    val summaries = remember(expenses) {
        buildMonthlySummaries(expenses)
    }

    // ---- 分析結果（大分類 × 月）----
    val analysisResult = remember(expenses) {
        buildAnalysisResult(expenses)
    }

    // ---- 列（月）----
    val columns = remember(summaries) {
        summaries.map {
            "${it.yearMonth.year}/${it.yearMonth.monthValue}"
        }
    }

    // ===== UI =====
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {

        // ===== CSV書き出しボタン =====
        item {
            val context = LocalContext.current

            Button(
                onClick = {
                    val csv = exportFullMonthlyAnalysisToCsv(
                        summaries = summaries,
                        expenses = expenses
                    )
                    val file = saveCsvToCache(context, csv)
                    shareCsv(context, file)
                }
            ) {
                Text("分析CSV（サマリー＋分類）")
            }

        }

        // ★ 月ヘッダーを固定
        stickyHeader {
            MonthHeader(
                columns = columns,
                horizontalScrollState = horizontalScrollState
            )
        }

        // ===== 上段：サマリー =====
        item {
            AnalysisTableSkeleton(
                rows = listOf("収入", "支出", "残高"),
                columns = columns,
                horizontalScrollState = horizontalScrollState
            ) { row, column ->

                val summary = summaries.firstOrNull {
                    "${it.yearMonth.year}/${it.yearMonth.monthValue}" == column
                } ?: return@AnalysisTableSkeleton 0

                when (row) {
                    "収入" -> summary.incomeTotal
                    "支出" -> summary.expenseTotal
                    "残高" -> summary.balance
                    else -> 0
                }
            }
        }

        item {
            Divider(
                thickness = 2.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // ===== 下段：分析 =====
        if (selectedMajor == null) {

            item {
                AnalysisTableSkeleton(
                    rows = analysisResult.keys.sorted(),
                    columns = columns,
                    horizontalScrollState = horizontalScrollState,
                    onRowClick = { selectedMajor = it },
                    valueAt = { row, column ->
                        analysisResult[row]?.get(column) ?: 0
                    }
                )
            }

        } else {

            item {
                Text(
                    text = "← 大分類へ戻る",
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { selectedMajor = null }
                )
            }

            item {
                val subResult = buildSubCategoryAnalysis(
                    expenses = expenses,
                    majorCategory = selectedMajor!!
                )

                AnalysisTableSkeleton(
                    rows = subResult.keys.sorted(),
                    columns = columns,
                    horizontalScrollState = horizontalScrollState,
                    valueAt = { row, column ->
                        subResult[row]?.get(column) ?: 0
                    }
                )
            }
        }
    }
}



@Composable
fun AnalysisTableScreen(
    analysisResult: Map<String, Map<String, Int>>
) {
    val horizontalScrollState = rememberScrollState() // ★ 追加
    val rows = analysisResult.keys.toList()
    val columns = analysisResult.values
        .flatMap { it.keys }
        .distinct()

    AnalysisTableSkeleton(
        rows = rows,
        columns = columns,
        horizontalScrollState = horizontalScrollState, // ← これ！！
        valueAt = { row, column ->
            analysisResult[row]?.get(column) ?: 0
        }
    )
}


fun buildAnalysisResult(
    expenses: List<Expense>
): Map<String, Map<String, Int>> {

    return expenses
        .filter { !it.isIncome }
        .groupBy { it.majorCategory }
        .mapValues { (_, list) ->
            list.groupBy { YearMonth.from(it.date) }
                .mapValues { entry ->
                    entry.value.sumOf { it.priceIncludeTax }
                }
                .mapKeys { (ym, _) ->
                    "${ym.year}/${ym.monthValue}"
                }
        }
}


@Composable
fun MonthHeader(
    columns: List<String>,
    horizontalScrollState: ScrollState
) {
    val labelWidth = 120.dp
    val cellWidth = 100.dp

    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(labelWidth)
                .height(48.dp)
        )

        Row(
            modifier = Modifier.horizontalScroll(horizontalScrollState)
        ) {
            columns.forEach { column ->
                Text(
                    text = column,
                    modifier = Modifier
                        .width(cellWidth)
                        .padding(8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    Divider(thickness = 2.dp)
}




@Composable
fun MajorCategoryAnalysisTable(expenses: List<Expense>) {
    // expenses → 月別 × 大分類 集計
    // AnalysisTableSkeleton に流す
}

fun buildMajorCategoryMonthlyTable(
    expenses: List<Expense>
): Pair<List<String>, Map<String, Map<String, Int>>> {

    val monthKeys = expenses
        .map { YearMonth.from(it.date) }
        .distinct()
        .sorted()
        .map { "${it.year}/${it.monthValue}" }

    val table = expenses
        .filter { !it.isIncome }
        .groupBy { it.majorCategory }
        .mapValues { (_, list) ->
            list
                .groupBy { YearMonth.from(it.date) }
                .mapValues { (_, monthList) ->
                    monthList.sumOf { it.priceIncludeTax }
                }
                .mapKeys { (ym, _) ->
                    "${ym.year}/${ym.monthValue}"
                }
        }

    return monthKeys to table
}

fun buildSubCategoryAnalysis(
    expenses: List<Expense>,
    majorCategory: String
): Map<String, Map<String, Int>> {

    return expenses
        .filter { !it.isIncome && it.majorCategory == majorCategory }
        .groupBy { it.minorCategory }
        .mapValues { (_, list) ->
            list.groupBy { YearMonth.from(it.date) }
                .mapValues { (_, items) ->
                    items.sumOf { it.priceIncludeTax }
                }
                .mapKeys { (ym, _) ->
                    "${ym.year}/${ym.monthValue}"
                }
        }
}

fun parseCsvToExpenses(csv: String): List<Expense> {

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
                Expense(
                    date = LocalDate.parse(cols[0].trim(), formatter),
                    store = cols[1].trim(),
                    name = cols[2].trim(),
                    majorCategory = cols[3].trim(),
                    minorCategory = cols[4].trim(),
                    priceExcludeTax = cols[5].trim().toInt(),
                    priceIncludeTax = cols[6].trim().toInt()
                )
            } catch (e: Exception) {
                null
            }
        }
}
@Composable
fun SummaryCard(
    expenses: List<Expense>,
    selectedTab: Int,
    baseMonth: YearMonth,
    baseYear: Int
) {

    val filteredExpenses = when (selectedTab) {
        // 日別・月別 → 表示中の月
        0, 1 -> expenses.filter {
            YearMonth.from(it.date) == baseMonth
        }

        // 年別 → 表示中の年（1月〜12月）
        2 -> expenses.filter {
            it.date.year == baseYear
        }

        else -> expenses
    }

    val income = filteredExpenses
        .filter { it.isIncome }
        .sumOf { it.priceIncludeTax }

    val expense = filteredExpenses
        .filter { !it.isIncome }
        .sumOf { it.priceIncludeTax }

    val title = when (selectedTab) {
        0, 1 -> "${baseMonth.year}年${baseMonth.monthValue}月の累計"
        2 -> "${baseYear}年の累計"
        else -> "累計"
    }

    SummaryCardUI(
        title = title,
        amount = income - expense
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
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onAdd: (Expense) -> Unit
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
                        Expense(
                            date = date,
                            store = "手入力",
                            name = name,
                            majorCategory = major,
                            minorCategory = minor,
                            priceExcludeTax = price,
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

fun calculateBalance(list: List<Expense>): Pair<Int, Int> {
    val inc = list.filter { it.isIncome }.sumOf { it.priceIncludeTax }
    val exp = list.filter { !it.isIncome }.sumOf { it.priceIncludeTax }
    return inc to exp
}
