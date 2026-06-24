package com.example.kincall.speaker

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import android.util.Log
import com.example.kincall.asr.AsrClient
import com.example.kincall.asr.AsrResult
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume

/**
 * 讯飞TTS语音合成模块
 *
 * 使用讯飞TTS WebSocket接口将文字转为语音，通过AudioTrack播放。
 * 专为老人场景优化：语速较慢（0.8倍），使用通话音量流。
 *
 * 设计说明：
 * - OnePlus 9 ColorOS 16 系统TTS不可用，故使用讯飞云端TTS
 * - 音频参数：16kHz采样率，16位PCM，单声道
 * - 使用STREAM_VOICE_CALL音量流，确保通话时也能听到
 *
 * @param context Android上下文，用于获取音频服务
 */
class Speaker(private val context: Context) {

    companion object {
        private const val TAG = "Speaker"

        // 讯飞TTS凭证
        private const val APP_ID = "d356e133"
        private const val API_KEY = "efe7a588756cb2d2631e6b709e538e64"
        private const val API_SECRET = "ODU3NzE1Y2VlZTdhMGM0YWM1MmRmMDNi"

        // 讯飞TTS WebSocket地址
        private const val TTS_HOST = "tts-api.xfyun.cn"
        private const val TTS_PATH = "/v2/tts"

        // 音频参数：16kHz, 16位PCM, 单声道
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // 语速：0.8倍（较慢，适合老人）
        private const val SPEECH_SPEED = 0.8f

        // TTS请求超时（毫秒）
        private const val TTS_TIMEOUT_MS = 30_000L
    }

    /** 音频管理器，用于控制音量流 */
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** OkHttp客户端 */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 当前的AudioTrack实例 */
    private var audioTrack: AudioTrack? = null

    /** 当前的WebSocket连接 */
    private var webSocket: WebSocket? = null

    /** 标记是否正在播放 */
    @Volatile
    private var isSpeaking = false

    /** 标记是否需要停止播放 */
    @Volatile
    private var shouldStop = false

    /**
     * 将文字转为语音并播放（挂起函数，播放完成后返回）
     *
     * 流程：
     * 1. 建立WebSocket连接（带鉴权）
     * 2. 发送TTS请求（包含文本和音频配置）
     * 3. 接收音频数据块，实时播放
     * 4. 等待播放完成
     *
     * @param text 要朗读的文字
     */
    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            Log.w(TAG, "文本为空，跳过播放")
            return@withContext
        }

        Log.d(TAG, "开始TTS: $text")
        isSpeaking = true
        shouldStop = false

        try {
            // 初始化AudioTrack
            initAudioTrack()

            // 构建鉴权URL
            val authUrl = buildAuthUrl()

            // 音频数据队列，用于在WebSocket回调和播放线程之间传递数据
            val audioQueue = LinkedBlockingQueue<ByteArray>()
            // 标记TTS流是否结束
            var ttsCompleted = false
            // 错误信息
            var errorMsg: String? = null

            // 使用suspendCancellableCoroutine等待TTS完成
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder()
                    .url(authUrl)
                    .build()

                webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket连接成功")

                        // 构建TTS请求帧
                        val ttsRequest = buildTtsRequest(text)
                        webSocket.send(ttsRequest.toString())
                        Log.d(TAG, "已发送TTS请求")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val jsonResponse = JsonParser.parseString(text).asJsonObject
                            val code = jsonResponse.get("code")?.asInt

                            if (code != 0) {
                                // 服务端返回错误
                                errorMsg = jsonResponse.get("message")?.asString ?: "未知错误"
                                Log.e(TAG, "TTS错误: code=$code, msg=$errorMsg")
                                audioQueue.clear()
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                                return
                            }

                            // 提取音频数据（base64编码）
                            val data = jsonResponse.getAsJsonObject("data")
                            val audioBase64 = data?.get("audio")?.asString

                            if (audioBase64 != null) {
                                // 解码base64音频数据
                                val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                                audioQueue.offer(audioBytes)
                            }

                            // 检查状态：2表示最后一帧
                            val status = data?.get("status")?.asInt
                            if (status == 2) {
                                Log.d(TAG, "TTS数据接收完成")
                                ttsCompleted = true
                                // 放入空数组作为结束标记
                                audioQueue.offer(ByteArray(0))
                                if (continuation.isActive) {
                                    continuation.resume(Unit)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析TTS响应失败: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket失败: ${t.message}")
                        errorMsg = t.message
                        audioQueue.clear()
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket已关闭: code=$code, reason=$reason")
                        if (!ttsCompleted && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                })

                // 协程取消时关闭WebSocket
                continuation.invokeOnCancellation {
                    webSocket?.close(1000, "取消")
                }
            }

            // 在播放线程中消费音频数据
            if (errorMsg == null) {
                playAudioFromQueue(audioQueue)
            }

        } catch (e: Exception) {
            Log.e(TAG, "TTS播放失败: ${e.message}")
        } finally {
            isSpeaking = false
            cleanupWebSocket()
        }
    }

    /**
     * 说完后立即开始语音识别
     * 典型场景：问完问题后等待老人回答
     *
     * @param prompt 要朗读的提示语
     * @param asrClient 语音识别客户端
     * @return ASR识别结果
     */
    suspend fun speakAndListen(prompt: String, asrClient: AsrClient): AsrResult {
        // 先播放提示语
        speak(prompt)

        // 播放完成后，开始语音识别
        Log.d(TAG, "提示语播放完毕，开始语音识别")
        return asrClient.recognize()
    }

    /**
     * 停止当前播放
     * 用于打断正在进行的语音播放
     */
    fun stop() {
        Log.d(TAG, "停止播放")
        shouldStop = true
        isSpeaking = false

        // 关闭WebSocket
        webSocket?.close(1000, "用户打断")
        webSocket = null

        // 停止AudioTrack
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                }
                track.flush()
            } catch (e: Exception) {
                Log.e(TAG, "停止AudioTrack失败: ${e.message}")
            }
        }
    }

    /**
     * 释放所有资源
     * 在Activity/Service销毁时调用
     */
    fun release() {
        stop()

        audioTrack?.let { track ->
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.e(TAG, "释放AudioTrack失败: ${e.message}")
            }
        }
        audioTrack = null

        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()

        Log.d(TAG, "Speaker资源已释放")
    }

    /**
     * 初始化AudioTrack（流式播放模式）
     *
     * 使用STREAM_VOICE_CALL音量流，确保在通话场景下也能播放
     */
    private fun initAudioTrack() {
        // 如果已有实例，先释放
        audioTrack?.release()

        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        // 使用较新的AudioTrack.Builder构建
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.TRANSFER_MODE_STREAM)
            .build()

        audioTrack?.play()
        Log.d(TAG, "AudioTrack已初始化并开始播放, 缓冲区大小: $bufferSize")
    }

    /**
     * 构建讯飞TTS WebSocket鉴权URL
     *
     * 鉴权流程（与ASR类似，但host和path不同）：
     * 1. 生成GMT格式日期
     * 2. 构造签名原始字符串: "host: tts-api.xfyun.cn\ndate: {date}\nGET /v2/tts HTTP/1.1"
     * 3. 使用HMAC-SHA256计算签名
     * 4. 构造并编码鉴权参数
     * 5. 拼接完整URL
     */
    private fun buildAuthUrl(): String {
        // GMT格式日期
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("GMT")
        val date = dateFormat.format(Date())

        // 签名原始字符串
        val signatureOrigin = "host: $TTS_HOST\ndate: $date\nGET $TTS_PATH HTTP/1.1"

        // HMAC-SHA256签名
        val signature = hmacSha256(API_SECRET, signatureOrigin)
        val signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)

        // 鉴权原始字符串
        val authorizationOrigin =
            "api_key=\"$API_KEY\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"$signatureBase64\""

        // Base64编码
        val authorization = Base64.encodeToString(
            authorizationOrigin.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        // URL编码并拼接
        val encodedAuth = URLEncoder.encode(authorization, "UTF-8")
        val encodedDate = URLEncoder.encode(date, "UTF-8")
        val encodedHost = URLEncoder.encode(TTS_HOST, "UTF-8")

        return "wss://$TTS_HOST$TTS_PATH?authorization=$encodedAuth&date=$encodedDate&host=$encodedHost"
    }

    /**
     * 构建TTS请求JSON
     *
     * 请求格式：
     * {
     *   "common": { "app_id": "xxx" },
     *   "business": {
     *     "aue": "raw",        // PCM原始格式
     *     "auf": "audio/L16;rate=16000",  // 16kHz 16位
     *     "vcn": "xiaoyan",    // 发音人
     *     "tte": "UTF8",       // 文本编码
     *     "speed": 50,         // 语速（对应0.8倍速）
     *     "volume": 50         // 音量
     *   },
     *   "data": {
     *     "status": 2,         // 一次性发送（单帧模式）
     *     "text": "base64编码的文本"
     *   }
     * }
     */
    private fun buildTtsRequest(text: String): JsonObject {
        // 文本需要base64编码
        val textBase64 = Base64.encodeToString(
            text.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        // 语速映射：0.8倍速 -> 讯飞speed值约为50（范围0-100，默认50）
        // 讯飞speed: 0=最慢, 50=正常, 100=最快
        // 0.8倍速对应约30（略慢于正常）
        val speedValue = (SPEECH_SPEED * 50).toInt().coerceIn(0, 100)

        return JsonObject().apply {
            // common: 应用ID
            add("common", JsonObject().apply {
                addProperty("app_id", APP_ID)
            })

            // business: 音频参数配置
            add("business", JsonObject().apply {
                addProperty("aue", "raw")                        // PCM原始格式
                addProperty("auf", "audio/L16;rate=16000")       // 16kHz 16位
                addProperty("vcn", "xiaoyan")                    // 发音人（小燕，温柔女声）
                addProperty("tte", "UTF8")                       // 文本编码
                addProperty("speed", speedValue)                 // 语速
                addProperty("volume", 50)                        // 音量
                addProperty("pitch", 50)                         // 音高
                addProperty("bgs", 0)                            // 无背景音
            })

            // data: 文本数据
            add("data", JsonObject().apply {
                addProperty("status", 2)                         // 2=单帧模式（一次性发送）
                addProperty("text", textBase64)                  // base64编码的文本
            })
        }
    }

    /**
     * 从队列中消费音频数据并播放
     *
     * @param audioQueue 音频数据队列
     */
    private fun playAudioFromQueue(audioQueue: LinkedBlockingQueue<ByteArray>) {
        while (!shouldStop) {
            try {
                // 从队列取数据，带超时
                val audioData = audioQueue.poll(500, TimeUnit.MILLISECONDS)

                if (audioData == null) {
                    // 超时，检查是否还在播放
                    if (audioQueue.isEmpty() && !isSpeaking) {
                        break
                    }
                    continue
                }

                // 空数组表示结束
                if (audioData.isEmpty()) {
                    Log.d(TAG, "收到结束标记，播放完毕")
                    break
                }

                // 写入AudioTrack播放
                audioTrack?.write(audioData, 0, audioData.size)

            } catch (e: InterruptedException) {
                Log.d(TAG, "播放线程被中断")
                break
            } catch (e: Exception) {
                Log.e(TAG, "播放音频数据失败: ${e.message}")
                break
            }
        }

        // 等待AudioTrack播放完缓冲区中的数据
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // 短暂等待，确保最后的数据播放完毕
                    Thread.sleep(100)
                }
            }
        } catch (e: Exception) {
            // 忽略
        }

        Log.d(TAG, "音频播放循环结束")
    }

    /**
     * 清理WebSocket连接
     */
    private fun cleanupWebSocket() {
        try {
            webSocket?.close(1000, "完成")
        } catch (e: Exception) {
            Log.e(TAG, "关闭WebSocket失败: ${e.message}")
        }
        webSocket = null
    }

    /**
     * HMAC-SHA256签名计算
     *
     * @param secret 密钥
     * @param content 待签名内容
     * @return 签名后的字节数组
     */
    private fun hmacSha256(secret: String, content: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(content.toByteArray(Charsets.UTF_8))
    }
}
