package com.example.myapplicationagt.service.interaction

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myapplicationagt.data.preferences.SettingsManager
import com.example.myapplicationagt.service.DouYinAccessibilityService
import com.example.myapplicationagt.service.DouYinElementDetector
import com.example.myapplicationagt.service.FloatingWindowService
import com.example.myapplicationagt.service.video.VideoContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.min
import kotlin.random.Random

/**
 * 视频交互管理器
 * 负责视频的交互操作，如点赞、评论、收藏等
 */
class VideoInteractionManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "VideoInteractManager"
        
        // 交互操作类型
        const val INTERACTION_NONE = 0
        const val INTERACTION_LIKE = 1
        const val INTERACTION_COMMENT = 2
        const val INTERACTION_FAVORITE = 3
        const val INTERACTION_SHARE = 4
        const val INTERACTION_FOLLOW = 5
    }
    
    // UI元素检测器
    private val elementDetector = DouYinElementDetector.getInstance()
    
    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 当前交互状态
    private val _currentInteraction = MutableStateFlow(INTERACTION_NONE)
    val currentInteraction: StateFlow<Int> = _currentInteraction.asStateFlow()
    
    // 交互统计
    private var likeCount = 0
    private var commentCount = 0
    private var favoriteCount = 0
    private var shareCount = 0
    private var followCount = 0
    private var totalInteractions = 0
    
    // 自动交互任务
    private var autoInteractionRunnable: Runnable? = null
    private var isAutoInteractionRunning = false
    
    /**
     * 重置统计数据
     */
    fun resetStats() {
        likeCount = 0
        commentCount = 0
        favoriteCount = 0
        shareCount = 0
        followCount = 0
        totalInteractions = 0
    }
    
    /**
     * 获取交互统计数据
     */
    fun getStats(): InteractionStats {
        return InteractionStats(
            likeCount = likeCount,
            commentCount = commentCount,
            favoriteCount = favoriteCount,
            shareCount = shareCount,
            followCount = followCount,
            totalInteractions = totalInteractions
        )
    }
    
    /**
     * 启动自动交互
     */
    fun startAutoInteraction() {
        if (isAutoInteractionRunning) return
        
        autoInteractionRunnable = object : Runnable {
            override fun run() {
                scope.launch {
                    try {
                        val service = DouYinAccessibilityService.instance
                        if (service != null) {
                            val elements = elementDetector.elements.value
                            val randomDelay = Random.nextLong(3000, 10000)
                            
                            // 尝试交互
                            val shouldInteract = Random.nextDouble() < 0.3 // 30%概率交互
                            if (shouldInteract) {
                                performRandomInteraction(elements)
                            }
                            
                            // 等待一段随机时间后再次检查
                            mainHandler.postDelayed(this@Runnable, randomDelay)
                        } else {
                            // 无障碍服务不可用，停止自动交互
                            stopAutoInteraction()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in auto interaction", e)
                        // 发生错误，稍后重试
                        mainHandler.postDelayed(this@Runnable, 5000)
                    }
                }
            }
        }
        
        isAutoInteractionRunning = true
        mainHandler.post(autoInteractionRunnable!!)
        Log.d(TAG, "Auto interaction started")
    }
    
    /**
     * 停止自动交互
     */
    fun stopAutoInteraction() {
        if (!isAutoInteractionRunning) return
        
        autoInteractionRunnable?.let { mainHandler.removeCallbacks(it) }
        autoInteractionRunnable = null
        isAutoInteractionRunning = false
        Log.d(TAG, "Auto interaction stopped")
    }
    
    /**
     * 执行随机交互操作
     */
    private fun performRandomInteraction(elements: DouYinUIElements) {
        val random = Random.nextDouble()
        
        when {
            random < 0.5 -> performLike(elements.likeButton) // 50%概率点赞
            random < 0.7 -> performComment(elements.commentButton) // 20%概率评论
            random < 0.85 -> performFavorite(elements.favoriteButton) // 15%概率收藏
            else -> performFollow(elements.followButton) // 15%概率关注
        }
    }
    
    /**
     * 尝试与当前视频交互
     * 根据视频内容和设置决定是否进行交互
     */
    fun tryInteractWithVideo(videoContent: VideoContent?) {
        if (videoContent == null) return
        
        // 获取元素
        val elements = elementDetector.elements.value
        
        // 随机决定是否进行交互
        scope.launch {
            // 点赞
            if (shouldLike(videoContent)) {
                performLike(elements.likeButton)
            }
            
            // 等待一段随机时间
            delay(Random.nextLong(500, 2000))
            
            // 评论
            if (shouldComment(videoContent)) {
                performComment(elements.commentButton)
            }
            
            // 等待一段随机时间
            delay(Random.nextLong(500, 2000))
            
            // 收藏
            if (shouldFavorite(videoContent)) {
                performFavorite(elements.favoriteButton)
            }
            
            // 等待一段随机时间
            delay(Random.nextLong(500, 2000))
            
            // 关注
            if (shouldFollow(videoContent)) {
                performFollow(elements.followButton)
            }
        }
    }
    
    /**
     * 基于概率和视频内容决定是否执行特定交互
     */
    private suspend fun shouldLike(videoContent: VideoContent): Boolean {
        // 获取点赞概率设置 (0-100)
        val baseProbability = settingsManager.likeProbability.first()
        
        // 如果有关键词匹配，增加概率
        val boost = if (videoContent.hasKeywordMatch) {
            videoContent.likeBoost
        } else {
            0
        }
        
        // 计算最终概率 (确保不超过100)
        val finalProbability = min(baseProbability + boost, 100)
        
        // 随机决定
        return Random.nextInt(100) < finalProbability
    }
    
    private suspend fun shouldComment(videoContent: VideoContent): Boolean {
        val baseProbability = settingsManager.commentProbability.first()
        val boost = if (videoContent.hasKeywordMatch) videoContent.commentBoost else 0
        val finalProbability = min(baseProbability + boost, 100)
        return Random.nextInt(100) < finalProbability
    }
    
    private suspend fun shouldFavorite(videoContent: VideoContent): Boolean {
        val baseProbability = settingsManager.collectProbability.first()
        val boost = if (videoContent.hasKeywordMatch) videoContent.likeBoost / 2 else 0
        val finalProbability = min(baseProbability + boost, 100)
        return Random.nextInt(100) < finalProbability
    }
    
    private suspend fun shouldFollow(videoContent: VideoContent): Boolean {
        val baseProbability = settingsManager.followProbability.first()
        val boost = if (videoContent.hasKeywordMatch) videoContent.followBoost else 0
        val finalProbability = min(baseProbability + boost, 100)
        return Random.nextInt(100) < finalProbability
    }
    
    /**
     * 执行点赞操作
     */
    private fun performLike(likeButton: AccessibilityNodeInfo?) {
        val service = DouYinAccessibilityService.instance ?: return
        _currentInteraction.value = INTERACTION_LIKE
        
        try {
            if (likeButton != null) {
                // 使用无障碍服务API点击
                val rect = Rect()
                likeButton.getBoundsInScreen(rect)
                service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Like performed using accessibility service")
            } else {
                // 使用固定位置点击
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                val x = screenWidth * 0.9f
                val y = screenHeight * 0.5f
                
                service.performClick(x, y)
                Log.d(TAG, "Like performed using fixed position")
            }
            
            // 更新统计
            likeCount++
            totalInteractions++
            FloatingWindowService.instance?.incrementInteractionCount()
            
            // 延迟将交互状态重置
            mainHandler.postDelayed({
                _currentInteraction.value = INTERACTION_NONE
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing like", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 执行评论操作
     */
    private fun performComment(commentButton: AccessibilityNodeInfo?) {
        val service = DouYinAccessibilityService.instance ?: return
        _currentInteraction.value = INTERACTION_COMMENT
        
        try {
            if (commentButton != null) {
                // 使用无障碍服务API点击
                val rect = Rect()
                commentButton.getBoundsInScreen(rect)
                service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Comment performed using accessibility service")
            } else {
                // 使用固定位置点击
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                val x = screenWidth * 0.9f
                val y = screenHeight * 0.6f
                
                service.performClick(x, y)
                Log.d(TAG, "Comment performed using fixed position")
            }
            
            // 更新统计
            commentCount++
            totalInteractions++
            FloatingWindowService.instance?.incrementInteractionCount()
            
            // 在评论框中输入随机评论
            scope.launch {
                delay(1000) // 等待评论框出现
                
                // 随机选择一条评论
                val comment = getRandomComment()
                
                // 检测评论框
                val elements = elementDetector.elements.value
                if (elements.commentEditText != null) {
                    // 输入评论文本
                    service.inputText(elements.commentEditText, comment)
                    
                    delay(500) // 短暂等待
                    
                    // 查找发送按钮并点击
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        val sendButtons = rootNode.findAccessibilityNodeInfosByText("发送")
                        if (sendButtons.isNotEmpty()) {
                            val sendButton = sendButtons[0]
                            val sendRect = Rect()
                            sendButton.getBoundsInScreen(sendRect)
                            service.performClick(sendRect.centerX().toFloat(), sendRect.centerY().toFloat())
                        }
                    }
                }
                
                // 延迟将交互状态重置
                delay(2000)
                _currentInteraction.value = INTERACTION_NONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing comment", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 执行收藏操作
     */
    private fun performFavorite(favoriteButton: AccessibilityNodeInfo?) {
        val service = DouYinAccessibilityService.instance ?: return
        _currentInteraction.value = INTERACTION_FAVORITE
        
        try {
            if (favoriteButton != null) {
                // 使用无障碍服务API点击
                val rect = Rect()
                favoriteButton.getBoundsInScreen(rect)
                service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Favorite performed using accessibility service")
            } else {
                // 使用固定位置点击
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                val x = screenWidth * 0.9f
                val y = screenHeight * 0.7f
                
                service.performClick(x, y)
                Log.d(TAG, "Favorite performed using fixed position")
            }
            
            // 更新统计
            favoriteCount++
            totalInteractions++
            FloatingWindowService.instance?.incrementInteractionCount()
            
            // 延迟将交互状态重置
            mainHandler.postDelayed({
                _currentInteraction.value = INTERACTION_NONE
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing favorite", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 执行关注操作
     */
    private fun performFollow(followButton: AccessibilityNodeInfo?) {
        val service = DouYinAccessibilityService.instance ?: return
        _currentInteraction.value = INTERACTION_FOLLOW
        
        try {
            if (followButton != null) {
                // 使用无障碍服务API点击
                val rect = Rect()
                followButton.getBoundsInScreen(rect)
                service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Follow performed using accessibility service")
            } else {
                // 点击视频作者头像，然后点击关注按钮
                scope.launch {
                    // 点击头像区域
                    val elements = elementDetector.elements.value
                    if (elements.authorAvatar != null) {
                        val rect = Rect()
                        elements.authorAvatar.getBoundsInScreen(rect)
                        service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                    } else {
                        // 使用固定位置
                        val screenWidth = context.resources.displayMetrics.widthPixels
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        
                        val x = screenWidth * 0.9f
                        val y = screenHeight * 0.3f
                        
                        service.performClick(x, y)
                    }
                    
                    Log.d(TAG, "Author avatar clicked")
                    
                    // 等待页面加载
                    delay(1500)
                    
                    // 尝试找到关注按钮
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        val followButtons = rootNode.findAccessibilityNodeInfosByText("关注")
                        if (followButtons.isNotEmpty()) {
                            val button = followButtons[0]
                            val buttonRect = Rect()
                            button.getBoundsInScreen(buttonRect)
                            service.performClick(buttonRect.centerX().toFloat(), buttonRect.centerY().toFloat())
                            Log.d(TAG, "Follow button clicked")
                        }
                    }
                    
                    // 等待关注完成
                    delay(1000)
                    
                    // 返回前一个页面
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }
            
            // 更新统计
            followCount++
            totalInteractions++
            FloatingWindowService.instance?.incrementInteractionCount()
            
            // 延迟将交互状态重置
            mainHandler.postDelayed({
                _currentInteraction.value = INTERACTION_NONE
            }, 4000) // 给关注操作更长的时间
        } catch (e: Exception) {
            Log.e(TAG, "Error performing follow", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 获取随机评论文本
     */
    private fun getRandomComment(): String {
        val comments = arrayOf(
            "不错啊",
            "学到了",
            "很好看",
            "支持一下",
            "很喜欢",
            "继续加油",
            "太棒了",
            "厉害了",
            "收藏了",
            "点赞"
        )
        
        return comments.random()
    }
}

/**
 * 交互统计数据类
 */
data class InteractionStats(
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val favoriteCount: Int = 0,
    val shareCount: Int = 0,
    val followCount: Int = 0,
    val totalInteractions: Int = 0
)