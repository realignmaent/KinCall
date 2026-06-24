package com.example.kincall.asr

/**
 * ASR（语音识别）结果数据类
 *
 * @param text 识别出的文本内容
 * @param isSuccess 是否识别成功
 * @param errorMessage 错误信息（仅在失败时有值）
 * @param latencyMs 识别耗时（毫秒）
 */
data class AsrResult(
    val text: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val latencyMs: Long = 0
)
