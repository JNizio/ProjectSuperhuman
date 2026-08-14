package com.projectsuperhuman.next

import androidx.compose.ui.graphics.Color

internal enum class InsightDirection(val label: String) {
    MOVE_TOGETHER("Move together"),
    MOVE_OPPOSITE("Move in opposite directions"),
    MORE_AFTER("Appears more often after"),
    MORE_STABLE_WITH("Looks steadier with")
}

internal enum class InsightConfidence(val label: String, val progress: Float) {
    EMERGING("Emerging", 0.34f),
    MODERATE("Moderate", 0.64f),
    STRONGER_PATTERN("Stronger pattern", 0.86f)
}

internal enum class InsightCategory(val label: String) {
    RECOVERY("Recovery"),
    ACTIVITY("Activity"),
    SYMPTOMS("Symptoms"),
    EMOTIONAL("Emotional"),
    CARDIOVASCULAR("Cardiovascular")
}

internal enum class InsightMetricTone(val color: Color) {
    SLEEP(Color(0xFF6575C7)),
    STRESS(Color(0xFF9B6DB2)),
    HEART(Color(0xFFD96D75)),
    ACTIVITY(Color(0xFF2AA5A4)),
    SYMPTOM(Color(0xFFE08A5C)),
    MOOD(Color(0xFF8B79C8)),
    ENVIRONMENT(Color(0xFF3A9CC0)),
    BLOOD_PRESSURE(Color(0xFF0D6CB4))
}

internal data class InsightMetricPresentation(
    val id: String,
    val label: String,
    val tone: InsightMetricTone
)

internal data class InsightPairedPoint(
    val label: String,
    val sourceLevel: Float,
    val targetLevel: Float
) {
    init {
        require(sourceLevel in 0f..1f)
        require(targetLevel in 0f..1f)
    }
}

internal data class InsightPresentation(
    val id: String,
    val sourceMetric: InsightMetricPresentation,
    val targetMetric: InsightMetricPresentation,
    val headline: String,
    val description: String,
    val strength: Float,
    val confidence: InsightConfidence,
    val timeframe: String,
    val sampleCount: Int,
    val freshness: String,
    val direction: InsightDirection,
    val category: InsightCategory,
    val timeline: List<InsightPairedPoint>,
    val relatedContext: List<String>,
    val worthWatching: String
) {
    init {
        require(id.isNotBlank())
        require(strength in 0f..1f)
        require(sampleCount >= 0)
    }
}

internal data class InsightsPresentationState(
    val summaryHeadline: String,
    val summaryDescription: String,
    val insights: List<InsightPresentation>
)

/** Presentation boundary. A future analytics adapter can replace this provider unchanged. */
internal fun interface InsightsPresentationProvider {
    fun load(): InsightsPresentationState
}

internal object InsightsUiRuntime {
    var provider: InsightsPresentationProvider = MockInsightsPresentationProvider
        private set

    fun install(provider: InsightsPresentationProvider) {
        this.provider = provider
    }
}

internal object MockInsightsPresentationProvider : InsightsPresentationProvider {
    override fun load(): InsightsPresentationState = InsightsPresentationState(
        summaryHeadline = "3 patterns worth watching",
        summaryDescription = "Recent connections across recovery, activity and daily context.",
        insights = listOf(
            InsightPresentation(
                id = "sleep-evening-stress",
                sourceMetric = InsightMetricPresentation("evening_stress", "Evening stress", InsightMetricTone.STRESS),
                targetMetric = InsightMetricPresentation("sleep_quality", "Sleep quality", InsightMetricTone.SLEEP),
                headline = "Better sleep tends to follow lower evening stress",
                description = "On calmer evenings, your next sleep score has often been higher in this mock history.",
                strength = 0.78f,
                confidence = InsightConfidence.STRONGER_PATTERN,
                timeframe = "Last 8 weeks",
                sampleCount = 42,
                freshness = "Updated today",
                direction = InsightDirection.MOVE_OPPOSITE,
                category = InsightCategory.RECOVERY,
                timeline = listOf(
                    InsightPairedPoint("W1", .72f, .38f), InsightPairedPoint("W2", .46f, .62f),
                    InsightPairedPoint("W3", .68f, .42f), InsightPairedPoint("W4", .30f, .78f),
                    InsightPairedPoint("W5", .52f, .60f), InsightPairedPoint("W6", .24f, .84f),
                    InsightPairedPoint("W7", .44f, .66f), InsightPairedPoint("W8", .32f, .76f)
                ),
                relatedContext = listOf("Weekdays", "Evening check-ins", "Sleep score"),
                worthWatching = "Whether the pattern remains when bedtime and activity are similar."
            ),
            InsightPresentation(
                id = "activity-resting-heart-rate",
                sourceMetric = InsightMetricPresentation("daily_activity", "Daily activity", InsightMetricTone.ACTIVITY),
                targetMetric = InsightMetricPresentation("resting_heart_rate", "Resting heart rate", InsightMetricTone.HEART),
                headline = "Resting heart rate has been lower on more active days",
                description = "More active days have lined up with a slightly lower resting heart rate in the mock sample.",
                strength = 0.62f,
                confidence = InsightConfidence.MODERATE,
                timeframe = "Last 6 weeks",
                sampleCount = 35,
                freshness = "Updated 2 days ago",
                direction = InsightDirection.MOVE_OPPOSITE,
                category = InsightCategory.ACTIVITY,
                timeline = listOf(
                    InsightPairedPoint("W1", .42f, .66f), InsightPairedPoint("W2", .70f, .45f),
                    InsightPairedPoint("W3", .55f, .57f), InsightPairedPoint("W4", .76f, .38f),
                    InsightPairedPoint("W5", .64f, .46f), InsightPairedPoint("W6", .82f, .34f)
                ),
                relatedContext = listOf("Steps", "Exercise days", "Resting readings"),
                worthWatching = "Whether recovery, hydration or workout intensity better explains the difference."
            ),
            InsightPresentation(
                id = "mood-daylight",
                sourceMetric = InsightMetricPresentation("daylight", "Brighter days", InsightMetricTone.ENVIRONMENT),
                targetMetric = InsightMetricPresentation("positive_mood", "Positive mood", InsightMetricTone.MOOD),
                headline = "Mood has been more positive on brighter days",
                description = "Positive emotional check-ins appear somewhat more often on brighter mock days.",
                strength = 0.43f,
                confidence = InsightConfidence.EMERGING,
                timeframe = "Last 30 days",
                sampleCount = 21,
                freshness = "Updated today",
                direction = InsightDirection.MOVE_TOGETHER,
                category = InsightCategory.EMOTIONAL,
                timeline = listOf(
                    InsightPairedPoint("1", .32f, .46f), InsightPairedPoint("5", .74f, .70f),
                    InsightPairedPoint("9", .48f, .52f), InsightPairedPoint("13", .82f, .76f),
                    InsightPairedPoint("17", .58f, .54f), InsightPairedPoint("21", .86f, .72f),
                    InsightPairedPoint("25", .38f, .50f), InsightPairedPoint("30", .66f, .64f)
                ),
                relatedContext = listOf("Daylight", "Weather", "Emotional check-ins"),
                worthWatching = "More check-ins across darker and brighter weeks would make this easier to judge."
            )
        )
    )
}
