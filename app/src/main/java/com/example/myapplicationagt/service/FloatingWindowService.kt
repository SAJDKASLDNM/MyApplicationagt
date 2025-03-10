package com.example.myapplicationagt.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.example.myapplicationagt.R
import com.example.myapplicationagt.data.preferences.SettingsManager
import com.example.myapplicationagt.service.interaction.LiveInteractionManager
import com.example.myapplicationagt.service.interaction.VideoInteractionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 悬浮窗服务
 * 提供一个悬浮控制面板，显示实时统计和控制按钮
 */
class FloatingWindowService : Service() {
    
    companion object {
        var instance: FloatingWindowService? = null
            private set
    }
    
    // 窗口管理器
    private lateinit var windowManager: WindowManager
    
    // 悬浮窗布局
    private lateinit var floatingView: View
    
    // 统计文本视图
    private lateinit var statTextView: TextView
    
    // 控制按钮
    private lateinit var btnToggleNurturing: Button
    private lateinit var btnToggleLive: Button
    private lateinit var btnSettings: Button
    private lateinit var btnClose: Button
    
    // 窗口参数
    private lateinit var params: WindowManager.LayoutParams
    
    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 养号模式是否活跃
    private var isNurturingActive = false
    
    // 直播互动模式是否活跃
    private var isLiveInteractionActive = false
    
    // 设置管理器
    private lateinit var settingsManager: SettingsManager
    
    // 视频交互管理器
    private lateinit var videoInteractionManager: VideoInteractionManager
    
    // 直播交互管理器
    private lateinit var liveInteractionManager: LiveInteractionManager
    
    // 统计数据
    private var videoCount = 0
    private var interactionCount = 0
    private var liveCount = 0
    private var sessionStartTime = 0L
    
    // 更新统计任务
    private var updateStatsRunnable: Runnable? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化窗口管理器
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 初始化设置管理器
        settingsManager = SettingsManager(this)
        
        // 初始化交互管理器
        videoInteractionManager = VideoInteractionManager(this, settingsManager)
        liveInteractionManager = LiveInteractionManager(this, settingsManager)
        
        // 创建悬浮窗
        createFloatingWindow()
        
        // 开始会话计时
        sessionStartTime = System.currentTimeMillis()
        
        // 开始定期更新统计
        startStatsUpdates()
        
        Toast.makeText(this, "悬浮窗口已启动", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停止统计更新
        stopStatsUpdates()
        
        // 移除悬浮窗
        if (::windowManager.isInitialized && ::floatingView.isInitialized) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        instance = null
    }
    
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    /**
     * 创建悬浮窗
     */
    private fun createFloatingWindow() {
        // 创建布局参数
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        // 设置位置为右上角
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 0
        params.y = 100
        
        // 加载布局
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_window, null)
        
        // 添加触摸监听器，实现拖动
        val rootLayout = floatingView.findViewById<CardView>(R.id.floating_root_layout)
        rootLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
        
        // 获取控件引用
        statTextView = floatingView.findViewById(R.id.text_stats)
        btnToggleNurturing = floatingView.findViewById(R.id.btn_toggle_nurturing)
        btnToggleLive = floatingView.findViewById(R.id.btn_toggle_live)
        btnSettings = floatingView.findViewById(R.id.btn_settings)
        btnClose = floatingView.findViewById(R.id.btn_close)
        
        // 设置按钮点击事件
        btnToggleNurturing.setOnClickListener {
            toggleNurturingMode()
        }
        
        btnToggleLive.setOnClickListener {
            toggleLiveInteractionMode()
        }
        
        btnSettings.setOnClickListener {
            // 打开设置活动
            val settingsIntent = Intent("com.example.myapplicationagt.SETTINGS")
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(settingsIntent)
        }
        
        btnClose.setOnClickListener {
            // 停止服务
            stopSelf()
        }
        
        // 将视图添加到窗口
        windowManager.addView(floatingView, params)
        
        // 更新统计数据
        updateStats()
    }
    
    /**
     * 切换养号模式
     */
    private fun toggleNurturingMode() {
        isNurturingActive = !isNurturingActive
        
        if (isNurturingActive) {
            // 如果启动养号模式，停止直播互动模式
            if (isLiveInteractionActive) {
                isLiveInteractionActive = false
                liveInteractionManager.stopAutoInteraction()
                btnToggleLive.text = "开始直播互动"
            }
            
            // 启动养号模式
            startNurturingMode()
            btnToggleNurturing.text = "停止养号"
            Toast.makeText(this, "已启动养号模式", Toast.LENGTH_SHORT).show()
        } else {
            // 停止养号模式
            stopNurturingMode()
            btnToggleNurturing.text = "开始养号"
            Toast.makeText(this, "已停止养号模式", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 切换直播互动模式
     */
    private fun toggleLiveInteractionMode() {
        isLiveInteractionActive = !isLiveInteractionActive
        
        if (isLiveInteractionActive) {
            // 如果启动直播互动模式，停止养号模式
            if (isNurturingActive) {
                isNurturingActive = false
                stopNurturingMode()
                btnToggleNurturing.text = "开始养号"
            }
            
            // 启动直播互动模式
            startLiveInteractionMode()
            btnToggleLive.text = "停止直播互动"
            Toast.makeText(this, "已启动直播互动模式", Toast.LENGTH_SHORT).show()
        } else {
            // 停止直播互动模式
            stopLiveInteractionMode()
            btnToggleLive.text = "开始直播互动"
            Toast.makeText(this, "已停止直播互动模式", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动养号模式
     */
    private fun startNurturingMode() {
        // 通知无障碍服务启动养号模式
        DouYinAccessibilityService.instance?.startAccountNurturingMode()
        
        // 启动视频交互管理器
        videoInteractionManager.startAutoInteraction()
    }
    
    /**
     * 停止养号模式
     */
    private fun stopNurturingMode() {
        // 通知无障碍服务停止养号模式
        DouYinAccessibilityService.instance?.stopAccountNurturingMode()
        
        // 停止视频交互管理器
        videoInteractionManager.stopAutoInteraction()
    }
    
    /**
     * 启动直播互动模式
     */
    private fun startLiveInteractionMode() {
        // 通知无障碍服务启动直播互动模式
        DouYinAccessibilityService.instance?.startLiveInteractionMode()
        
        // 启动直播交互管理器
        liveInteractionManager.startAutoInteraction()
    }
    
    /**
     * 停止直播互动模式
     */
    private fun stopLiveInteractionMode() {
        // 通知无障碍服务停止直播互动模式
        DouYinAccessibilityService.instance?.stopLiveInteractionMode()
        
        // 停止直播交互管理器
        liveInteractionManager.stopAutoInteraction()
    }
    
    /**
     * 开始定期更新统计
     */
    private fun startStatsUpdates() {
        updateStatsRunnable = object : Runnable {
            override fun run() {
                updateStats()
                mainHandler.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        
        mainHandler.post(updateStatsRunnable!!)
    }
    
    /**
     * 停止定期更新统计
     */
    private fun stopStatsUpdates() {
        updateStatsRunnable?.let { mainHandler.removeCallbacks(it) }
        updateStatsRunnable = null
    }
    
    /**
     * 更新统计数据
     */
    private fun updateStats() {
        val sessionDurationMs = System.currentTimeMillis() - sessionStartTime
        val sessionDurationMinutes = sessionDurationMs / 60000
        val sessionDurationSeconds = (sessionDurationMs % 60000) / 1000
        
        val statsText = """
            运行时间: ${sessionDurationMinutes}分${sessionDurationSeconds}秒
            视频浏览: $videoCount 个
            互动次数: $interactionCount 次
            直播互动: $liveCount 次
        """.trimIndent()
        
        statTextView.text = statsText
    }
    
    /**
     * 增加视频计数
     */
    fun incrementVideoCount() {
        videoCount++
        // 不需要立即更新UI，因为已经有定期更新任务
    }
    
    /**
     * 增加互动计数
     */
    fun incrementInteractionCount() {
        interactionCount++
        // 不需要立即更新UI，因为已经有定期更新任务
    }
    
    /**
     * 增加直播互动计数
     */
    fun incrementLiveCount() {
        liveCount++
        // 不需要立即更新UI，因为已经有定期更新任务
    }
    
    /**
     * 获取当前视频计数
     */
    fun getVideoCount(): Int {
        return videoCount
    }
    
    /**
     * 获取当前互动计数
     */
    fun getInteractionCount(): Int {
        return interactionCount
    }
    
    /**
     * 获取当前直播互动计数
     */
    fun getLiveCount(): Int {
        return liveCount
    }
    
    /**
     * 获取会话运行时间（毫秒）
     */
    fun getSessionDuration(): Long {
        return System.currentTimeMillis() - sessionStartTime
    }
}