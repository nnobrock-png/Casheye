package com.example.casheye.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.casheye.CategoryRepository
import com.example.casheye.ReceiptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

// コンストラクタに modelName を追加（デフォルトは現在使用中の 2.0-flash）
class GeminiAnalyzer(
    private val apiKey: String,
    private val modelName: String = "gemini-2.0-flash"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * 【新機能】このAPIキーで利用可能なモデルの一覧を取得する
     * 設定画面のリスト表示に使用します
     */
    suspend fun fetchAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder().url(url).get().build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val modelsArray = json.getJSONArray("models")
                val list = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val name = m.getString("name").removePrefix("models/")
                    // 生成機能(generateContent)に対応しているモデルのみ抽出
                    val methods = m.getJSONArray("supportedGenerationMethods")
                    var canGenerate = false
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") canGenerate = true
                    }
                    if (canGenerate) list.add(name)
                }
                return@withContext list.sorted()
            }
        } catch (e: Exception) {
            Log.e("GeminiAnalyzer", "モデル一覧の取得に失敗: ${e.message}")
            return@withContext listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro") // 失敗時のフォールバック
        }
    }

    private fun generatePrompt(): String {
        val categoryInstructions = CategoryRepository.getPromptInstructions()

        return """
            添付されたレシート画像を解析し、以下のJSON形式でデータを作成してください。
            複数枚ある場合は重複に注意し、全商品を網羅してください。

            「レシート全体の計算方式を把握してから抽出を開始してください。
            例えば『外税』表記の店舗であれば、各商品の価格を税抜として処理し、
            内税表記であればそのまま税込として処理すること。」

            【重要ルール】
            1. 1商品1行：複数購入（×2など）は必ず単価を抽出し、個数分だけ要素を作成すること。
            2. 価格判定：レシートの税表記（内税/外税）を判定し、正確な税抜・税込価格を算出すること。
            3. 合計欄無視：レシート下部の「合計」「税額」を商品価格と混同しないこと。
            4. 税表記の先行確認: 「まずレシート下部の合計欄周辺を確認し、『外税』『税抜』『内税』『税込』のいずれの方式か特定せよ」
            5. 個別の税率判定: 「商品名横の『＊』『軽』『＃』などの記号を、レシート内の凡例（8%対象など）と照らし合わせよ」
            6. 計算の優先順位: 「『外税』表記がある店舗の場合、商品横の数値は『税抜』と判断し、一律で税込額を計算せよ」

            【出力JSONフォーマット】
            {
              "receipts": [
                {
                  "date": "YYYY-MM-DD",
                  "store": "店舗名",
                  "items": [
                    {
                      "name": "商品名",
                      "major_category": "大分類",
                      "minor_category": "中分類",
                      "price_excl_tax": 税抜価格(数値),
                      "price_incl_tax": 税込価格(数値)
                    }
                  ]
                }
              ]
            }

            【分類マップ（厳守）】
            $categoryInstructions
        """.trimIndent()
    }

    suspend fun analyzeMultipleReceiptImages(bitmaps: List<Bitmap>): List<ReceiptItem> = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) return@withContext emptyList()

        try {
            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", generatePrompt()))

            bitmaps.forEach { bitmap ->
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                partsArray.put(JSONObject().put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Image)
                }))
            }

            val jsonRequest = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", partsArray)
                }))
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.1)
                })
            }

            // URLのモデル名の部分を変数 modelName に差し替え
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(jsonRequest.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e("GeminiREST", "APIエラー ($modelName): $responseBody")
                    return@withContext emptyList()
                }

                try {
                    val jsonResponse = JSONObject(responseBody)
                    val rawJsonText = jsonResponse.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text")

                    val cleanCsv = convertJsonToCsv(rawJsonText)
                    return@withContext CsvParser.parse(cleanCsv)

                } catch (e: Exception) {
                    Log.e("GeminiREST", "パース失敗", e)
                    return@withContext emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiREST", "解析エラー: ${e.message}")
            return@withContext emptyList()
        }
    }

    private fun convertJsonToCsv(jsonString: String): String {
        val sb = StringBuilder("購入日,購入店舗,商品名,分類大分類,分類中分類,税抜価格,税込価格\n")
        try {
            val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
            val root = JSONObject(cleanJson)
            val receipts = root.getJSONArray("receipts")

            for (i in 0 until receipts.length()) {
                val receipt = receipts.getJSONObject(i)
                val date = receipt.getString("date")
                val store = receipt.getString("store").replace(",", " ")
                val items = receipt.getJSONArray("items")

                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                    val name = item.getString("name").replace(",", " ")
                    val major = item.getString("major_category")
                    val minor = item.getString("minor_category")
                    val pNet = item.getInt("price_excl_tax")
                    val pTax = item.getInt("price_incl_tax")

                    sb.append("$date,$store,$name,$major,$minor,$pNet,$pTax\n")
                }
            }
        } catch (e: JSONException) {
            Log.e("GeminiREST", "JSONパース失敗: ${e.message}")
        }
        return sb.toString()
    }

    suspend fun analyzeReceiptImage(bitmap: Bitmap): List<ReceiptItem> {
        return analyzeMultipleReceiptImages(listOf(bitmap))
    }
}