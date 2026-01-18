package com.example.casheye.utils

import com.example.casheye.ReceiptItem
import android.util.Log

object CsvParser {
    fun parse(csvText: String): List<ReceiptItem> {
        Log.d("CsvParser", "解析を開始するテキスト:\n$csvText")

        val lines = csvText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") && !it.startsWith("購入日") }

        // CsvParser.kt の mapNotNull 内を少し強化
        return lines.mapNotNull { line ->
            val cols = line.split(",").map { it.trim() }

            if (cols.size >= 7) {
                try {
                    ReceiptItem(
                        date = cols[0],
                        store = cols[1],
                        name = cols[2],
                        majorCategory = cols[3],
                        minorCategory = cols[4],
                        priceNet = cols[5].toDoubleOrNull()?.toInt() ?: 0,
                        priceIncludeTax = cols[6].toDoubleOrNull()?.toInt() ?: 0
                    )
                } catch (e: Exception) {
                    Log.e("CsvParser", "変換失敗: $line", e)
                    null
                }
            } else {
                // ここが Logcat に出ている場合は GeminiAnalyzer 側の sb.append が足りない
                Log.w("CsvParser", "列不足(${cols.size}個): $line")
                null
            }
        }
    }
}