package com.moobehgames.snoresafe.core.tags

import com.moobehgames.snoresafe.core.session.SessionSummary
import kotlinx.serialization.Serializable

@Serializable
enum class TagKind {
    /** Something tried to snore less: mouthguard, nasal strip, side sleeping… */
    REMEDY,

    /** Something that may make snoring worse: alcohol, a late meal, a blocked nose… */
    FACTOR,
}

/** Built-in remedies and factors. Stored by name, so the order can change freely. */
enum class BuiltInTag(val kind: TagKind) {
    MOUTHGUARD(TagKind.REMEDY),
    NASAL_STRIP(TagKind.REMEDY),
    NASAL_DILATOR(TagKind.REMEDY),
    NASAL_SPRAY(TagKind.REMEDY),
    SALINE_RINSE(TagKind.REMEDY),
    SIDE_SLEEPING(TagKind.REMEDY),
    POSITIONAL_DEVICE(TagKind.REMEDY),
    RAISED_HEAD(TagKind.REMEDY),
    ANTI_SNORE_PILLOW(TagKind.REMEDY),
    MOUTH_TAPE(TagKind.REMEDY),
    CHIN_STRAP(TagKind.REMEDY),
    THROAT_EXERCISES(TagKind.REMEDY),
    HUMIDIFIER(TagKind.REMEDY),
    AIR_PURIFIER(TagKind.REMEDY),
    ALLERGY_MEDICATION(TagKind.REMEDY),
    CPAP(TagKind.REMEDY),

    ALCOHOL(TagKind.FACTOR),
    LATE_MEAL(TagKind.FACTOR),
    CAFFEINE(TagKind.FACTOR),
    BLOCKED_NOSE(TagKind.FACTOR),
    ILL(TagKind.FACTOR),
    SMOKED(TagKind.FACTOR),
    SLEEPING_PILL(TagKind.FACTOR),
    VERY_TIRED(TagKind.FACTOR),
    EXERCISED(TagKind.FACTOR),
    STRESSED(TagKind.FACTOR),
    PERIOD(TagKind.FACTOR),
    NOT_MY_BED(TagKind.FACTOR);

    val id: String get() = "b:$name"
}

@Serializable
data class CustomTag(val id: String, val kind: TagKind, val name: String) {
    init {
        require(id.startsWith("c:")) { "Custom tag ids start with c:" }
    }
}

/** One remedy or factor, built-in or the user's own. */
data class TagRef(val id: String, val kind: TagKind, val builtIn: BuiltInTag?, val customName: String?)

object TagCatalog {
    const val MAX_CUSTOM_NAME = 40

    fun all(custom: List<CustomTag>): List<TagRef> =
        BuiltInTag.entries.map { TagRef(it.id, it.kind, it, null) } + custom.map { TagRef(it.id, it.kind, null, it.name) }

    fun find(id: String, custom: List<CustomTag>): TagRef? = all(custom).firstOrNull { it.id == id }

    fun builtIn(id: String): BuiltInTag? = if (id.startsWith("b:")) BuiltInTag.entries.firstOrNull { it.id == id } else null
}

/** Everything the user adds to a night. Kept apart from the measured [SessionSummary]. */
@Serializable
data class NightLog(
    val sessionId: String,
    val tags: Set<String> = emptySet(),
    val note: String = "",
    val weightKg: Float? = null,
    /** How rested the user felt on waking, 1 (exhausted) to 5 (refreshed). */
    val restRating: Int? = null,
) {
    init {
        require(restRating == null || restRating in 1..5) { "Rest rating is 1..5" }
        require(weightKg == null || weightKg in 20f..400f) { "Implausible weight" }
    }

    val isEmpty: Boolean get() = tags.isEmpty() && note.isBlank() && weightKg == null && restRating == null
}

/** How a remedy or factor relates to the Snore Score: nights with it versus nights without. */
data class TagImpact(
    val tagId: String,
    val nightsWith: Int,
    val nightsWithout: Int,
    val averageWith: Float,
    val averageWithout: Float,
) {
    /** Negative = quieter nights with this tag. */
    val change: Float get() = averageWith - averageWithout
}

object TagImpacts {
    const val MIN_NIGHTS_EACH_SIDE = 2

    /** Most helpful (largest drop in score) first. Tags without enough nights on both sides are left out. */
    fun compute(sessions: List<SessionSummary>, logs: Map<String, NightLog>, minNights: Int = MIN_NIGHTS_EACH_SIDE): List<TagImpact> {
        val nights = sessions.filter { it.monitoredMs > 0 }
        val tagIds = nights.flatMap { logs[it.id]?.tags.orEmpty() }.toSet()
        return tagIds.mapNotNull { tag ->
            val (with, without) = nights.partition { logs[it.id]?.tags?.contains(tag) == true }
            if (with.size < minNights || without.size < minNights) return@mapNotNull null
            TagImpact(
                tagId = tag,
                nightsWith = with.size,
                nightsWithout = without.size,
                averageWith = with.map { it.snoreScore }.average().toFloat(),
                averageWithout = without.map { it.snoreScore }.average().toFloat(),
            )
        }.sortedBy { it.change }
    }
}
