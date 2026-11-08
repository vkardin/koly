package io.github.vkardin.koly.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.vkardin.koly.sync.SyncManager
import io.github.vkardin.koly.sync.SyncWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** The single upcoming alarm: a warning is due or some place changes state. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var sync: SyncManager

    override fun onReceive(context: Context, intent: Intent) {
        runAsync { sync.onAlarm() }
    }
}

/** Reboot, app update, clock or time zone change: plan everything again. */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {
    @Inject lateinit var sync: SyncManager

    override fun onReceive(context: Context, intent: Intent) {
        SyncWork.schedulePeriodic(context)
        runAsync { sync.refreshLocal() }
    }
}
