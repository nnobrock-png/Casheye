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
private const val ReceiptItem_PREFS_NAME = "casheye_ReceiptItems"
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
fun saveReceiptItems(context: Context, ReceiptItems: List<ReceiptItem>) {
    val header = "購入日,購入店舗,商品名,大分類,中分類,税抜価格,税込価格"
    val data = ReceiptItems.joinToString("\n") {
        // 修正後
        "${it.date},${it.store},${it.name},${it.majorCategory},${it.minorCategory},${it.priceNet},${it.priceIncludeTax}"
    }
    val csv = "$header\n$data"
    context.getSharedPreferences(ReceiptItem_PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_CSV_DATA, csv)
        .apply()
}

fun loadReceiptItems(context: Context): List<ReceiptItem> {
    val prefs = context.getSharedPreferences(ReceiptItem_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CSV_DATA, null)?.let { parseCsv(it) } ?: emptyList()
}

// CSVパース（変更なし）
fun parseCsv(csv: String): List<ReceiptItem> {
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    return csv.lines().dropWhile { it.isBlank() || it.startsWith("購入日") }.mapNotNull { line ->
        val p = line.split(",")
        if (p.size < 7) return@mapNotNull null
        try {
            ReceiptItem(
                // 修正後：[cite: 2026-01-05] 仕様の String型 に合わせて、そのまま渡します
                date = p[0].trim(),
                store = p[1].trim(),
                name = p[2].trim(),
                majorCategory = p[3].trim(),
                minorCategory = p[4].trim(),
                priceNet = p[5].trim().toInt(),
                priceIncludeTax = p[6].trim().toInt()
            )
        } catch (e: Exception) {
            null
        }
    }
}

