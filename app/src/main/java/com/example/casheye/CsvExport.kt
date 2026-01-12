package com.example.casheye

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.UUID

// 1. 引数名を小文字の 'item' に修正
fun ReceiptItemToCsvLine(item: ReceiptItem): String {
    val type = "expense"

    return listOf(
        UUID.randomUUID().toString(),
        item.date,                        // 購入日 (YYYY-MM-DD) [cite: 2026-01-05]
        item.date.substring(0, 4),        // 年
        item.date.substring(5, 7),        // 月
        type,
        item.store,
        item.name,
        item.majorCategory,
        item.minorCategory,
        item.priceIncludeTax.toString(),  // 税込価格 [cite: 2026-01-05]
        "manual"
    ).joinToString(",") { it.escapeCsv() }
}

fun String.escapeCsv(): String {
    return if (contains(",") || contains("\"") || contains("\n")) {
        "\"${replace("\"", "\"\"")}\""
    } else this
}

// 2. 引数名を小文字の 'items' に修正
fun exportReceiptItemsToCsv(items: List<ReceiptItem>): String {
    val header = listOf(
        "id","date","year","month","type",
        "store","name","major","minor","amount","source"
    ).joinToString(",")

    val body = items.joinToString("\n") { ReceiptItemToCsvLine(it) }
    return "$header\n$body"
}

fun saveCsvToCache(context: Context, csv: String): File {
    // 日付表記を YYYY-MM-DD に統一 [cite: 2026-01-05]
    val fileName = "casheye_${LocalDate.now()}.csv"
    val file = File(context.cacheDir, fileName)
    file.writeText(csv, Charsets.UTF_8)
    return file
}

fun shareCsv(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "CSVを共有"))
}

// --- 以降の分析系関数について ---
// String型の日付(YYYY-MM-DD)から「年月」を判定する補助関数
fun isSameMonth(dateStr: String, year: Int, month: Int): Boolean {
    return dateStr.startsWith("%04d-%02d".format(year, month))
}

fun exportFullMonthlyAnalysisToCsv(
    items: List<ReceiptItem>,
    summaries: List<MonthlySummary>
): String {
    // 1. monthStr ("YYYY-MM") を基準に月リストを作成してソート [cite: 2026-01-05]
    val months = summaries.map { it.monthStr }.distinct().sorted()
    val headerSuffix = months.joinToString(",")

    val sb = StringBuilder()

    // サマリー行の作成
    sb.append("サマリー,").append(headerSuffix).append("\n")
    listOf("収入", "支出", "残高").forEach { label ->
        sb.append(label)
        months.forEach { mStr ->
            val summary = summaries.firstOrNull { it.monthStr == mStr }
            val value = when (label) {
                "収入" -> summary?.incomeTotal ?: 0
                "支出" -> summary?.ReceiptItemTotal ?: 0
                "残高" -> summary?.balance ?: 0
                else -> 0
            }
            sb.append(",").append(value)
        }
        sb.append("\n")
    }

    sb.append("\n")

    // 大分類行の作成
    sb.append("大分類,").append(headerSuffix).append("\n")
    val majorCategories = items
        .map { it.majorCategory }
        .distinct()
        .sorted()

    majorCategories.forEach { major ->
        sb.append(major)
        months.forEach { mStr ->
            // String型の日付 (YYYY-MM-DD) の先頭7文字が一致するか判定 [cite: 2026-01-05]
            val total = items
                .filter { it.majorCategory == major && it.date.startsWith(mStr) }
                .sumOf { it.priceIncludeTax }

            sb.append(",").append(total)
        }
        sb.append("\n")
    }

    return sb.toString()
}
/**
 * 完全復元（インポート）用CSVを生成する
 * [cite: 2026-01-05] の processCsvResults 関数でそのまま読み込める形式
 */
fun exportForImportBackup(items: List<ReceiptItem>): String {
    val sb = StringBuilder()

    // 1. ヘッダー行 (processCsvResults が期待する順番)
    sb.append("購入日,購入店舗,商品名,分類大分類,分類中分類,税抜価格,税込価格\n")

    // 2. データ行
    items.forEach { item ->
        val line = listOf(
            item.date,            // 購入日
            item.store,           // 購入店舗
            item.name,            // 商品名
            item.majorCategory,   // 分類大分類
            item.minorCategory,   // 分類中分類
            item.priceNet.toString(),        // 税抜価格
            item.priceIncludeTax.toString()  // 税込価格
        ).joinToString(",") { it.escapeCsv() } // カンマを含む店舗名などに対応

        sb.append(line).append("\n")
    }

    return sb.toString()
}