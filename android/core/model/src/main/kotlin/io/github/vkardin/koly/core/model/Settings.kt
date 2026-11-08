package io.github.vkardin.koly.core.model

import kotlinx.serialization.Serializable

const val MAX_PLACES = 10

@Serializable
data class Place(
    val id: String,
    val label: String,
    val regionId: String,
    val group: String,
)

/** Layout of the home screen: A "card and bar", B "day ring", C "all places". */
enum class HomeView { CARD, RING, LIST }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NotifyScope { MAIN, ALL }

val LEAD_MINUTES_OPTIONS = listOf(5, 10, 15, 30, 60)

data class NotifySettings(
    val beforeOff: Boolean = true,
    val beforeOn: Boolean = true,
    val scheduleChanged: Boolean = true,
    val tomorrowPublished: Boolean = false,
    val leadMinutes: Int = 15,
    val scope: NotifyScope = NotifyScope.MAIN,
    val quietNight: Boolean = true,
)

data class AppSettings(
    val onboardingDone: Boolean = false,
    val homeView: HomeView = HomeView.CARD,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Colors of the home screen widgets; SYSTEM also means wallpaper colors for «Мінімал». */
    val widgetTheme: ThemeMode = ThemeMode.DARK,
    val places: List<Place> = emptyList(),
    val mainPlaceId: String? = null,
    val notify: NotifySettings = NotifySettings(),
) {
    val mainPlace: Place? get() = places.firstOrNull { it.id == mainPlaceId } ?: places.firstOrNull()

    val notifyPlaces: List<Place>
        get() = if (notify.scope == NotifyScope.ALL) places else listOfNotNull(mainPlace)
}
