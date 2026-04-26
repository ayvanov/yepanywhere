package com.yepanywhere.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProjectEntity::class,
        SessionEntity::class,
        TimelineEntity::class,
        TimelineMessageEntity::class,
        InboxItemEntity::class,
        PendingRequestEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
internal abstract class SessionCacheDatabase : RoomDatabase() {
    abstract fun sessionCacheDao(): SessionCacheDao

    companion object {
        private const val DATABASE_NAME = "supervisor_session_cache.db"

        fun open(context: Context): SessionCacheDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SessionCacheDatabase::class.java,
                DATABASE_NAME,
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
