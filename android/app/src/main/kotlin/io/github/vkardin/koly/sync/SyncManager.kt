package io.github.vkardin.koly.sync

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.data.RefreshResult
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.SyncTrigger
import io.github.vkardin.koly.core.data.buildPlaceView
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.model.KyivNow
import io.github.vkardin.koly.core.model.formatHm
import io.github.vkardin.koly.core.model.scheduleSignature
import io.github.vkardin.koly.core.ui.appLanguage
import io.github.vkardin.koly.notify.AlarmReceiver
import io.github.vkardin.koly.notify.Channel
import io.github.vkardin.koly.notify.Notice
import io.github.vkardin.koly.notify.NoticeKind
import io.github.vkardin.koly.notify.Notifier
import io.github.vkardin.koly.notify.changeMoments
import io.github.vkardin.koly.notify.isQuietTime
import io.github.vkardin.koly.notify.planNotices
import io.github.vkardin.koly.widget.KolyWidgets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import io.github.vkardin.koly.core.ui.R as UiR

/**
 * Glue between data and the system: refreshes data, posts change notifications,
 * keeps the single upcoming alarm and refreshes widgets.
 */
@Singleton
class SyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val schedules: ScheduleRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) : SyncTrigger {
    private val prefs = context.getSharedPreferences("notify_state", Context.MODE_PRIVATE)
    private val localMutex = Mutex()

    override suspend fun syncNow(): RefreshResult {
        val s = settings.current()
        val result = schedules.refresh(s.places.map { it.regionId })
        if (result.ok) checkChanges(settings.current())
        refreshLocal()
        return result
    }

    override suspend fun refreshLocal() {
        localMutex.withLock {
            val now = KyivNow.now()
            val views = views(settings.current(), now)
            deliverDue(settings.current(), views, now)
            scheduleNextAlarm(settings.current(), views, now)
        }
        KolyWidgets.updateAll(context)
    }

    /** Called by the alarm: shows warnings that are due, then plans the next alarm. */
    suspend fun onAlarm() = refreshLocal()

    private suspend fun views(s: AppSettings, now: KyivNow): List<PlaceView> {
        val snapshot = schedules.current()
        val lang = context.appLanguage()
        return s.places.map { buildPlaceView(it, snapshot, now, lang) }
    }

    private fun deliverDue(s: AppSettings, all: List<PlaceView>, now: KyivNow) {
        val inScope = all.filter { v -> s.notifyPlaces.any { it.id == v.place.id } }
        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().toMutableSet()
        val due = planNotices(inScope, s.notify, now).filter {
            !it.fireAt.isAfter(now.instant.plusSeconds(DUE_SLACK_SECONDS)) && it.key !in sent
        }
        if (due.isEmpty()) return
        for (n in due) {
            sent += n.key
            if (s.notify.quietNight && isQuietTime(now.instant)) continue
            show(n, now)
        }
        // Keep only keys of changes that are still ahead.
        val keep = sent.filter { key ->
            key.split("|").getOrNull(1)?.toLongOrNull()?.let { it > now.instant.epochSecond - 86_400 } ?: false
        }.toSet()
        prefs.edit { putStringSet(KEY_SENT, keep) }
    }

    private fun show(n: Notice, now: KyivNow) {
        val minutes = ((Duration.between(now.instant, n.changeAt).seconds + 59) / 60).toInt().coerceAtLeast(1)
        val at = formatHm(n.changeMinutes)
        val until = n.untilMinutes?.let { formatHm(it) }
        val id = n.key.hashCode()
        when (n.kind) {
            NoticeKind.OFF -> notifier.show(
                Channel.BEFORE_OFF,
                id,
                context.getString(UiR.string.n_off_title, n.placeLabel, minutes),
                if (until != null) context.getString(UiR.string.n_off_body, at, until) else context.getString(UiR.string.n_off_body_open, at),
            )
            NoticeKind.MAYBE -> notifier.show(
                Channel.BEFORE_OFF,
                id,
                context.getString(UiR.string.n_maybe_title, n.placeLabel, minutes),
                context.getString(UiR.string.n_maybe_body, at),
            )
            NoticeKind.ON -> notifier.show(
                Channel.BEFORE_ON,
                id,
                context.getString(UiR.string.n_on_title, n.placeLabel, minutes),
                context.getString(UiR.string.n_on_body, at),
            )
        }
    }

    /** Schedule changes of today and the first publication of tomorrow. */
    private suspend fun checkChanges(s: AppSettings) {
        val now = KyivNow.now()
        val snapshot = schedules.current()
        val quiet = s.notify.quietNight && isQuietTime(now.instant)
        val known = prefs.getStringSet(KEY_SIGNATURES, emptySet()).orEmpty()
            .mapNotNull { e -> e.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap().toMutableMap()
        val seenTomorrow = prefs.getStringSet(KEY_TOMORROW, emptySet()).orEmpty().toMutableSet()
        for (place in s.places) {
            val region = snapshot.regions[place.regionId] ?: continue
            val inScope = s.notifyPlaces.any { it.id == place.id }
            val base = "${place.id}|${place.regionId}|${place.group}"
            val todayKey = "$base|${now.date}"
            val sig = scheduleSignature(region, place.group, now.date)
            val firstTime = known.keys.none { it.startsWith("$base|") }
            if (region.day(now.date.toString()) != null) {
                val prev = known[todayKey]
                known[todayKey] = sig
                if (prev != null && prev != sig && inScope && s.notify.scheduleChanged && !quiet) {
                    notifier.show(
                        Channel.CHANGES,
                        "changed|${place.id}".hashCode(),
                        context.getString(UiR.string.n_changed_title, place.label),
                        context.getString(UiR.string.n_changed_body),
                    )
                }
            }
            val tomorrowKey = "$base|${now.tomorrow}"
            if (region.day(now.tomorrow.toString()) != null && tomorrowKey !in seenTomorrow) {
                seenTomorrow += tomorrowKey
                if (!firstTime && inScope && s.notify.tomorrowPublished && !quiet) {
                    val view = buildPlaceView(place, snapshot, now, context.appLanguage())
                    val count = view.status.outagesTomorrow
                    notifier.show(
                        Channel.CHANGES,
                        "tomorrow|${place.id}".hashCode(),
                        context.getString(UiR.string.n_tomorrow_title, place.label),
                        if (count == 0) {
                            context.getString(UiR.string.n_tomorrow_body_none)
                        } else {
                            context.resources.getQuantityString(UiR.plurals.n_tomorrow_body, count, count)
                        },
                    )
                }
            }
        }
        val recent = setOf(now.date.toString(), now.tomorrow.toString())
        prefs.edit {
            putStringSet(KEY_SIGNATURES, known.filterKeys { k -> recent.any { k.endsWith(it) } }.map { "${it.key}=${it.value}" }.toSet())
            putStringSet(KEY_TOMORROW, seenTomorrow.filter { k -> recent.any { k.endsWith(it) } }.toSet())
        }
    }

    private fun scheduleNextAlarm(s: AppSettings, all: List<PlaceView>, now: KyivNow) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty()
        val inScope = all.filter { v -> s.notifyPlaces.any { it.id == v.place.id } }
        val nextNotice = planNotices(inScope, s.notify, now).firstOrNull { it.key !in sent && it.fireAt.isAfter(now.instant) }?.fireAt
        val nextChange = changeMoments(all, now).firstOrNull()
        val next: Instant? = listOfNotNull(nextNotice, nextChange?.plusSeconds(5)).minOrNull()
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = next.toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private companion object {
        const val ALARM_REQUEST = 100
        const val DUE_SLACK_SECONDS = 60L
        const val KEY_SENT = "sent"
        const val KEY_SIGNATURES = "signatures"
        const val KEY_TOMORROW = "tomorrow_seen"
    }
}
