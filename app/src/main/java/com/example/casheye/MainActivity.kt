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
        recordRecurringTransactionsIfNeeded(context, expenses, recurringTransactions)
            ?.let { expenses = it }
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
                        IconButton(onClick = { exportExpensesToCSV(context, expenses) }) {
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

@Composable
fun MonthlyTableScreen(expenses: List<Expense>) {

    // ---- 大分類の展開状態 ----
    var expandedMajor by remember { mutableStateOf<String?>(null) }

    // ---- 月次サマリー ----
    val summaries = remember(expenses) {
        buildMonthlySummaries(expenses)
    }

    val months = remember(summaries) {
        summaries.map { it.yearMonth }
    }

    // 大分類一覧（支出のみ）
    val majorCategories = remember(expenses) {
        expenses
            .filter { !it.isIncome }
            .map { it.majorCategory }
            .distinct()
            .sorted()
    }

    Column(Modifier.fillMaxSize()) {

        // ===== 月ヘッダ =====
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 100.dp)
        ) {
            months.forEach { month ->
                Text(
                    text = "${month.year}/${month.monthValue}",
                    modifier = Modifier.width(90.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider()

        // ===== 収入・支出・残高 =====
        val summaryRows = listOf(
            "収入" to { s: MonthlySummary -> s.incomeTotal },
            "支出" to { s: MonthlySummary -> s.expenseTotal },
            "残高" to { s: MonthlySummary -> s.balance }
        )

        summaryRows.forEach { (label, valueFunc) ->
            Row {
                Text(label, Modifier.width(100.dp).padding(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    summaries.forEach { s ->
                        Text(
                            text = "¥%,d".format(valueFunc(s)),
                            modifier = Modifier.width(90.dp).padding(4.dp),
                            fontWeight = FontWeight.Medium,
                            color = when (label) {
                                "収入" -> MaterialTheme.colorScheme.primary
                                "支出" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 2.dp)

        // ===== 大分類 =====
        majorCategories.forEach { major ->

            // --- 大分類行 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedMajor =
                            if (expandedMajor == major) null else major
                    }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = major,
                    modifier = Modifier.width(100.dp).padding(4.dp),
                    fontWeight = FontWeight.Bold
                )
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    summaries.forEach { s ->
                        val value = s.majorCategoryTotals[major] ?: 0
                        Text(
                            text = if (value == 0) "–" else "¥%,d".format(value),
                            modifier = Modifier.width(90.dp).padding(4.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ===== 中分類（展開時）=====
            if (expandedMajor == major) {

                // 中分類一覧
                val minorCategories = expenses
                    .filter { !it.isIncome && it.majorCategory == major }
                    .map { it.minorCategory }
                    .distinct()
                    .sorted()

                minorCategories.forEach { minor ->

                    Row {
                        Text(
                            text = "・$minor",
                            modifier = Modifier
                                .width(100.dp)
                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            fontSize = 12.sp
                        )

                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            months.forEach { month ->
                                val value = expenses
                                    .filter {
                                        !it.isIncome &&
                                                it.majorCategory == major &&
                                                it.minorCategory == minor &&
                                                YearMonth.from(it.date) == month
                                    }
                                    .sumOf { it.priceIncludeTax }

                                Text(
                                    text = if (value == 0) "–" else "¥%,d".format(value),
                                    modifier = Modifier.width(90.dp).padding(2.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
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
    onRowClick: ((String) -> Unit)? = null,
    valueAt: (row: String, column: String) -> Int
) {
    val labelWidth = 120.dp
    val cellWidth = 100.dp
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        // ===== ヘッダー =====
        Row {
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

        // ===== 本体 =====
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(rows) { rowLabel ->

                Row {

                    // ← 行ラベル（大分類など）
                    Text(
                        text = rowLabel,
                        modifier = Modifier
                            .width(labelWidth)
                            .padding(8.dp)
                            .clickable(enabled = onRowClick != null) {
                                onRowClick?.invoke(rowLabel)
                            }
                    )

                    // ← 月別セル
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
}

@Composable
fun AnalysisScreen(expenses: List<Expense>) {

    var selectedMajor by remember { mutableStateOf<String?>(null) }

    val summaries = remember(expenses) {
        buildMonthlySummaries(expenses)
    }

    val analysisResult = remember(expenses) {
        buildAnalysisResult(expenses)
    }

    val columns = summaries.map {
        "${it.yearMonth.year}/${it.yearMonth.monthValue}"
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ===== 上段：収入・支出・残高 =====
        Box(modifier = Modifier.weight(1f)) {
            AnalysisTableSkeleton(
                rows = listOf("収入", "支出", "残高"),
                columns = columns
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

        Divider(thickness = 3.dp)

        // ===== 下段：大分類 or 中分類 =====
        Box(modifier = Modifier.weight(2f)) {

            if (selectedMajor == null) {
                // ---- 大分類一覧 ----
                AnalysisTableSkeleton(
                    rows = analysisResult.keys.sorted(),
                    columns = columns,
                    onRowClick = { major: String ->
                        selectedMajor = major
                    },
                    valueAt = { row: String, column: String ->
                        analysisResult[row]?.get(column) ?: 0
                    }
                )



            } else {
                // ---- 中分類ドリルダウン ----
                val subResult = remember(selectedMajor) {
                    buildSubCategoryAnalysis(expenses, selectedMajor!!)
                }

                Column {

                    // ← 戻る
                    Text(
                        "← 大分類へ戻る",
                        modifier = Modifier
                            .padding(8.dp)
                            .clickable { selectedMajor = null }
                    )

                    AnalysisTableSkeleton(
                        rows = subResult.keys.sorted(),
                        columns = columns,
                        onRowClick = null,
                        valueAt = { row: String, column: String ->
                            subResult[row]?.get(column) ?: 0
                        }
                    )

                }
            }
        }
    }
}



@Composable
fun AnalysisTableScreen(
    analysisResult: Map<String, Map<String, Int>>
) {
    val rows = analysisResult.keys.toList()
    val columns = analysisResult.values
        .flatMap { it.keys }
        .distinct()

    AnalysisTableSkeleton(
        rows = rows,
        columns = columns,
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


@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("支出を追加") },
        text = { Text("（ここに入力フォームが入ります）") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
