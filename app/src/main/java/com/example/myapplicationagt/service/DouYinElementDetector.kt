package com.example.myapplicationagt.service

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 抖音界面元素检测器
 * 负责检测抖音界面上的关键UI元素
 */
class DouYinElementDetector {
    
    companion object {
        private const val TAG = "DouYinElementDetector"
        
        // 抖音界面元素ID前缀
        private const val DOUYIN_ID_PREFIX = "com.ss.android.ugc.aweme:id/"
        
        // 常见的抖音元素ID（部分，实际使用时可能需要更新）
        private const val ID_LIKE_BUTTON = "${DOUYIN_ID_PREFIX}e3t" // 点赞按钮
        private const val ID_COMMENT_BUTTON = "${DOUYIN_ID_PREFIX}d0d" // 评论按钮
        private const val ID_FAVORITE_BUTTON = "${DOUYIN_ID_PREFIX}afz" // 收藏按钮
        private const val ID_SHARE_BUTTON = "${DOUYIN_ID_PREFIX}b19" // 分享按钮
        private const val ID_AUTHOR_AVATAR = "${DOUYIN_ID_PREFIX}bcu" // 作者头像
        private const val ID_VIDEO_DESC = "${DOUYIN_ID_PREFIX}at6" // 视频描述
        private const val ID_FOLLOW_BUTTON = "${DOUYIN_ID_PREFIX}aga" // 关注按钮
        private const val ID_COMMENT_EDIT_TEXT = "${DOUYIN_ID_PREFIX}b9l" // 评论编辑框
        private const val ID_LIVE_COMMENT_EDIT_TEXT = "${DOUYIN_ID_PREFIX}a1_" // 直播评论编辑框
        
        // 直播相关元素ID
        private const val ID_LIVE_LIKE_BUTTON = "${DOUYIN_ID_PREFIX}f2a" // 直播点赞按钮
        private const val ID_LIVE_COMMENT_BUTTON = "${DOUYIN_ID_PREFIX}a1_" // 直播评论按钮
        private const val ID_LIVE_GIFT_BUTTON = "${DOUYIN_ID_PREFIX}afy" // 直播礼物按钮
        private const val ID_LIVE_FOLLOW_BUTTON = "${DOUYIN_ID_PREFIX}age" // 直播关注按钮
        private const val ID_LIVE_USER_COUNT = "${DOUYIN_ID_PREFIX}e5u" // 直播观看人数
        private const val ID_LIVE_DESCRIPTION = "${DOUYIN_ID_PREFIX}title" // 直播标题
        
        // 单例实例
        private var INSTANCE: DouYinElementDetector? = null
        
        fun getInstance(): DouYinElementDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DouYinElementDetector().also { INSTANCE = it }
            }
        }
    }
    
    // 当前已检测到的UI元素状态
    private val _elements = MutableStateFlow(DouYinUIElements())
    val elements: StateFlow<DouYinUIElements> = _elements.asStateFlow()
    
    /**
     * 检测界面元素
     * @param rootNode 根节点
     * @return 是否检测到任何元素
     */
    fun detectElements(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) {
            Log.d(TAG, "Root node is null")
            return false
        }
        
        val detectedElements = DouYinUIElements()
        var elementsDetected = false
        
        try {
            // 检测点赞按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIKE_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.likeButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Like button detected")
                }
            }
            
            // 检测评论按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_COMMENT_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.commentButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Comment button detected")
                }
            }
            
            // 检测收藏按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_FAVORITE_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.favoriteButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Favorite button detected")
                }
            }
            
            // 检测分享按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_SHARE_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.shareButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Share button detected")
                }
            }
            
            // 检测作者头像
            rootNode.findAccessibilityNodeInfosByViewId(ID_AUTHOR_AVATAR)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.authorAvatar = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Author avatar detected")
                }
            }
            
            // 检测视频描述
            rootNode.findAccessibilityNodeInfosByViewId(ID_VIDEO_DESC)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.videoDescription = nodes[0]
                    detectedElements.videoDescriptionText = nodes[0].text?.toString() ?: ""
                    elementsDetected = true
                    Log.d(TAG, "Video description detected: ${detectedElements.videoDescriptionText}")
                }
            }
            
            // 检测关注按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_FOLLOW_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.followButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Follow button detected")
                }
            }
            
            // 检测评论编辑框
            rootNode.findAccessibilityNodeInfosByViewId(ID_COMMENT_EDIT_TEXT)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.commentEditText = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Comment edit text detected")
                }
            }
            
            // 检测直播评论编辑框
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_COMMENT_EDIT_TEXT)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveCommentEditText = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Live comment edit text detected")
                }
            }
            
            // 检测评论区域（通过文本内容）
            rootNode.findAccessibilityNodeInfosByText("写评论...")?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.commentAreaDetected = true
                    elementsDetected = true
                    Log.d(TAG, "Comment area detected")
                }
            }
            
            // 检测直播区域（通过文本内容）
            rootNode.findAccessibilityNodeInfosByText("直播间")?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveRoomDetected = true
                    elementsDetected = true
                    Log.d(TAG, "Live room detected")
                }
            }
            
            // 检测直播点赞按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_LIKE_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveLikeButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Live like button detected")
                }
            }
            
            // 检测直播评论按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_COMMENT_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveCommentButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Live comment button detected")
                }
            }
            
            // 检测直播礼物按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_GIFT_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveGiftButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Live gift button detected")
                }
            }
            
            // 检测直播关注按钮
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_FOLLOW_BUTTON)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveFollowButton = nodes[0]
                    elementsDetected = true
                    Log.d(TAG, "Live follow button detected")
                }
            }
            
            // 检测直播观看人数
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_USER_COUNT)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveUserCount = nodes[0]
                    detectedElements.liveUserCountText = nodes[0].text?.toString() ?: ""
                    elementsDetected = true
                    Log.d(TAG, "Live user count detected: ${detectedElements.liveUserCountText}")
                }
            }
            
            // 检测直播标题
            rootNode.findAccessibilityNodeInfosByViewId(ID_LIVE_DESCRIPTION)?.let { nodes ->
                if (nodes.isNotEmpty()) {
                    detectedElements.liveDescription = nodes[0]
                    detectedElements.liveDescriptionText = nodes[0].text?.toString() ?: ""
                    elementsDetected = true
                    Log.d(TAG, "Live description detected: ${detectedElements.liveDescriptionText}")
                }
            }
            
            if (elementsDetected) {
                // 更新状态
                _elements.value = detectedElements
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting elements", e)
        }
        
        return elementsDetected
    }
    
    /**
     * 清除检测到的元素
     */
    fun clearElements() {
        _elements.value = DouYinUIElements()
    }
    
    /**
     * 获取元素位置
     */
    fun getElementRect(nodeInfo: AccessibilityNodeInfo?): Rect? {
        if (nodeInfo == null) return null
        
        val rect = Rect()
        nodeInfo.getBoundsInScreen(rect)
        return rect
    }
}

/**
 * 抖音界面元素数据类
 * 存储检测到的UI元素信息
 */
class DouYinUIElements {
    // 视频界面元素
    var likeButton: AccessibilityNodeInfo? = null
    var commentButton: AccessibilityNodeInfo? = null
    var favoriteButton: AccessibilityNodeInfo? = null
    var shareButton: AccessibilityNodeInfo? = null
    var authorAvatar: AccessibilityNodeInfo? = null
    var videoDescription: AccessibilityNodeInfo? = null
    var videoDescriptionText: String = ""
    var followButton: AccessibilityNodeInfo? = null
    var commentEditText: AccessibilityNodeInfo? = null
    
    // 直播界面元素
    var liveCommentEditText: AccessibilityNodeInfo? = null
    var liveLikeButton: AccessibilityNodeInfo? = null
    var liveCommentButton: AccessibilityNodeInfo? = null
    var liveGiftButton: AccessibilityNodeInfo? = null
    var liveFollowButton: AccessibilityNodeInfo? = null
    var liveUserCount: AccessibilityNodeInfo? = null
    var liveUserCountText: String = ""
    var liveDescription: AccessibilityNodeInfo? = null
    var liveDescriptionText: String = ""
    
    // 界面类型检测标志
    var commentAreaDetected: Boolean = false
    var liveRoomDetected: Boolean = false
}