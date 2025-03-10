package com.example.myapplicationagt.service.capture

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
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 屏幕捕获管理器
 * 负责请求和管理屏幕捕获权限，并提供屏幕截图功能
 */
class ScreenCaptureManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenCaptureManager"
        
        // 请求码
        const val REQUEST_MEDIA_PROJECTION = 1001
        
        // 虚拟显示名称
        private const val VIRTUAL_DISPLAY_NAME = "screen_capture"
        
        // 默认缩放比例
        private const val DEFAULT_SCALE_FACTOR = 0.5f
    }
    
    // MediaProjection 相关
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // 屏幕参数
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    
    // 缩放后的参数
    private var captureWidth = 0
    private var captureHeight = 0
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 是否正在捕获
    private var isCapturing = false
    
    init {
        initScreenParams()
        mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    
    /**
     * 初始化屏幕参数
     */
    private fun initScreenParams() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        
        // 计算缩放后的尺寸
        updateCaptureSize(DEFAULT_SCALE_FACTOR)
        
        Log.d(TAG, "Screen params: $screenWidth x $screenHeight, density: $screenDensity")
    }
    
    /**
     * 更新截图尺寸
     */
    private fun updateCaptureSize(scaleFactor: Float) {
        captureWidth = (screenWidth * scaleFactor).toInt()
        captureHeight = (screenHeight * scaleFactor).toInt()
        Log.d(TAG, "Capture size: $captureWidth x $captureHeight")
    }
    
    /**
     * 设置MediaProjection对象（从权限请求结果中获取）
     */
    fun setMediaProjection(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        Log.d(TAG, "MediaProjection created: ${mediaProjection != null}")
    }
    
    /**
     * 检查是否有投影令牌
     */
    fun hasProjectionToken(): Boolean {
        return mediaProjection != null
    }
    
    /**
     * 开始屏幕捕获
     */
    fun startCapturing() {
        if (isCapturing) {
            Log.d(TAG, "Already capturing")
            return
        }
        
        if (mediaProjection == null) {
            Log.e(TAG, "Cannot start capturing: no media projection token")
            return
        }
        
        try {
            // 创建ImageReader
            imageReader = ImageReader.newInstance(
                captureWidth, 
                captureHeight, 
                PixelFormat.RGBA_8888, 
                2
            )
            
            // 创建虚拟显示
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                captureWidth,
                captureHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                mainHandler
            )
            
            isCapturing = true
            Log.d(TAG, "Screen capturing started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
            stopCapturing()
        }
    }
    
    /**
     * 停止屏幕捕获
     */
    fun stopCapturing() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            
            virtualDisplay = null
            imageReader = null
            isCapturing = false
            
            Log.d(TAG, "Screen capturing stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen capture", e)
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stopCapturing()
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "ScreenCaptureManager released")
    }
    
    /**
     * 捕获当前屏幕图像
     * @return 屏幕截图
     */
    suspend fun captureScreen(): Bitmap? {
        if (!isCapturing || imageReader == null) {
            Log.d(TAG, "Cannot capture: not capturing or no image reader")
            return null
        }
        
        return withContext(Dispatchers.IO) {
            try {
                var image: Image? = null
                try {
                    // 获取最新图像，非阻塞方式
                    image = imageReader?.acquireLatestImage()
                    
                    if (image == null) {
                        Log.d(TAG, "No image available")
                        return@withContext null
                    }
                    
                    // 将Image转换为Bitmap
                    val bitmap = imageToBitmap(image)
                    Log.d(TAG, "Screen captured: ${bitmap.width} x ${bitmap.height}")
                    return@withContext bitmap
                } finally {
                    // 确保Image被关闭
                    image?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screen", e)
                return@withContext null
            }
        }
    }
    
    /**
     * 将Image转换为Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        
        // 获取图像平面
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        
        // 创建Bitmap
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, 
            height, 
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        // 如果有padding，裁剪到正确大小
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }
    
    /**
     * 更新截图比例
     */
    fun updateScaleFactor(scaleFactor: Float) {
        // 检查比例是否有效
        val validScale = when {
            scaleFactor <= 0.1f -> 0.1f
            scaleFactor > 1.0f -> 1.0f
            else -> scaleFactor
        }
        
        // 更新尺寸
        updateCaptureSize(validScale)
        
        // 如果正在捕获，需要重启捕获过程
        if (isCapturing) {
            stopCapturing()
            startCapturing()
        }
    }
    
    /**
     * 获取屏幕宽度
     */
    fun getScreenWidth(): Int = screenWidth
    
    /**
     * 获取屏幕高度
     */
    fun getScreenHeight(): Int = screenHeight
    
    /**
     * 获取当前捕获宽度
     */
    fun getCaptureWidth(): Int = captureWidth
    
    /**
     * 获取当前捕获高度
     */
    fun getCaptureHeight(): Int = captureHeight
}