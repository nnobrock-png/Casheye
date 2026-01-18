package com.example.casheye

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object CategoryRepository {
    private const val PREFS_NAME = "category_prefs"
    private const val KEY_MAP = "category_map"

    private val defaultMap = mapOf(
        "食費" to listOf("精肉", "魚介", "野菜", "果物", "パン", "惣菜", "菓子", "飲料", "酒類", "調味料", "インスタント食品", "乳製品", "冷凍食品", "加工食品", "その他食品"),
        "日用品" to listOf("洗剤", "紙類", "消耗品", "文房具", "キッチン用品", "バス・トイレ用品", "衛生用品", "その他"),
        "車両費" to listOf("ガソリン", "駐車場代", "メンテナンス", "その他"),
        "交通" to listOf("電車", "バス", "タクシー", "その他"),
        "外食費" to listOf("昼食", "夕食", "カフェ", "テイクアウト", "その他"),
        "医療費" to listOf("診療代", "薬代", "検査代", "その他"),
        "保険代" to listOf("生命保険", "損害保険", "自動車保険", "その他"),
        "収入" to listOf("給与", "副収入", "還付金", "その他")
    )

    private var currentMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_MAP, null)

        if (json != null) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = Types.newParameterizedType(Map::class.java, String::class.java, List::class.java)
                val adapter = moshi.adapter<Map<String, List<String>>>(type)
                val savedMap = adapter.fromJson(json) ?: emptyMap()

                currentMap = defaultMap.mapValues { (major, defaultMinors) ->
                    val savedMinors = savedMap[major] ?: emptyList()
                    (defaultMinors + savedMinors).distinct().toMutableList()
                }.toMutableMap()
            } catch (e: Exception) {
                resetToDefault()
            }
        } else {
            resetToDefault()
        }
    }

    private fun resetToDefault() {
        currentMap = defaultMap.mapValues { it.value.toMutableList() }.toMutableMap()
    }

    fun getCategoryMap(): Map<String, List<String>> = currentMap

    fun getMinors(major: String): List<String> = currentMap[major] ?: listOf("その他")

    // ★ エラー解消: RecurringTransactionsScreen から呼ばれるエイリアス
    fun addCategory(context: Context, major: String, minor: String) {
        addMinorCategory(context, major, minor)
    }

    // 中分類を追加
    fun addMinorCategory(context: Context, major: String, newMinor: String) {
        if (major.isBlank() || newMinor.isBlank()) return

        // 大分類が存在しない場合（新規追加）にも対応
        val list = currentMap[major] ?: mutableListOf()
        if (!list.contains(newMinor)) {
            val lastIndex = list.indexOfFirst { it.contains("その他") }
            if (lastIndex != -1) {
                list.add(lastIndex, newMinor)
            } else {
                list.add(newMinor)
            }
            currentMap[major] = list // Mapに確実に反映
            saveToPrefs(context)
        }
    }

    fun removeMinorCategory(context: Context, major: String, minor: String): Boolean {
        val isDefault = defaultMap[major]?.contains(minor) ?: false
        if (isDefault) return false

        val removed = currentMap[major]?.remove(minor) ?: false
        if (removed) {
            saveToPrefs(context)
        }
        return removed
    }

    private fun saveToPrefs(context: Context) {
        try {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(Map::class.java, String::class.java, List::class.java)
            val adapter = moshi.adapter<Map<String, List<String>>>(type)
            val json = adapter.toJson(currentMap)

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MAP, json)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPromptInstructions(): String {
        return currentMap.entries.joinToString("\n") { (major, minors) ->
            "- $major: ${minors.joinToString(", ")}"
        }
    }

    fun isDefaultCategory(major: String, minor: String): Boolean {
        return defaultMap[major]?.contains(minor) ?: false
    }
}