package com.example.kincall

import android.app.Application
import com.example.kincall.asr.AsrClient
import com.example.kincall.asr.XfAsrClient
import com.example.kincall.llm.ConversationManager
import com.example.kincall.data.KinCallDatabase
import com.example.kincall.data.repository.ContactRepository
import com.example.kincall.intent.IntentMatcher
import com.example.kincall.llm.MiMoClient
import com.example.kincall.speaker.Speaker

/**
 * KinCall 应用程序类
 *
 * 作为手动 DI（依赖注入）容器，提供全局单例：
 * - Database / Repository（数据层）
 * - IntentMatcher（意图匹配）
 * - AsrClient（语音识别）
 * - Speaker（语音合成）
 * - MiMoClient（大语言模型）
 * - ConversationManager（对话管理）
 *
 * 所有依赖使用 lazy 延迟初始化，避免启动时阻塞。
 */
class KinCallApp : Application() {

    // ==================== 数据层 ====================

    /** Room 数据库单例 */
    val database: KinCallDatabase by lazy {
        KinCallDatabase.getDatabase(this)
    }

    /** 联系人仓库，封装 DAO 操作 */
    val contactRepository: ContactRepository by lazy {
        ContactRepository(database.contactDao())
    }

    // ==================== 意图匹配 ====================

    /** 意图匹配器，用于从语音文本中识别通话意图并匹配联系人 */
    val intentMatcher: IntentMatcher by lazy {
        IntentMatcher().also { it.setRepository(contactRepository) }
    }

    // ==================== 语音识别 (ASR) ====================

    /** 讯飞语音识别客户端 */
    val asrClient: AsrClient by lazy {
        XfAsrClient(
            context = this,
            appId = "d356e133",
            apiKey = "efe7a588756cb2d2631e6b709e538e64",
            apiSecret = "ODU3NzE1Y2VlZTdhMGM0YWM1MmRmMDNi"
        )
    }

    // ==================== 语音合成 (TTS) ====================

    /** 系统 TTS 引擎封装 */
    val speaker: Speaker by lazy { Speaker(this) }

    // ==================== 大语言模型 (LLM) ====================

    /** MiMo 大语言模型客户端 */
    val mimoClient: MiMoClient by lazy {
        MiMoClient(
            apiKey = "tp-ci5eeqxvxxrb7uex6rvnbe4jr4eiinsxlr1mqgfrqr8llczp",
            baseUrl = "https://token-plan-cn.xiaomimimo.com/v1",
            model = "mimo-v2.5-pro"
        )
    }

    // ==================== 对话管理 ====================

    /** 对话管理器，协调 ASR → LLM → 匹配 → TTS 的完整对话流程 */
    val conversationManager: ConversationManager by lazy {
        ConversationManager(
            mimoClient = mimoClient,
            asrClient = asrClient,
            speaker = speaker,
            contactRepository = contactRepository,
            intentMatcher = intentMatcher
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 所有依赖使用 lazy，无需在此初始化
    }
}
