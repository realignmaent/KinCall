package com.example.kincall.asr

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 讯飞语音识别客户端
 *
 * 使用 OkHttp WebSocket 连接讯飞 IAT 接口进行实时语音识别。
 * 录音参数：PCM 16-bit、16kHz、单声道，每帧 1280 字节（40ms）。
 *
 * 静音检测：连续 37 帧（1.5 秒）振幅低于 500 时自动停止。
 * 最大录音时长：8 秒。
 *
 * @param context Android 上下文
 * @param appId 讯飞 App ID
 * @param apiKey 讯飞 API Key
 * @param apiSecret 讯飞 API Secret
 * @param host 讯飞服务域名，不同业务可能使用不同域名
 */
@SuppressLint("MissingPermission")
class XfAsrClient(
    private val context: Context,
    private val appId: String,
    private val apiKey: String,
    private val apiSecret: String,
    private val host: String = "iat-api.xfyun.cn"
) : AsrClient {

    companion object {
        private const val TAG = "XfAsrClient"

        // 音频参数
        private const val SAMPLE_RATE = 16000          // 采样率 16kHz
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO  // 单声道
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT  // 16-bit PCM
        private const val FRAME_SIZE = 1280             // 每帧 1280 字节 = 640 个采样点 = 40ms

        // 静音检测参数
        private const val SILENCE_THRESHOLD = 500       // 振幅阈值
        private const val SILENCE_FRAME_COUNT = 37      // 连续静音帧数（37 × 40ms = 1.48s ≈ 1.5s）

        // 最大录音时长
        private const val MAX_RECORDING_MS = 8000L      // 8 秒

        // WebSocket 连接超时
        private const val WS_CONNECT_TIMEOUT_MS = 10000L
        private const val WS_RESULT_TIMEOUT_MS = 15000L
    }

    private var audioRecord: AudioRecord? = null
    private var webSocket: WebSocket? = null
    private val isRecording = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    // 识别结果
    private var resultText = StringBuilder()
    private var errorMsg: String? = null
    private var isError = false

    // WebSocket 连接完成信号
    private val wsConnected = CountDownLatch(1)
    // 识别完成信号
    private val recognitionDone = CountDownLatch(1)

    /**
     * 执行语音识别
     * 在协程中运行，录音并发送到讯飞进行识别
     *
     * @return 识别结果
     */
    override suspend fun recognize(): AsrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 检查录音权限
        if (!initAudioRecord()) {
            return@withContext AsrResult(
                text = "",
                isSuccess = false,
                errorMessage = "初始化录音器失败，请检查麦克风权限",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // 构建鉴权 URL 并连接 WebSocket
        val url = XfAsrAuth.buildAuthUrl(apiKey, apiSecret, host)
        connectWebSocket(url)

        // 等待 WebSocket 连接建立
        if (!wsConnected.await(WS_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            release()
            return@withContext AsrResult(
                text = "",
                isSuccess = false,
                errorMessage = "WebSocket 连接超时",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // 如果连接出错
        if (isError) {
            release()
            return@withContext AsrResult(
                text = "",
                isSuccess = false,
                errorMessage = errorMsg ?: "WebSocket 连接失败",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }

        // 发送开始帧
        sendStartFrame()

        // 开始录音并发送音频数据
        startRecordingAndSending()

        // 等待识别结果（设置超时）
        val completed = withTimeoutOrNull(WS_RESULT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { continuation ->
                // 在后台线程等待识别完成
                Thread {
                    recognitionDone.await()
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }.start()

                continuation.invokeOnCancellation {
                    recognitionDone.countDown()
                }
            }
        }

        // 如果超时，标记为失败
        val latency = System.currentTimeMillis() - startTime
        release()

        if (completed == null) {
            return@withContext AsrResult(
                text = resultText.toString(),
                isSuccess = false,
                errorMessage = "识别超时",
                latencyMs = latency
            )
        }

        if (isError) {
            return@withContext AsrResult(
                text = resultText.toString(),
                isSuccess = false,
                errorMessage = errorMsg ?: "识别失败",
                latencyMs = latency
            )
        }

        AsrResult(
            text = resultText.toString(),
            isSuccess = true,
            latencyMs = latency
        )
    }

    /**
     * 停止录音
     */
    override fun stopRecording() {
        isStopped.set(true)
        isRecording.set(false)
    }

    /**
     * 释放所有资源
     */
    override fun release() {
        isRecording.set(false)
        isStopped.set(true)

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放 AudioRecord 异常", e)
        }
        audioRecord = null

        try {
            webSocket?.close(1000, "正常关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭 WebSocket 异常", e)
        }
        webSocket = null

        // 确保等待线程不会阻塞
        recognitionDone.countDown()
    }

    /**
     * 初始化 AudioRecord
     * @return 是否初始化成功
     */
    private fun initAudioRecord(): Boolean {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "无法获取最小缓冲区大小")
            return false
        }

        return try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(bufferSize, FRAME_SIZE * 4)  // 确保缓冲区足够大
            )
            audioRecord?.state == AudioRecord.STATE_INITIALIZED
        } catch (e: Exception) {
            Log.e(TAG, "初始化 AudioRecord 失败", e)
            false
        }
    }

    /**
     * 连接 WebSocket
     */
    private fun connectWebSocket(url: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)  // WebSocket 不设读取超时
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 连接成功")
                wsConnected.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "收到识别结果: $text")
                parseResult(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 连接失败", t)
                errorMsg = t.message ?: "WebSocket 连接失败"
                isError = true
                wsConnected.countDown()
                recognitionDone.countDown()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 已关闭: code=$code, reason=$reason")
                recognitionDone.countDown()
            }
        })
    }

    /**
     * 发送开始帧
     * 包含 App ID 和音频格式信息
     */
    private fun sendStartFrame() {
        val data = JSONObject().apply {
            put("common", JSONObject().apply {
                put("app_id", appId)
            })
            put("business", JSONObject().apply {
                put("language", "zh_cn")           // 中文
                put("domain", "iat")               // 听写领域
                put("accent", "mandarin")           // 普通话
                put("vad_eos", 3000)                // 语音结束检测超时 3000ms
                put("dwa", "wpgs")                  // 动态修正
                put("ptt", 0)                       // 不返回标点
                put("nunum", 1)                     // 数字以阿拉伯数字输出
            })
            put("data", JSONObject().apply {
                put("status", 0)                    // 0 = 第一帧
                put("format", "audio/L16;rate=16000")  // 音频格式
                put("encoding", "raw")              // 原始 PCM
            })
        }
        webSocket?.send(data.toString())
    }

    /**
     * 开始录音并持续发送音频帧
     * 在当前线程（IO 线程）中运行
     */
    private fun startRecordingAndSending() {
        audioRecord?.startRecording()
        isRecording.set(true)

        var silenceFrames = 0       // 连续静音帧计数
        var frameCount = 0          // 总帧数
        val buffer = ByteArray(FRAME_SIZE)
        val startTime = System.currentTimeMillis()

        while (isRecording.get()) {
            // 检查是否超过最大录音时长
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed >= MAX_RECORDING_MS) {
                Log.d(TAG, "达到最大录音时长 ${MAX_RECORDING_MS}ms，自动停止")
                break
            }

            // 读取音频数据
            val readSize = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: -1
            if (readSize <= 0) {
                Log.w(TAG, "读取音频数据失败: readSize=$readSize")
                continue
            }

            frameCount++

            // 静音检测：计算本帧的最大振幅
            val maxAmplitude = calculateMaxAmplitude(buffer, readSize)
            if (maxAmplitude < SILENCE_THRESHOLD) {
                silenceFrames++
                if (silenceFrames >= SILENCE_FRAME_COUNT) {
                    Log.d(TAG, "检测到连续静音 $silenceFrames 帧，自动停止录音")
                    break
                }
            } else {
                silenceFrames = 0  // 重置静音计数
            }

            // 发送音频帧（status=1 表示中间帧）
            val audioBase64 = Base64.getEncoder().encodeToString(buffer.copyOf(readSize))
            val frame = JSONObject().apply {
                put("data", JSONObject().apply {
                    put("status", 1)               // 1 = 中间帧
                    put("format", "audio/L16;rate=16000")
                    put("encoding", "raw")
                    put("audio", audioBase64)
                })
            }
            webSocket?.send(frame.toString())
        }

        // 发送结束帧（status=2）
        isRecording.set(false)
        val endFrame = JSONObject().apply {
            put("data", JSONObject().apply {
                put("status", 2)                   // 2 = 最后一帧
                put("format", "audio/L16;rate=16000")
                put("encoding", "raw")
                put("audio", "")                   // 空音频
            })
        }
        webSocket?.send(endFrame.toString())
        Log.d(TAG, "录音结束，共发送 $frameCount 帧")
    }

    /**
     * 计算 PCM 16-bit 音频的最大振幅
     * 用于静音检测
     *
     * @param buffer 音频数据缓冲区
     * @param length 有效数据长度
     * @return 最大振幅（绝对值）
     */
    private fun calculateMaxAmplitude(buffer: ByteArray, length: Int): Int {
        var maxAmplitude = 0
        // 每 2 个字节组成一个 16-bit 采样点
        var i = 0
        while (i < length - 1) {
            // 小端序：低字节在前
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val amplitude = kotlin.math.abs(sample)
            if (amplitude > maxAmplitude) {
                maxAmplitude = amplitude
            }
            i += 2
        }
        return maxAmplitude
    }

    /**
     * 解析讯飞识别结果 JSON
     *
     * 响应格式示例：
     * {
     *   "code": 0,
     *   "data": {
     *     "status": 2,
     *     "result": {
     *       "ws": [
     *         { "cw": [{ "w": "你好" }] }
     *       ],
     *       "pgs": "apd",
     *       "rg": [0, 3]
     *     }
     *   }
     * }
     *
     * @param jsonStr JSON 字符串
     */
    private fun parseResult(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val code = json.optInt("code", -1)

            // 检查返回码
            if (code != 0) {
                val message = json.optString("message", "未知错误")
                Log.e(TAG, "讯飞返回错误: code=$code, message=$message")
                errorMsg = "讯飞错误($code): $message"
                isError = true
                recognitionDone.countDown()
                return
            }

            val data = json.optJSONObject("data") ?: return
            val status = data.optInt("status", 0)

            // 提取识别文本
            val result = data.optJSONObject("result")
            if (result != null) {
                val ws = result.optJSONArray("ws")
                if (ws != null) {
                    val pgs = result.optString("pgs", "")

                    // "rpl" 表示替换模式，需要清空之前的结果
                    if (pgs == "rpl") {
                        val rg = result.optJSONArray("rg")
                        if (rg != null && rg.length() >= 2) {
                            // 替换模式下的处理（简化实现：清空重新拼接）
                            resultText.clear()
                        }
                    }

                    // 拼接本段识别文本
                    val segmentText = extractTextFromWs(ws)
                    resultText.append(segmentText)
                    Log.d(TAG, "当前识别文本: $resultText")
                }
            }

            // status=2 表示最后一帧，识别完成
            if (status == 2) {
                Log.d(TAG, "识别完成，最终文本: $resultText")
                recognitionDone.countDown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析识别结果失败: $jsonStr", e)
            errorMsg = "解析结果失败: ${e.message}"
            isError = true
            recognitionDone.countDown()
        }
    }

    /**
     * 从 ws 数组中提取文本
     * ws[].cw[].w 拼接
     */
    private fun extractTextFromWs(ws: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until ws.length()) {
            val wordGroup = ws.optJSONObject(i) ?: continue
            val cw = wordGroup.optJSONArray("cw") ?: continue
            for (j in 0 until cw.length()) {
                val word = cw.optJSONObject(j) ?: continue
                sb.append(word.optString("w", ""))
            }
        }
        return sb.toString()
    }
}
