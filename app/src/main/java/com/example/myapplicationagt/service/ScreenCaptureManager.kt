package com.example.myapplicationagt.service

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 屏幕捕获管理器
 * 负责处理媒体投影和屏幕捕获功能
 */
class ScreenCaptureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_MEDIA_PROJECTION = 1001
        
        // 单例实例
        @Volatile
        private var INSTANCE: ScreenCaptureManager? = null
        
        fun getInstance(context: Context): ScreenCaptureManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCaptureManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // 媒体投影管理器
    private val projectionManager: MediaProjectionManager = 
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    
    // 媒体投影
    private var mediaProjection: MediaProjection? = null
    
    // 虚拟显示
    private var virtualDisplay: VirtualDisplay? = null
    
    // 图像读取器
    private var imageReader: ImageReader? = null
    
    // 屏幕宽高
    private val screenWidth: Int
    private val screenHeight: Int
    private val screenDensity: Int
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 初始化屏幕参数
    init {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        Log.d(TAG, "Screen size: $screenWidth x $screenHeight, density: $screenDensity")
    }
    
    /**
     * 请求媒体投影权限
     * 
     * 在Activity中调用此方法，并在onActivityResult中调用handleActivityResult
     */
    fun requestMediaProjection(activity: Activity) {
        val intent = projectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }
    
    /**
     * 处理Activity结果
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_MEDIA_PROJECTION) return false
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            Log.d(TAG, "Media projection obtained successfully")
            return true
        }
        
        Log.e(TAG, "Failed to get media projection, resultCode: $resultCode")
        return false
    }
    
    /**
     * 检查是否已获取媒体投影权限
     */
    fun hasMediaProjection(): Boolean {
        return mediaProjection != null
    }
    
    /**
     * 创建图像读取器
     */
    @SuppressLint("WrongConstant")
    private fun createImageReaderIfNeeded() {
        if (imageReader == null) {
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            Log.d(TAG, "ImageReader created")
        }
    }
    
    /**
     * 创建虚拟显示
     */
    private fun createVirtualDisplayIfNeeded() {
        if (virtualDisplay == null && mediaProjection != null && imageReader != null) {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )
            Log.d(TAG, "VirtualDisplay created")
        }
    }
    
    /**
     * 捕获屏幕图像
     */
    suspend fun captureScreen(): Bitmap = suspendCancellableCoroutine { continuation ->
        try {
            if (mediaProjection == null) {
                continuation.resumeWithException(IllegalStateException("Media projection not ready"))
                return@suspendCancellableCoroutine
            }
            
            createImageReaderIfNeeded()
            createVirtualDisplayIfNeeded()
            
            val imageReader = this.imageReader ?: throw IllegalStateException("ImageReader is null")
            
            // 设置监听器
            val listener = ImageReader.OnImageAvailableListener { reader ->
                var image: Image? = null
                var bitmap: Bitmap? = null
                
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * screenWidth
                        
                        // 创建Bitmap
                        bitmap = Bitmap.createBitmap(
                            screenWidth + rowPadding / pixelStride,
                            screenHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap?.copyPixelsFromBuffer(buffer)
                        
                        // 裁剪到屏幕大小
                        val croppedBitmap = Bitmap.createBitmap(
                            bitmap!!, 0, 0, screenWidth, screenHeight
                        )
                        
                        continuation.resume(croppedBitmap)
                    } else {
                        continuation.resumeWithException(IllegalStateException("Failed to acquire image"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing screen", e)
                    continuation.resumeWithException(e)
                } finally {
                    image?.close()
                    bitmap?.recycle()
                }
            }
            
            imageReader.setOnImageAvailableListener(listener, mainHandler)
            
            continuation.invokeOnCancellation {
                imageReader.setOnImageAvailableListener(null, null)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up screen capture", e)
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * 释放资源
     */
    fun destroy() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            Log.d(TAG, "Resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
}