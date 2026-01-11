package com.example.casheye

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

import java.util.UUID

fun expenseToCsvLine(expense: Expense): String {
    val type = if (expense.isIncome) "income" else "expense"

    return listOf(
        UUID.randomUUID().toString(),
        expense.date.toString(),
        expense.date.year.toString(),
        expense.date.monthValue.toString(),
        type,
        expense.store,
        expense.name,
        expense.majorCategory,
        expense.minorCategory,
        expense.priceIncludeTax.toString(),
        "manual"
    ).joinToString(",") { it.escapeCsv() }
}

fun String.escapeCsv(): String {
    return if (contains(",") || contains("\"") || contains("\n")) {
        "\"${replace("\"", "\"\"")}\""
    } else this
}

fun exportExpensesToCsv(expenses: List<Expense>): String {
    val header = listOf(
        "id","date","year","month","type",
        "store","name","major","minor","amount","source"
    ).joinToString(",")

    val body = expenses.joinToString("\n") { expenseToCsvLine(it) }
    return "$header\n$body"
}

fun saveCsvToCache(context: Context, csv: String): File {
    val fileName = "casheye_${LocalDate.now()}.csv"
    val file = File(context.cacheDir, fileName)
    file.writeText(csv, Charsets.UTF_8)
    return file
}

fun shareCsv(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(
        Intent.createChooser(intent, "CSVを共有")
    )
}


fun exportAnalysisResultToCsv(
    analysisResult: Map<String, Map<String, Int>>,
    columns: List<String>
): String {

    val sb = StringBuilder()

    // ヘッダー
    sb.append("項目,")
    sb.append(columns.joinToString(","))
    sb.append("\n")

    // 本体
    analysisResult.forEach { (row, values) ->
        sb.append(row.escapeCsv())
        columns.forEach { col ->
            sb.append(",")
            sb.append((values[col] ?: 0).toString())
        }
        sb.append("\n")
    }

    return sb.toString()
}

fun exportMonthlyAnalysisToCsv(


    months: List<YearMonth>,
    expenses: List<Expense>
): String {

    val header = buildList {
        add("category")
        months.forEach { add("${it.year}/${it.monthValue}") }
    }.joinToString(",")

    val majorCategories = expenses
        .filter { !it.isIncome }
        .map { it.majorCategory }
        .distinct()
        .sorted()

    val lines = majorCategories.map { major ->

        val values = months.map { ym ->
            expenses
                .filter {
                    !it.isIncome &&
                            it.majorCategory == major &&
                            YearMonth.from(it.date) == ym
                }
                .sumOf { it.priceIncludeTax }
                .toString()
        }

        listOf(major.escapeCsv(), *values.toTypedArray())
            .joinToString(",")
    }

    return header + "\n" + lines.joinToString("\n")
}

fun exportMonthlyTableToCsv(
    months: List<YearMonth>,
    summaries: List<MonthlySummary>,
    expenses: List<Expense>
): String {

    val header = buildString {
        append("区分")
        months.forEach { append(",${it.year}/${it.monthValue}") }
    }

    val lines = mutableListOf<String>()
    lines += header

    fun row(label: String, values: List<Int>) {
        lines += (listOf(label) + values.map { it.toString() })
            .joinToString(",")
    }

    row("収入", months.map { ym ->
        summaries.firstOrNull { it.yearMonth == ym }?.incomeTotal ?: 0
    })

    row("支出", months.map { ym ->
        summaries.firstOrNull { it.yearMonth == ym }?.expenseTotal ?: 0
    })

    row("残高", months.map { ym ->
        summaries.firstOrNull { it.yearMonth == ym }?.balance ?: 0
    })

    return lines.joinToString("\n")
}


fun exportFullAnalysisToCsv(
    expenses: List<Expense>,
    summaries: List<MonthlySummary>
): String {

    val header = "type,level,category,subCategory,yearMonth,amount"

    val lines = mutableListOf<String>()
    lines += header

    val months = summaries.map { it.yearMonth }

    // ===== ① サマリー =====
    summaries.forEach { summary ->
        val ym = "${summary.yearMonth.year}/${summary.yearMonth.monthValue}"

        lines += listOf(
            listOf("summary","total","収入","",ym,summary.incomeTotal),
            listOf("summary","total","支出","",ym,summary.expenseTotal),
            listOf("summary","total","残高","",ym,summary.balance)
        ).joinToString("\n") {
            it.joinToString(",") { v -> v.toString().escapeCsv() }
        }
    }

    // ===== ② 大分類 =====
    val majorCategories = expenses
        .filter { !it.isIncome }
        .map { it.majorCategory }
        .distinct()

    majorCategories.forEach { major ->
        months.forEach { ymObj ->
            val ym = "${ymObj.year}/${ymObj.monthValue}"

            val total = expenses
                .filter {
                    !it.isIncome &&
                            it.majorCategory == major &&
                            YearMonth.from(it.date) == ymObj
                }
                .sumOf { it.priceIncludeTax }

            lines += listOf(
                "major","major",major,"",ym,total
            ).joinToString(",") { it.toString().escapeCsv() }
        }
    }

    // ===== ③ 中分類 =====
    majorCategories.forEach { major ->

        val subs = expenses
            .filter { !it.isIncome && it.majorCategory == major }
            .map { it.minorCategory }
            .distinct()

        subs.forEach { sub ->
            months.forEach { ymObj ->
                val ym = "${ymObj.year}/${ymObj.monthValue}"

                val total = expenses
                    .filter {
                        !it.isIncome &&
                                it.majorCategory == major &&
                                it.minorCategory == sub &&
                                YearMonth.from(it.date) == ymObj
                    }
                    .sumOf { it.priceIncludeTax }

                lines += listOf(
                    "minor","minor",major,sub,ym,total
                ).joinToString(",") { it.toString().escapeCsv() }
            }
        }
    }

    return lines.joinToString("\n")
}

fun exportFullMonthlyAnalysisToCsv(
    expenses: List<Expense>,
    summaries: List<MonthlySummary>
): String {

    val months: List<YearMonth> = summaries.map { it.yearMonth }.sorted()
    // 日付表記を YYYY-MM 形式に統一（スラッシュを使用しない）
    val columnLabels = months.map { "${it.year}-${"%02d".format(it.monthValue)}" }
    val headerSuffix = columnLabels.joinToString(",")

    val sb = StringBuilder()

    // ===== サマリー =====
    sb.append("サマリー,").append(headerSuffix).append("\n")
    listOf("収入", "支出", "残高").forEach { label ->
        sb.append(label)
        months.forEach { ym ->
            val summary = summaries.firstOrNull { it.yearMonth == ym }
            val value = when (label) {
                "収入" -> summary?.incomeTotal ?: 0
                "支出" -> summary?.expenseTotal ?: 0
                "残高" -> summary?.balance ?: 0
                else -> 0
            }
            sb.append(",").append(value)
        }
        sb.append("\n")
    }

    sb.append("\n") // セクション間の空行

    // ===== 大分類 =====
    sb.append("大分類,").append(headerSuffix).append("\n")
    val majorCategories = expenses
        .filter { !it.isIncome }
        .map { it.majorCategory }
        .distinct()
        .sorted()

    majorCategories.forEach { major ->
        sb.append(major)
        months.forEach { ym ->
            val total = expenses
                .filter { !it.isIncome && it.majorCategory == major && YearMonth.from(it.date) == ym }
                .sumOf { it.priceIncludeTax }
            sb.append(",").append(total)
        }
        sb.append("\n")
    }

    sb.append("\n") // セクション間の空行

    // ===== 中分類 =====
    sb.append("中分類,").append(headerSuffix).append("\n")
    val subCategoryGroups = expenses
        .filter { !it.isIncome }
        .groupBy { it.minorCategory to it.majorCategory }
        .toSortedMap(compareBy({ it.second }, { it.first }))

    subCategoryGroups.forEach { (key, list) ->
        val (sub, major) = key
        sb.append("$sub（$major）") // 親の大分類を括弧で付記
        months.forEach { ym ->
            val total = list
                .filter { YearMonth.from(it.date) == ym }
                .sumOf { it.priceIncludeTax }
            sb.append(",").append(total)
        }
        sb.append("\n")
    }

    return sb.toString()
}