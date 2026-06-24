package com.example.kincall.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 联系人实体类
 * 存储用户手动录入的亲人信息，用于语音识别匹配和TTS播报
 */
@Entity(tableName = "contacts")
data class Contact(
    /** 联系人ID，自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 真实姓名，用于TTS确认播报（如"正在拨打给大儿子张三"） */
    val name: String,

    /** 亲属关系，可选（如"大儿子"、"二闺女"、"老伴"） */
    val relation: String? = null,

    /** 电话号码 */
    val phone: String,

    /** 头像路径，MVP阶段暂不使用 */
    val photoPath: String? = null,

    /** 排序顺序，用于控制联系人列表显示顺序 */
    val sortOrder: Int = 0,

    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 更新时间戳 */
    val updatedAt: Long = System.currentTimeMillis()
)
