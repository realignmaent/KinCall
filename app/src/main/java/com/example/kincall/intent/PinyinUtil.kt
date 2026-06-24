package com.example.kincall.intent

/**
 * 文本匹配工具类
 *
 * MVP版本不使用拼音库（TinyPinyin有JitPack问题），直接使用中文字符匹配。
 * 提供三种匹配策略：
 * 1. 子串匹配（substringMatchScore）- 判断短字符串是否包含在长字符串中
 * 2. 编辑距离相似度（levenshteinSimilarity）- 基于Levenshtein距离的模糊匹配
 * 3. 综合相似度（calculateSimilarity）- 结合上述策略的主入口
 */
object PinyinUtil {

    /**
     * 子串匹配分数
     *
     * 判断 shorter 是否与 longer 有较高的匹配度：
     * - 完全相等 → 1.0
     * - shorter 是 longer 的子串，且 longer 长度不超过 shorter 的 1.5 倍 → 0.85
     * - 字符重叠率 ≥ 50% → 0.85
     * - 其他 → 0.0
     *
     * @param shorter 较短的字符串
     * @param longer 较长的字符串
     * @return 匹配分数 0.0 ~ 1.0
     */
    fun substringMatchScore(shorter: String, longer: String): Double {
        // 完全相等
        if (shorter == longer) return 1.0

        // shorter 是 longer 的子串，且 longer 长度不超过 shorter 的 1.5 倍
        if (longer.contains(shorter) && longer.length <= (shorter.length * 1.5)) {
            return 0.85
        }

        // 计算唯一字符的重叠率
        val shorterChars = shorter.toSet()
        val longerChars = longer.toSet()
        val overlapRatio = shorterChars.intersect(longerChars).size.toDouble() / shorterChars.size

        // 重叠率 ≥ 50% 时返回 0.85
        if (overlapRatio >= 0.5) {
            return 0.85
        }

        return 0.0
    }

    /**
     * Levenshtein 编辑距离相似度
     *
     * 计算两个字符串之间的编辑距离，并归一化到 0.0 ~ 1.0 范围。
     * similarity = 1.0 - (distance / maxLength)
     *
     * @param a 字符串a
     * @param b 字符串b
     * @return 相似度 0.0 ~ 1.0
     */
    fun levenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val distance = levenshteinDistance(a, b)
        val maxLength = maxOf(a.length, b.length)

        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * 计算两个字符串的Levenshtein编辑距离
     *
     * 使用动态规划算法，时间复杂度 O(m*n)
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length

        // dp[i][j] 表示 a[0..i-1] 转换到 b[0..j-1] 所需的最少编辑操作数
        val dp = Array(m + 1) { IntArray(n + 1) }

        // 初始化边界条件
        for (i in 0..m) dp[i][0] = i  // a 的前 i 个字符转换为空串需要 i 次删除
        for (j in 0..n) dp[0][j] = j  // 空串转换为 b 的前 j 个字符需要 j 次插入

        // 填充 DP 表
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1  // 字符相同则无需替换
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,       // 删除
                    dp[i][j - 1] + 1,       // 插入
                    dp[i - 1][j - 1] + cost  // 替换
                )
            }
        }

        return dp[m][n]
    }

    /**
     * 综合相似度计算（主入口）
     *
     * 按优先级尝试不同匹配策略：
     * 1. 空字符串 → 0.0
     * 2. 完全相等 → 1.0
     * 3. 一方包含另一方 → substringMatchScore
     * 4. 都不满足 → levenshteinSimilarity
     *
     * @param query 用户输入的查询文本（通常是语音识别结果）
     * @param candidate 候选文本（联系人姓名、关系或别名）
     * @return 相似度分数 0.0 ~ 1.0
     */
    fun calculateSimilarity(query: String, candidate: String): Double {
        // 任一为空返回 0.0
        if (query.isEmpty() || candidate.isEmpty()) return 0.0

        // 完全相等
        if (query == candidate) return 1.0

        // 判断是否一方包含另一方
        val shorter: String
        val longer: String
        if (query.length <= candidate.length) {
            shorter = query
            longer = candidate
        } else {
            shorter = candidate
            longer = query
        }

        // 如果短串包含在长串中，使用子串匹配评分
        if (longer.contains(shorter)) {
            return substringMatchScore(shorter, longer)
        }

        // 其他情况使用 Levenshtein 编辑距离
        return levenshteinSimilarity(query, candidate)
    }
}
