package com.example.kincall.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.kincall.data.dao.ContactDao
import com.example.kincall.data.entity.Alias
import com.example.kincall.data.entity.Contact

/**
 * KinCall数据库
 * 包含联系人和别名两个表
 */
@Database(
    entities = [Contact::class, Alias::class],
    version = 1,
    exportSchema = false  // 生产环境建议设为true并配置schema导出
)
abstract class KinCallDatabase : RoomDatabase() {

    /** 获取联系人DAO */
    abstract fun contactDao(): ContactDao

    companion object {
        /** 数据库单例实例 */
        @Volatile
        private var INSTANCE: KinCallDatabase? = null

        /**
         * 获取数据库单例
         * 使用双重检查锁定确保线程安全
         */
        fun getDatabase(context: Context): KinCallDatabase {
            // 如果已有实例，直接返回
            return INSTANCE ?: synchronized(this) {
                // 再次检查，避免多线程重复创建
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KinCallDatabase::class.java,
                    "kincall_database"  // 数据库文件名
                )
                    // 版本升级时的迁移策略，MVP阶段暂不实现
                    // .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()  // 开发阶段允许销毁重建
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
