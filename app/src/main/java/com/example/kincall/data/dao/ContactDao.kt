package com.example.kincall.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact
import kotlinx.coroutines.flow.Flow

/**
 * 联系人数据访问对象
 * 提供联系人和别名的CRUD操作
 */
@Dao
interface ContactDao {

    // ==================== 联系人操作 ====================

    /**
     * 获取所有联系人列表（Flow响应式）
     * 按排序顺序和创建时间排序
     */
    @Query("SELECT * FROM contacts ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllContacts(): Flow<List<Contact>>

    /**
     * 根据ID获取单个联系人
     */
    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): Contact?

    /**
     * 插入联系人，返回新生成的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    /**
     * 更新联系人信息
     */
    @Update
    suspend fun updateContact(contact: Contact)

    /**
     * 删除联系人
     */
    @Delete
    suspend fun deleteContact(contact: Contact)

    // ==================== 别名操作 ====================

    /**
     * 获取指定联系人的所有别名
     */
    @Query("SELECT * FROM aliases WHERE contactId = :contactId")
    suspend fun getAliases(contactId: Long): List<Alias>

    /**
     * 插入别名，返回新生成的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: Alias): Long

    /**
     * 删除别名
     */
    @Delete
    suspend fun deleteAlias(alias: Alias)

    // ==================== 匹配器专用 ====================

    /**
     * 同步获取所有联系人（非Flow版本）
     * 供IntentMatcher在初始化时一次性加载数据
     */
    @Query("SELECT * FROM contacts ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllContactsSync(): List<Contact>

    /**
     * 同步获取所有别名（非Flow版本）
     * 供IntentMatcher在初始化时一次性加载数据
     */
    @Query("SELECT * FROM aliases")
    suspend fun getAllAliasesSync(): List<Alias>
}
