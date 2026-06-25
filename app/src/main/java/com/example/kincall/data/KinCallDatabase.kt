package com.example.kincall.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.kincall.data.dao.ChatHistoryDao
import com.example.kincall.data.dao.ContactDao
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.ChatHistory
import com.example.kincall.data.entity.Contact

/**
 * KinCall数据库
 * 包含联系人、别名和聊天记录三个表
 */
@Database(
    entities = [Contact::class, Alias::class, ChatHistory::class],
    version = 2,  // 版本升级，添加聊天记录表
    exportSchema = false
)
abstract class KinCallDatabase : RoomDatabase() {

    /** 获取联系人DAO */
    abstract fun contactDao(): ContactDao

    /** 获取聊天记录DAO */
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        /** 数据库单例实例 */
        @Volatile
        private var INSTANCE: KinCallDatabase? = null

        /**
         * 获取数据库单例
         */
        fun getDatabase(context: Context): KinCallDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KinCallDatabase::class.java,
                    "kincall_database"
                )
                    .fallbackToDestructiveMigration()  // 开发阶段允许销毁重建
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
