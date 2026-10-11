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
        RomLibraryEntity::class,
        RomGameEntity::class,
    ],
    version = LauncherDatabase.VERSION,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun dao(): LauncherDao
    abstract fun romDao(): RomDao

    companion object {
        const val VERSION = 3
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `rom_library` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `emulatorPackage` TEXT, `launchMode` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `rom_game` (`libraryId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL, `title` TEXT NOT NULL, `modified` INTEGER NOT NULL, `coverUri` TEXT, PRIMARY KEY(`libraryId`, `uri`), FOREIGN KEY(`libraryId`) REFERENCES `rom_library`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): LauncherDatabase =
            Room.databaseBuilder(context.applicationContext, LauncherDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
