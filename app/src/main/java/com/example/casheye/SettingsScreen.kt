package com.example.casheye

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun SettingsScreen(
    recurringTransactions: List<RecurringTransaction>,
    onAdd: (RecurringTransaction) -> Unit,
    onUpdate: (RecurringTransaction) -> Unit,
    onDelete: (RecurringTransaction) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<RecurringTransaction?>(null) }

    Column(Modifier.padding(8.dp)) {
        Text("定期収支の設定", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(recurringTransactions) { transaction ->
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text("新しい定期項目を追加")
        }
    }

    if (showDialog) {
        RecurringTransactionDialog(
            transaction = editingTransaction,
            onDismiss = { showDialog = false },
            onSave = { transaction ->
                if (editingTransaction == null) onAdd(transaction) else onUpdate(transaction)
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
            Text("開始月: $period", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
    var title by remember { mutableStateOf(transaction?.title ?: "") }
    var amount by remember { mutableStateOf(transaction?.amount?.toString() ?: "") }
    var majorCategory by remember { mutableStateOf(transaction?.majorCategory ?: "") }
    var minorCategory by remember { mutableStateOf(transaction?.minorCategory ?: "") }
    var dayOfMonth by remember { mutableStateOf(transaction?.dayOfMonth?.toString() ?: "1") }

    val now = YearMonth.now()
    var startYear by remember { mutableStateOf(transaction?.startYearMonth?.year?.toString() ?: now.year.toString()) }
    var startMonth by remember { mutableStateOf(transaction?.startYearMonth?.monthValue?.toString() ?: now.monthValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction == null) "定期項目の追加" else "設定の編集") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("項目名 (例: 家賃、給与)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("金額") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = majorCategory,
                        onValueChange = { majorCategory = it },
                        label = { Text("大分類") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = minorCategory,
                        onValueChange = { minorCategory = it },
                        label = { Text("中分類") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dayOfMonth,
                        onValueChange = { dayOfMonth = it },
                        label = { Text("記帳日 (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("自動記載の開始月", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startYear,
                            onValueChange = { startYear = it },
                            label = { Text("年") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = startMonth,
                            onValueChange = { startMonth = it },
                            label = { Text("月") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val y = startYear.toIntOrNull() ?: now.year
                    val m = startMonth.toIntOrNull()?.coerceIn(1, 12) ?: now.monthValue
                    val amt = amount.toIntOrNull() ?: 0
                    val dom = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: 1

                    if (title.isNotBlank()) {
                        onSave(RecurringTransaction(
                            id = transaction?.id ?: UUID.randomUUID().toString(),
                            title = title,
                            amount = amt,
                            majorCategory = majorCategory,
                            minorCategory = minorCategory,
                            dayOfMonth = dom,
                            startYearMonth = YearMonth.of(y, m),
                            endYearMonth = transaction?.endYearMonth
                        ))
                        onDismiss()
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (transaction != null) {
                    TextButton(
                        onClick = { onDelete(transaction); onDismiss() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("削除") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    )
}

