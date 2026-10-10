package dev.pluto.launcher.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        HomeWidgetEntity::class,
    ],
    version = LauncherDatabase.VERSION,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun dao(): LauncherDao

    companion object {
        const val VERSION = 2
        const val NAME = "launcher.db"

        /**
         * Every schema change must add a hand-written [Migration] here and a test in
         * `androidTest/.../MigrationTest`. Destructive fallback is deliberately NOT
         * enabled: losing a user's organisation silently is worse than failing loudly.
         */
        /** 2: widgets on the home grid. Existing rows are untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `home_widget` (`appWidgetId` INTEGER NOT NULL, " +
                        "`provider` TEXT NOT NULL, `rows` INTEGER NOT NULL, PRIMARY KEY(`appWidgetId`))",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): LauncherDatabase =
            Room.databaseBuilder(context.applicationContext, LauncherDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
