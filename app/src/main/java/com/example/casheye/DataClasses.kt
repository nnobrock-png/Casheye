package com.example.casheye

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// 1. ここにあった ReceiptItem の残骸（波括弧など）をすべて削除しました。
//    (ReceiptItem は新しく作った ReceiptItem.kt に任せます)

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
    val isIncome: Boolean = false
)

// 3. 月ごとの集計用データ
data class MonthlySummary(
    val monthStr: String, // "2026-01" 形式
    val incomeTotal: Int,
    val ReceiptItemTotal: Int,
    val balance: Int,
    val majorCategoryTotals: Map<String, Int> = emptyMap()
)