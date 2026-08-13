package com.projectsuperhuman.next.trudy

import com.projectsuperhuman.next.core.HealthDomain
import kotlin.math.round

internal object TrudyEmotionalResponseText {
    fun selfReport(phrase: String): String = if (phrase.startsWith("in ")) "being $phrase" else "feeling $phrase"

    fun qualityTail(confidence: TrudyConfidence, status: TrudyDataQualityStatus): String = when {
        status == TrudyDataQualityStatus.STALE -> " The latest check-ins are a little old, so I'd treat that cautiously."
        confidence == TrudyConfidence.LOW || status == TrudyDataQualityStatus.SPARSE || status == TrudyDataQualityStatus.LIMITED ->
            " It's still a tentative pattern with the amount of data available."
        else -> ""
    }

    fun driverPhrase(domain: HealthDomain, metric: String): String = when (domain) {
        HealthDomain.EXERCISE -> when {
            "minute" in metric -> "on days when you exercised more"
            "steps" in metric -> "on higher-step days"
            else -> "on more active days"
        }
        HealthDomain.SLEEP -> when {
            "continuity" in metric -> "around nights when your sleep was more continuous"
            "total" in metric || "duration" in metric -> "around nights when you slept longer"
            "deep" in metric -> "around nights with more deep sleep"
            else -> "around nights with higher sleep scores"
        }
        else -> "when your ${driverLabel(domain)} measure was higher"
    }

    fun driverLabel(domain: HealthDomain): String = when (domain) {
        HealthDomain.EXERCISE -> "activity"
        HealthDomain.SLEEP -> "sleep"
        else -> domain.name.lowercase().replace('_', ' ')
    }

    fun sign(value: Double): Int = when {
        value > 1e-6 -> 1
        value < -1e-6 -> -1
        else -> 0
    }

    fun format(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
