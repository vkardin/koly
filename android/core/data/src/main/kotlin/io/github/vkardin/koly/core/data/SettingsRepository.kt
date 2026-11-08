package io.github.vkardin.koly.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vkardin.koly.core.model.AppSettings
import io.github.vkardin.koly.core.model.HomeView
import io.github.vkardin.koly.core.model.KolyJson
import io.github.vkardin.koly.core.model.LEAD_MINUTES_OPTIONS
import io.github.vkardin.koly.core.model.MAX_PLACES
import io.github.vkardin.koly.core.model.NotifyScope
import io.github.vkardin.koly.core.model.NotifySettings
import io.github.vkardin.koly.core.model.Place
import io.github.vkardin.koly.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = store.data.map(::read).distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboardingDone() = store.edit { it[ONBOARDING] = true }

    suspend fun setHomeView(view: HomeView) = store.edit { it[HOME_VIEW] = view.name }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setWidgetTheme(mode: ThemeMode) = store.edit { it[WIDGET_THEME] = mode.name }

    suspend fun updateNotify(transform: (NotifySettings) -> NotifySettings) = store.edit { prefs ->
        val n = transform(readNotify(prefs))
        prefs[N_BEFORE_OFF] = n.beforeOff
        prefs[N_BEFORE_ON] = n.beforeOn
        prefs[N_CHANGED] = n.scheduleChanged
        prefs[N_TOMORROW] = n.tomorrowPublished
        prefs[N_LEAD] = n.leadMinutes
        prefs[N_SCOPE] = n.scope.name
        prefs[N_QUIET] = n.quietNight
    }

    /** Adds a place; returns null when the limit of [MAX_PLACES] is reached. */
    suspend fun addPlace(label: String, regionId: String, group: String): Place? {
        var added: Place? = null
        store.edit { prefs ->
            val places = readPlaces(prefs)
            if (places.size >= MAX_PLACES) return@edit
            val place = Place(UUID.randomUUID().toString(), label.trim(), regionId, group)
            writePlaces(prefs, places + place)
            if (prefs[MAIN_PLACE] == null) prefs[MAIN_PLACE] = place.id
            added = place
        }
        return added
    }

    suspend fun updatePlace(place: Place) = store.edit { prefs ->
        writePlaces(prefs, readPlaces(prefs).map { if (it.id == place.id) place.copy(label = place.label.trim()) else it })
    }

    suspend fun removePlace(id: String) = store.edit { prefs ->
        val rest = readPlaces(prefs).filterNot { it.id == id }
        writePlaces(prefs, rest)
        if (prefs[MAIN_PLACE] == id) {
            rest.firstOrNull()?.let { prefs[MAIN_PLACE] = it.id } ?: prefs.remove(MAIN_PLACE)
        }
    }

    suspend fun movePlace(id: String, delta: Int) = store.edit { prefs ->
        val list = readPlaces(prefs).toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@edit
        list.add(to, list.removeAt(from))
        writePlaces(prefs, list)
    }

    suspend fun setMainPlace(id: String) = store.edit { it[MAIN_PLACE] = id }

    suspend fun reset() = store.edit { it.clear() }

    private fun read(prefs: Preferences): AppSettings = AppSettings(
        onboardingDone = prefs[ONBOARDING] ?: false,
        homeView = enumOr(prefs[HOME_VIEW], HomeView.CARD),
        theme = enumOr(prefs[THEME], ThemeMode.SYSTEM),
        dynamicColor = prefs[DYNAMIC_COLOR] ?: false,
        widgetTheme = enumOr(prefs[WIDGET_THEME], ThemeMode.DARK),
        places = readPlaces(prefs),
        mainPlaceId = prefs[MAIN_PLACE],
        notify = readNotify(prefs),
    )

    private fun readNotify(prefs: Preferences): NotifySettings {
        val d = NotifySettings()
        return NotifySettings(
            beforeOff = prefs[N_BEFORE_OFF] ?: d.beforeOff,
            beforeOn = prefs[N_BEFORE_ON] ?: d.beforeOn,
            scheduleChanged = prefs[N_CHANGED] ?: d.scheduleChanged,
            tomorrowPublished = prefs[N_TOMORROW] ?: d.tomorrowPublished,
            leadMinutes = (prefs[N_LEAD] ?: d.leadMinutes).takeIf { it in LEAD_MINUTES_OPTIONS } ?: d.leadMinutes,
            scope = enumOr(prefs[N_SCOPE], d.scope),
            quietNight = prefs[N_QUIET] ?: d.quietNight,
        )
    }

    private fun readPlaces(prefs: Preferences): List<Place> = try {
        prefs[PLACES]?.let { KolyJson.decodeFromString(PLACE_LIST, it) }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun writePlaces(prefs: MutablePreferences, places: List<Place>) {
        prefs[PLACES] = KolyJson.encodeToString(PLACE_LIST, places.take(MAX_PLACES))
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private companion object {
        val PLACE_LIST = ListSerializer(Place.serializer())
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val HOME_VIEW = stringPreferencesKey("home_view")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WIDGET_THEME = stringPreferencesKey("widget_theme")
        val PLACES = stringPreferencesKey("places")
        val MAIN_PLACE = stringPreferencesKey("main_place")
        val N_BEFORE_OFF = booleanPreferencesKey("notify_before_off")
        val N_BEFORE_ON = booleanPreferencesKey("notify_before_on")
        val N_CHANGED = booleanPreferencesKey("notify_changed")
        val N_TOMORROW = booleanPreferencesKey("notify_tomorrow")
        val N_LEAD = intPreferencesKey("notify_lead")
        val N_SCOPE = stringPreferencesKey("notify_scope")
        val N_QUIET = booleanPreferencesKey("notify_quiet")
    }
}
