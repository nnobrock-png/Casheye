package com.example.casheye.utils

import com.example.casheye.Expense
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CsvParser {
    // 日付の形式（YYYY-MM-DD）を指定
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun parseCsvToExpenses(csvText: String): List<Expense> {
        val lines = csvText.lines()
        if (lines.size <= 1) return emptyList()

        val expenses = mutableListOf<Expense>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val columns = line.split(",")
            if (columns.size >= 7) {
                try {
                    // 日付文字列を LocalDate に変換
                    val dateString = columns[0].trim().replace("/", "-") // スラッシュをハイフンに置換
                    val date = LocalDate.parse(dateString, dateFormatter)

                    expenses.add(
                        Expense(
                            date = date,
                            store = columns[1].trim(),
                            name = columns[2].trim(),
                            majorCategory = columns[3].trim(),   // 名前を合わせました
                            minorCategory = columns[4].trim(),   // 名前を合わせました
                            priceExcludeTax = columns[5].trim().toDouble().toInt(), // 名前を合わせました
                            priceIncludeTax = columns[6].trim().toDouble().toInt()  // 名前を合わせました
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("CsvParser", "Line parse error: $line", e)
                    continue
                }
            }
        }
        return expenses
    }

    fun parse(csvText: String): List<Expense> = parseCsvToExpenses(csvText)
}