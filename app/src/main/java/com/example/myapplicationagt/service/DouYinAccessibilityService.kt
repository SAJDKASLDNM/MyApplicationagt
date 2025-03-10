package com.example.myapplicationagt.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DouYinAccessibilityService : AccessibilityService() {
    
    companion object {
        var instance: DouYinAccessibilityService? = null
            private set
        
        // 抖音包名
        const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        
        // 服务是否运行中
        val isRunning: Boolean
            get() = instance != null
            
        // 抖音界面类型
        const val SCREEN_TYPE_UNKNOWN = 0
        const val SCREEN_TYPE_FEED = 1        // 视频浏览界面
        const val SCREEN_TYPE_PROFILE = 2     // 用户个人主页
        const val SCREEN_TYPE_LIVE = 3        // 直播界面
        const val SCREEN_TYPE_COMMENT = 4     // 评论区界面
    }
    
    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 养号模式是否运行
    private var isAccountNurturingActive = false
    
    // 直播互动模式是否运行
    private var isLiveInteractionActive = false
    
    // 当前界面类型
    private var currentScreenType = SCREEN_TYPE_UNKNOWN
    
    // 视频停留时间控制
    private var minWatchTimeMs = 5000L // 最短观看时间5秒
    private var maxWatchTimeMs = 30000L // 最长观看时间30秒
    private var currentVideoStartTime = 0L
    
    // 视频统计
    private var videoViewCount = 0
    private var interactionCount = 0
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Toast.makeText(this, "抖音助手服务已启动", Toast.LENGTH_SHORT).show()
        
        try {
            // 启动悬浮窗服务
            startService(Intent(this, FloatingWindowService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        
        // 停止所有任务
        stopAllTasks()
        
        try {
            // 停止悬浮窗服务
            stopService(Intent(this, FloatingWindowService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        Toast.makeText(this, "抖音助手服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 这里将处理来自抖音应用的无障碍事件
        if (event.packageName != DOUYIN_PACKAGE) return
        
        // 检测当前界面类型
        detectScreenType(event)
        
        // 根据当前活动模式处理事件
        when {
            isAccountNurturingActive -> handleAccountNurturingEvent(event)
            isLiveInteractionActive -> handleLiveInteractionEvent(event)
        }
    }
    
    override fun onInterrupt() {
        // 服务中断时的处理
        stopAllTasks()
    }
    
    /**
     * 检测当前抖音界面类型
     */
    private fun detectScreenType(event: AccessibilityEvent) {
        val nodeInfo = event.source ?: rootInActiveWindow ?: return
        
        val oldScreenType = currentScreenType
        
        // 检测视频浏览界面
        if (hasNodeWithDescription(nodeInfo, "视频") || 
            hasNodeWithDescription(nodeInfo, "推荐") ||
            hasNodeWithDescription(nodeInfo, "首页")) {
            currentScreenType = SCREEN_TYPE_FEED
        }
        // 检测直播界面
        else if (hasNodeWithDescription(nodeInfo, "直播中") || 
                 hasNodeWithDescription(nodeInfo, "观看人数") || 
                 hasNodeWithDescription(nodeInfo, "礼物")) {
            currentScreenType = SCREEN_TYPE_LIVE
        }
        // 检测评论区
        else if (hasNodeWithDescription(nodeInfo, "评论") && 
                 hasNodeWithText(nodeInfo, "写评论...")) {
            currentScreenType = SCREEN_TYPE_COMMENT
        }
        // 检测用户主页
        else if (hasNodeWithDescription(nodeInfo, "关注") && 
                 hasNodeWithDescription(nodeInfo, "粉丝") && 
                 hasNodeWithDescription(nodeInfo, "获赞")) {
            currentScreenType = SCREEN_TYPE_PROFILE
        }
        else {
            // 无法确定的界面，保持原状态
            // currentScreenType = SCREEN_TYPE_UNKNOWN
        }
        
        // 如果界面类型发生变化，重置计时器
        if (oldScreenType != currentScreenType) {
            resetVideoTimer()
        }
    }
    
    /**
     * 检查节点是否包含指定描述的子节点
     */
    private fun hasNodeWithDescription(nodeInfo: AccessibilityNodeInfo, description: String): Boolean {
        return nodeInfo.findAccessibilityNodeInfosByText(description).isNotEmpty()
    }
    
    /**
     * 检查节点是否包含指定文本的子节点
     */
    private fun hasNodeWithText(nodeInfo: AccessibilityNodeInfo, text: String): Boolean {
        return nodeInfo.findAccessibilityNodeInfosByText(text).isNotEmpty()
    }
    
    /**
     * 重置视频计时器
     */
    private fun resetVideoTimer() {
        currentVideoStartTime = System.currentTimeMillis()
    }
    
    /**
     * 处理养号模式事件
     */
    private fun handleAccountNurturingEvent(event: AccessibilityEvent) {
        when (currentScreenType) {
            SCREEN_TYPE_FEED -> handleFeedScreen()
            SCREEN_TYPE_LIVE -> handleLiveScreen()
            SCREEN_TYPE_PROFILE -> handleProfileScreen()
            SCREEN_TYPE_COMMENT -> handleCommentScreen()
        }
    }
    
    /**
     * 处理视频浏览界面
     */
    private fun handleFeedScreen() {
        val currentTime = System.currentTimeMillis()
        val watchTime = currentTime - currentVideoStartTime
        
        // 检查是否达到最小观看时间
        if (watchTime >= minWatchTimeMs) {
            // 随机决定是否执行互动操作
            val shouldInteract = Math.random() < 0.3 // 30%概率互动
            
            if (shouldInteract) {
                performRandomInteraction()
            }
            
            // 检查是否达到最大观看时间
            if (watchTime >= maxWatchTimeMs || Math.random() < 0.2) { // 达到最大时间或20%概率提前切换
                // 切换到下一个视频
                swipeToNextVideo()
            }
        }
    }
    
    /**
     * 执行随机互动操作
     */
    private fun performRandomInteraction() {
        val rand = Math.random()
        
        when {
            rand < 0.6 -> performLike() // 60%概率点赞
            rand < 0.8 -> performComment() // 20%概率评论
            else -> performFollow() // 20%概率关注
        }
        
        interactionCount++
        FloatingWindowService.instance?.incrementInteractionCount()
    }
    
    /**
     * 执行点赞操作
     */
    private fun performLike() {
        // 点击右侧爱心按钮位置
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val x = screenWidth * 0.9f
        val y = screenHeight * 0.5f
        
        performClick(x, y)
    }
    
    /**
     * 执行评论操作
     */
    private fun performComment() {
        // 点击右侧评论按钮位置
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val x = screenWidth * 0.9f
        val y = screenHeight * 0.6f
        
        performClick(x, y)
        
        // 等待评论区出现后输入评论
        serviceScope.launch {
            delay(1000) // 等待1秒
            
            // 查找评论输入框
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val commentEditTexts = rootNode.findAccessibilityNodeInfosByText("写评论...")
                if (commentEditTexts.isNotEmpty()) {
                    val commentEditText = commentEditTexts[0]
                    
                    // 获取随机评论文本
                    val commentText = getRandomComment()
                    
                    // 设置评论文本
                    val bundle = Bundle()
                    bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, commentText)
                    commentEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    
                    // 点击发送按钮
                    delay(500)
                    
                    // 查找发送按钮
                    val sendButtons = rootNode.findAccessibilityNodeInfosByText("发送")
                    if (sendButtons.isNotEmpty()) {
                        val sendButton = sendButtons[0]
                        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }
            }
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
    
    /**
     * 执行关注操作
     */
    private fun performFollow() {
        // 点击用户头像
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        val x = screenWidth * 0.9f
        val y = screenHeight * 0.3f
        
        performClick(x, y)
        
        // 等待跳转到用户主页后点击关注按钮
        serviceScope.launch {
            delay(1000) // 等待1秒
            
            // 查找关注按钮
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val followButtons = rootNode.findAccessibilityNodeInfosByText("关注")
                if (followButtons.isNotEmpty()) {
                    val followButton = followButtons[0]
                    followButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            
            // 返回前一个界面
            delay(1000)
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }
    
    /**
     * 处理直播界面
     */
    private fun handleLiveScreen() {
        // 实现直播页面的交互逻辑
    }
    
    /**
     * 处理个人主页界面
     */
    private fun handleProfileScreen() {
        // 实现个人主页的交互逻辑
    }
    
    /**
     * 处理评论区界面
     */
    private fun handleCommentScreen() {
        // 实现评论区的交互逻辑
    }
    
    /**
     * 处理直播互动事件
     */
    private fun handleLiveInteractionEvent(event: AccessibilityEvent) {
        // 实现直播互动的逻辑
    }
    
    /**
     * 执行滑动到下一个视频
     */
    private fun swipeToNextVideo() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        
        // 从屏幕中下方向上滑动
        val path = Path()
        path.moveTo(screenWidth / 2f, screenHeight * 0.7f)
        path.lineTo(screenWidth / 2f, screenHeight * 0.3f)
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
        
        dispatchGesture(gestureBuilder.build(), null, null)
        
        // 更新计数并重置计时器
        videoViewCount++
        FloatingWindowService.instance?.incrementVideoCount()
        resetVideoTimer()
    }
    
    /**
     * 执行点击操作
     */
    private fun performClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        
        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 10))
        
        dispatchGesture(gestureBuilder.build(), null, null)
    }
    
    /**
     * 停止所有任务
     */
    private fun stopAllTasks() {
        isAccountNurturingActive = false
        isLiveInteractionActive = false
    }
    
    /**
     * 启动养号模式
     */
    fun startAccountNurturingMode() {
        if (!isAccountNurturingActive) {
            isAccountNurturingActive = true
            // 重置计时器
            resetVideoTimer()
        }
    }
    
    /**
     * 停止养号模式
     */
    fun stopAccountNurturingMode() {
        isAccountNurturingActive = false
    }
    
    /**
     * 启动直播互动模式
     */
    fun startLiveInteractionMode() {
        if (!isLiveInteractionActive) {
            isLiveInteractionActive = true
        }
    }
    
    /**
     * 停止直播互动模式
     */
    fun stopLiveInteractionMode() {
        isLiveInteractionActive = false
    }
    
    /**
     * 增加视频计数
     */
    fun incrementVideoCount() {
        videoViewCount++
    }
    
    /**
     * 增加互动计数
     */
    fun incrementInteractionCount() {
        interactionCount++
    }
}