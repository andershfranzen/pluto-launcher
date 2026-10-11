package dev.pluto.launcher.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rom_library")
data class RomLibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val treeUri: String,
    val emulatorPackage: String?,
    val launchMode: String,
)

@Entity(
    tableName = "rom_game", primaryKeys = ["libraryId", "uri"],
    foreignKeys = [ForeignKey(entity = RomLibraryEntity::class, parentColumns = ["id"], childColumns = ["libraryId"], onDelete = ForeignKey.CASCADE)],
)
data class RomGameEntity(
    val libraryId: Long,
    val uri: String,
    val fileName: String,
    val title: String,
    val modified: Long,
    val coverUri: String?,
)

data class RomLibraryRows(
    @Embedded val library: RomLibraryEntity,
    @Relation(parentColumn = "id", entityColumn = "libraryId") val games: List<RomGameEntity>,
)

@Dao
abstract class RomDao {
    @Transaction @Query("SELECT * FROM rom_library ORDER BY id")
    abstract fun observeLibraries(): Flow<List<RomLibraryRows>>
    @Query("SELECT * FROM rom_library WHERE id = :id")
    abstract suspend fun library(id: Long): RomLibraryEntity?
    @Query("SELECT * FROM rom_game WHERE libraryId = :id")
    abstract suspend fun games(id: Long): List<RomGameEntity>
    @Insert abstract suspend fun insertLibrary(library: RomLibraryEntity): Long
    @Update abstract suspend fun updateLibrary(library: RomLibraryEntity)
    @Query("DELETE FROM rom_library WHERE id = :id") abstract suspend fun deleteLibrary(id: Long)
    @Query("DELETE FROM rom_game WHERE libraryId = :id") abstract suspend fun clearGames(id: Long)
    @Insert abstract suspend fun insertGames(games: List<RomGameEntity>)
    @Transaction
    open suspend fun saveLibrary(row: RomLibraryEntity): Long {
        if (row.id == 0L) return insertLibrary(row)
        val old = library(row.id) ?: error("This ROM shelf no longer exists")
        updateLibrary(row)
        if (old.treeUri != row.treeUri) clearGames(row.id)
        return row.id
    }
    @Transaction
    open suspend fun replaceGames(id: Long, treeUri: String, games: List<RomGameEntity>) {
        if (library(id)?.treeUri != treeUri) return
        require(games.all { it.libraryId == id }) { "ROM games must belong to this shelf" }
        // Compare inside the transaction, after validating the folder: a rescan of an
        // unchanged shelf should neither rewrite its rows nor invalidate Room observers.
        val existing = games(id)
        if (existing.size == games.size) {
            val byUri = HashMap<String, RomGameEntity>(existing.size)
            existing.forEach { byUri[it.uri] = it }
            if (games.all { byUri.remove(it.uri) == it }) return
        }
        clearGames(id)
        insertGames(games)
    }
}
