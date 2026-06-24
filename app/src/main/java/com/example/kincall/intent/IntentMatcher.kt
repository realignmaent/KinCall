package com.example.kincall.intent

import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact
import com.example.kincall.data.repository.ContactRepository

/**
 * 意图匹配器
 *
 * 核心匹配算法，从用户语音文本中识别通话意图并匹配联系人。
 *
 * 匹配流程（6步）：
 * 1. 预处理：去除标点符号、空格
 * 2. 意图提取：必须包含"打"或"给"（通话意图）
 * 3. 目标提取：去除前缀和后缀，提取联系人称呼
 * 4. 分数计算：对每个联系人的姓名、关系、别名计算最高匹配分
 * 5. 结果筛选：阈值检查、歧义检查
 * 6. 返回结果
 */
class IntentMatcher {

    /**
     * 匹配结果数据类
     *
     * @param contact 匹配到的联系人，未匹配到时为 null
     * @param reason 匹配原因说明（用于调试和日志）
     * @param confidence 置信度 0.0 ~ 1.0
     * @param matchSource 匹配来源：name/relation/alias_text
     */
    data class MatchResult(
        val contact: Contact?,
        val reason: String,
        val confidence: Double,
        val matchSource: String = ""
    )

    companion object {
        /** 最低匹配阈值，低于此分数视为未匹配 */
        private const val MIN_SCORE_THRESHOLD = 0.85

        /** 歧义阈值，前两名分数差距小于此值视为有歧义 */
        private const val AMBIGUITY_THRESHOLD = 0.1

        /** 通话意图关键词 */
        private val CALL_INTENT_KEYWORDS = listOf("打", "给")

        /** 需要去除的前缀列表（按长度降序排列，优先匹配长前缀） */
        private val PREFIXES_TO_REMOVE = listOf(
            "我要打电话给",
            "我想打电话给",
            "帮我打电话给",
            "我要给",
            "我想给",
            "打电话给",
            "打给",
            "帮我打",
            "我要",
            "我想",
            "帮我",
            "给"
        )

        /** 需要去除的后缀列表（按长度降序排列，优先匹配长后缀） */
        private val SUFFIXES_TO_REMOVE = listOf(
            "打个电话",
            "打电话",
            "打个电话吧",
            "打电话吧",
            "一下",
            "电话",
            "吧"
        )
    }

    // ==================== 数据缓存 ====================

    /** 联系人仓库引用（用于 refresh 加载数据） */
    private var contactRepository: ContactRepository? = null

    /** 缓存的联系人列表 */
    private var cachedContacts: List<Contact> = emptyList()

    /** 缓存的别名映射 */
    private var cachedAliasMap: Map<Long, List<Alias>> = emptyMap()

    /**
     * 注入联系人仓库
     * 在 Application 初始化时调用
     *
     * @param repository 联系人仓库实例
     */
    fun setRepository(repository: ContactRepository) {
        contactRepository = repository
    }

    /**
     * 刷新缓存数据
     * 从仓库重新加载联系人和别名数据
     * 通讯录变更后需要调用此方法
     */
    suspend fun refresh() {
        contactRepository?.let { repo ->
            val (contacts, aliasMap) = repo.getMatchData()
            cachedContacts = contacts
            cachedAliasMap = aliasMap
        }
    }

    /**
     * 快速匹配联系人（兼容 ConversationManager 的调用方式）
     *
     * 使用缓存数据，调用 match() 方法进行匹配。
     * 如果缓存为空，先尝试刷新。
     *
     * @param text 用户语音文本
     * @return 匹配到的联系人，未匹配返回 null
     */
    suspend fun matchContact(text: String): Contact? {
        // 确保缓存有数据
        if (cachedContacts.isEmpty()) {
            refresh()
        }
        val result = match(text, cachedContacts, cachedAliasMap)
        return result.contact
    }

    /**
     * 执行意图匹配
     *
     * @param userText 用户语音识别出的文本
     * @param contacts 联系人列表
     * @param aliasMap 联系人ID → 别名列表 的映射
     * @return 匹配结果，包含联系人、置信度和匹配来源
     */
    fun match(
        userText: String,
        contacts: List<Contact>,
        aliasMap: Map<Long, List<Alias>>
    ): MatchResult {
        // ========== 第1步：预处理 ==========
        val cleaned = preprocess(userText)
        if (cleaned.isEmpty()) {
            return MatchResult(null, "输入为空", 0.0)
        }

        // ========== 第2步：提取意图 ==========
        // 必须包含"打"或"给"才算有通话意图
        val hasCallIntent = CALL_INTENT_KEYWORDS.any { cleaned.contains(it) }
        if (!hasCallIntent) {
            return MatchResult(null, "未检测到通话意图", 0.0)
        }

        // ========== 第3步：提取目标（联系人称呼） ==========
        val target = extractTarget(cleaned)
        if (target.isEmpty()) {
            return MatchResult(null, "未能提取联系人称呼", 0.0)
        }

        // ========== 第4步：计算每个联系人的最佳匹配分数 ==========
        data class ScoredContact(
            val contact: Contact,
            val score: Double,
            val source: String,
            val matchedText: String
        )

        val scoredList = contacts.mapNotNull { contact ->
            // 收集所有候选文本：姓名、关系、别名
            val candidates = mutableListOf<Pair<String, String>>()  // <候选文本, 来源>

            // 姓名匹配
            candidates.add(contact.name to "name")

            // 关系匹配
            contact.relation?.let {
                if (it.isNotEmpty()) {
                    candidates.add(it to "relation")
                }
            }

            // 别名匹配
            val aliases = aliasMap[contact.id] ?: emptyList()
            aliases.forEach { alias ->
                candidates.add(alias.text to "alias_text")
            }

            // 找到最佳匹配
            val bestMatch = candidates.maxByOrNull { (text, _) ->
                PinyinUtil.calculateSimilarity(target, text)
            }

            bestMatch?.let { (text, source) ->
                ScoredContact(
                    contact = contact,
                    score = PinyinUtil.calculateSimilarity(target, text),
                    source = source,
                    matchedText = text
                )
            }
        }

        if (scoredList.isEmpty()) {
            return MatchResult(null, "无联系人可匹配", 0.0)
        }

        // ========== 第5步：排序并筛选 ==========
        val sorted = scoredList.sortedByDescending { it.score }
        val top = sorted[0]

        // 分数低于阈值
        if (top.score < MIN_SCORE_THRESHOLD) {
            return MatchResult(
                null,
                "最高分 ${"%.2f".format(top.score)} 低于阈值 $MIN_SCORE_THRESHOLD",
                top.score
            )
        }

        // 歧义检查：前两名分数差距太小
        if (sorted.size >= 2) {
            val second = sorted[1]
            if (top.score - second.score < AMBIGUITY_THRESHOLD) {
                return MatchResult(
                    null,
                    "有歧义：${top.contact.name}(${top.matchedText}) " +
                            "vs ${second.contact.name}(${second.matchedText})，" +
                            "分差 ${"%.2f".format(top.score - second.score)}",
                    top.score
                )
            }
        }

        // ========== 第6步：返回最佳匹配 ==========
        return MatchResult(
            contact = top.contact,
            reason = "匹配${top.source}「${top.matchedText}」",
            confidence = top.score,
            matchSource = top.source
        )
    }

    /**
     * 预处理：去除标点符号和多余空格
     */
    private fun preprocess(text: String): String {
        // 去除中文和英文标点符号
        val punctuation = "[，。！？、；：\"\"''（）【】《》\\s\\p{Punct}]".toRegex()
        return text.replace(punctuation, "").trim()
    }

    /**
     * 提取目标：去除前缀和后缀，得到联系人称呼
     */
    private fun extractTarget(text: String): String {
        var target = text

        // 去除前缀（按长度降序，优先匹配长前缀）
        for (prefix in PREFIXES_TO_REMOVE) {
            if (target.startsWith(prefix)) {
                target = target.removePrefix(prefix)
                break  // 只去除一个前缀
            }
        }

        // 去除后缀（按长度降序，优先匹配长后缀）
        for (suffix in SUFFIXES_TO_REMOVE) {
            if (target.endsWith(suffix)) {
                target = target.removeSuffix(suffix)
                break  // 只去除一个后缀
            }
        }

        return target.trim()
    }

    /**
     * 判断文本是否包含拨打电话的意图
     *
     * @param text 用户语音文本
     * @return true表示用户想打电话
     */
    fun hasCallIntent(text: String): Boolean {
        val cleaned = preprocess(text)
        return CALL_INTENT_KEYWORDS.any { cleaned.contains(it) }
    }

    /**
     * 判断文本是否表示确认（如"对"、"好"、"嗯"、"是"）
     *
     * @param text 用户语音文本
     * @return true表示确认
     */
    fun isConfirmation(text: String): Boolean {
        val confirmWords = listOf("对", "好", "嗯", "是", "行", "可以", "好的", "对的", "是的", "没错", "对对", "好好")
        val cleaned = preprocess(text)
        return confirmWords.any { cleaned.contains(it) }
    }

    /**
     * 判断文本是否表示否定（如"不对"、"不是"、"不要"）
     *
     * @param text 用户语音文本
     * @return true表示否定
     */
    fun isNegation(text: String): Boolean {
        val negationWords = listOf("不对", "不是", "不要", "不", "没", "没有", "算了", "取消", "不用")
        val cleaned = preprocess(text)
        return negationWords.any { cleaned.startsWith(it) || cleaned.contains(it) }
    }
}
