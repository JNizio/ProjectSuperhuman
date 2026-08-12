package com.projectsuperhuman.next

import java.util.Locale

/**
 * Adds a deliberately non-specific condition choice above ICD-11 search results.
 *
 * A user may know that they have a condition without knowing its exact coded subtype. In that
 * case Project Superhuman must preserve that uncertainty instead of silently turning a broad
 * statement into a more specific diagnosis. The general entry is therefore a first-class profile
 * record with no ICD code; the specific ICD matches remain available underneath for users who do
 * know the exact diagnosis.
 *
 * This layer is generic: it works for every sufficiently relevant condition search, not only a
 * hand-written list such as hypertension or arthritis.
 */
internal object ClinicalConditionHierarchy {
    private const val GENERAL_PREFIX = "general:"
    private const val GENERAL_SOURCE = "Project Superhuman general condition"
    private const val GENERAL_VERSION = "1"

    // These are anatomical/measurement words that can return many ICD rows but are not, by
    // themselves, sensible conditions to save. More descriptive searches such as "heart disease"
    // or "chronic back pain" are still allowed.
    private val blockedSingleTerms = setOf(
        "blood", "pressure", "heart", "cardiac", "lung", "lungs", "kidney", "renal", "liver",
        "brain", "skin", "bone", "joint", "stomach", "bowel", "colon", "thyroid", "nerve",
        "muscle", "eye", "eyes", "ear", "ears", "pain", "symptom", "test", "abnormal",
        "disease", "disorder", "condition", "syndrome"
    )

    // Common synonyms resolve to one stable broad family ID so, for example, searching
    // "hypertension" today and "high blood pressure" later does not create two general records.
    // Everything not listed here still gets the same generic broad-option behaviour from its query.
    private val preferredLabels = mapOf(
        "hypertension" to "High blood pressure (Hypertension)",
        "high blood pressure" to "High blood pressure (Hypertension)",
        "high bp" to "High blood pressure (Hypertension)",
        "raised blood pressure" to "High blood pressure (Hypertension)",
        "arthritis" to "Arthritis",
        "anemia" to "Anaemia",
        "anaemia" to "Anaemia",
        "diabetes" to "Diabetes",
        "heart disease" to "Heart disease",
        "cardiac disease" to "Heart disease",
        "kidney disease" to "Kidney disease",
        "renal disease" to "Kidney disease",
        "thyroid disease" to "Thyroid disease",
        "cancer" to "Cancer",
        "anxiety" to "Anxiety",
        "depression" to "Depression",
        "asthma" to "Asthma",
        "epilepsy" to "Epilepsy",
        "migraine" to "Migraine",
        "acid reflux" to "Acid reflux",
        "reflux" to "Acid reflux",
        "heartburn" to "Acid reflux",
        "ibs" to "Irritable bowel syndrome (IBS)",
        "gerd" to "Acid reflux (GERD)",
        "gord" to "Acid reflux (GORD)",
        "copd" to "COPD",
        "adhd" to "ADHD",
        "ckd" to "Chronic kidney disease (CKD)",
        "afib" to "Atrial fibrillation (AFib)",
        "a fib" to "Atrial fibrillation (AFib)",
        "pcos" to "Polycystic ovary syndrome (PCOS)",
        "ms" to "Multiple sclerosis (MS)",
        "t1d" to "Type 1 diabetes",
        "t2d" to "Type 2 diabetes"
    )

    fun search(
        vault: ClinicalConditionVault,
        rawQuery: String,
        limit: Int = 30
    ): List<ClinicalConditionSearchResult> {
        val specific = ClinicalConditionSearchEngine.search(vault, rawQuery, maxOf(limit, 30))
        if (specific.isEmpty()) return emptyList()

        val general = buildGeneralResult(rawQuery, specific)
        if (general == null) return specific.take(limit)

        return buildList {
            add(general)
            specific
                .asSequence()
                .filterNot { sameVisibleCondition(it, general) }
                .take((limit - 1).coerceAtLeast(0))
                .forEach(::add)
        }
    }

    fun isGeneral(condition: ClinicalCondition): Boolean =
        condition.id.startsWith(GENERAL_PREFIX) || condition.source == GENERAL_SOURCE

    fun isGeneral(condition: ActiveClinicalCondition): Boolean =
        condition.id.startsWith(GENERAL_PREFIX) || condition.source == GENERAL_SOURCE

    fun generalFamilyId(condition: ClinicalCondition): String? =
        condition.id.takeIf { it.startsWith(GENERAL_PREFIX) }

    private fun buildGeneralResult(
        rawQuery: String,
        specific: List<ClinicalConditionSearchResult>
    ): ClinicalConditionSearchResult? {
        val query = normalize(rawQuery)
        if (query.length < 2) return null

        // If the user entered an exact ICD code, they have explicitly selected coding-level detail.
        if (specific.any { it.condition.code.isNotBlank() && it.condition.code.equals(rawQuery.trim(), ignoreCase = true) }) {
            return null
        }

        val tokens = query.split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
        if (tokens.size == 1 && tokens.first() in blockedSingleTerms) return null

        // Avoid creating a broad profile record from a very weak substring hit. Exact/common-name
        // and prefix matches from the underlying search score much higher than this threshold.
        if (specific.take(8).none { it.score >= 500 }) return null

        val display = preferredLabels[query] ?: humanize(rawQuery)
        if (display.isBlank()) return null

        val key = generalKey(display)
        if (key.isBlank()) return null

        val condition = ClinicalCondition(
            id = "$GENERAL_PREFIX$key",
            code = "",
            title = display,
            source = GENERAL_SOURCE,
            sourceVersion = GENERAL_VERSION
        )

        return ClinicalConditionSearchResult(
            condition = condition,
            displayTitle = display,
            officialTitle = display,
            score = Int.MAX_VALUE,
            matchLabel = "General condition"
        )
    }

    private fun sameVisibleCondition(
        specific: ClinicalConditionSearchResult,
        general: ClinicalConditionSearchResult
    ): Boolean {
        // Keep genuinely coded rows underneath, even if their friendly label is identical. The user
        // should still be able to choose "general / subtype unknown" versus a real ICD category.
        return specific.condition.code.isBlank() &&
            normalize(specific.displayTitle) == normalize(general.displayTitle)
    }

    private fun humanize(raw: String): String {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isBlank()) return ""
        if (trimmed.length <= 5 && trimmed.none { it.isWhitespace() }) {
            return trimmed.uppercase(Locale.ROOT)
        }
        return trimmed.split(' ').joinToString(" ") { word ->
            when {
                word.isBlank() -> word
                word.all { it.isDigit() } -> word
                else -> word.lowercase(Locale.ROOT)
                    .replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString() }
            }
        }
    }

    private fun generalKey(value: String): String = normalize(value)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(80)

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
}
