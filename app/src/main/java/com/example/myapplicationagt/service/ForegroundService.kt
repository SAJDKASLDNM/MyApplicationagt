package com.example.myapplicationagt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplicationagt.MainActivity
import com.example.myapplicationagt.R
import com.example.myapplicationagt.helper.ActivityResultHelper
import com.example.myapplicationagt.service.capture.ScreenCaptureManager
import com.example.myapplicationagt.service.recognition.ScreenRecognitionManager

/**
 * 前台服务
 * 保持应用在后台运行，同时处理媒体投影数据
 */
class ForegroundService : Service() {
    
    companion object {
        private const val TAG = "ForegroundService"
        
        // 通知渠道ID
        private const val CHANNEL_ID = "foreground_service_channel"
        
        // 通知ID
        private const val NOTIFICATION_ID = 1001
        
        // 服务动作
        const val ACTION_START_SERVICE = "com.example.myapplicationagt.START_FOREGROUND"
        const val ACTION_STOP_SERVICE = "com.example.myapplicationagt.STOP_FOREGROUND"
        const val ACTION_MEDIA_PROJECTION_DATA = "com.example.myapplicationagt.MEDIA_PROJECTION_DATA"
        
        // 媒体投影数据键
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }
    
    // 服务绑定器
    private val binder = LocalBinder()
    
    // 媒体投影相关
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var screenRecognitionManager: ScreenRecognitionManager? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Foreground service created")
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 初始化管理器
        screenCaptureManager = ScreenCaptureManager(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                // 启动前台服务
                startForeground(NOTIFICATION_ID, createNotification())
                Log.d(TAG, "Foreground service started")
            }
            ACTION_STOP_SERVICE -> {
                // 停止服务
                Log.d(TAG, "Stopping foreground service")
                stopForeground(true)
                stopSelf()
            }
            ACTION_MEDIA_PROJECTION_DATA -> {
                // 处理媒体投影数据
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                
                if (resultCode != 0 && data != null) {
                    handleMediaProjectionData(resultCode, data)
                }
            }
            ActivityResultHelper.ACTION_MEDIA_PROJECTION_RESULT -> {
                // 处理权限辅助活动返回的结果
                val resultCode = intent.getIntExtra(ActivityResultHelper.EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(ActivityResultHelper.EXTRA_DATA)
                
                if (resultCode != 0 && data != null) {
                    handleMediaProjectionData(resultCode, data)
                }
            }
        }
        
        // 如果服务被系统杀死，重新创建时重新启动
        return START_STICKY
    }
    
    /**
     * 处理媒体投影数据
     */
    private fun handleMediaProjectionData(resultCode: Int, data: Intent) {
        Log.d(TAG, "Handling media projection data")
        
        try {
            // 设置媒体投影
            screenCaptureManager?.setMediaProjection(resultCode, data)
            
            // 初始化屏幕识别管理器（如果需要）
            if (screenRecognitionManager == null) {
                screenRecognitionManager = ScreenRecognitionManager.getInstance(
                    this,
                    SettingsManager(this)
                )
            }
            
            // 开始屏幕识别
            screenRecognitionManager?.startRecognition()
            
            // 更新通知
            updateNotificationText("正在运行 - 已获取屏幕读取权限")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling media projection data", e)
            updateNotificationText("运行错误 - 无法获取屏幕读取权限")
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "抖音助手服务"
            val descriptionText = "保持抖音助手在后台运行"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        // 创建PendingIntent打开主活动
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建停止服务的Intent
        val stopIntent = Intent(this, ForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // 构建通知
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("抖音助手")
            .setContentText("服务正在运行")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * 更新通知文本
     */
    private fun updateNotificationText(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("抖音助手")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground service destroyed")
        
        // 释放屏幕捕获资源
        screenCaptureManager?.release()
        screenCaptureManager = null
        
        // 停止屏幕识别
        screenRecognitionManager?.stopRecognition()
        screenRecognitionManager = null
    }
}