package com.example.casheye.utils

import android.graphics.Bitmap
import com.example.casheye.Expense
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAnalyzer(apiKey: String) {
    // あなたの専用プロンプトをここにセット
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    private val prompt = """
        添付されたレシート画像を解析し、家計簿入力用のCSVデータを作成してください。

        【CSV出力仕様（厳守）】
        以下の列名を、順番・表記・文字・カンマ含めて完全一致で使用してください。
        購入日,購入店舗,商品名,分類大分類,分類中分類,税抜価格,税込価格

        【重要ルール】
        ・CSVはヘッダー行を必ず含める
        ・列名に空白を含めない
        ・各列は必ずカンマ区切りとする
        ・不要な空白を入れない
        ・1商品＝1行で出力すること（「×2」などは分解して単価を記載）
        ・割引、ポイント、合計金額などは除外すること
        ・軽減税率対象商品は8%、それ以外は10%で税込価格を計算すること
        ・分類大分類（例：食費）、分類中分類（例：精肉、魚介、酒類）として推測して出力すること

        【出力形式】
        CSV形式のみをコードブロックで出力すること。
        説明文は一切不要。
    """.trimIndent()

    suspend fun analyzeReceiptImage(bitmap: Bitmap): List<Expense> = withContext(Dispatchers.IO) {
        try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )

            val csvText = response.text?.replace("```csv", "")?.replace("```", "")?.trim() ?: ""
            // CsvParserを使用してExpenseリストに変換（後ほど作成）
            return@withContext CsvParser.parse(csvText)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}