package io.github.vkardin.koly.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Raw JSON of a published file, as last received from the server. */
@Entity(tableName = "cached_file", primaryKeys = ["key"])
data class CachedFileEntity(
    val key: String,
    val json: String,
    val etag: String?,
    val lastModified: String?,
    val receivedAt: Long,
)

/** Planned minutes without power per region, group and day; kept for statistics. */
@Entity(tableName = "day_history", primaryKeys = ["regionId", "groupName", "date"])
data class DayHistoryEntity(
    val regionId: String,
    val groupName: String,
    val date: String,
    val offMinutes: Int,
    val maybeMinutes: Int,
)

@Entity(tableName = "sync_meta", primaryKeys = ["id"])
data class SyncMetaEntity(
    val id: Int = 0,
    val checkedAt: Long?,
    val lastAttemptFailed: Boolean,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_file")
    fun observeAll(): Flow<List<CachedFileEntity>>

    @Query("SELECT * FROM cached_file WHERE `key` = :key")
    suspend fun get(key: String): CachedFileEntity?

    @Upsert
    suspend fun put(entity: CachedFileEntity)

    @Query("UPDATE cached_file SET receivedAt = :at WHERE `key` = :key")
    suspend fun touch(key: String, at: Long)

    @Query("DELETE FROM cached_file WHERE `key` NOT IN (:keep)")
    suspend fun deleteExcept(keep: List<String>)

    @Query("SELECT * FROM sync_meta WHERE id = 0")
    fun observeMeta(): Flow<SyncMetaEntity?>

    @Query("SELECT * FROM sync_meta WHERE id = 0")
    suspend fun meta(): SyncMetaEntity?

    @Upsert
    suspend fun putMeta(meta: SyncMetaEntity)

    @Query("DELETE FROM cached_file")
    suspend fun clearFiles()
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun putAll(items: List<DayHistoryEntity>)

    @Query(
        "SELECT * FROM day_history WHERE regionId = :regionId AND groupName = :group " +
            "AND date >= :fromDate AND date <= :toDate ORDER BY date",
    )
    fun observe(regionId: String, group: String, fromDate: String, toDate: String): Flow<List<DayHistoryEntity>>

    @Query("DELETE FROM day_history WHERE date < :beforeDate")
    suspend fun deleteBefore(beforeDate: String)

    @Query("DELETE FROM day_history")
    suspend fun clear()
}

@Database(
    entities = [CachedFileEntity::class, DayHistoryEntity::class, SyncMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KolyDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun historyDao(): HistoryDao
}
