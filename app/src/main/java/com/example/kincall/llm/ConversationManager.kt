package com.example.kincall.llm

import android.util.Log
import com.example.kincall.asr.AsrClient
import com.example.kincall.asr.AsrResult
import com.example.kincall.data.entity.Contact
import com.example.kincall.data.repository.ContactRepository
import com.example.kincall.intent.IntentMatcher
import com.example.kincall.speaker.Speaker

/**
 * 语音通话对话管理器
 *
 * 管理老人拨打电话的多轮对话流程：
 * 1. 播放问候语，等待老人说出想打给谁
 * 2. 通过ASR识别语音，送入LLM理解意图
 * 3. 从通讯录匹配联系人，用简单中文确认
 * 4. 根据老人回答决定拨打电话或继续询问
 *
 * 设计原则：
 * - 回复简短，用老人能听懂的话
 * - 快速路径：IntentMatcher直接匹配时跳过LLM
 * - 容错：ASR失败或LLM异常时优雅降级
 *
 * @param mimoClient 小米MiMo大模型客户端
 * @param asrClient 语音识别客户端
 * @param speaker 语音合成播放器
 * @param contactRepository 联系人数据仓库
 * @param intentMatcher 意图匹配器（快速路径）
 */
class ConversationManager(
    private val mimoClient: MiMoClient,
    private val asrClient: AsrClient,
    private val speaker: Speaker,
    private val contactRepository: ContactRepository,
    private val intentMatcher: IntentMatcher
) {
    companion object {
        private const val TAG = "ConversationManager"

        /**
         * 系统提示词
         * 告诉LLM它的角色、能力边界和回复风格
         */
        private const val SYSTEM_PROMPT = """你是一个帮助老人打电话的语音助手。你的名字叫小帮。老人会对你说想打给谁，你需要：
1. 理解老人想打给谁
2. 从通讯录中找到匹配的联系人
3. 用简单的中文确认：打给您{关系}{姓名}，对吗？
4. 如果老人说对/好/嗯/是，回复'好的，马上帮您拨打'
5. 如果老人说不对/不是，回复'好的，那您想打给谁呢？'
6. 回复要简短，用老人能听懂的话。不要用标点符号，不要用括号。"""
    }

    /** 当前对话状态 */
    private var state = ConversationState()

    /**
     * 开始新对话
     * 重置状态并返回问候语
     *
     * @return 问候语文本（如"您好，我是小帮，您想打给谁呀？"）
     */
    suspend fun startConversation(): String {
        Log.d(TAG, "开始新对话")

        // 重置对话状态
        state = ConversationState()

        // 刷新意图匹配器数据（确保通讯录是最新的）
        intentMatcher.refresh()

        // 构建初始消息（只有系统提示）
        state = state.copy(
            messages = listOf(ChatMessage.system(SYSTEM_PROMPT))
        )

        // 返回问候语
        return "您好 我是小帮 您想打给谁呀"
    }

    /**
     * 处理已识别的用户文本（跳过ASR，直接处理文本）
     *
     * 供 VoiceCallActivity 使用，它自己完成 ASR 后调用此方法。
     *
     * @param userText 已识别的用户文本
     * @return 对话结果（拨打电话/需要澄清/取消/错误）
     */
    suspend fun processUserText(userText: String): ConversationResult {
        return try {
            Log.d(TAG, "处理用户文本: $userText")

            if (userText.isBlank()) {
                return ConversationResult.Error("没听清 您能再说一遍吗")
            }

            // 检查是否在等待确认状态
            if (state.isWaitingConfirm && state.currentContact != null) {
                return handleConfirmation(userText)
            }

            // 尝试快速路径（IntentMatcher直接匹配）
            val quickMatch = tryQuickMatch(userText)
            if (quickMatch != null) {
                return quickMatch
            }

            // 走LLM理解路径
            return processWithLLM(userText)

        } catch (e: Exception) {
            Log.e(TAG, "处理文本失败: ${e.message}")
            ConversationResult.Error("出了点问题 您能再说一遍吗")
        }
    }

    /**
     * 处理用户语音输入（内部包含ASR识别）
     *
     * 完整流程：
     * 1. 录音并ASR识别
     * 2. 尝试快速路径（IntentMatcher直接匹配）
     * 3. 快速路径失败则走LLM理解
     * 4. 根据LLM回复判断意图，返回结果
     *
     * @return 对话结果（拨打电话/需要澄清/取消/错误）
     */
    suspend fun processUserSpeech(): ConversationResult {
        return try {
            Log.d(TAG, "等待用户语音输入...")

            // 第一步：录音并识别
            val asrResult = asrClient.recognize()

            if (!asrResult.isSuccess) {
                Log.w(TAG, "ASR识别失败: ${asrResult.errorMessage}")
                return ConversationResult.Error("没听清 您能再说一遍吗")
            }

            val userText = asrResult.text.trim()
            Log.d(TAG, "ASR识别结果: $userText")

            if (userText.isBlank()) {
                return ConversationResult.Error("没听清 您能再说一遍吗")
            }

            // 第二步：检查是否在等待确认状态
            if (state.isWaitingConfirm && state.currentContact != null) {
                return handleConfirmation(userText)
            }

            // 第三步：尝试快速路径（IntentMatcher直接匹配）
            val quickMatch = tryQuickMatch(userText)
            if (quickMatch != null) {
                return quickMatch
            }

            // 第四步：走LLM理解路径
            return processWithLLM(userText)

        } catch (e: Exception) {
            Log.e(TAG, "处理语音失败: ${e.message}")
            ConversationResult.Error("出了点问题 您能再说一遍吗")
        }
    }

    /**
     * 快速路径：使用IntentMatcher直接匹配联系人
     *
     * 优势：不调用LLM，响应更快，节省API调用
     * 适用：用户说"打给大儿子"这类明确的指令
     *
     * @param userText 用户语音文本
     * @return 如果快速匹配成功返回ConversationResult，否则null
     */
    private suspend fun tryQuickMatch(userText: String): ConversationResult? {
        // 检查是否包含拨打电话的意图
        if (!intentMatcher.hasCallIntent(userText)) {
            return null
        }

        // 尝试匹配联系人
        val contact = intentMatcher.matchContact(userText) ?: return null

        Log.d(TAG, "快速路径匹配成功: ${contact.name}")

        // 构建确认回复
        val relation = contact.relation ?: ""
        val confirmMsg = "打给您${relation}${contact.name} 对吗"

        // 更新状态：等待用户确认
        state = state.copy(
            currentContact = contact,
            isWaitingConfirm = true,
            messages = state.messages + ChatMessage.user(userText) + ChatMessage.assistant(confirmMsg)
        )

        return ConversationResult.NeedClarification(confirmMsg)
    }

    /**
     * 处理确认状态下的用户回复
     *
     * @param userText 用户的确认/否定回复
     * @return 拨打或重新询问
     */
    private suspend fun handleConfirmation(userText: String): ConversationResult {
        val contact = state.currentContact!!

        when {
            // 用户确认：对/好/嗯/是/行/可以
            intentMatcher.isConfirmation(userText) -> {
                Log.d(TAG, "用户确认拨打电话: ${contact.name}")
                state = state.copy(isWaitingConfirm = false)
                return ConversationResult.Calling(contact)
            }

            // 用户否定：不对/不是/不要/错了
            intentMatcher.isNegation(userText) -> {
                Log.d(TAG, "用户否定，重新询问")
                state = state.copy(
                    currentContact = null,
                    isWaitingConfirm = false,
                    messages = state.messages + ChatMessage.user(userText) +
                            ChatMessage.assistant("好的 那您想打给谁呢")
                )
                return ConversationResult.NeedClarification("好的 那您想打给谁呢")
            }

            // 用户说了新的名字（如"不是，我要打给二女儿"）
            else -> {
                Log.d(TAG, "用户可能说了新的联系人: $userText")
                // 重置确认状态，让processUserSpeech重新处理
                state = state.copy(
                    currentContact = null,
                    isWaitingConfirm = false
                )
                // 尝试从新文本中匹配联系人
                return processWithLLM(userText)
            }
        }
    }

    /**
     * 使用LLM理解用户意图
     *
     * 流程：
     * 1. 将用户输入添加到对话历史
     * 2. 调用MiMo获取回复
     * 3. 分析回复，判断是确认拨打还是需要继续对话
     *
     * @param userText 用户语音文本
     * @return 对话结果
     */
    private suspend fun processWithLLM(userText: String): ConversationResult {
        // 构建通讯录上下文，帮助LLM理解有哪些联系人
        val contactContext = buildContactContext()

        // 将用户消息和通讯录上下文一起发送
        val userMessage = if (contactContext.isNotEmpty()) {
            "$userText\n\n[通讯录信息]\n$contactContext"
        } else {
            userText
        }

        // 更新消息列表
        val updatedMessages = state.messages + ChatMessage.user(userMessage)

        Log.d(TAG, "调用LLM，消息数: ${updatedMessages.size}")

        // 调用MiMo获取回复
        val llmReply = try {
            mimoClient.chat(updatedMessages)
        } catch (e: Exception) {
            Log.e(TAG, "LLM调用失败: ${e.message}")
            return ConversationResult.Error("网络不太好 您能再说一遍吗")
        }

        Log.d(TAG, "LLM回复: $llmReply")

        // 清理LLM回复（去掉可能的标点符号）
        val cleanReply = llmReply.replace("[，。！？、,.!?]".toRegex(), " ").trim()

        // 更新对话状态
        state = state.copy(
            messages = updatedMessages + ChatMessage.assistant(cleanReply)
        )

        // 分析LLM回复，判断意图
        return analyzeLlmReply(cleanReply, userText)
    }

    /**
     * 分析LLM回复，判断用户意图
     *
     * 判断逻辑：
     * 1. 如果回复包含"拨打"关键词 → 尝试从对话上下文找到联系人
     * 2. 如果回复是确认性问题（"对吗"）→ 用IntentMatcher尝试匹配
     * 3. 其他情况 → 作为需要澄清的回复返回
     *
     * @param llmReply LLM的回复文本
     * @param userText 用户原始输入
     * @return 对话结果
     */
    private suspend fun analyzeLlmReply(llmReply: String, userText: String): ConversationResult {
        // 检查是否表示要拨打电话
        val isCallingIntent = llmReply.contains("拨打") || llmReply.contains("打给") && llmReply.contains("好的")

        if (isCallingIntent) {
            // LLM表示要拨打，尝试找到对应的联系人
            val contact = findContactFromContext(userText)
            if (contact != null) {
                Log.d(TAG, "LLM确认拨打: ${contact.name}")
                return ConversationResult.Calling(contact)
            }
        }

        // 如果LLM回复是确认性问题，尝试匹配联系人
        if (llmReply.contains("对吗") || llmReply.contains("对不对")) {
            val contact = intentMatcher.matchContact(userText)
            if (contact != null) {
                state = state.copy(
                    currentContact = contact,
                    isWaitingConfirm = true
                )
                return ConversationResult.NeedClarification(cleanReplyForTts(llmReply))
            }
        }

        // 默认：作为需要继续对话的回复
        return ConversationResult.NeedClarification(cleanReplyForTts(llmReply))
    }

    /**
     * 从用户输入中查找匹配的联系人
     * 结合IntentMatcher和通讯录数据
     *
     * @param userText 用户语音文本
     * @return 匹配的联系人，未找到返回null
     */
    private suspend fun findContactFromContext(userText: String): Contact? {
        // 先用IntentMatcher快速匹配
        val matched = intentMatcher.matchContact(userText)
        if (matched != null) {
            return matched
        }

        // 如果IntentMatcher没匹配到，从通讯录中模糊搜索
        val matchData = contactRepository.getMatchData()
        val contacts = matchData.first
        val aliasMap = matchData.second

        // 遍历联系人，检查姓名或别名是否在用户文本中
        for (contact in contacts) {
            if (userText.contains(contact.name)) {
                return contact
            }
            // 检查别名
            val aliases = aliasMap[contact.id] ?: emptyList()
            for (alias in aliases) {
                if (userText.contains(alias.text)) {
                    return contact
                }
            }
        }

        return null
    }

    /**
     * 构建通讯录上下文信息
     * 帮助LLM了解有哪些联系人可供选择
     *
     * @return 格式化的通讯录文本
     */
    private suspend fun buildContactContext(): String {
        return try {
            val matchData = contactRepository.getMatchData()
            val contacts = matchData.first
            val aliasMap = matchData.second

            if (contacts.isEmpty()) {
                return "通讯录为空"
            }

            val sb = StringBuilder("联系人列表：\n")
            for (contact in contacts) {
                val relation = contact.relation ?: ""
                sb.append("- ${contact.name}")
                if (relation.isNotEmpty()) {
                    sb.append("（$relation）")
                }
                // 添加别名信息
                val aliases = aliasMap[contact.id] ?: emptyList()
                if (aliases.isNotEmpty()) {
                    sb.append("，别名：${aliases.joinToString("、") { it.text }}")
                }
                sb.append("\n")
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "构建通讯录上下文失败: ${e.message}")
            ""
        }
    }

    /**
     * 清理LLM回复文本，使其适合TTS朗读
     * - 去掉标点符号
     * - 去掉括号
     * - 去掉多余空格
     *
     * @param text 原始LLM回复
     * @return 清理后的文本
     */
    private fun cleanReplyForTts(text: String): String {
        return text
            .replace("[，。！？、,.!?]".toRegex(), " ")   // 去掉标点
            .replace("[（）()\\[\\]【】]".toRegex(), "")     // 去掉括号
            .replace("\\s+".toRegex(), " ")                  // 合并空格
            .trim()
    }

    /**
     * 获取当前等待确认的联系人
     * 供外部UI显示确认信息
     */
    fun getPendingContact(): Contact? = state.currentContact

    /**
     * 检查是否正在等待确认
     */
    fun isWaitingForConfirmation(): Boolean = state.isWaitingConfirm

    /**
     * 重置对话状态
     * 用于重新开始对话
     */
    fun reset() {
        state = ConversationState()
        Log.d(TAG, "对话状态已重置")
    }
}

/**
 * 对话状态数据类
 *
 * @param messages 对话消息历史（包含system、user、assistant消息）
 * @param currentContact 当前正在确认的联系人（可null）
 * @param isWaitingConfirm 是否正在等待用户确认
 */
data class ConversationState(
    val messages: List<ChatMessage> = emptyList(),
    val currentContact: Contact? = null,
    val isWaitingConfirm: Boolean = false
)

/**
 * 对话结果密封类
 * 表示对话处理的四种可能结果
 */
sealed class ConversationResult {

    /**
     * 拨打电话
     * 用户确认了联系人，可以拨打电话
     *
     * @param contact 要拨打的联系人
     */
    data class Calling(val contact: Contact) : ConversationResult()

    /**
     * 需要澄清
     * LLM的回复需要用户进一步回答（如确认、选择）
     *
     * @param llmReply LLM的回复文本，需要TTS播放给用户
     */
    data class NeedClarification(val llmReply: String) : ConversationResult()

    /**
     * 取消
     * 用户不想打电话了
     */
    data object Cancelled : ConversationResult()

    /**
     * 错误
     * ASR失败、网络异常等
     *
     * @param message 错误信息，需要TTS播放给用户
     */
    data class Error(val message: String) : ConversationResult()
}
