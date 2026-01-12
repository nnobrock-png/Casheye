package com.example.casheye

// あなたの最新プロンプト（CSV安定版）に完全対応した設計図 [cite: 2026-01-05]
data class ReceiptItem(
    val date: String,          // 購入日 (YYYY-MM-DD) [cite: 2026-01-05]
    val store: String,         // 購入店舗 [cite: 2026-01-05]
    val name: String,          // 商品名 [cite: 2026-01-05]
    val majorCategory: String, // 分類大分類 [cite: 2026-01-05]
    val minorCategory: String, // 分類中分類 [cite: 2026-01-05]
    val priceNet: Int,         // 税抜価格 [cite: 2026-01-05]
    val priceIncludeTax: Int   // 税込価格 [cite: 2026-01-05]
) {
    // 判定ロジックを追加：大分類に「収入」や「給与」が含まれていれば true になる
    val isIncome: Boolean
        get() = majorCategory.contains("収入") || majorCategory.contains("給与")
}