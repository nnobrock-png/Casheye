package com.example.casheye

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CategoryManagementScreen() {
    val context = LocalContext.current
    // 画面更新用の状態。getValue/setValueのインポートがないと 'by' で赤字になります
    var categoryMap by remember { mutableStateOf(CategoryRepository.getCategoryMap()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "分類マスター管理",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "※基本分類は削除できません。自身で追加した中分類のみ削除可能です。",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            // mapのforEachループをLazyColumn内で安全に回すため、各セクションを構成
            categoryMap.forEach { (major, minors) ->
                // 大分類の見出し（StickyHeaderのように振る舞うitem）
                item(key = major) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = major,
                            modifier = Modifier.padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // 中分類のリスト表示
                items(minors) { minor ->
                    // CategoryRepositoryにisDefaultCategory関数が定義されている必要があります
                    val isDefault = CategoryRepository.isDefaultCategory(major, minor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surface),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "・$minor", fontSize = 15.sp)

                        // 基本分類でない場合のみ削除ボタンを表示
                        if (!isDefault) {
                            IconButton(
                                onClick = {
                                    val success = CategoryRepository.removeMinorCategory(context, major, minor)
                                    if (success) {
                                        categoryMap = CategoryRepository.getCategoryMap() // 表示を更新
                                        Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "削除に失敗しました", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "削除",
                                    tint = Color.Gray
                                )
                            }
                        } else {
                            // アイコンボタンと同じ幅のスペースを空けて位置を揃える
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = Color.LightGray.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}