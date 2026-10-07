package dev.pluto.launcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        HomeItemEntity::class,
        DockSlotEntity::class,
        FolderEntity::class,
        FolderAppEntity::class,
        CategoryEntity::class,
        CategoryAppEntity::class,
        HiddenAppEntity::class,
        RecentLaunchEntity::class,
    ],
    version = LauncherDatabase.VERSION,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun dao(): LauncherDao

    companion object {
        const val VERSION = 1
        const val NAME = "launcher.db"

        /**
         * Every schema change must add a hand-written [Migration] here and a test in
         * `androidTest/.../MigrationTest`. Destructive fallback is deliberately NOT
         * enabled: losing a user's organisation silently is worse than failing loudly.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): LauncherDatabase =
            Room.databaseBuilder(context.applicationContext, LauncherDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
