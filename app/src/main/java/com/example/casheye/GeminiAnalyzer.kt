package com.example.casheye.utils

import android.graphics.Bitmap
import com.example.casheye.ReceiptItem // ← ここが赤文字なら Alt+Enter でパスを再確認してください
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiAnalyzer(apiKey: String) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    // 保存された最新プロンプト（CSV安定版）を反映 [cite: 2026-01-05]
    private val prompt = """
        添付されたレシート画像を解析し、家計簿入力用のCSVデータを作成してください。

        【CSV出力仕様（厳守）】
        購入日,購入店舗,商品名,分類大分類,分類中分類,税抜価格,税込価格

        【重要ルール】
        ・CSVはヘッダー行を必ず含める
        ・列名に空白を含めない
        ・列名に括弧を使わない
        ・各列は必ずカンマ区切りとする
        ・不要な空白を入れない
        ・同じ商品を複数購入し「×2」「2点」などと記載されていても、必ず1商品＝1行で出力すること [cite: 2026-01-05]
        ・金額は必ず単価を使用すること [cite: 2026-01-05]
        ・割引、ポイント、商品券、釣銭、合計金額、税額のみの行は出力しない [cite: 2026-01-05]
        ・軽減税率対象商品は8%、それ以外は10%で税込価格を計算すること [cite: 2026-01-05]
        ・日付の表記は「YYYY-MM-DD」の形式にすること（例：2026-01-12） [cite: 2026-01-05]

        【分類】
        一般的な家計簿分類を推測し、分類大分類（例：食費）、分類中分類（例：精肉、魚介、酒類）として出力すること [cite: 2026-01-05]

        【出力形式】
        CSV形式のみを出力すること。説明文は一切不要。
    """.trimIndent()

    suspend fun analyzeReceiptImage(bitmap: Bitmap): List<ReceiptItem> = withContext(Dispatchers.IO) {
        try {
            val response = generativeModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )

            // CSV抽出ロジックを強化：```などの囲み記号をより確実に除去
            val rawText = response.text ?: ""
            val csvText = rawText.lines()
                .filter { it.isNotBlank() && !it.startsWith("```") }
                .joinToString("\n")
                .trim()

            if (csvText.isEmpty()) return@withContext emptyList()

            // 修正された CsvParser を呼び出す
            return@withContext CsvParser.parse(csvText)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }
}