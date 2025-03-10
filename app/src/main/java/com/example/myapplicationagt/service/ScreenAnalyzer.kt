package com.example.myapplicationagt.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 屏幕内容分析器
 * 负责捕获屏幕内容并进行分析，提取文本信息
 */
class ScreenAnalyzer(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenAnalyzer"
        
        // 视频底部区域文本分析区域比例(相对于屏幕高度)
        private const val BOTTOM_TEXT_AREA_START_RATIO = 0.7f
        private const val BOTTOM_TEXT_AREA_END_RATIO = 0.9f
        
        // 视频右侧互动按钮区域比例(相对于屏幕宽度)
        private const val RIGHT_BUTTONS_AREA_START_RATIO = 0.85f
        private const val RIGHT_BUTTONS_AREA_END_RATIO = 1.0f
    }
    
    // 文本识别器
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    // 窗口管理器
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    // 屏幕宽高
    private val display: Display = windowManager.defaultDisplay
    private val screenWidth: Int
    private val screenHeight: Int
    
    // 图像读取器
    private var imageReader: ImageReader? = null
    
    // 主线程Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // 屏幕捕获管理器
    private val screenCaptureManager = ScreenCaptureManager.getInstance(context)
    
    // 初始化屏幕宽高
    init {
        val metrics = context.resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        
        Log.d(TAG, "Screen size: $screenWidth x $screenHeight")
    }
    
    /**
     * 获取视频底部文本区域
     */
    private fun getBottomTextArea(): Rect {
        val startY = (screenHeight * BOTTOM_TEXT_AREA_START_RATIO).toInt()
        val endY = (screenHeight * BOTTOM_TEXT_AREA_END_RATIO).toInt()
        
        return Rect(0, startY, screenWidth, endY)
    }
    
    /**
     * 获取视频右侧互动按钮区域
     */
    private fun getRightButtonsArea(): Rect {
        val startX = (screenWidth * RIGHT_BUTTONS_AREA_START_RATIO).toInt()
        
        return Rect(startX, 0, screenWidth, screenHeight)
    }
    
    /**
     * 创建图像读取器
     */
    @SuppressLint("WrongConstant")
    private fun createImageReader() {
        imageReader?.close()
        
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )
    }
    
    /**
     * 分析视频底部文本
     */
    suspend fun analyzeBottomText(): String? {
        if (!screenCaptureManager.hasMediaProjection()) {
            Log.e(TAG, "Media projection not ready")
            return null
        }
        
        try {
            // 捕获屏幕图像
            val screenBitmap = screenCaptureManager.captureScreen()
            
            // 获取底部区域
            val bottomRect = getBottomTextArea()
            val bottomAreaBitmap = Bitmap.createBitmap(
                screenBitmap,
                bottomRect.left,
                bottomRect.top,
                bottomRect.width(),
                bottomRect.height()
            )
            
            // 分析文本
            val result = recognizeText(bottomAreaBitmap)
            
            // 释放资源
            bottomAreaBitmap.recycle()
            screenBitmap.recycle()
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing bottom text", e)
            return null
        }
    }
    
    /**
     * 流式获取视频底部文本
     */
    fun bottomTextFlow(): Flow<String> = callbackFlow {
        if (!screenCaptureManager.hasMediaProjection()) {
            close()
            return@callbackFlow
        }
        
        val interval = 1000L // 每秒分析一次
        
        val runnable = object : Runnable {
            override fun run() {
                try {
                    // 使用协程库的launch会更好，但为简单起见，这里使用Thread
                    Thread {
                        try {
                            val screenBitmap = screenCaptureManager.captureScreen()
                            val bottomRect = getBottomTextArea()
                            val bottomAreaBitmap = Bitmap.createBitmap(
                                screenBitmap,
                                bottomRect.left,
                                bottomRect.top,
                                bottomRect.width(),
                                bottomRect.height()
                            )
                            
                            recognizeText(bottomAreaBitmap)?.let { text ->
                                if (text.isNotEmpty()) {
                                    trySend(text)
                                }
                            }
                            
                            bottomAreaBitmap.recycle()
                            screenBitmap.recycle()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in bottomTextFlow", e)
                        }
                    }.start()
                } finally {
                    // 定期执行
                    mainHandler.postDelayed(this, interval)
                }
            }
        }
        
        // 开始定期执行
        mainHandler.post(runnable)
        
        // 当Flow被关闭时清理资源
        awaitClose {
            mainHandler.removeCallbacks(runnable)
        }
    }
    
    /**
     * 识别图像中的文本
     */
    private suspend fun recognizeText(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { text ->
                val result = processTextRecognitionResult(text)
                continuation.resume(result)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                continuation.resume(null)
            }
    }
    
    /**
     * 处理文本识别结果
     */
    private fun processTextRecognitionResult(text: Text): String {
        val stringBuilder = StringBuilder()
        
        for (textBlock in text.textBlocks) {
            for (line in textBlock.lines) {
                stringBuilder.append(line.text).append("\n")
            }
        }
        
        return stringBuilder.toString().trim()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        textRecognizer.close()
        imageReader?.close()
        imageReader = null
    }
}