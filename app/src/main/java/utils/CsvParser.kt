package com.example.casheye.utils

import com.example.casheye.ReceiptItem // ReceiptItem ではなく ReceiptItem を使用

object CsvParser {
    fun parse(csvText: String): List<ReceiptItem> {
        val lines = csvText.lines()
        // ヘッダー行を含めて2行以上ない場合は空リストを返す
        if (lines.size <= 1) return emptyList()

        val items = mutableListOf<ReceiptItem>()

        // ヘッダーを飛ばして2行目から解析
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val columns = line.split(",")
            // 最新プロンプトの列数（7列）に一致するか確認 [cite: 2026-01-05]
            if (columns.size >= 7) {
                try {
                    items.add(
                        ReceiptItem(
                            // 購入日: YYYY-MM-DD 形式をそのまま文字列として保持 [cite: 2026-01-05]
                            date = columns[0].trim(),
                            // 購入店舗
                            store = columns[1].trim(),
                            // 商品名
                            name = columns[2].trim(),
                            // 分類大分類 [cite: 2026-01-05]
                            majorCategory = columns[3].trim(),
                            // 分類中分類 [cite: 2026-01-05]
                            minorCategory = columns[4].trim(),
                            // 税抜価格 (数値に変換)
                            priceNet = columns[5].trim().toDouble().toInt(),
                            // 税込価格 (8%/10%計算済みの値) [cite: 2026-01-05]
                            priceIncludeTax = columns[6].trim().toDouble().toInt()
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CsvParser", "Line parse error: $line", e)
                    continue
                }
            }
        }
        return items
    }
}