package com.example.casheye

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ResultActivity : AppCompatActivity() {

    // 現在の入れ替えモードを保持（true: 税込を税抜へ移す / false: 税抜を税込へ移す）
    private var isSwapModeToNet = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // 1. データ受け取り
        val receiptItems = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("RECEIPT_ITEMS", ArrayList::class.java) as? ArrayList<ReceiptItem>
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("RECEIPT_ITEMS") as? ArrayList<ReceiptItem>
        } ?: arrayListOf()

        // 2. UIコンポーネントの紐付け
        val editCommonDate: EditText = findViewById(R.id.editCommonDate)
        val editCommonStore: EditText = findViewById(R.id.editCommonStore)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)

        // 初期値をヘッダーにセット
        if (receiptItems.isNotEmpty()) {
            editCommonDate.setText(receiptItems[0].date)
            editCommonStore.setText(receiptItems[0].store)
        }

        // 3. RecyclerView設定
        val adapter = ReceiptAdapter(receiptItems)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 4. 一括入替ボタンのロジック（交互スライド）
        findViewById<Button>(R.id.btnSwapTax).setOnClickListener {
            receiptItems.forEachIndexed { index, item ->
                // 現在の各行の税率比率を取得（取れない場合は1.10）
                val ratio = if (item.priceNet > 0) {
                    item.priceIncludeTax.toDouble() / item.priceNet.toDouble()
                } else {
                    1.10
                }

                if (isSwapModeToNet) {
                    // 【1回目】税込欄にある数値を税抜欄へ移動し、税込を再計算
                    val newNet = item.priceIncludeTax
                    val newTaxIn = (newNet * ratio).toInt()
                    receiptItems[index] = item.copy(priceNet = newNet, priceIncludeTax = newTaxIn)
                } else {
                    // 【2回目】税抜欄にある数値を税込欄へ移動し、税抜を再計算
                    val newTaxIn = item.priceNet
                    val newNet = (newTaxIn / ratio).toInt()
                    receiptItems[index] = item.copy(priceNet = newNet, priceIncludeTax = newTaxIn)
                }
            }

            // モードを反転
            isSwapModeToNet = !isSwapModeToNet

            adapter.notifyDataSetChanged()

            val msg = if (!isSwapModeToNet) "税込価格を税抜へ移動しました" else "税抜価格を税込へ移動しました"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // 5. 保存ボタン
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val finalDate = editCommonDate.text.toString()
            val finalStore = editCommonStore.text.toString()

            val updatedItems = adapter.getItems().map {
                it.copy(date = finalDate, store = finalStore)
            }

            if (updatedItems.isNotEmpty()) {
                try {
                    val currentItems = loadReceiptItems(this).toMutableList()
                    currentItems.addAll(updatedItems)
                    saveReceiptItems(this, currentItems)
                    Toast.makeText(this, "${updatedItems.size}件を登録しました", Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "保存エラー: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}