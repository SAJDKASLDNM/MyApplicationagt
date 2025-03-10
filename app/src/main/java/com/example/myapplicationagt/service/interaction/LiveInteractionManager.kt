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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

/**
 * 直播互动管理器
 * 负责直播的交互操作，如点赞、评论和送礼物等
 */
class LiveInteractionManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    companion object {
        private const val TAG = "LiveInteractManager"
        
        // 交互操作类型
        const val INTERACTION_NONE = 0
        const val INTERACTION_LIKE = 1
        const val INTERACTION_COMMENT = 2
        const val INTERACTION_GIFT = 3
        const val INTERACTION_FOLLOW = 4
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
    
    // 直播互动任务
    private var interactionJob: Job? = null
    
    // 交互统计
    private var likeCount = 0
    private var commentCount = 0
    private var giftCount = 0
    private var followCount = 0
    private var totalInteractions = 0
    
    // 预设评论列表
    private val commentsList = listOf(
        "主播好漂亮",
        "真好看",
        "主播说话真好听",
        "主播玩的真不错",
        "支持主播",
        "主播笑起来真好看",
        "这个直播间氛围不错",
        "主播今天状态很好啊",
        "喜欢你的直播",
        "主播真可爱",
        "主播好有才华",
        "主播可以跟粉丝互动一下吗",
        "6666666",
        "厉害了",
        "这个真的不错",
        "主播还会开播吗",
        "主播多久开播一次",
        "很有意思"
    )
    
    /**
     * 重置统计数据
     */
    fun resetStats() {
        likeCount = 0
        commentCount = 0
        giftCount = 0
        followCount = 0
        totalInteractions = 0
    }
    
    /**
     * 获取交互统计数据
     */
    fun getStats(): LiveInteractionStats {
        return LiveInteractionStats(
            likeCount = likeCount,
            commentCount = commentCount,
            giftCount = giftCount,
            followCount = followCount,
            totalInteractions = totalInteractions
        )
    }
    
    /**
     * 开始自动互动
     */
    fun startAutoInteraction() {
        // 如果已经在运行，先停止
        stopAutoInteraction()
        
        // 启动新的互动任务
        interactionJob = scope.launch {
            try {
                while (true) {
                    // 获取互动间隔 (默认3-15秒)
                    val minInterval = 3000L
                    val maxInterval = 15000L
                    val interval = Random.nextLong(minInterval, maxInterval)
                    
                    // 随机决定互动类型并执行
                    performRandomInteraction()
                    
                    // 等待一段时间再次互动
                    delay(interval)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto interaction", e)
            }
        }
        
        Log.d(TAG, "Auto interaction started")
    }
    
    /**
     * 停止自动互动
     */
    fun stopAutoInteraction() {
        interactionJob?.cancel()
        interactionJob = null
        Log.d(TAG, "Auto interaction stopped")
    }
    
    /**
     * 执行随机互动
     */
    suspend fun performRandomInteraction() {
        // 获取界面元素
        val elements = elementDetector.elements.value
        
        // 获取各种互动的概率
        val likeProbability = settingsManager.liveLikeProbability.first()
        val commentProbability = settingsManager.liveCommentProbability.first()
        val giftProbability = settingsManager.liveGiftProbability.first()
        
        // 生成随机数
        val rand = Random.nextInt(100)
        
        // 根据概率决定互动类型
        when {
            rand < likeProbability -> performLike(elements.liveLikeButton)
            rand < likeProbability + commentProbability -> performComment(elements.liveCommentButton)
            rand < likeProbability + commentProbability + giftProbability -> performGiftSending(elements.liveGiftButton)
            else -> {
                // 如果都不满足，有20%概率关注主播
                if (Random.nextInt(100) < 20) {
                    performFollow(elements.liveFollowButton)
                }
            }
        }
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
                // 使用固定位置点击 (直播界面右下角)
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                val x = screenWidth * 0.9f
                val y = screenHeight * 0.85f
                
                // 连续点击多次产生连击效果
                val clickCount = Random.nextInt(1, 10)
                for (i in 0 until clickCount) {
                    service.performClick(x, y)
                    Thread.sleep(100)
                }
                
                Log.d(TAG, "Like performed $clickCount times using fixed position")
            }
            
            // 更新统计
            likeCount += 1
            totalInteractions += 1
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
            // 首先点击评论框
            if (commentButton != null) {
                // 使用无障碍服务API点击
                val rect = Rect()
                commentButton.getBoundsInScreen(rect)
                service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Comment box clicked using accessibility service")
            } else {
                // 使用固定位置点击评论框
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                val x = screenWidth * 0.5f
                val y = screenHeight * 0.95f
                
                service.performClick(x, y)
                Log.d(TAG, "Comment box clicked using fixed position")
            }
            
            // 等待评论框出现
            scope.launch {
                delay(1000)
                
                // 随机选择一条评论
                val comment = getRandomComment()
                
                // 检测评论输入框
                val elements = elementDetector.elements.value
                if (elements.liveCommentEditText != null) {
                    // 输入评论文本
                    service.inputText(elements.liveCommentEditText, comment)
                    
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
                            
                            // 更新统计
                            commentCount++
                            totalInteractions++
                            FloatingWindowService.instance?.incrementInteractionCount()
                            
                            Log.d(TAG, "Comment sent: $comment")
                        }
                    }
                } else {
                    Log.d(TAG, "Comment edit text not found")
                }
                
                // 延迟将交互状态重置
                delay(1000)
                _currentInteraction.value = INTERACTION_NONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing comment", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 执行送礼物操作
     */
    private fun performGiftSending(giftButton: AccessibilityNodeInfo?) {
        val service = DouYinAccessibilityService.instance ?: return
        _currentInteraction.value = INTERACTION_GIFT
        
        try {
            // 点击礼物按钮
            if (giftButton != null) {
                // 使用无障碍服务API点击
                val rect = Rect()
                giftButton.getBoundsInScreen(rect)
                service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                Log.d(TAG, "Gift button clicked using accessibility service")
            } else {
                // 使用固定位置点击
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                val x = screenWidth * 0.8f
                val y = screenHeight * 0.95f
                
                service.performClick(x, y)
                Log.d(TAG, "Gift button clicked using fixed position")
            }
            
            // 等待礼物面板出现
            scope.launch {
                delay(1000)
                
                // 点击第一个免费礼物 (通常在礼物面板的最左边)
                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels
                
                // 点击最左边区域，大概率是免费礼物
                val x = screenWidth * 0.1f
                val y = screenHeight * 0.7f
                
                service.performClick(x, y)
                Log.d(TAG, "Free gift clicked")
                
                delay(500)
                
                // 点击发送按钮
                val rootNode = service.rootInActiveWindow
                if (rootNode != null) {
                    val sendButtons = rootNode.findAccessibilityNodeInfosByText("发送")
                    if (sendButtons.isNotEmpty()) {
                        val sendButton = sendButtons[0]
                        val sendRect = Rect()
                        sendButton.getBoundsInScreen(sendRect)
                        service.performClick(sendRect.centerX().toFloat(), sendRect.centerY().toFloat())
                        
                        // 更新统计
                        giftCount++
                        totalInteractions++
                        FloatingWindowService.instance?.incrementInteractionCount()
                        
                        Log.d(TAG, "Gift sent")
                    }
                }
                
                // 等待一段时间后关闭礼物面板 (如果还没自动关闭)
                delay(2000)
                
                // 点击背景区域以关闭礼物面板
                service.performClick(screenWidth * 0.5f, screenHeight * 0.3f)
                
                // 重置交互状态
                delay(500)
                _currentInteraction.value = INTERACTION_NONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing gift sending", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 执行关注主播操作
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
                
                // 更新统计
                followCount++
                totalInteractions++
                FloatingWindowService.instance?.incrementInteractionCount()
                
                Log.d(TAG, "Follow performed using accessibility service")
            } else {
                // 使用查找关注按钮的方式
                val rootNode = service.rootInActiveWindow
                if (rootNode != null) {
                    val followButtons = rootNode.findAccessibilityNodeInfosByText("关注")
                    for (button in followButtons) {
                        val rect = Rect()
                        button.getBoundsInScreen(rect)
                        
                        // 检查按钮是否在屏幕上方区域，这通常是关注主播的按钮
                        val screenHeight = context.resources.displayMetrics.heightPixels
                        if (rect.top < screenHeight * 0.3) {
                            service.performClick(rect.centerX().toFloat(), rect.centerY().toFloat())
                            
                            // 更新统计
                            followCount++
                            totalInteractions++
                            FloatingWindowService.instance?.incrementInteractionCount()
                            
                            Log.d(TAG, "Follow performed by finding button")
                            break
                        }
                    }
                }
            }
            
            // 延迟将交互状态重置
            mainHandler.postDelayed({
                _currentInteraction.value = INTERACTION_NONE
            }, 2000)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing follow", e)
            _currentInteraction.value = INTERACTION_NONE
        }
    }
    
    /**
     * 获取随机评论文本
     */
    private fun getRandomComment(): String {
        return commentsList.random()
    }
    
    /**
     * 检测直播间互动热度
     */
    fun detectLiveInteractionLevel(): LiveInteractionLevel {
        try {
            val elements = elementDetector.elements.value
            
            // 获取直播人数文本
            val userCountText = elements.liveUserCountText
            
            // 根据直播观看人数判断热度
            val viewerCount = parseViewerCount(userCountText)
            
            return when {
                viewerCount >= 10000 -> LiveInteractionLevel.HIGH
                viewerCount >= 1000 -> LiveInteractionLevel.MEDIUM
                else -> LiveInteractionLevel.LOW
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting live interaction level", e)
            return LiveInteractionLevel.MEDIUM
        }
    }
    
    /**
     * 解析观看人数文本
     */
    private fun parseViewerCount(text: String): Int {
        return try {
            // 处理形如 "1.2万" 的文本
            if (text.contains("万")) {
                val numPart = text.replace("万", "").toFloat()
                (numPart * 10000).toInt()
            } else {
                text.replace(",", "").toInt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing viewer count: $text", e)
            0
        }
    }
}

/**
 * 直播互动统计数据类
 */
data class LiveInteractionStats(
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val giftCount: Int = 0,
    val followCount: Int = 0,
    val totalInteractions: Int = 0
)

/**
 * 直播互动热度等级
 */
enum class LiveInteractionLevel {
    LOW,     // 低互动热度
    MEDIUM,  // 中等互动热度
    HIGH     // 高互动热度
}