package dev.pluto.launcher.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Home favourites grid. [itemId] is [dev.pluto.launcher.model.HomeItem.id]. */
@Entity(tableName = "home_item")
data class HomeItemEntity(
    @PrimaryKey val itemId: String,
    val position: Int,
)

/** Dock slot 0..4. Empty slots have no row. [appKey] is [dev.pluto.launcher.model.AppKey.encode]. */
@Entity(tableName = "dock_slot", indices = [Index(value = ["appKey"], unique = true)])
data class DockSlotEntity(
    @PrimaryKey val slot: Int,
    val appKey: String,
)

@Entity(tableName = "folder")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** An app belongs to at most one folder. */
@Entity(
    tableName = "folder_app",
    foreignKeys = [ForeignKey(
        entity = FolderEntity::class,
        parentColumns = ["id"],
        childColumns = ["folderId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("folderId")],
)
data class FolderAppEntity(
    @PrimaryKey val appKey: String,
    val folderId: Long,
    val position: Int,
)

/** [builtIn] is a [dev.pluto.launcher.model.BuiltInCategory] name, or null for user categories. */
@Entity(tableName = "category", indices = [Index(value = ["builtIn"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val builtIn: String?,
    val position: Int,
)

@Entity(
    tableName = "category_app",
    primaryKeys = ["categoryId", "appKey"],
    foreignKeys = [ForeignKey(
        entity = CategoryEntity::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("appKey")],
)
data class CategoryAppEntity(
    val categoryId: Long,
    val appKey: String,
)

@Entity(tableName = "hidden_app")
data class HiddenAppEntity(
    @PrimaryKey val appKey: String,
)

/** Launches made from this launcher only; not system Recents or playtime. */
@Entity(tableName = "recent_launch", indices = [Index("launchedAt")])
data class RecentLaunchEntity(
    @PrimaryKey val appKey: String,
    val launchedAt: Long,
)

/** A widget on the home grid ([dev.pluto.launcher.model.HomeItem.Widget]): its host id, provider and height. */
@Entity(tableName = "home_widget")
data class HomeWidgetEntity(
    @PrimaryKey val appWidgetId: Int,
    /** The provider's flattened ComponentName. */
    val provider: String,
    /** Height in home grid rows. */
    val rows: Int,
)
