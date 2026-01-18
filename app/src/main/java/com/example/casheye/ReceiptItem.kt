package com.example.casheye
import java.io.Serializable

data class ReceiptItem(
    val date: String,
    val store: String,
    val name: String,
    val majorCategory: String,
    val minorCategory: String,
    val priceNet: Int,
    val priceIncludeTax: Int
) : Serializable { // ← ここが Serializable になっているか再確認！
    val isIncome: Boolean
        get() = majorCategory.contains("収入") || majorCategory.contains("給与")
}