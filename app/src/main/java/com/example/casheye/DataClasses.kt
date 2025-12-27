package com.example.casheye

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// 1. 実際の支出・収入データ
data class Expense(
    val date: LocalDate,
    val store: String,
    val name: String,
    val majorCategory: String,
    val minorCategory: String,
    val priceExcludeTax: Int,
    val priceIncludeTax: Int
) {
    // 判定ロジック：大分類が「収入」や「給与」を含んでいればプラス
    val isIncome: Boolean
        get() = majorCategory.contains("収入") || majorCategory.contains("給与")
}

// 2. 定期実行の設定データ
data class RecurringTransaction(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Int,
    val majorCategory: String,
    val minorCategory: String,
    val dayOfMonth: Int,
    val startYearMonth: YearMonth,
    val endYearMonth: YearMonth? = null,
    val isIncome: Boolean = false  // ここにカンマやカッコのミスがあった可能性が高いです
)