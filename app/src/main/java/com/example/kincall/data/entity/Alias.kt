package com.example.kincall.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 别名实体类
 * 存储联系人的各种称呼别名，用于语音识别匹配
 * 例如：联系人"张三"可以有别名"老大"、"大宝"、"大儿子"等
 */
@Entity(
    tableName = "aliases",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.CASCADE  // 删除联系人时级联删除其所有别名
        )
    ],
    indices = [
        Index(value = ["contactId"]),  // 按联系人ID查询别名
        Index(value = ["pinyin"])       // 按拼音匹配语音识别结果
    ]
)
data class Alias(
    /** 别名ID，自增主键 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联的联系人ID */
    val contactId: Long,

    /** 别名文本（如"老大"、"大宝"、"妈妈"） */
    val text: String,

    /** 别名的拼音，写入时自动生成，用于语音识别匹配 */
    val pinyin: String
)
