package com.example.kincall.llm

/**
 * 聊天消息数据类
 * 对应OpenAI兼容API的消息格式
 *
 * @param role 角色：system（系统提示）、user（用户）、assistant（助手）
 * @param content 消息内容
 */
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        /** 系统提示消息 */
        fun system(content: String) = ChatMessage("system", content)

        /** 用户消息 */
        fun user(content: String) = ChatMessage("user", content)

        /** 助手回复消息 */
        fun assistant(content: String) = ChatMessage("assistant", content)
    }
}
