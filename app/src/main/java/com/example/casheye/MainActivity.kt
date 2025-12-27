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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CasheyeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CashEyeApp(modifier = Modifier.padding(innerPadding))
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
                    .height(120.dp),
                maxLines = 6
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

    // 設定値の読み込み
    val prefs = context.getSharedPreferences("casheye_prefs", Context.MODE_PRIVATE)
    var startYear by remember { mutableIntStateOf(prefs.getInt("start_year", 2025)) }
    var startMonth by remember { mutableIntStateOf(prefs.getInt("start_month", 1)) }

    val runAutoRecord = {
        val updated = recordRecurringTransactionsIfNeeded(context, expenses, recurringTransactions)
        if (updated != null) expenses = updated
    }

    LaunchedEffect(Unit) {
        expenses = loadExpenses(context)
        recurringTransactions = loadRecurringTransactions(context)
        runAutoRecord()
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

    Column(modifier = modifier.padding(8.dp)) {

        CsvImportSection { csv ->
            val imported = parseCsv(csv)
            if (imported.isNotEmpty()) {
                expenses = (expenses + imported)
                    .distinctBy { "${it.date}-${it.name}-${it.priceIncludeTax}" }
                    .sortedByDescending { it.date }

                saveExpenses(context, expenses)
            }
        }

        val (totalInc, totalExp) = calculateBalance(expenses)

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("現在の総残高", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("¥%,d".format(totalInc - totalExp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { exportExpensesToCSV(context, expenses) }) {
                        Icon(Icons.Default.Share, contentDescription = "共有")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("収入合計", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Text("¥%,d".format(totalInc), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("支出合計", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                        Text("¥%,d".format(totalExp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        val tabs = listOf("日別", "月別", "年別", "明細", "グラフ", "設定") // グラフを割り込ませました
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                    Text(title, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }
        }

        when (selectedTab) {
            0 -> HierarchicalExpenseList(expenses, "date", onDelete, onEdit = { editingExpense = it })
            1 -> HierarchicalExpenseList(expenses, "month", onDelete, onEdit = { editingExpense = it })
            2 -> HierarchicalExpenseList(expenses, "year", onDelete, onEdit = { editingExpense = it })
            3 -> FullHistoryDatabaseScreen(expenses, onDelete, onEdit = { editingExpense = it })
            4 -> ChartScreen(expenses)
            5 -> SettingsScreen(
                recurringTransactions = recurringTransactions,
                startYear = startYear,
                startMonth = startMonth,
                onStartSettingsChange = { y, m ->
                    startYear = y; startMonth = m
                    prefs.edit().putInt("start_year", y).putInt("start_month", m).apply()
                },
                onAdd = { new ->
                    recurringTransactions = recurringTransactions + new
                    saveRecurringTransactions(context, recurringTransactions)
                    runAutoRecord()
                },
                onDelete = { target ->
                    recurringTransactions = recurringTransactions.filter { it.id != target.id }
                    saveRecurringTransactions(context, recurringTransactions)
                },
                onUpdateRecurring = { updated ->
                    recurringTransactions = recurringTransactions.map { if (it.id == updated.id) updated else it }
                    saveRecurringTransactions(context, recurringTransactions)
                }
            )
        }
    }

    editingExpense?.let { target ->
        EditExpenseDialog(expense = target, onDismiss = { editingExpense = null }, onSave = { updated -> onUpdate(target, updated) })
    }
}

@Composable
fun HierarchicalExpenseList(expenses: List<Expense>, type: String, onDelete: (Expense) -> Unit, onEdit: (Expense) -> Unit) {
    var expandedHeaders by remember { mutableStateOf(setOf<String>()) }
    var expandedMajors by remember { mutableStateOf(setOf<String>()) }
    val grouped = when (type) {
        "date" -> expenses.groupBy { it.date.toString() }
        "month" -> expenses.groupBy { YearMonth.from(it.date).toString() }
        else -> expenses.groupBy { "${it.date.year}年度" }
    }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (header, listForHeader) ->
            item {
                val (inc, exp) = calculateBalance(listForHeader)
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        expandedHeaders = if (expandedHeaders.contains(header)) expandedHeaders - header else expandedHeaders + header
                    },
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(header, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("入: ¥%,d".format(inc), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("出: ¥%,d".format(exp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (expandedHeaders.contains(header)) {
                listForHeader.groupBy { it.majorCategory }.forEach { (major, mList) ->
                    val majorKey = "$header-$major"
                    item {
                        Surface(modifier = Modifier.fillMaxWidth().clickable {
                            expandedMajors = if (expandedMajors.contains(majorKey)) expandedMajors - majorKey else expandedMajors + majorKey
                        }) {
                            Row(Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp, end = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (expandedMajors.contains(majorKey)) "▼ $major" else "▶ $major", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("%,d 円".format(mList.sumOf { it.priceIncludeTax }), fontSize = 14.sp)
                            }
                        }
                    }
                    if (expandedMajors.contains(majorKey)) {
                        items(mList) { expense ->
                            ExpenseItemRow(expense, onDelete, onEdit, paddingStart = 44.dp)
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

@Composable
fun EditExpenseDialog(expense: Expense, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var name by remember { mutableStateOf(expense.name) }
    var store by remember { mutableStateOf(expense.store) }
    var priceStr by remember { mutableStateOf(expense.priceIncludeTax.toString()) }
    var major by remember { mutableStateOf(expense.majorCategory) }
    var minor by remember { mutableStateOf(expense.minorCategory) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("明細を修正") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("商品名") })
                OutlinedTextField(value = store, onValueChange = { store = it }, label = { Text("店舗名") })
                OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("税込価格") })
                OutlinedTextField(value = major, onValueChange = { major = it }, label = { Text("大分類") })
                OutlinedTextField(value = minor, onValueChange = { minor = it }, label = { Text("中分類") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val newPrice = priceStr.toIntOrNull() ?: expense.priceIncludeTax
                onSave(expense.copy(name = name, store = store, priceIncludeTax = newPrice, majorCategory = major, minorCategory = minor))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

fun calculateBalance(list: List<Expense>): Pair<Int, Int> {
    val inc = list.filter { it.isIncome }.sumOf { it.priceIncludeTax }
    val exp = list.filter { !it.isIncome }.sumOf { it.priceIncludeTax }
    return Pair(inc, exp)
}

fun recordRecurringTransactionsIfNeeded(context: Context, currentExpenses: List<Expense>, recurringTransactions: List<RecurringTransaction>): List<Expense>? {
    if (recurringTransactions.isEmpty()) return null
    val newGenerated = mutableListOf<Expense>()
    val today = LocalDate.now()
    val currentMonth = YearMonth.from(today)
    val existingKeys = currentExpenses.map { "${it.date}-${it.name}-${it.priceIncludeTax}" }.toSet()
    recurringTransactions.forEach { transaction ->
        var month = transaction.startYearMonth
        while (!month.isAfter(currentMonth)) {
            val day = transaction.dayOfMonth.coerceIn(1, month.lengthOfMonth())
            val targetDate = month.atDay(day)
            if (targetDate.isAfter(today)) break
            val key = "${targetDate}-${transaction.title}-${transaction.amount}"
            if (!existingKeys.contains(key)) {
                newGenerated.add(Expense(targetDate, "定期", transaction.title, if (transaction.isIncome) "収入" else transaction.majorCategory, transaction.minorCategory, transaction.amount, transaction.amount))
            }
            month = month.plusMonths(1)
        }
    }
    return if (newGenerated.isNotEmpty()) {
        val updated = (currentExpenses + newGenerated).sortedByDescending { it.date }
        saveExpenses(context, updated)
        updated
    } else null
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
            OutlinedTextField(value = major, onValueChange = { major = it }, label = { Text("大分類") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = minor, onValueChange = { minor = it }, label = { Text("中分類") }, modifier = Modifier.fillMaxWidth())
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

                    OutlinedTextField(value = editMajor, onValueChange = { editMajor = it }, label = { Text("大分類") })
                    OutlinedTextField(value = editMinor, onValueChange = { editMinor = it }, label = { Text("中分類") })

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