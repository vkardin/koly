package io.github.vkardin.koly.core.data

import io.github.vkardin.koly.core.data.db.CacheDao
import io.github.vkardin.koly.core.data.db.CachedFileEntity
import io.github.vkardin.koly.core.data.db.DayHistoryEntity
import io.github.vkardin.koly.core.data.db.HistoryDao
import io.github.vkardin.koly.core.data.db.SyncMetaEntity
import io.github.vkardin.koly.core.model.IndexFile
import io.github.vkardin.koly.core.model.KYIV
import io.github.vkardin.koly.core.model.KolyJson
import io.github.vkardin.koly.core.model.PowerState
import io.github.vkardin.koly.core.model.RegionFile
import io.github.vkardin.koly.core.model.buildSegments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class DataSnapshot(
    val index: IndexFile? = null,
    val regions: Map<String, RegionFile> = emptyMap(),
    /** Last successful check against the server. */
    val checkedAt: Instant? = null,
    /** The last refresh attempt failed (no network or server error). */
    val offline: Boolean = false,
)

data class RefreshResult(
    val ok: Boolean,
    /** Region ids whose content changed in this refresh. */
    val changedRegions: Set<String> = emptySet(),
)

@Singleton
class ScheduleRepository @Inject constructor(
    private val api: DataApi,
    private val cache: CacheDao,
    private val history: HistoryDao,
) {
    private val mutex = Mutex()

    val snapshot: Flow<DataSnapshot> = combine(
        cache.observeAll().distinctUntilChanged().map(::decode),
        cache.observeMeta(),
    ) { files, meta ->
        files.copy(
            checkedAt = meta?.checkedAt?.let(Instant::ofEpochMilli),
            offline = meta?.lastAttemptFailed ?: false,
        )
    }.distinctUntilChanged().flowOn(Dispatchers.Default)

    suspend fun current(): DataSnapshot = snapshot.first()

    fun history(regionId: String, group: String, from: LocalDate, to: LocalDate): Flow<List<DayHistoryEntity>> =
        history.observe(regionId, group, from.toString(), to.toString())

    /**
     * Refreshes the index and the given regions. Valid cached data is never
     * replaced with empty or broken responses.
     */
    suspend fun refresh(regionIds: Collection<String>): RefreshResult = mutex.withLock {
        val ids = regionIds.toSet()
        val changed = HashSet<String>()
        val ok = try {
            fetch(INDEX_KEY, "index.json") { body ->
                KolyJson.decodeFromString(IndexFile.serializer(), body).isValid
            }
            var allRegionsOk = true
            for (id in ids) {
                try {
                    val outcome = fetch(regionKey(id), "regions/$id.json") { body ->
                        KolyJson.decodeFromString(RegionFile.serializer(), body).isValidFor(id)
                    }
                    if (outcome == Outcome.CHANGED) changed += id
                    recordHistory(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // One region failing keeps its last valid copy and does not stop the rest.
                    allRegionsOk = false
                }
            }
            cache.deleteExcept(listOf(INDEX_KEY) + ids.map(::regionKey))
            history.deleteBefore(LocalDate.now(KYIV).minusDays(HISTORY_DAYS).toString())
            allRegionsOk || ids.isEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        val previous = cache.meta()
        cache.putMeta(
            SyncMetaEntity(
                checkedAt = if (ok) System.currentTimeMillis() else previous?.checkedAt,
                lastAttemptFailed = !ok,
            ),
        )
        RefreshResult(ok, changed)
    }

    /** Loads one region on demand, for example to show its source link before a place is saved. */
    suspend fun fetchRegion(id: String): RegionFile? {
        current().regions[id]?.let { return it }
        return try {
            when (val r = api.get("regions/$id.json", null, null)) {
                is FetchResult.Ok -> KolyJson.decodeFromString(RegionFile.serializer(), r.body).takeIf { it.isValidFor(id) }
                FetchResult.NotModified -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clear() {
        cache.clearFiles()
        history.clear()
        cache.putMeta(SyncMetaEntity(checkedAt = null, lastAttemptFailed = false))
    }

    private enum class Outcome { CHANGED, SAME }

    private suspend fun fetch(key: String, path: String, validate: (String) -> Boolean): Outcome {
        val cached = cache.get(key)
        val now = System.currentTimeMillis()
        return when (val r = api.get(path, cached?.etag, cached?.lastModified)) {
            FetchResult.NotModified -> {
                if (cached != null) cache.touch(key, now)
                Outcome.SAME
            }
            is FetchResult.Ok -> {
                if (!validate(r.body)) throw IllegalStateException("invalid $path")
                if (cached?.json == r.body) {
                    cache.put(cached.copy(etag = r.etag, lastModified = r.lastModified, receivedAt = now))
                    Outcome.SAME
                } else {
                    cache.put(CachedFileEntity(key, r.body, r.etag, r.lastModified, now))
                    Outcome.CHANGED
                }
            }
        }
    }

    private suspend fun recordHistory(regionId: String) {
        val json = cache.get(regionKey(regionId))?.json ?: return
        val region = KolyJson.decodeFromString(RegionFile.serializer(), json)
        val items = region.days.flatMap { day ->
            day.groups.map { (group, intervals) ->
                val segs = buildSegments(intervals)
                DayHistoryEntity(
                    regionId = regionId,
                    groupName = group,
                    date = day.date,
                    offMinutes = segs.filter { it.state == PowerState.OFF }.sumOf { it.length },
                    maybeMinutes = segs.filter { it.state == PowerState.MAYBE }.sumOf { it.length },
                )
            }
        }
        if (items.isNotEmpty()) history.putAll(items)
    }

    private fun decode(files: List<CachedFileEntity>): DataSnapshot {
        var index: IndexFile? = null
        val regions = HashMap<String, RegionFile>()
        for (f in files) {
            try {
                if (f.key == INDEX_KEY) {
                    index = KolyJson.decodeFromString(IndexFile.serializer(), f.json)
                } else if (f.key.startsWith(REGION_PREFIX)) {
                    regions[f.key.removePrefix(REGION_PREFIX)] = KolyJson.decodeFromString(RegionFile.serializer(), f.json)
                }
            } catch (_: Exception) {
                // A broken cache entry is ignored; the next refresh replaces it.
            }
        }
        return DataSnapshot(index = index, regions = regions)
    }

    companion object {
        private const val INDEX_KEY = "index"
        private const val REGION_PREFIX = "region:"
        private const val HISTORY_DAYS = 30L
        fun regionKey(id: String) = REGION_PREFIX + id
    }
}
