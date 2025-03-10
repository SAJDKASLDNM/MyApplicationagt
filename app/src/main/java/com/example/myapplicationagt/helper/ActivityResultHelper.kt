package com.example.myapplicationagt.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplicationagt.service.capture.ScreenCaptureManager

/**
 * ActivityResultHelper
 * 用于处理需要Activity.startActivityForResult()的操作
 * 包括MediaProjection权限请求等
 */
class ActivityResultHelper : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ActivityResultHelper"
        
        // 服务连接状态广播
        const val ACTION_MEDIA_PROJECTION_RESULT = "com.example.myapplicationagt.MEDIA_PROJECTION_RESULT"
        
        // 权限请求结果
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 处理请求类型
        when {
            intent.hasExtra("request_code") -> {
                val requestCode = intent.getIntExtra("request_code", -1)
                handleRequest(requestCode)
            }
            else -> {
                Log.e(TAG, "No request code provided")
                finish()
            }
        }
    }
    
    /**
     * 处理不同类型的请求
     */
    private fun handleRequest(requestCode: Int) {
        when (requestCode) {
            ScreenCaptureManager.REQUEST_MEDIA_PROJECTION -> requestMediaProjection()
            else -> {
                Log.e(TAG, "Unknown request code: $requestCode")
                finish()
            }
        }
    }
    
    /**
     * 请求MediaProjection权限
     */
    private fun requestMediaProjection() {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            startActivityForResult(captureIntent, ScreenCaptureManager.REQUEST_MEDIA_PROJECTION)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting media projection", e)
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            ScreenCaptureManager.REQUEST_MEDIA_PROJECTION -> {
                handleMediaProjectionResult(resultCode, data)
            }
        }
        
        // 完成活动
        finish()
    }
    
    /**
     * 处理MediaProjection权限请求结果
     */
    private fun handleMediaProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Media projection permission denied")
            return
        }
        
        Log.d(TAG, "Media projection permission granted")
        
        // 广播结果给服务
        val resultIntent = Intent(ACTION_MEDIA_PROJECTION_RESULT)
        resultIntent.putExtra(EXTRA_RESULT_CODE, resultCode)
        resultIntent.putExtra(EXTRA_DATA, data)
        sendBroadcast(resultIntent)
    }
}