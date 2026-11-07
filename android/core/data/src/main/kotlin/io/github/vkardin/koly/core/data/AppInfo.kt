package io.github.vkardin.koly.core.data

/** Facts about the running app, provided by the app module. */
data class AppInfo(
    val versionName: String,
    val versionCode: Int,
)

/** Runs a full sync: data refresh followed by alarms, widgets and change notifications. */
interface SyncTrigger {
    suspend fun syncNow(): RefreshResult

    /** Recomputes alarms and widgets from the cache, without network. */
    suspend fun refreshLocal()
}
