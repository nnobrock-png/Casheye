package com.example.casheye

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun RecurringTransactionsScreen(
    recurringTransactions: List<RecurringTransaction>,
    onAdd: (RecurringTransaction) -> Unit,
    onUpdate: (RecurringTransaction, RecurringTransaction) -> Unit,
    onDelete: (RecurringTransaction) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<RecurringTransaction?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "定期収支の登録",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            "毎月の固定費や収入を登録すると、指定日に自動で家計簿へ記帳されます。",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recurringTransactions, key = { it.id }) { transaction ->
                RecurringTransactionItem(
                    transaction = transaction,
                    onClick = {
                        editingTransaction = it
                        showDialog = true
                    }
                )
            }
        }

        Button(
            onClick = {
                editingTransaction = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Text("新しい定期項目を追加")
        }
    }

    if (showDialog) {
        RecurringTransactionDialog(
            transaction = editingTransaction,
            onDismiss = { showDialog = false },
            onSave = { newTransaction ->
                if (editingTransaction == null) onAdd(newTransaction)
                else onUpdate(editingTransaction!!, newTransaction)
            },
            onDelete = { onDelete(it) }
        )
    }
}

@Composable
private fun RecurringTransactionItem(transaction: RecurringTransaction, onClick: (RecurringTransaction) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(transaction) },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(transaction.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("%,d 円".format(transaction.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("毎月 ${transaction.dayOfMonth} 日記帳", style = MaterialTheme.typography.bodyMedium)
            Text("分類: ${transaction.majorCategory} > ${transaction.minorCategory}", style = MaterialTheme.typography.bodySmall)

            val formatter = DateTimeFormatter.ofPattern("yyyy年M月")
            val period = if (transaction.endYearMonth != null) {
                "${transaction.startYearMonth.format(formatter)} ~ ${transaction.endYearMonth.format(formatter)}"
            } else {
                "${transaction.startYearMonth.format(formatter)} ~ 無期限"
            }
            Text("期間: $period", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringTransactionDialog(
    transaction: RecurringTransaction?,
    onDismiss: () -> Unit,
    onSave: (RecurringTransaction) -> Unit,
    onDelete: (RecurringTransaction) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(transaction?.title ?: "") }
    var amount by remember { mutableStateOf(transaction?.amount?.toString() ?: "") }
    var dayOfMonth by remember { mutableStateOf(transaction?.dayOfMonth?.toString() ?: "1") }

    // カテゴリ選択の状態
    val categoryMap = CategoryRepository.getCategoryMap()
    var majorCategory by remember { mutableStateOf(transaction?.majorCategory ?: "") }
    var minorCategory by remember { mutableStateOf(transaction?.minorCategory ?: "") }
    var majorExpanded by remember { mutableStateOf(false) }
    var minorExpanded by remember { mutableStateOf(false) }

    val now = YearMonth.now()
    var startYear by remember { mutableStateOf(transaction?.startYearMonth?.year?.toString() ?: now.year.toString()) }
    var startMonth by remember { mutableStateOf(transaction?.startYearMonth?.monthValue?.toString() ?: now.monthValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction == null) "定期項目の追加" else "設定の編集") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("項目名") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                    // 大分類ドロップダウン
                    ExposedDropdownMenuBox(
                        expanded = majorExpanded,
                        onExpandedChange = { majorExpanded = !majorExpanded },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = majorCategory,
                            onValueChange = { majorCategory = it },
                            label = { Text("大分類") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = majorExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = majorExpanded, onDismissRequest = { majorExpanded = false }) {
                            categoryMap.keys.forEach { major ->
                                DropdownMenuItem(
                                    text = { Text(major) },
                                    onClick = { majorCategory = major; majorExpanded = false; minorCategory = "" }
                                )
                            }
                        }
                    }

                    // 中分類ドロップダウン
                    ExposedDropdownMenuBox(
                        expanded = minorExpanded,
                        onExpandedChange = { minorExpanded = !minorExpanded },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = minorCategory,
                            onValueChange = { minorCategory = it },
                            label = { Text("中分類") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minorExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = minorExpanded, onDismissRequest = { minorExpanded = false }) {
                            val minors = categoryMap[majorCategory] ?: emptyList()
                            minors.forEach { minor ->
                                DropdownMenuItem(
                                    text = { Text(minor) },
                                    onClick = { minorCategory = minor; minorExpanded = false }
                                )
                            }
                        }
                    }

                    OutlinedTextField(value = dayOfMonth, onValueChange = { dayOfMonth = it }, label = { Text("記帳日 (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

                    Text("自動記載の開始月", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = startYear, onValueChange = { startYear = it }, label = { Text("年") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(value = startMonth, onValueChange = { startMonth = it }, label = { Text("月") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val y = startYear.toIntOrNull() ?: now.year
                val m = startMonth.toIntOrNull()?.coerceIn(1, 12) ?: now.monthValue
                if (title.isNotBlank()) {
                    CategoryRepository.addCategory(context, majorCategory, minorCategory)
                    onSave(RecurringTransaction(
                        id = transaction?.id ?: UUID.randomUUID().toString(),
                        title = title, amount = amount.toIntOrNull() ?: 0,
                        majorCategory = majorCategory, minorCategory = minorCategory,
                        dayOfMonth = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                        startYearMonth = YearMonth.of(y, m)
                    ))
                    onDismiss()
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Box(Modifier.fillMaxWidth()) {
                if (transaction != null) {
                    TextButton(onClick = { onDelete(transaction); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.align(Alignment.CenterStart)) { Text("削除") }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) { Text("キャンセル") }
            }
        }
    )
}