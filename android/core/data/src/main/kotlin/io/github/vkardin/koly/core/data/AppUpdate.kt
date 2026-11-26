package io.github.vkardin.koly.core.data

import kotlinx.coroutines.flow.StateFlow

/** Where an update of the app stands. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val sizeBytes: Long) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState

    /** Android asks the user to allow installing apps from this app first. */
    data class NeedsPermission(val version: String) : UpdateState
    data class Installing(val version: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/** Updates the app from its published releases, for installs that did not come from a store. */
interface AppUpdater {
    val state: StateFlow<UpdateState>

    /** False when a store installed the app: the store keeps it up to date. */
    val supported: Boolean

    /** Looks for a newer version; without [force], at most every few hours. */
    suspend fun check(force: Boolean = false)

    /** Downloads the newer version, checks it and hands it to the system installer. */
    suspend fun install()

    /** The version the user chose to postpone, so it is not offered again on start. */
    fun postpone(version: String)
    fun isPostponed(version: String): Boolean
}
