package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.CoreMetricRegistry
import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.MetricDefinition
import com.projectsuperhuman.next.core.MetricRegistry

enum class TrudyEnvironmentalMetricSemantic {
    AIR_TEMPERATURE,
    RELATIVE_HUMIDITY,
    PRECIPITATION,
    DAYLIGHT_DURATION,
    AIR_PRESSURE,
    WIND_SPEED
}

enum class TrudyEnvironmentalTargetSemantic { SLEEP, FEELING, EXERCISE, MOOD, HEADACHE }
enum class TrudyEnvironmentalFreshness { CURRENT, AGING, STALE, UNKNOWN }
enum class TrudyEnvironmentalIntentKind { CURRENT_ENVIRONMENT, ASSOCIATION }

data class TrudyEnvironmentalMetricBinding(
    val semantic: TrudyEnvironmentalMetricSemantic,
    val metricId: String,
    val displayName: String = semantic.defaultDisplayName()
) {
    init {
        require(metricId.isNotBlank()) { "Environmental metricId must not be blank" }
        require(displayName.isNotBlank()) { "Environmental displayName must not be blank" }
    }
}

data class TrudyEnvironmentalTargetBinding(
    val semantic: TrudyEnvironmentalTargetSemantic,
    val domain: HealthDomain,
    val metricId: String,
    val displayName: String,
    val higherOutcomePhrase: String = "${displayName.sentenceStart()} tended to be higher",
    val lowerOutcomePhrase: String = "${displayName.sentenceStart()} tended to be lower"
) {
    init {
        require(metricId.isNotBlank()) { "Target metricId must not be blank" }
        require(displayName.isNotBlank()) { "Target displayName must not be blank" }
        require(higherOutcomePhrase.isNotBlank() && lowerOutcomePhrase.isNotBlank())
    }
}

data class TrudyEnvironmentalConfig(
    val environmentDomain: HealthDomain,
    val metrics: List<TrudyEnvironmentalMetricBinding>,
    val targets: List<TrudyEnvironmentalTargetBinding> = emptyList(),
    val currentFreshnessHours: Double = 6.0,
    val agingFreshnessHours: Double = 24.0,
    val associationAlignmentWindowMs: Long = 21_600_000L,
    val maxBroadAssociationMetrics: Int = 4
) {
    init {
        require(metrics.isNotEmpty()) { "At least one Environmental metric binding is required" }
        require(metrics.map { it.semantic }.distinct().size == metrics.size)
        require(metrics.map { it.metricId }.distinct().size == metrics.size)
        require(targets.map { it.semantic }.distinct().size == targets.size)
        require(currentFreshnessHours.isFinite() && currentFreshnessHours > 0.0)
        require(agingFreshnessHours.isFinite() && agingFreshnessHours >= currentFreshnessHours)
        require(associationAlignmentWindowMs in 0..TrudyStatistics.MAX_ALIGNMENT_WINDOW_MS)
        require(maxBroadAssociationMetrics in 1..6)
    }

    fun metric(semantic: TrudyEnvironmentalMetricSemantic): TrudyEnvironmentalMetricBinding? =
        metrics.firstOrNull { it.semantic == semantic }

    fun target(semantic: TrudyEnvironmentalTargetSemantic): TrudyEnvironmentalTargetBinding? =
        targets.firstOrNull { it.semantic == semantic }

    companion object {
        fun discover(
            registry: MetricRegistry = CoreMetricRegistry,
            additionalTargets: List<TrudyEnvironmentalTargetBinding> = emptyList()
        ): TrudyEnvironmentalConfig? {
            val domain = HealthDomain.entries.firstOrNull { it.name == ENVIRONMENT_DOMAIN_NAME }
                ?: return null
            return fromRegistry(domain, registry, additionalTargets)
        }

        fun fromRegistry(
            environmentDomain: HealthDomain,
            registry: MetricRegistry = CoreMetricRegistry,
            additionalTargets: List<TrudyEnvironmentalTargetBinding> = emptyList()
        ): TrudyEnvironmentalConfig? {
            val definitions = registry.definitions(environmentDomain)
            val metricBindings = TrudyEnvironmentalMetricSemantic.entries.mapNotNull { semantic ->
                semantic.findDefinition(definitions)?.let { TrudyEnvironmentalMetricBinding(semantic, it.id) }
            }
            if (metricBindings.isEmpty()) return null

            val automaticTargets = buildList {
                registry.definition(HealthDomain.SLEEP, "sleep_score")?.let {
                    add(
                        TrudyEnvironmentalTargetBinding(
                            TrudyEnvironmentalTargetSemantic.SLEEP,
                            HealthDomain.SLEEP,
                            it.id,
                            "your sleep",
                            "Your sleep has tended to be better",
                            "Your sleep has tended to be worse"
                        )
                    )
                }
                registry.definition(HealthDomain.EXERCISE, "exercise_minutes")?.let {
                    add(
                        TrudyEnvironmentalTargetBinding(
                            TrudyEnvironmentalTargetSemantic.EXERCISE,
                            HealthDomain.EXERCISE,
                            it.id,
                            "your exercise",
                            "You tended to exercise more",
                            "You tended to exercise less"
                        )
                    )
                }
            }
            val overridden = additionalTargets.map { it.semantic }.toSet()
            return TrudyEnvironmentalConfig(
                environmentDomain,
                metricBindings,
                automaticTargets.filterNot { it.semantic in overridden } + additionalTargets
            )
        }

        private const val ENVIRONMENT_DOMAIN_NAME = "ENVIRONMENT"
    }
}

data class TrudyEnvironmentalGrounding(
    val sources: List<String>,
    val latestMeasurementEpochMs: Long?,
    val freshness: TrudyEnvironmentalFreshness,
    val dataQuality: TrudyDataQualityEvidence?,
    val measurementReferences: List<TrudyEvidenceReference>
)

data class TrudyEnvironmentalSynthesis(
    val text: String,
    val evidenceReferences: List<TrudyEvidenceReference>,
    val grounding: TrudyEnvironmentalGrounding
)

object TrudyEnvironmentalPolicy {
    const val MODEL_INSTRUCTION: String = """ENVIRONMENTAL EVIDENCE POLICY
Environmental values are stored measurements, not a live weather service. Never invent current weather or imply that a stale reading describes current conditions.
Preserve the supplied source, measurement timestamp, freshness/data-quality state, metric identity, and unit. Do not silently replace or convert them.
Use deterministic Trudy trend/association results for calculations; do not estimate correlations from prose or eyeball raw rows.
Environmental relationships with sleep, exercise, mood, symptoms, or other domains are personal associations unless stronger evidence is explicitly supplied. Express that uncertainty naturally in context instead of repeating stock labels such as 'association is not causation' or 'descriptive trend'.
Do not diagnose a medical condition or a medical cause from an environmental pattern."""
}

internal fun String.sentenceStart(): String = replaceFirstChar { char ->
    if (char.isLowerCase()) char.titlecase() else char.toString()
}

private fun TrudyEnvironmentalMetricSemantic.defaultDisplayName(): String = when (this) {
    TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE -> "temperature"
    TrudyEnvironmentalMetricSemantic.RELATIVE_HUMIDITY -> "humidity"
    TrudyEnvironmentalMetricSemantic.PRECIPITATION -> "precipitation"
    TrudyEnvironmentalMetricSemantic.DAYLIGHT_DURATION -> "daylight"
    TrudyEnvironmentalMetricSemantic.AIR_PRESSURE -> "air pressure"
    TrudyEnvironmentalMetricSemantic.WIND_SPEED -> "wind speed"
}

private fun TrudyEnvironmentalMetricSemantic.findDefinition(
    definitions: List<MetricDefinition>
): MetricDefinition? {
    val tokens = when (this) {
        TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE -> listOf("air_temperature", "temperature", "temp")
        TrudyEnvironmentalMetricSemantic.RELATIVE_HUMIDITY -> listOf("relative_humidity", "humidity", "humid")
        TrudyEnvironmentalMetricSemantic.PRECIPITATION -> listOf("precipitation", "precip", "rain")
        TrudyEnvironmentalMetricSemantic.DAYLIGHT_DURATION -> listOf("daylight_duration", "daylight", "sunlight", "day_length")
        TrudyEnvironmentalMetricSemantic.AIR_PRESSURE -> listOf("air_pressure", "barometric", "pressure")
        TrudyEnvironmentalMetricSemantic.WIND_SPEED -> listOf("wind_speed", "wind")
    }
    return tokens.firstNotNullOfOrNull { token ->
        definitions.firstOrNull { definition ->
            (listOf(definition.id) + definition.aliases).any { token in it.lowercase() }
        }
    }
}