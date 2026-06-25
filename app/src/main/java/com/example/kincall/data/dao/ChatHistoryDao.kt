package com.example.kincall.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.kincall.data.entity.ChatHistory
import kotlinx.coroutines.flow.Flow

/**
 * 聊天记录数据访问对象
 */
@Dao
interface ChatHistoryDao {

    /**
     * 获取所有聊天记录（按时间倒序）
     */
    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ChatHistory>>

    /**
     * 获取最近N条记录
     */
    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<ChatHistory>>

    /**
     * 插入一条聊天记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ChatHistory): Long

    /**
     * 清空所有聊天记录
     */
    @Query("DELETE FROM chat_history")
    suspend fun deleteAll()

    /**
     * 获取记录总数
     */
    @Query("SELECT COUNT(*) FROM chat_history")
    suspend fun getCount(): Int
}
