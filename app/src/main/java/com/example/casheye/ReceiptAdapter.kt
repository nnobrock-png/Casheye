package com.example.casheye

import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.RecyclerView

class ReceiptAdapter(private val items: MutableList<ReceiptItem>) :
    RecyclerView.Adapter<ReceiptAdapter.ViewHolder>() {

    // 大分類は Repository のキーから取得
    private val majorCategories get() = CategoryRepository.getCategoryMap().keys.toList()

    fun getItems(): List<ReceiptItem> = items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_receipt_edit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val editName: EditText = view.findViewById(R.id.editName)
        val editNet: EditText = view.findViewById(R.id.editPriceNet)
        val editTaxIn: EditText = view.findViewById(R.id.editPriceTaxIn)
        val rgTaxRate: RadioGroup = view.findViewById(R.id.rgTaxRate)
        val rb8: RadioButton = view.findViewById(R.id.rb8)
        val rb10: RadioButton = view.findViewById(R.id.rb10)
        val spinnerMajor: Spinner = view.findViewById(R.id.spinnerMajorCategory)
        val spinnerMinor: Spinner = view.findViewById(R.id.spinnerMinorCategory)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteRow)

        fun bind(item: ReceiptItem) {
            val context = itemView.context

            // リスナー解除
            rgTaxRate.setOnCheckedChangeListener(null)
            spinnerMajor.onItemSelectedListener = null
            spinnerMinor.onItemSelectedListener = null

            editName.setText(item.name)
            editNet.setText(item.priceNet.toString())
            editTaxIn.setText(item.priceIncludeTax.toString())

            val ratio = if (item.priceNet > 0) item.priceIncludeTax.toDouble() / item.priceNet.toDouble() else 1.10
            if (ratio < 1.09) rb8.isChecked = true else rb10.isChecked = true

            // --- 大分類Spinner設定 ---
            val majorAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, majorCategories)
            majorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMajor.adapter = majorAdapter

            val majIndex = majorCategories.indexOf(item.majorCategory).let { if (it == -1) majorCategories.indexOf("その他") else it }
            spinnerMajor.setSelection(if (majIndex == -1) 0 else majIndex, false)

            // --- 中分類Spinner設定 (関数化して大分類変更時にも呼べるようにする) ---
            updateMinorSpinner(item.majorCategory, item.minorCategory)

            // --- リスナー設定 ---

            rgTaxRate.setOnCheckedChangeListener { _, checkedId ->
                val net = editNet.text.toString().toIntOrNull() ?: 0
                val rate = if (checkedId == R.id.rb8) 1.08 else 1.10
                val newTaxIn = (net * rate).toInt()
                editTaxIn.setText(newTaxIn.toString())
                items[adapterPosition] = items[adapterPosition].copy(priceIncludeTax = newTaxIn)
            }

            editNet.addTextChangedListener {
                val net = it.toString().toIntOrNull() ?: 0
                val rate = if (rb8.isChecked) 1.08 else 1.10
                val newTaxIn = (net * rate).toInt()
                if (editTaxIn.text.toString() != newTaxIn.toString()) {
                    editTaxIn.setText(newTaxIn.toString())
                }
                items[adapterPosition] = items[adapterPosition].copy(priceNet = net, priceIncludeTax = newTaxIn)
            }

            editTaxIn.addTextChangedListener {
                val taxIn = it.toString().toIntOrNull() ?: 0
                items[adapterPosition] = items[adapterPosition].copy(priceIncludeTax = taxIn)
            }

            editName.addTextChangedListener {
                items[adapterPosition] = items[adapterPosition].copy(name = it.toString())
            }

            spinnerMajor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val newMajor = majorCategories[pos]
                    items[adapterPosition] = items[adapterPosition].copy(majorCategory = newMajor)
                    // 大分類が変わったら中分類の選択肢を更新
                    updateMinorSpinner(newMajor, "その他")
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

            btnDelete.setOnClickListener {
                items.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
                notifyItemRangeChanged(adapterPosition, items.size)
            }
        }

        // 中分類Spinnerを更新するヘルパー関数
        private fun updateMinorSpinner(major: String, currentMinor: String) {
            val context = itemView.context
            val minors = CategoryRepository.getMinors(major)
            val minorAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, minors)
            minorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerMinor.adapter = minorAdapter

            val minIndex = minors.indexOf(currentMinor).let { if (it == -1) minors.indexOf("その他") else it }
            spinnerMinor.setSelection(if (minIndex == -1) 0 else minIndex, false)

            spinnerMinor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    val selected = minors[pos]
                    if (selected == "その他") {
                        showNewCategoryDialog(major)
                    } else {
                        items[adapterPosition] = items[adapterPosition].copy(minorCategory = selected)
                    }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }

        // 新規中分類入力ダイアログ
        private fun showNewCategoryDialog(major: String) {
            val context = itemView.context
            val input = EditText(context).apply { hint = "新しい中分類名" }

            AlertDialog.Builder(context)
                .setTitle("「$major」に分類追加")
                .setView(input)
                .setPositiveButton("追加") { _, _ ->
                    val newMinor = input.text.toString().trim()
                    if (newMinor.isNotEmpty()) {
                        // Repositoryに追加（保存）
                        CategoryRepository.addMinorCategory(context, major, newMinor)
                        // データの更新
                        items[adapterPosition] = items[adapterPosition].copy(minorCategory = newMinor)
                        // UIの再描画
                        notifyItemChanged(adapterPosition)
                    }
                }
                .setNegativeButton("キャンセル") { _, _ ->
                    // キャンセルされたら「その他」のままにするか、前の値に戻す処理
                    spinnerMinor.setSelection(0)
                }
                .show()
        }
    }
}