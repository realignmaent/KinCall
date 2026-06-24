package com.example.kincall.llm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 小米MiMo大模型客户端
 * 使用OpenAI兼容的API接口
 *
 * @param apiKey API密钥
 * @param baseUrl API基础地址，默认为小米MiMo的地址
 * @param model 模型名称，默认为 mimo-v2.5-pro
 */
class MiMoClient(
    private val apiKey: String,
    private val baseUrl: String = "https://token-plan-cn.xiaomimimo.com/v1",
    private val model: String = "mimo-v2.5-pro"
) {
    companion object {
        private const val TAG = "MiMoClient"

        /** JSON媒体类型 */
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /** OkHttp客户端，设置较长超时（LLM响应可能较慢） */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Gson实例，用于JSON序列化/反序列化 */
    private val gson = Gson()

    /**
     * 发送聊天请求并获取助手回复
     *
     * @param messages 消息列表，包含系统提示和对话历史
     * @return 助手的回复文本
     * @throws IOException 网络错误
     * @throws IllegalStateException API返回错误
     */
    suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        // 构建请求体
        val requestBody = buildRequestBody(messages)

        // 构建HTTP请求
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        Log.d(TAG, "发送请求到: $url, 模型: $model, 消息数: ${messages.size}")

        // 使用协程包装异步请求
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)

            // 协程取消时取消请求
            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "请求失败: ${e.message}")
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val responseBody = response.body?.string()
                            ?: throw IllegalStateException("响应体为空")

                        Log.d(TAG, "响应状态码: ${response.code}")

                        if (!response.isSuccessful) {
                            Log.e(TAG, "API错误: $responseBody")
                            throw IllegalStateException("API请求失败 (${response.code}): $responseBody")
                        }

                        // 解析响应，提取助手回复
                        val assistantReply = parseResponse(responseBody)

                        Log.d(TAG, "助手回复: $assistantReply")

                        if (continuation.isActive) {
                            continuation.resume(assistantReply)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理响应失败: ${e.message}")
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
    }

    /**
     * 构建请求体JSON
     *
     * 格式：
     * {
     *   "model": "mimo-v2.5-pro",
     *   "messages": [...],
     *   "temperature": 0.7,
     *   "max_tokens": 200
     * }
     */
    private fun buildRequestBody(messages: List<ChatMessage>): JsonObject {
        val messagesArray = gson.toJsonTree(messages).asJsonArray

        return JsonObject().apply {
            addProperty("model", model)
            add("messages", messagesArray)
            addProperty("temperature", 0.7)
            addProperty("max_tokens", 200)
        }
    }

    /**
     * 解析API响应，提取助手回复内容
     *
     * 响应格式：
     * {
     *   "choices": [
     *     {
     *       "message": {
     *         "role": "assistant",
     *         "content": "回复内容"
     *       }
     *     }
     *   ]
     * }
     */
    private fun parseResponse(responseBody: String): String {
        val jsonResponse = JsonParser.parseString(responseBody).asJsonObject

        // 提取 choices 数组
        val choices = jsonResponse.getAsJsonArray("choices")
            ?: throw IllegalStateException("响应中缺少choices字段")

        if (choices.size() == 0) {
            throw IllegalStateException("choices数组为空")
        }

        // 提取第一条选择的message.content
        val firstChoice = choices[0].asJsonObject
        val message = firstChoice.getAsJsonObject("message")
            ?: throw IllegalStateException("响应中缺少message字段")

        return message.get("content")?.asString
            ?: throw IllegalStateException("响应中缺少content字段")
    }

    /**
     * 释放资源
     */
    fun release() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
