package com.example.casheye

import android.content.Context
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// --- Moshi用のカスタムアダプター ---
// これがないと YearMonth を保存する際にアプリがクラッシュします
class YearMonthAdapter {
    @ToJson
    fun toJson(value: YearMonth): String = value.toString() // 例: "2025-12"

    @FromJson
    fun fromJson(value: String): YearMonth = YearMonth.parse(value)
}

// ファイル名とキー
private const val EXPENSE_PREFS_NAME = "casheye_expenses"
private const val RECURRING_PREFS_NAME = "casheye_recurring"
private const val KEY_CSV_DATA = "csv_data"
private const val KEY_RECURRING_TRANSACTIONS = "recurring_transactions"

// Moshiインスタンスの設定（アダプターを登録）
private val moshi: Moshi = Moshi.Builder()
    .add(YearMonthAdapter())
    .add(KotlinJsonAdapterFactory())
    .build()

// --- 定期収支データの保存・読み込み ---
fun saveRecurringTransactions(context: Context, transactions: List<RecurringTransaction>) {
    val type = Types.newParameterizedType(List::class.java, RecurringTransaction::class.java)
    val jsonAdapter = moshi.adapter<List<RecurringTransaction>>(type)
    val json = jsonAdapter.toJson(transactions)
    context.getSharedPreferences(RECURRING_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_RECURRING_TRANSACTIONS, json)
        .apply()
}

fun loadRecurringTransactions(context: Context): List<RecurringTransaction> {
    val prefs = context.getSharedPreferences(RECURRING_PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_RECURRING_TRANSACTIONS, null)
    return if (json != null) {
        val type = Types.newParameterizedType(List::class.java, RecurringTransaction::class.java)
        val jsonAdapter = moshi.adapter<List<RecurringTransaction>>(type)
        try {
            jsonAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }
}

// --- 支出データ（CSV）の保存・読み込み ---
fun saveExpenses(context: Context, expenses: List<Expense>) {
    val header = "購入日,購入店舗,商品名,大分類,中分類,税抜価格,税込価格"
    val data = expenses.joinToString("\n") {
        "${it.date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))},${it.store},${it.name},${it.majorCategory},${it.minorCategory},${it.priceExcludeTax},${it.priceIncludeTax}"
    }
    val csv = "$header\n$data"
    context.getSharedPreferences(EXPENSE_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_CSV_DATA, csv)
        .apply()
}

fun loadExpenses(context: Context): List<Expense> {
    val prefs = context.getSharedPreferences(EXPENSE_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CSV_DATA, null)?.let { parseCsv(it) } ?: emptyList()
}

// CSVパース（変更なし）
fun parseCsv(csv: String): List<Expense> {
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    return csv.lines().dropWhile { it.isBlank() || it.startsWith("購入日") }.mapNotNull { line ->
        val p = line.split(",")
        if (p.size < 7) return@mapNotNull null
        try {
            Expense(
                date = LocalDate.parse(p[0].trim(), formatter),
                store = p[1].trim(),
                name = p[2].trim(),
                majorCategory = p[3].trim(),
                minorCategory = p[4].trim(),
                priceExcludeTax = p[5].trim().toInt(),
                priceIncludeTax = p[6].trim().toInt()
            )
        } catch (e: Exception) {
            null
        }
    }
}

