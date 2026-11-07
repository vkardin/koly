package io.github.vkardin.koly.widget

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.vkardin.koly.core.data.PlaceView
import io.github.vkardin.koly.core.data.ScheduleRepository
import io.github.vkardin.koly.core.data.SettingsRepository
import io.github.vkardin.koly.core.data.buildPlaceView
import io.github.vkardin.koly.core.model.KyivNow
import io.github.vkardin.koly.core.ui.appLanguage

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsRepository
    fun schedules(): ScheduleRepository
}

/** Which place each widget instance shows. */
object WidgetPlaces {
    private const val PREFS = "widget_places"

    fun get(context: Context, appWidgetId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(appWidgetId.toString(), null)

    fun set(context: Context, appWidgetId: Int, placeId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(appWidgetId.toString(), placeId) }

    fun remove(context: Context, appWidgetIds: IntArray) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { appWidgetIds.forEach { remove(it.toString()) } }
}

data class WidgetModel(
    val now: KyivNow,
    val views: List<PlaceView>,
    /** The place chosen for this widget, or the main place. */
    val chosen: PlaceView?,
)

suspend fun loadWidgetModel(context: Context, appWidgetId: Int?): WidgetModel {
    val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val settings = ep.settings().current()
    val snapshot = ep.schedules().current()
    val now = KyivNow.now()
    val lang = context.appLanguage()
    val views = settings.places.map { buildPlaceView(it, snapshot, now, lang) }
    val wanted = appWidgetId?.let { WidgetPlaces.get(context, it) }
    val chosen = views.firstOrNull { it.place.id == wanted } ?: views.firstOrNull { it.place.id == settings.mainPlace?.id }
    return WidgetModel(now, views, chosen)
}
