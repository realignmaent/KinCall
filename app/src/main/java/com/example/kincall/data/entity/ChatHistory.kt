package com.example.kincall.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 聊天记录实体类
 * 存储用户与小帮的对话历史
 */
@Entity(tableName = "chat_history")
data class ChatHistory(
    /** 记录ID，自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 对话时间戳 */
    val timestamp: Long = System.currentTimeMillis(),

    /** 用户说了什么 */
    val userText: String = "",

    /** 系统回复了什么 */
    val systemReply: String = "",

    /** 是否成功拨号 */
    val callSuccess: Boolean = false,

    /** 拨打的联系人姓名 */
    val contactName: String? = null,

    /** 拨打的联系人电话 */
    val contactPhone: String? = null,

    /** 对话状态：idle/listening/processing/confirming/dialing/error */
    val state: String = "idle"
)
