package com.example.kincall.asr

/**
 * ASR 客户端接口
 *
 * 定义语音识别的标准操作，支持不同厂商的实现（如讯飞、百度等）
 */
interface AsrClient {

    /**
     * 开始录音并执行语音识别
     * @return 识别结果
     */
    suspend fun recognize(): AsrResult

    /**
     * 停止录音
     */
    fun stopRecording()

    /**
     * 释放所有资源（录音器、WebSocket 连接等）
     */
    fun release()
}
