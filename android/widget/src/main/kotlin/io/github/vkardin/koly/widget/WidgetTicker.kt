package io.github.vkardin.koly.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Refreshes widgets at the start of every minute so the countdown stays right.
 * The alarm is not a wake-up alarm: while the screen is off nothing runs, and the
 * pending tick is delivered as soon as the device wakes up.
 */
object WidgetTicker {
    private val receivers = listOf(
        CardsSmallReceiver::class.java, CardsMediumReceiver::class.java, CardsLargeReceiver::class.java,
        RingReceiver::class.java, PillReceiver::class.java, TextReceiver::class.java,
    )

    fun hasWidgets(context: Context): Boolean {
        val m = AppWidgetManager.getInstance(context)
        return receivers.any { m.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    private fun intent(context: Context) = PendingIntent.getBroadcast(
        context,
        7,
        Intent(context, WidgetTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (!hasWidgets(context)) {
            am.cancel(intent(context))
            return
        }
        val now = System.currentTimeMillis()
        val next = now - now % 60_000 + 60_000 + 500
        am.set(AlarmManager.RTC, next, intent(context))
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                KolyWidgets.updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
