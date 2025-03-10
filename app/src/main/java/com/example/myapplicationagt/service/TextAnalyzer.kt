package com.example.myapplicationagt.service

import android.util.Log
import com.example.myapplicationagt.data.model.Keyword
import java.util.regex.Pattern

/**
 * 文本分析器
 * 负责分析视频文本内容，进行关键词匹配，提取有用信息
 */
class TextAnalyzer {
    
    companion object {
        private const val TAG = "TextAnalyzer"
        
        // 常见的抖音视频属性标记
        private val HASHTAG_PATTERN = Pattern.compile("#([^#]+)#") // 话题标签模式
        private val MENTION_PATTERN = Pattern.compile("@([\\w\\u4e00-\\u9fa5]+)") // @用户模式
        
        // 单例实例
        private var INSTANCE: TextAnalyzer? = null
        
        fun getInstance(): TextAnalyzer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TextAnalyzer().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 分析视频描述文本
     */
    fun analyzeVideoDescription(text: String): VideoTextInfo {
        val info = VideoTextInfo(originalText = text)
        
        try {
            // 提取话题标签
            val hashtagMatcher = HASHTAG_PATTERN.matcher(text)
            while (hashtagMatcher.find()) {
                val hashtag = hashtagMatcher.group(1)?.trim() ?: continue
                if (hashtag.isNotEmpty()) {
                    info.hashtags.add(hashtag)
                }
            }
            
            // 提取@用户
            val mentionMatcher = MENTION_PATTERN.matcher(text)
            while (mentionMatcher.find()) {
                val mention = mentionMatcher.group(1)?.trim() ?: continue
                if (mention.isNotEmpty()) {
                    info.mentions.add(mention)
                }
            }
            
            // 清理文本，去除标签
            val cleanText = text
                .replace(HASHTAG_PATTERN.toRegex(), "")
                .replace(MENTION_PATTERN.toRegex(), "")
                .trim()
            
            info.cleanText = cleanText
            
            Log.d(TAG, "Analyzed text: $info")
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing text", e)
        }
        
        return info
    }
    
    /**
     * 匹配关键词
     */
    fun matchKeywords(text: String, keywords: List<Keyword>): KeywordMatchResult {
        val matchResult = KeywordMatchResult()
        
        if (text.isBlank() || keywords.isEmpty()) {
            return matchResult
        }
        
        try {
            val lowerText = text.lowercase()
            
            // 遍历所有关键词进行匹配
            for (keyword in keywords) {
                if (!keyword.enabled) continue
                
                val keywordValue = keyword.value.lowercase()
                
                if (lowerText.contains(keywordValue)) {
                    matchResult.matchedKeywords.add(keyword)
                    
                    // 根据关键词的优先级和提升值计算加成
                    val baseBoost = keyword.priority * 2
                    
                    matchResult.likeBoost += keyword.likeBoost + baseBoost
                    matchResult.commentBoost += keyword.commentBoost + baseBoost
                    matchResult.followBoost += keyword.followBoost + baseBoost
                    
                    // 记录匹配位置
                    val startIndex = lowerText.indexOf(keywordValue)
                    if (startIndex >= 0) {
                        val match = KeywordMatch(
                            keyword = keyword,
                            startIndex = startIndex,
                            endIndex = startIndex + keywordValue.length
                        )
                        matchResult.matches.add(match)
                    }
                }
            }
            
            // 计算总体匹配分数
            matchResult.matchScore = calculateMatchScore(matchResult)
            
            Log.d(TAG, "Keyword match result: $matchResult")
        } catch (e: Exception) {
            Log.e(TAG, "Error matching keywords", e)
        }
        
        return matchResult
    }
    
    /**
     * 计算总体匹配分数
     */
    private fun calculateMatchScore(result: KeywordMatchResult): Int {
        // 基础分为匹配到的关键词数量
        var score = result.matchedKeywords.size * 10
        
        // 加上所有匹配关键词的优先级总和
        score += result.matchedKeywords.sumOf { it.priority } * 5
        
        // 不同类别的关键词有额外加成
        val categories = result.matchedKeywords.map { it.category }.toSet()
        score += categories.size * 15
        
        return score
    }
    
    /**
     * 提取作者信息
     */
    fun extractAuthorInfo(text: String): AuthorInfo {
        val info = AuthorInfo()
        
        // 在实际应用中，这里可能需要更复杂的逻辑
        // 这里仅做简单示例
        
        // 如果文本包含@，可能是用户名
        val atIndex = text.indexOf('@')
        if (atIndex >= 0) {
            val endIndex = text.indexOf(' ', atIndex)
            if (endIndex > atIndex) {
                info.username = text.substring(atIndex + 1, endIndex)
            } else {
                info.username = text.substring(atIndex + 1)
            }
        }
        
        return info
    }
}

/**
 * 视频文本信息数据类
 */
data class VideoTextInfo(
    val originalText: String,
    var cleanText: String = "",
    val hashtags: MutableList<String> = mutableListOf(),
    val mentions: MutableList<String> = mutableListOf()
)

/**
 * 关键词匹配结果
 */
data class KeywordMatchResult(
    val matchedKeywords: MutableList<Keyword> = mutableListOf(),
    val matches: MutableList<KeywordMatch> = mutableListOf(),
    var likeBoost: Int = 0,
    var commentBoost: Int = 0,
    var followBoost: Int = 0,
    var matchScore: Int = 0
) {
    fun hasMatches(): Boolean = matchedKeywords.isNotEmpty()
}

/**
 * 关键词匹配详情
 */
data class KeywordMatch(
    val keyword: Keyword,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * 作者信息
 */
data class AuthorInfo(
    var username: String = "",
    var description: String = "",
    var followerCount: Int = 0,
    var verified: Boolean = false
)