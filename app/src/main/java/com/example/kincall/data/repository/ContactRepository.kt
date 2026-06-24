package com.example.kincall.data.repository

import com.example.kincall.data.dao.ContactDao
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact
import kotlinx.coroutines.flow.Flow

/**
 * 联系人仓库
 * 封装ContactDao，为ViewModel提供数据访问接口
 */
class ContactRepository(private val contactDao: ContactDao) {

    // ==================== 联系人操作 ====================

    /**
     * 获取所有联系人（Flow响应式）
     */
    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    /**
     * 根据ID获取联系人
     */
    suspend fun getContactById(id: Long): Contact? = contactDao.getContactById(id)

    /**
     * 插入联系人，返回新生成的ID
     */
    suspend fun insertContact(contact: Contact): Long = contactDao.insertContact(contact)

    /**
     * 更新联系人
     */
    suspend fun updateContact(contact: Contact) = contactDao.updateContact(contact)

    /**
     * 删除联系人
     */
    suspend fun deleteContact(contact: Contact) = contactDao.deleteContact(contact)

    // ==================== 别名操作 ====================

    /**
     * 获取指定联系人的所有别名
     */
    suspend fun getAliases(contactId: Long): List<Alias> = contactDao.getAliases(contactId)

    /**
     * 插入别名
     */
    suspend fun insertAlias(alias: Alias): Long = contactDao.insertAlias(alias)

    /**
     * 删除别名
     */
    suspend fun deleteAlias(alias: Alias) = contactDao.deleteAlias(alias)

    // ==================== 匹配器数据加载 ====================

    /**
     * 获取匹配器所需的完整数据
     * 返回值：Pair<联系人列表, Map<联系人ID, 别名列表>>
     *
     * 用法示例：
     * val (contacts, aliasMap) = repository.getMatchData()
     * val contactAliases = aliasMap[contactId] ?: emptyList()
     */
    suspend fun getMatchData(): Pair<List<Contact>, Map<Long, List<Alias>>> {
        // 同步加载所有联系人
        val contacts = contactDao.getAllContactsSync()

        // 同步加载所有别名
        val allAliases = contactDao.getAllAliasesSync()

        // 按contactId分组，构建Map结构
        // 这样IntentMatcher可以快速查找每个联系人的所有别名
        val aliasMap = allAliases.groupBy { it.contactId }

        return Pair(contacts, aliasMap)
    }
}
