package io.github.vkardin.koly.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vkardin.koly.MainActivity
import io.github.vkardin.koly.R
import io.github.vkardin.koly.core.ui.R as UiR
import javax.inject.Inject
import javax.inject.Singleton

enum class Channel(val id: String, val title: Int, val importance: Int) {
    BEFORE_OFF("before_off", UiR.string.channel_before_off, NotificationManager.IMPORTANCE_HIGH),
    BEFORE_ON("before_on", UiR.string.channel_before_on, NotificationManager.IMPORTANCE_DEFAULT),
    CHANGES("schedule_changes", UiR.string.channel_changes, NotificationManager.IMPORTANCE_DEFAULT),
}

@Singleton
class Notifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Channel names follow the app language, so they are refreshed on every start. */
    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        for (ch in Channel.entries) {
            nm.createNotificationChannel(NotificationChannel(ch.id, context.getString(ch.title), ch.importance))
        }
    }

    fun show(channel: Channel, id: Int, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFFC24A.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission was revoked in the meantime.
        }
    }

    fun showTest() = show(Channel.CHANGES, 1, context.getString(UiR.string.app_name), context.getString(UiR.string.n_test_body))
}
