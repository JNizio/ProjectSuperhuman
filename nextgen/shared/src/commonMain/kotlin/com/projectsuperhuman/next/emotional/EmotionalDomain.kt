package com.projectsuperhuman.next.emotional

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue

/**
 * Stable metric identifier for one bipolar emotional axis.
 *
 * IDs are storage semantics, not UI copy. Display labels may change without changing this value.
 */
data class EmotionalMetricId(val value: String) {
    init {
        require(value.matches(METRIC_ID_PATTERN)) {
            "Emotional metric IDs must use the stable 'emotional_*' lowercase snake_case namespace"
        }
    }
}

/** Canonical Emotional metric IDs. These strings are part of the persisted data contract. */
object EmotionalMetricIds {
    val VALENCE = EmotionalMetricId("emotional_valence")
    val CALMNESS = EmotionalMetricId("emotional_calmness")
    val ENERGY = EmotionalMetricId("emotional_energy")
    val CONFIDENCE = EmotionalMetricId("emotional_confidence")
    val CONNECTEDNESS = EmotionalMetricId("emotional_connectedness")
    val FOCUS = EmotionalMetricId("emotional_focus")

    val canonical: Set<EmotionalMetricId> = setOf(
        VALENCE,
        CALMNESS,
        ENERGY,
        CONFIDENCE,
        CONNECTEDNESS,
        FOCUS
    )
}

/**
 * Numeric contract shared by every bipolar Emotional metric.
 *
 * - -1.0 is the negative mathematical pole.
 * -  0.0 is an explicit neutral/middle observation, never a synonym for missing data.
 * - +1.0 is the positive mathematical pole.
 *
 * "Positive" and "negative" describe numeric polarity only. They do not assign moral value.
 */
object EmotionalNumericSemantics {
    const val MIN = -1.0
    const val NEUTRAL = 0.0
    const val MAX = 1.0
    const val CANONICAL_UNIT = "score"
    const val VERSION = 1
}

enum class BipolarDirection {
    TOWARD_NEGATIVE_POLE,
    NEUTRAL,
    TOWARD_POSITIVE_POLE
}

/** Type-safe normalized value that cannot contain NaN, infinity, or an out-of-range score. */
data class BipolarScaleValue(val normalized: Double) {
    init {
        require(normalized.isFinite()) { "Emotional scale value must be finite" }
        require(normalized in EmotionalNumericSemantics.MIN..EmotionalNumericSemantics.MAX) {
            "Emotional scale value must be between -1.0 and 1.0"
        }
    }

    val direction: BipolarDirection
        get() = when {
            normalized < EmotionalNumericSemantics.NEUTRAL -> BipolarDirection.TOWARD_NEGATIVE_POLE
            normalized > EmotionalNumericSemantics.NEUTRAL -> BipolarDirection.TOWARD_POSITIVE_POLE
            else -> BipolarDirection.NEUTRAL
        }

    val isNeutral: Boolean
        get() = normalized == EmotionalNumericSemantics.NEUTRAL
}

/**
 * Stable semantic pole plus default copy. Only [semanticId] participates in domain meaning;
 * [defaultLabel] is presentation-friendly fallback text and can be changed independently.
 */
data class EmotionalPoleDefinition(
    val semanticId: String,
    val defaultLabel: String
) {
    init {
        require(semanticId.matches(SEMANTIC_ID_PATTERN)) { "Pole semantic ID must be lowercase snake_case" }
        require(defaultLabel.isNotBlank()) { "Pole default label must not be blank" }
    }
}

/**
 * Definition of a normalized bipolar emotional axis.
 *
 * A value of +1 always means the [positivePole], -1 always means the [negativePole], and 0 is
 * neutral. UI orientation (left/right), wording, localization, and visual treatment must map onto
 * this definition instead of redefining the stored numeric meaning.
 */
data class BipolarScaleDefinition(
    val metricId: EmotionalMetricId,
    val positivePole: EmotionalPoleDefinition,
    val negativePole: EmotionalPoleDefinition,
    val semanticsVersion: Int = EmotionalNumericSemantics.VERSION
) {
    init {
        require(positivePole.semanticId != negativePole.semanticId) { "Bipolar poles must be distinct" }
        require(semanticsVersion == EmotionalNumericSemantics.VERSION) {
            "Unsupported Emotional numeric semantics version: $semanticsVersion"
        }
    }

    val neutralValue: Double = EmotionalNumericSemantics.NEUTRAL
}

/** Extensible definition boundary for built-in or future emotional axes. */
interface EmotionalScaleCatalog {
    fun definition(metricId: EmotionalMetricId): BipolarScaleDefinition?
    fun definitions(): List<BipolarScaleDefinition>
}

/** Canonical first-party scales. Adding a future axis does not require changing entry shape. */
object CanonicalEmotionalScaleCatalog : EmotionalScaleCatalog {
    private val scales = listOf(
        BipolarScaleDefinition(
            metricId = EmotionalMetricIds.VALENCE,
            positivePole = EmotionalPoleDefinition("positive_valence", "Happy"),
            negativePole = EmotionalPoleDefinition("negative_valence", "Sad")
        ),
        BipolarScaleDefinition(
            metricId = EmotionalMetricIds.CALMNESS,
            positivePole = EmotionalPoleDefinition("calm", "Calm"),
            negativePole = EmotionalPoleDefinition("anxious", "Anxious")
        ),
        BipolarScaleDefinition(
            metricId = EmotionalMetricIds.ENERGY,
            positivePole = EmotionalPoleDefinition("energetic", "Energetic"),
            negativePole = EmotionalPoleDefinition("drained", "Drained")
        ),
        BipolarScaleDefinition(
            metricId = EmotionalMetricIds.CONFIDENCE,
            positivePole = EmotionalPoleDefinition("confident", "Confident"),
            negativePole = EmotionalPoleDefinition("insecure", "Insecure")
        ),
        BipolarScaleDefinition(
            metricId = EmotionalMetricIds.CONNECTEDNESS,
            positivePole = EmotionalPoleDefinition("connected", "Connected"),
            negativePole = EmotionalPoleDefinition("lonely", "Lonely")
        ),
        BipolarScaleDefinition(
            metricId = EmotionalMetricIds.FOCUS,
            positivePole = EmotionalPoleDefinition("focused", "Focused"),
            negativePole = EmotionalPoleDefinition("distracted", "Distracted")
        )
    )

    private val byId = scales.associateBy { it.metricId }

    init {
        require(byId.size == scales.size) { "Canonical Emotional scale IDs must be unique" }
    }

    override fun definition(metricId: EmotionalMetricId): BipolarScaleDefinition? = byId[metricId]

    override fun definitions(): List<BipolarScaleDefinition> = scales
}

data class EmotionalObservation(
    val metricId: EmotionalMetricId,
    val value: BipolarScaleValue
)

enum class EmotionalSourceType(val wireId: String) {
    SELF_REPORT("self_report"),
    IMPORTED("imported"),
    DERIVED("derived")
}

/** Source identity and source-specific metadata preserved independently from display labels. */
data class EmotionalSourceMetadata(
    val sourceId: String,
    val type: EmotionalSourceType,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(sourceId.isNotBlank()) { "Emotional source ID must not be blank" }
    }
}

/**
 * One timestamped emotional check-in containing any non-empty subset of registered scales.
 * Partial entries are intentional: absence means unobserved, while an explicit 0.0 means neutral.
 */
data class EmotionalEntry(
    val timestampEpochMs: Long,
    val source: EmotionalSourceMetadata,
    val observations: List<EmotionalObservation>,
    val metadata: Map<String, String> = emptyMap()
)

enum class EmotionalValidationCode {
    INVALID_TIMESTAMP,
    EMPTY_OBSERVATIONS,
    DUPLICATE_METRIC,
    UNKNOWN_METRIC
}

data class EmotionalValidationIssue(
    val code: EmotionalValidationCode,
    val metricId: EmotionalMetricId? = null,
    val message: String
)

data class EmotionalValidationResult(val issues: List<EmotionalValidationIssue>) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

/** Boundary validation for an entry before it is exposed to storage or downstream analysis. */
object EmotionalEntryValidator {
    fun validate(
        entry: EmotionalEntry,
        catalog: EmotionalScaleCatalog = CanonicalEmotionalScaleCatalog
    ): EmotionalValidationResult {
        val issues = mutableListOf<EmotionalValidationIssue>()
        if (entry.timestampEpochMs <= 0L) {
            issues += EmotionalValidationIssue(
                EmotionalValidationCode.INVALID_TIMESTAMP,
                message = "Emotional observation timestamp must be greater than zero"
            )
        }
        if (entry.observations.isEmpty()) {
            issues += EmotionalValidationIssue(
                EmotionalValidationCode.EMPTY_OBSERVATIONS,
                message = "Emotional entry must contain at least one observation"
            )
        }

        val seen = mutableSetOf<EmotionalMetricId>()
        entry.observations.forEach { observation ->
            if (!seen.add(observation.metricId)) {
                issues += EmotionalValidationIssue(
                    EmotionalValidationCode.DUPLICATE_METRIC,
                    observation.metricId,
                    "Emotional entry contains the same metric more than once"
                )
            }
            if (catalog.definition(observation.metricId) == null) {
                issues += EmotionalValidationIssue(
                    EmotionalValidationCode.UNKNOWN_METRIC,
                    observation.metricId,
                    "Emotional metric is not registered in the supplied scale catalog"
                )
            }
        }
        return EmotionalValidationResult(issues)
    }

    fun requireValid(
        entry: EmotionalEntry,
        catalog: EmotionalScaleCatalog = CanonicalEmotionalScaleCatalog
    ): EmotionalEntry {
        val result = validate(entry, catalog)
        require(result.isValid) { result.issues.joinToString("; ") { it.message } }
        return entry
    }
}

/**
 * Contract that converts Emotional domain entries into Project Superhuman's canonical numeric rows.
 * It deliberately does not persist anything; Data Vault ownership remains outside this module.
 */
fun interface EmotionalHealthValueMapper {
    fun map(entry: EmotionalEntry): List<HealthValue>
}

class DefaultEmotionalHealthValueMapper(
    private val catalog: EmotionalScaleCatalog = CanonicalEmotionalScaleCatalog
) : EmotionalHealthValueMapper {
    override fun map(entry: EmotionalEntry): List<HealthValue> {
        EmotionalEntryValidator.requireValid(entry, catalog)
        val sourceRecordBase = entry.metadata["sourceRecordId"] ?: entry.source.metadata["sourceRecordId"]

        return entry.observations.map { observation ->
            val metadata = buildMap {
                putAll(entry.source.metadata)
                putAll(entry.metadata)
                remove("sourceRecordId")
                if (!sourceRecordBase.isNullOrBlank()) {
                    put("sourceRecordId", "$sourceRecordBase:${observation.metricId.value}")
                }
                put("emotionalSourceType", entry.source.type.wireId)
                put("emotionalSemanticsVersion", EmotionalNumericSemantics.VERSION.toString())
            }

            HealthValue(
                domain = HealthDomain.EMOTIONAL,
                metric = observation.metricId.value,
                value = observation.value.normalized,
                unit = EmotionalNumericSemantics.CANONICAL_UNIT,
                timestampEpochMs = entry.timestampEpochMs,
                source = entry.source.sourceId,
                metadata = metadata
            )
        }
    }
}

private val METRIC_ID_PATTERN = Regex("^emotional_[a-z0-9]+(?:_[a-z0-9]+)*$")
private val SEMANTIC_ID_PATTERN = Regex("^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$")
