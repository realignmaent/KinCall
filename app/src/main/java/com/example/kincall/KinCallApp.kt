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
 * 作为手动 DI（依赖注入）容器，提供全局单例
 */
class KinCallApp : Application() {

    // ==================== 数据层 ====================

    /** Room 数据库单例 */
    val database: KinCallDatabase by lazy {
        KinCallDatabase.getDatabase(this)
    }

    /** 联系人仓库 */
    val contactRepository: ContactRepository by lazy {
        ContactRepository(database.contactDao())
    }

    /** 聊天记录 DAO */
    val chatHistoryDao by lazy {
        database.chatHistoryDao()
    }

    // ==================== 意图匹配 ====================

    val intentMatcher: IntentMatcher by lazy {
        IntentMatcher().also { it.setRepository(contactRepository) }
    }

    // ==================== 语音识别 (ASR) ====================

    val asrClient: AsrClient by lazy {
        XfAsrClient(
            context = this,
            appId = "d356e133",
            apiKey = "efe7a588756cb2d2631e6b709e538e64",
            apiSecret = "ODU3NzE1Y2VlZTdhMGM0YWM1MmRmMDNi"
        )
    }

    // ==================== 语音合成 (TTS) ====================

    val speaker: Speaker by lazy { Speaker(this) }

    // ==================== 大语言模型 (LLM) ====================

    val mimoClient: MiMoClient by lazy {
        MiMoClient(
            apiKey = "tp-ci5eeqxvxxrb7uex6rvnbe4jr4eiinsxlr1mqgfrqr8llczp",
            baseUrl = "https://token-plan-cn.xiaomimimo.com/v1",
            model = "mimo-v2.5-pro"
        )
    }

    // ==================== 对话管理 ====================

    val conversationManager: ConversationManager by lazy {
        ConversationManager(
            mimoClient = mimoClient,
            asrClient = asrClient,
            speaker = speaker,
            contactRepository = contactRepository,
            intentMatcher = intentMatcher,
            chatHistoryDao = chatHistoryDao
        )
    }

    override fun onCreate() {
        super.onCreate()
    }
}
