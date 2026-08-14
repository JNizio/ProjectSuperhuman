package com.projectsuperhuman.next.trudy.performance

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.environment.EnvironmentalMetricIds
import com.projectsuperhuman.next.trudy.TrudyExperimentKind

/** Methodological knowledge consumed by Agent 2's planner/integration layer. */
object PerformanceExperimentMethodology {
    val principles: List<PerformanceExperimentPrinciple> = listOf(
        principle("hypothesis", "Hypothesis", "State a directional or deliberately open question before seeing the intervention result.", "Changing the hypothesis after seeing the data increases hindsight bias."),
        principle("baseline", "Baseline", "Observe the primary outcome under the current routine using the same measurement method.", "A baseline during illness, travel or an unusual schedule may not represent the usual state."),
        principle("single_intervention", "Intervention", "Prefer one reversible, low-risk change that can be repeated consistently.", "Changing several behaviours together prevents attribution to one component."),
        principle("primary_outcome", "Primary outcome", "Choose one main canonical outcome before the experiment and keep secondary outcomes separate.", "Selecting whichever outcome improved after the fact inflates apparent success."),
        principle("confounders", "Confounders", "Pre-identify likely co-varying factors and record important deviations.", "Unmeasured confounders remain possible even with careful logging."),
        principle("duration", "Duration", "Choose periods long enough to collect repeated observations and cover expected day-to-day variability.", "Longer is not automatically better when adherence falls or the context changes."),
        principle("consistency", "Consistency", "Keep measurement timing, device, units and major routine factors comparable.", "Consistently biased measurement can still produce a misleading result."),
        principle("measurement_noise", "Measurement noise", "Inspect variability and source quality rather than comparing only two single values.", "Wearable precision does not guarantee clinical accuracy."),
        principle("repeated_measures", "Repeated measures", "Use several baseline and intervention observations; repeated periods can strengthen a reversible N-of-1 comparison where safe.", "Repeated observations from one person do not become population evidence."),
        principle("interpretation", "Interpretation", "Report whether results support, do not support, or are inconclusive for this person under similar conditions.", "A personal experiment does not prove universal causality or treatment efficacy.")
    )

    val blueprints: List<PerformanceExperimentBlueprint> = listOf(
        PerformanceExperimentBlueprint(
            id = "caffeine_sleep_timing",
            topicIds = setOf("caffeine_experiment", "caffeine_sleep"),
            question = "Does moving my caffeine earlier change my sleep under otherwise similar conditions?",
            hypothesis = "An earlier, consistent caffeine cut-off may be associated with improved sleep continuity or duration for this user.",
            baselineDays = 7,
            interventionDays = 14,
            primaryOutcome = core(HealthDomain.SLEEP, "sleep_total_minutes", PerformanceMetricRole.PRIMARY_OUTCOME, "Main repeated sleep-duration outcome."),
            secondaryOutcomes = listOf(
                core(HealthDomain.SLEEP, "sleep_awake_minutes", PerformanceMetricRole.SECONDARY_OUTCOME, "Continuity context."),
                core(HealthDomain.SLEEP, "sleep_efficiency_pct", PerformanceMetricRole.SECONDARY_OUTCOME, "Derived continuity context."),
                core(HealthDomain.SLEEP, "sleep_start_epoch_ms", PerformanceMetricRole.CONTEXT, "Schedule stability context.")
            ),
            exposureOrIntervention = "Keep the usual total caffeine amount stable where safe, record dose and time, then move the final caffeine to one pre-selected earlier cut-off.",
            consistencyInstructions = listOf("Use the same sleep device/source.", "Keep intended sleep schedule reasonably stable.", "Record missed or extra caffeine rather than hiding deviations."),
            confounders = listOf("sleep schedule", "illness", "alcohol", "unusual exercise load", "travel", "acute stress"),
            interpretationLimitations = listOf("Caffeine response is dose- and person-dependent.", "An uncontrolled result cannot isolate all co-occurring changes.", "The result applies only to similar personal conditions."),
            safetyNotes = listOf("Do not increase caffeine to make the experiment more obvious.", "Avoid abrupt cessation when it is likely to cause meaningful withdrawal.", "Do not alter prescribed medication."),
            evidenceSourceIds = setOf("gardiner_caffeine_sleep_2025", "cent_n_of_1_2015"),
            existingExperimentKind = TrudyExperimentKind.EARLIER_CAFFEINE_CUTOFF
        ),
        PerformanceExperimentBlueprint(
            id = "sleep_schedule_consistency",
            topicIds = setOf("sleep_schedule_experiment", "sleep_timing"),
            question = "Does a more consistent sleep schedule change my sleep outcome?",
            hypothesis = "A more consistent sleep opportunity may be associated with a more stable or improved selected sleep outcome.",
            baselineDays = 7,
            interventionDays = 14,
            primaryOutcome = core(HealthDomain.SLEEP, "sleep_total_minutes", PerformanceMetricRole.PRIMARY_OUTCOME, "Primary sleep outcome without relying only on a composite score."),
            secondaryOutcomes = listOf(
                core(HealthDomain.SLEEP, "sleep_start_epoch_ms", PerformanceMetricRole.SECONDARY_OUTCOME, "Bedtime consistency."),
                core(HealthDomain.SLEEP, "sleep_end_epoch_ms", PerformanceMetricRole.SECONDARY_OUTCOME, "Wake-time consistency."),
                core(HealthDomain.SLEEP, "sleep_awake_minutes", PerformanceMetricRole.CONTEXT, "Continuity context.")
            ),
            exposureOrIntervention = "Choose a feasible bedtime and wake-time window while preserving adequate sleep opportunity.",
            consistencyInstructions = listOf("Use local clock time and record travel/time-zone changes.", "Keep the same sleep tracker.", "Do not shorten sleep merely to hit the schedule."),
            confounders = listOf("shift work", "late social events", "travel", "illness", "caffeine timing", "unusual stress"),
            interpretationLimitations = listOf("Schedule consistency and sleep opportunity may change together.", "Wearable sleep timing is estimated.", "Two weeks may not represent every season or work pattern."),
            safetyNotes = listOf("Do not deliberately restrict needed sleep.", "Do not attempt an inflexible schedule that conflicts with safety-critical work or caregiving."),
            evidenceSourceIds = setOf("aasm_sleep_duration_2015", "aasm_consumer_sleep_technology_2018", "cent_n_of_1_2015"),
            existingExperimentKind = TrudyExperimentKind.SLEEP_SCHEDULE_CONSISTENCY
        ),
        PerformanceExperimentBlueprint(
            id = "exercise_timing_sleep",
            topicIds = setOf("exercise_timing_experiment", "exercise_sleep"),
            question = "Does exercising in a different time window change my subsequent sleep?",
            hypothesis = "Changing exercise timing while holding session type and load approximately stable may be associated with a change in sleep.",
            baselineDays = 7,
            interventionDays = 14,
            primaryOutcome = core(HealthDomain.SLEEP, "sleep_awake_minutes", PerformanceMetricRole.PRIMARY_OUTCOME, "Repeated sleep-continuity outcome."),
            secondaryOutcomes = listOf(
                core(HealthDomain.SLEEP, "sleep_total_minutes", PerformanceMetricRole.SECONDARY_OUTCOME, "Sleep duration."),
                core(HealthDomain.EXERCISE, "exercise_minutes", PerformanceMetricRole.CONFOUNDER, "Keep session duration comparable."),
                core(HealthDomain.EXERCISE, "heart_rate_avg_bpm", PerformanceMetricRole.CONFOUNDER, "Approximate internal intensity context.")
            ),
            exposureOrIntervention = "Use one feasible exercise-time window while keeping activity type, approximate duration and effort similar to baseline sessions.",
            consistencyInstructions = listOf("Compare the same exercise modality where possible.", "Record session end time.", "Do not raise intensity for the experiment."),
            confounders = listOf("exercise intensity", "bedtime", "caffeine", "room temperature", "late meals", "stress"),
            interpretationLimitations = listOf("Evening exercise is not uniformly harmful to sleep.", "Small timing samples are vulnerable to day-of-week effects.", "Association does not prove the mechanism."),
            safetyNotes = listOf("Do not perform exercise that is unsafe for current symptoms or restrictions.", "Stop for concerning symptoms."),
            evidenceSourceIds = setOf("stutz_evening_exercise_sleep_2019", "cent_n_of_1_2015"),
            existingExperimentKind = TrudyExperimentKind.EXERCISE_TIMING
        ),
        PerformanceExperimentBlueprint(
            id = "mindfulness_stress_routine",
            topicIds = setOf("mindfulness_experiment", "mindfulness"),
            question = "Does a consistent brief mindfulness routine coincide with a change in recorded stress?",
            hypothesis = "A consistent mindfulness routine may be associated with lower post-session or daily stress self-report for this user.",
            baselineDays = 7,
            interventionDays = 14,
            primaryOutcome = core(HealthDomain.MINDFULNESS, "stress_after", PerformanceMetricRole.PRIMARY_OUTCOME, "Post-session stress self-report."),
            secondaryOutcomes = listOf(
                core(HealthDomain.MINDFULNESS, "stress_before", PerformanceMetricRole.CONTEXT, "Pre-session state."),
                core(HealthDomain.MINDFULNESS, "mindfulness_session_minutes", PerformanceMetricRole.EXPOSURE, "Session dose/adherence."),
                core(HealthDomain.MINDFULNESS, "mood_score", PerformanceMetricRole.SECONDARY_OUTCOME, "Mood context.")
            ),
            exposureOrIntervention = "Use the same comfortable, brief mindfulness routine at a similar time and record stress before and after.",
            consistencyInstructions = listOf("Keep the routine type and approximate duration stable.", "Record skipped sessions.", "Use the same stress scale anchors."),
            confounders = listOf("acute stressor", "sleep", "exercise", "caffeine", "setting", "expectancy"),
            interpretationLimitations = listOf("Self-report can be influenced by expectancy and context.", "Pre/post change does not prove a lasting effect.", "Mindfulness effects vary between people."),
            safetyNotes = listOf("Stop or change the exercise if it meaningfully worsens distress.", "Do not use the experiment as a replacement for mental-health care."),
            evidenceSourceIds = setOf("goyal_meditation_2014", "cent_n_of_1_2015"),
            existingExperimentKind = TrudyExperimentKind.MINDFULNESS_ROUTINE
        ),
        PerformanceExperimentBlueprint(
            id = "bedroom_temperature_sleep",
            topicIds = setOf("bedroom_temperature_experiment", "sleep_environment"),
            question = "Does a modest, comfortable bedroom-temperature change coincide with different sleep continuity?",
            hypothesis = "A comfortable bedroom-temperature adjustment may be associated with improved sleep continuity for this user.",
            baselineDays = 7,
            interventionDays = 14,
            primaryOutcome = core(HealthDomain.SLEEP, "sleep_awake_minutes", PerformanceMetricRole.PRIMARY_OUTCOME, "Repeated sleep-continuity outcome."),
            secondaryOutcomes = listOf(
                core(HealthDomain.SLEEP, "sleep_efficiency_pct", PerformanceMetricRole.SECONDARY_OUTCOME, "Derived continuity estimate."),
                environment(EnvironmentalMetricIds.TEMPERATURE_C, PerformanceMetricRole.CONTEXT, "Outdoor/local-area context only; not a bedroom measurement."),
                environment(EnvironmentalMetricIds.RELATIVE_HUMIDITY_PCT, PerformanceMetricRole.CONFOUNDER, "Humidity context.")
            ),
            exposureOrIntervention = "Use one modest, comfortable bedroom setting and, where possible, record the actual bedroom temperature rather than substituting outdoor weather.",
            consistencyInstructions = listOf("Keep bedding and sleepwear similar.", "Use the same sleep tracker.", "Record nights when heating, windows or room use differed."),
            confounders = listOf("bedding", "sleepwear", "humidity", "noise", "illness", "bed partner", "outdoor versus indoor measurement"),
            interpretationLimitations = listOf("Outdoor temperature is not indoor exposure.", "Comfort and acclimatisation differ.", "A personal association does not prove thermal causation."),
            safetyNotes = listOf("Keep changes within a comfortable, non-extreme range.", "Do not use hazardous heating, cooling or ventilation practices."),
            evidenceSourceIds = setOf("okamoto_sleep_thermal_2012", "cent_n_of_1_2015")
        )
    )

    private val byId = blueprints.associateBy { it.id }

    init {
        require(principles.map { it.id }.distinct().size == principles.size)
        require(byId.size == blueprints.size)
        val topics = PerformanceKnowledgeCatalog.topics.map { it.id }.toSet()
        val sources = PerformanceEvidenceCatalog.sources.map { it.id }.toSet()
        blueprints.forEach { blueprint ->
            require(blueprint.topicIds.all { it in topics }) { "Unknown topic on ${blueprint.id}" }
            require(blueprint.evidenceSourceIds.all { it in sources }) { "Unknown evidence on ${blueprint.id}" }
        }
    }

    fun blueprint(id: String): PerformanceExperimentBlueprint? = byId[id]

    fun forTopics(topicIds: Set<String>): List<PerformanceExperimentBlueprint> =
        blueprints.filter { blueprint -> blueprint.topicIds.any { it in topicIds } }

    private fun principle(id: String, name: String, guidance: String, limitation: String) =
        PerformanceExperimentPrinciple(id, name, guidance, limitation)

    private fun core(domain: HealthDomain, metric: String, role: PerformanceMetricRole, why: String) =
        PerformanceMetricBinding(domain, metric, role, PerformanceMetricOrigin.CORE_METRIC_REGISTRY, why)

    private fun environment(metric: String, role: PerformanceMetricRole, why: String) =
        PerformanceMetricBinding(HealthDomain.ENVIRONMENT, metric, role, PerformanceMetricOrigin.ENVIRONMENTAL_DOMAIN, why)
}
