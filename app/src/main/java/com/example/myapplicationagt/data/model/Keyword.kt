package com.example.myapplicationagt.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关键词数据模型
 * 用于存储关键词信息，支持精确匹配和模糊匹配
 */
@Entity(tableName = "keywords")
data class Keyword(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // 关键词文本
    val text: String,
    
    // 匹配类型
    val matchType: Int,
    
    // 权重系数
    val boostFactor: Int,
    
    // 是否启用
    val isEnabled: Boolean = true,
    
    // 匹配次数
    val matchCount: Int = 0
) {
    companion object {
        // 匹配类型常量
        const val MATCH_TYPE_EXACT = 0  // 精确匹配
        const val MATCH_TYPE_FUZZY = 1  // 模糊匹配（不区分大小写）
    }
}