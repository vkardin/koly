package io.github.vkardin.koly.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Data contract of the published JSON files, schema v1. */
const val SCHEMA_VERSION = 1

val KolyJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
enum class RegionStatus {
    @SerialName("ok") OK,
    @SerialName("stale") STALE,
    @SerialName("error") ERROR,
    @SerialName("no_schedules") NO_SCHEDULES,
}

@Serializable
enum class IntervalType {
    @SerialName("off") OFF,
    @SerialName("maybe") MAYBE,
}

@Serializable
data class LocalizedName(
    val uk: String = "",
    val en: String = "",
    val ru: String = "",
) {
    fun forLanguage(lang: String): String = when (lang) {
        "en" -> en.ifBlank { uk }
        "ru" -> ru.ifBlank { uk }
        else -> uk
    }
}

@Serializable
data class RegionIndexEntry(
    val id: String,
    val name: LocalizedName = LocalizedName(),
    val provider: String = "",
    val groups: List<String> = emptyList(),
    val updatedAt: String? = null,
    val status: RegionStatus = RegionStatus.ERROR,
)

@Serializable
data class IndexFile(
    val schemaVersion: Int = 0,
    val generatedAt: String? = null,
    val regions: List<RegionIndexEntry> = emptyList(),
) {
    val isValid: Boolean get() = schemaVersion == SCHEMA_VERSION && regions.isNotEmpty()
}

@Serializable
data class Interval(
    val from: String,
    val to: String,
    val type: IntervalType = IntervalType.MAYBE,
)

@Serializable
data class DaySchedule(
    val date: String,
    val groups: Map<String, List<Interval>> = emptyMap(),
)

@Serializable
data class Emergency(
    val active: Boolean = false,
    val note: String? = null,
    val since: String? = null,
)

@Serializable
data class RegionFile(
    val id: String,
    val provider: String = "",
    val sourceUrl: String? = null,
    val fetchedAt: String? = null,
    val updatedAt: String? = null,
    val status: RegionStatus = RegionStatus.ERROR,
    val emergency: Emergency = Emergency(),
    val days: List<DaySchedule> = emptyList(),
) {
    fun isValidFor(expectedId: String): Boolean = id == expectedId

    fun day(date: String): DaySchedule? = days.firstOrNull { it.date == date }
}
