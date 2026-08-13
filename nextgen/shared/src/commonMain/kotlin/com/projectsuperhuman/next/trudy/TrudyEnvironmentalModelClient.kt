package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.emotional.EmotionalMetricIds
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds

/** Makes the Environmental tools/synthesizer reachable from every Trudy model runtime. */
class TrudyEnvironmentalModelClient(
    private val delegate: TrudyModelClient,
    private val config: TrudyEnvironmentalConfig = canonicalTrudyEnvironmentalConfig()
) : TrudyModelClient {
    private val tools = TrudyEnvironmentalTools(config)
    private val synthesizer = TrudyEnvironmentalSynthesizer(config)

    override suspend fun complete(request: TrudyModelRequest): TrudyModelResult {
        val intent = parseEnvironmentalIntent(request.userRequest) ?: return delegate.complete(request)

        return when (intent) {
            is EnvironmentalIntent.Current -> handleCurrent(request, intent)
            is EnvironmentalIntent.Association -> handleAssociation(request, intent)
        }
    }

    private fun handleCurrent(request: TrudyModelRequest, intent: EnvironmentalIntent.Current): TrudyModelResult {
        val requested = intent.metric?.let { listOf(it) } ?: config.metrics
        if (request.toolResults.isEmpty()) return TrudyModelResult(requestedTools = tools.currentState())
        val successful = request.toolResults.filter { it !is TrudyToolResult.Failure }
        if (successful.isEmpty()) return TrudyModelResult(responseText = "I couldn't access stored environmental readings right now.")
        val synthesis = synthesizer.current(requested, successful)
        return TrudyModelResult(responseText = synthesis.text, evidenceReferences = synthesis.evidenceReferences)
    }

    private suspend fun handleAssociation(request: TrudyModelRequest, intent: EnvironmentalIntent.Association): TrudyModelResult {
        val target = config.target(intent.target)
            ?: return TrudyModelResult(responseText = synthesizer.unavailableTarget(intent.target))
        val requested = intent.metric?.let { listOf(it) } ?: config.metrics
        if (request.toolResults.isEmpty()) {
            return TrudyModelResult(
                requestedTools = if (requested.size == 1) tools.association(requested.first(), target)
                else tools.associations(requested, target)
            )
        }

        val associations = request.toolResults.filterIsInstance<AssociationToolResult>()
        if (associations.isEmpty()) {
            return TrudyModelResult(responseText = "I don't have enough aligned environmental and ${target.displayName.lowercase()} data to answer that yet.")
        }
        val syntheses = associations.mapNotNull { result ->
            val metric = config.metrics.firstOrNull { it.metricId == result.association.leftMetricId } ?: return@mapNotNull null
            synthesizer.association(metric, target, result.association)
        }
        if (syntheses.isEmpty()) return delegate.complete(request)
        return TrudyModelResult(
            responseText = syntheses.joinToString(" ") { it.text },
            evidenceReferences = syntheses.flatMap { it.evidenceReferences }.distinct().take(48)
        )
    }

    private fun parseEnvironmentalIntent(message: String): EnvironmentalIntent? {
        val text = message.lowercase()
        val metric = when {
            "humidity" in text || "humid" in text -> config.metric(TrudyEnvironmentalMetricSemantic.RELATIVE_HUMIDITY)
            "rain" in text || "precip" in text -> config.metric(TrudyEnvironmentalMetricSemantic.PRECIPITATION)
            "pressure" in text || "barometric" in text -> config.metric(TrudyEnvironmentalMetricSemantic.AIR_PRESSURE)
            "wind" in text -> config.metric(TrudyEnvironmentalMetricSemantic.WIND_SPEED)
            "temperature" in text || "weather" in text || "hot" in text || "cold" in text -> config.metric(TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE)
            else -> null
        }
        val environmentMentioned = metric != null || listOf("environment", "weather", "outdoor conditions").any { it in text }
        if (!environmentMentioned) return null

        val target = when {
            "sleep" in text -> TrudyEnvironmentalTargetSemantic.SLEEP
            "exercise" in text || "workout" in text || "activity" in text -> TrudyEnvironmentalTargetSemantic.EXERCISE
            "anxious" in text || "anxiety" in text || "calm" in text -> TrudyEnvironmentalTargetSemantic.FEELING
            "mood" in text || "feel" in text || "happy" in text || "sad" in text -> TrudyEnvironmentalTargetSemantic.MOOD
            "headache" in text || "headaches" in text -> TrudyEnvironmentalTargetSemantic.HEADACHE
            else -> null
        }
        val relationship = listOf("affect", "linked", "related", "relationship", "correl", "when", "worse", "better", "change with").any { it in text }
        return if (target != null && relationship) EnvironmentalIntent.Association(metric, target)
        else EnvironmentalIntent.Current(metric)
    }

    private sealed interface EnvironmentalIntent {
        data class Current(val metric: TrudyEnvironmentalMetricBinding?) : EnvironmentalIntent
        data class Association(
            val metric: TrudyEnvironmentalMetricBinding?,
            val target: TrudyEnvironmentalTargetSemantic
        ) : EnvironmentalIntent
    }
}

fun canonicalTrudyEnvironmentalConfig(): TrudyEnvironmentalConfig = TrudyEnvironmentalConfig(
    environmentDomain = HealthDomain.ENVIRONMENT,
    metrics = listOf(
        TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.AIR_TEMPERATURE, EnvironmentalMetricIds.TEMPERATURE_C),
        TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.RELATIVE_HUMIDITY, EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT),
        TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.PRECIPITATION, EnvironmentalMetricIds.PRECIPITATION_MM),
        TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.AIR_PRESSURE, EnvironmentalMetricIds.SURFACE_PRESSURE_HPA),
        TrudyEnvironmentalMetricBinding(TrudyEnvironmentalMetricSemantic.WIND_SPEED, EnvironmentalMetricIds.WIND_SPEED_MPS)
    ),
    targets = listOf(
        TrudyEnvironmentalTargetBinding(
            TrudyEnvironmentalTargetSemantic.SLEEP,
            HealthDomain.SLEEP,
            "sleep_score",
            "your sleep",
            "Your sleep has tended to be better",
            "Your sleep has tended to be worse"
        ),
        TrudyEnvironmentalTargetBinding(
            TrudyEnvironmentalTargetSemantic.EXERCISE,
            HealthDomain.EXERCISE,
            "exercise_minutes",
            "your exercise",
            "You tended to exercise more",
            "You tended to exercise less"
        ),
        TrudyEnvironmentalTargetBinding(
            TrudyEnvironmentalTargetSemantic.MOOD,
            HealthDomain.EMOTIONAL,
            EmotionalMetricIds.VALENCE.value,
            "your mood",
            "You tended to report a more positive mood",
            "You tended to report a lower mood"
        ),
        TrudyEnvironmentalTargetBinding(
            TrudyEnvironmentalTargetSemantic.FEELING,
            HealthDomain.EMOTIONAL,
            EmotionalMetricIds.CALMNESS.value,
            "how calm you felt",
            "You tended to report feeling calmer",
            "You tended to report feeling more anxious"
        )
    )
)

fun TrudyModelClient.withEnvironmentalReasoning(): TrudyModelClient =
    if (this is TrudyEnvironmentalModelClient) this else TrudyEnvironmentalModelClient(this)
