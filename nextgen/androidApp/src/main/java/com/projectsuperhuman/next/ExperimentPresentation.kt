package com.projectsuperhuman.next

internal enum class ExperimentStatus(val label: String) {
    DRAFT("Draft"),
    ACTIVE("Active"),
    COMPLETED("Completed")
}

internal enum class ExperimentPhase(val label: String) {
    BASELINE("Baseline"),
    INTERVENTION("Intervention"),
    FOLLOW_UP("Follow-up"),
    COMPLETE("Complete")
}

internal enum class ExperimentOutcomeRole { PRIMARY, SECONDARY }

internal enum class ExperimentConfidence(val label: String, val explanation: String) {
    TOO_EARLY("Too early to tell", "There are not enough observations to separate a real pattern from normal day-to-day variation."),
    POSSIBLE_SIGNAL("Possible signal", "The direction is interesting, but more repeated observations would be needed before relying on it."),
    CONSISTENT_CHANGE("Consistent change", "The change appears repeatedly in this experiment. It is still personal evidence, not proof of cause.")
}

internal enum class ExperimentDeviceStatus(val label: String) {
    CONNECTED("Connected"),
    NOT_CONNECTED("Not connected"),
    AVAILABLE_LATER("Available later")
}

internal data class ExperimentProgress(
    val currentDay: Int,
    val totalDays: Int,
    val completedCheckIns: Int,
    val plannedCheckIns: Int
) {
    val fraction: Float
        get() = if (totalDays <= 0) 0f else (currentDay.toFloat() / totalDays).coerceIn(0f, 1f)
}

internal data class ExperimentOutcome(
    val id: String,
    val title: String,
    val role: ExperimentOutcomeRole,
    val unit: String = "score",
    val beforeValue: Double? = null,
    val duringValue: Double? = null
)

internal data class ExperimentIntervention(
    val category: String,
    val title: String,
    val instructions: String
)

internal data class ExperimentDevice(
    val id: String,
    val title: String,
    val description: String,
    val status: ExperimentDeviceStatus
)

internal data class ExperimentTimelineItem(
    val title: String,
    val detail: String,
    val phase: ExperimentPhase,
    val completed: Boolean
)

internal data class ExperimentObservation(
    val dayLabel: String,
    val phase: ExperimentPhase,
    val primaryValue: String,
    val note: String? = null,
    val completed: Boolean = true
)

internal data class ExperimentResultSummary(
    val headline: String,
    val confidence: ExperimentConfidence,
    val primaryChange: String,
    val secondaryChanges: List<String>,
    val isFinal: Boolean
)

internal data class ExperimentPresentation(
    val id: String,
    val title: String,
    val hypothesis: String,
    val status: ExperimentStatus,
    val phase: ExperimentPhase,
    val progress: ExperimentProgress,
    val intervention: ExperimentIntervention,
    val outcomes: List<ExperimentOutcome>,
    val nextCheckIn: String?,
    val scheduleSummary: String,
    val timeline: List<ExperimentTimelineItem>,
    val observations: List<ExperimentObservation>,
    val devices: List<ExperimentDevice> = emptyList(),
    val notes: String? = null,
    val result: ExperimentResultSummary? = null
) {
    val primaryOutcome: ExperimentOutcome?
        get() = outcomes.firstOrNull { it.role == ExperimentOutcomeRole.PRIMARY }
}

internal enum class ExperimentCreationStep(val number: Int, val title: String) {
    TESTING(1, "What are you testing?"),
    CHANGING(2, "What are you changing?"),
    MEASURING(3, "What will you measure?"),
    SCHEDULE(4, "Schedule"),
    REVIEW(5, "Review")
}

internal data class ExperimentDraft(
    val testingTarget: String? = null,
    val intervention: String? = null,
    val outcomes: Set<String> = emptySet(),
    val primaryOutcome: String? = null,
    val startDateLabel: String = "Tomorrow",
    val durationDays: Int = 7,
    val dailyFrequency: Int = 1,
    val baselineDays: Int = 2,
    val interventionDays: Int = 5,
    val device: ExperimentDevice? = null,
    val customTestingTarget: String = "",
    val customIntervention: String = "",
    val customTitle: String? = null
) {
    fun canContinue(step: ExperimentCreationStep): Boolean = when (step) {
        ExperimentCreationStep.TESTING -> testingTarget != null && (testingTarget != "Custom" || customTestingTarget.isNotBlank())
        ExperimentCreationStep.CHANGING -> intervention != null &&
            (intervention != "Custom intervention" || customIntervention.isNotBlank()) &&
            (intervention != "Device / sensor" || device != null)
        ExperimentCreationStep.MEASURING -> outcomes.isNotEmpty() && primaryOutcome in outcomes
        ExperimentCreationStep.SCHEDULE -> durationDays > 0 && baselineDays >= 0 && interventionDays > 0 && baselineDays + interventionDays <= durationDays
        ExperimentCreationStep.REVIEW -> true
    }

    fun resolvedTestingTarget(): String = customTestingTarget.takeIf { testingTarget == "Custom" && it.isNotBlank() } ?: testingTarget.orEmpty()

    fun resolvedIntervention(): String = customIntervention.takeIf { intervention == "Custom intervention" && it.isNotBlank() } ?: intervention.orEmpty()

    fun protocolTitle(): String = customTitle?.takeIf { it.isNotBlank() }
        ?: listOf(resolvedIntervention(), resolvedTestingTarget()).filter { it.isNotBlank() }.joinToString(" → ").ifBlank { "New personal experiment" }
}

internal object ExperimentOptions {
    val testingTargets = listOf("Sleep", "Energy", "Mood", "Pain", "Focus", "Heart rate", "Blood pressure", "Performance", "Custom")
    val interventions = listOf("Caffeine", "Exercise", "Breathing", "Light", "Temperature", "Posture", "Device / sensor", "Custom intervention")
    val outcomes = listOf("Sleep", "Heart rate", "Mood", "Symptoms", "Body temperature", "Blood pressure", "Steps", "Manual score")
}

internal object MockExperimentData {
    val devices = listOf(
        ExperimentDevice("wii", "Nintendo Wii Remote", "Movement and balance input", ExperimentDeviceStatus.AVAILABLE_LATER),
        ExperimentDevice("phone", "Phone sensors", "Motion, orientation and reaction tasks", ExperimentDeviceStatus.CONNECTED),
        ExperimentDevice("wearable", "Wearable", "Health readings shared by a watch", ExperimentDeviceStatus.NOT_CONNECTED),
        ExperimentDevice("bluetooth", "Bluetooth sensor", "Flexible future sensor input", ExperimentDeviceStatus.AVAILABLE_LATER),
        ExperimentDevice("manual", "Manual input", "Scores, notes and observations", ExperimentDeviceStatus.CONNECTED)
    )

    val active = ExperimentPresentation(
        id = "morning-light",
        title = "Morning light exposure",
        hypothesis = "Getting outdoor light within 30 minutes of waking may improve sleep quality and daytime energy.",
        status = ExperimentStatus.ACTIVE,
        phase = ExperimentPhase.INTERVENTION,
        progress = ExperimentProgress(currentDay = 4, totalDays = 7, completedCheckIns = 6, plannedCheckIns = 10),
        intervention = ExperimentIntervention("Light", "20 minutes of morning light", "Go outside within 30 minutes of waking and record completion."),
        outcomes = listOf(
            ExperimentOutcome("sleep", "Sleep quality", ExperimentOutcomeRole.PRIMARY, beforeValue = 6.1, duringValue = 6.7),
            ExperimentOutcome("mood", "Mood", ExperimentOutcomeRole.SECONDARY, beforeValue = 6.4, duringValue = 6.6),
            ExperimentOutcome("energy", "Energy", ExperimentOutcomeRole.SECONDARY, beforeValue = 5.8, duringValue = 6.2)
        ),
        nextCheckIn = "Tonight · 21:00",
        scheduleSummary = "2 baseline days · 5 intervention days · twice daily",
        timeline = listOf(
            ExperimentTimelineItem("Baseline", "Days 1–2 · normal routine", ExperimentPhase.BASELINE, completed = true),
            ExperimentTimelineItem("Morning light", "Days 3–7 · 20 minutes outdoors", ExperimentPhase.INTERVENTION, completed = false),
            ExperimentTimelineItem("Review", "After the final evening check-in", ExperimentPhase.FOLLOW_UP, completed = false)
        ),
        observations = listOf(
            ExperimentObservation("Day 4 · AM", ExperimentPhase.INTERVENTION, "Energy 6/10", "Overcast, still completed 20 min"),
            ExperimentObservation("Day 3 · PM", ExperimentPhase.INTERVENTION, "Sleep 7/10", "Fell asleep a little faster"),
            ExperimentObservation("Day 2 · PM", ExperimentPhase.BASELINE, "Sleep 6/10", "Normal routine")
        ),
        devices = listOf(devices[1], devices[4]),
        notes = "Keep wake time roughly consistent so the routine is easier to interpret.",
        result = ExperimentResultSummary(
            headline = "Sleep quality is slightly higher so far during the intervention period.",
            confidence = ExperimentConfidence.TOO_EARLY,
            primaryChange = "+0.6 sleep-quality points",
            secondaryChanges = listOf("Mood +0.2", "Energy +0.4"),
            isFinal = false
        )
    )

    val recent = listOf(
        recentExperiment("caffeine", "Caffeine cutoff", "Sleep", "+0.8", ExperimentConfidence.POSSIBLE_SIGNAL),
        recentExperiment("cold", "Cold shower trial", "Energy", "+0.3", ExperimentConfidence.TOO_EARLY),
        recentExperiment("breathing", "Evening breathing", "Resting HR", "−2 bpm", ExperimentConfidence.CONSISTENT_CHANGE),
        recentExperiment("posture", "Posture reminder", "Pain", "−0.5", ExperimentConfidence.POSSIBLE_SIGNAL),
        recentExperiment("wii", "Wii balance test", "Balance score", "+4%", ExperimentConfidence.TOO_EARLY)
    )

    private fun recentExperiment(
        id: String,
        title: String,
        outcome: String,
        change: String,
        confidence: ExperimentConfidence
    ) = ExperimentPresentation(
        id = id,
        title = title,
        hypothesis = "A structured change may produce a useful personal signal in $outcome.",
        status = ExperimentStatus.COMPLETED,
        phase = ExperimentPhase.COMPLETE,
        progress = ExperimentProgress(7, 7, 7, 7),
        intervention = ExperimentIntervention("Routine", title, "Follow the planned protocol consistently."),
        outcomes = listOf(ExperimentOutcome(outcome.lowercase(), outcome, ExperimentOutcomeRole.PRIMARY, beforeValue = 6.0, duringValue = 6.4)),
        nextCheckIn = null,
        scheduleSummary = "7 days · daily check-in",
        timeline = listOf(ExperimentTimelineItem("Complete", "All planned observations recorded", ExperimentPhase.COMPLETE, true)),
        observations = emptyList(),
        result = ExperimentResultSummary(
            headline = "$outcome changed during this trial.",
            confidence = confidence,
            primaryChange = change,
            secondaryChanges = emptyList(),
            isFinal = true
        )
    )

    fun fromDraft(draft: ExperimentDraft): ExperimentPresentation {
        val primary = draft.primaryOutcome ?: draft.outcomes.firstOrNull() ?: "Manual score"
        return ExperimentPresentation(
            id = "draft-preview",
            title = draft.protocolTitle(),
            hypothesis = "Changing ${draft.resolvedIntervention().lowercase()} may affect ${draft.resolvedTestingTarget().lowercase()}.",
            status = ExperimentStatus.DRAFT,
            phase = ExperimentPhase.BASELINE,
            progress = ExperimentProgress(0, draft.durationDays, 0, draft.durationDays * draft.dailyFrequency),
            intervention = ExperimentIntervention(draft.intervention.orEmpty(), draft.resolvedIntervention(), "Protocol details can be refined when real storage is connected."),
            outcomes = draft.outcomes.map { title -> ExperimentOutcome(title.lowercase().replace(' ', '-'), title, if (title == primary) ExperimentOutcomeRole.PRIMARY else ExperimentOutcomeRole.SECONDARY) },
            nextCheckIn = "Not scheduled · UI preview",
            scheduleSummary = "${draft.baselineDays} baseline days · ${draft.interventionDays} intervention days · ${draft.dailyFrequency}× daily",
            timeline = listOf(
                ExperimentTimelineItem("Baseline", "${draft.baselineDays} days", ExperimentPhase.BASELINE, false),
                ExperimentTimelineItem("Intervention", "${draft.interventionDays} days", ExperimentPhase.INTERVENTION, false),
                ExperimentTimelineItem("Review", "After the final check-in", ExperimentPhase.FOLLOW_UP, false)
            ),
            observations = emptyList(),
            devices = listOfNotNull(draft.device),
            notes = "Presentation preview only — this protocol has not been saved or started.",
            result = null
        )
    }
}
