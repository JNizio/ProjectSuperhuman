package com.projectsuperhuman.next

import java.util.Locale

/**
 * Consumer-facing search layer over the raw ICD catalogue.
 *
 * ICD remains the canonical stored record, but users can search using everyday language
 * ("colon cancer", "high blood pressure", "acid reflux", etc.). Results are ranked by
 * likely human intent and common names are displayed before formal coding terminology.
 */
internal data class ClinicalConditionSearchResult(
    val condition: ClinicalCondition,
    val displayTitle: String,
    val officialTitle: String,
    val score: Int,
    val matchLabel: String
)

internal object ClinicalConditionSearchEngine {
    private data class Route(
        val commonName: String,
        val aliases: List<String>,
        val searchHints: List<String>,
        val officialSignals: List<List<String>>,
        val popularity: Int
    )

    private val routes = listOf(
        Route(
            "Colon cancer",
            listOf("colon cancer", "bowel cancer", "colorectal cancer", "cancer of the colon", "large bowel cancer"),
            listOf("malignant neoplasm colon", "malignant neoplasm large intestine", "colon"),
            listOf(listOf("malignant", "colon"), listOf("malignant", "large intestine")),
            100
        ),
        Route(
            "Lung cancer",
            listOf("lung cancer", "cancer of the lung", "bronchial cancer"),
            listOf("malignant neoplasm lung", "malignant neoplasm bronchus", "lung"),
            listOf(listOf("malignant", "lung"), listOf("malignant", "bronchus")),
            99
        ),
        Route(
            "Breast cancer",
            listOf("breast cancer", "cancer of the breast"),
            listOf("malignant neoplasm breast", "breast"),
            listOf(listOf("malignant", "breast")),
            99
        ),
        Route(
            "Prostate cancer",
            listOf("prostate cancer", "cancer of the prostate"),
            listOf("malignant neoplasm prostate", "prostate"),
            listOf(listOf("malignant", "prostate")),
            98
        ),
        Route(
            "Skin cancer",
            listOf("skin cancer", "cancer of the skin"),
            listOf("malignant neoplasm skin", "skin"),
            listOf(listOf("malignant", "skin")),
            95
        ),
        Route(
            "Melanoma",
            listOf("melanoma", "malignant melanoma", "melanoma skin cancer"),
            listOf("melanoma"),
            listOf(listOf("melanoma")),
            94
        ),
        Route(
            "Cervical cancer",
            listOf("cervical cancer", "cervix cancer", "cancer of the cervix"),
            listOf("malignant neoplasm cervix", "cervix"),
            listOf(listOf("malignant", "cervix")),
            93
        ),
        Route(
            "Ovarian cancer",
            listOf("ovarian cancer", "ovary cancer", "cancer of the ovary"),
            listOf("malignant neoplasm ovary", "ovary"),
            listOf(listOf("malignant", "ovary")),
            92
        ),
        Route(
            "Pancreatic cancer",
            listOf("pancreatic cancer", "pancreas cancer", "cancer of the pancreas"),
            listOf("malignant neoplasm pancreas", "pancreas"),
            listOf(listOf("malignant", "pancreas")),
            91
        ),
        Route(
            "Stomach cancer",
            listOf("stomach cancer", "gastric cancer", "cancer of the stomach"),
            listOf("malignant neoplasm stomach", "stomach"),
            listOf(listOf("malignant", "stomach")),
            90
        ),
        Route(
            "Liver cancer",
            listOf("liver cancer", "cancer of the liver"),
            listOf("malignant neoplasm liver", "liver"),
            listOf(listOf("malignant", "liver")),
            89
        ),
        Route(
            "Kidney cancer",
            listOf("kidney cancer", "renal cancer", "cancer of the kidney"),
            listOf("malignant neoplasm kidney", "kidney"),
            listOf(listOf("malignant", "kidney")),
            88
        ),
        Route(
            "Bladder cancer",
            listOf("bladder cancer", "cancer of the bladder"),
            listOf("malignant neoplasm bladder", "bladder"),
            listOf(listOf("malignant", "bladder")),
            87
        ),
        Route(
            "High blood pressure (Hypertension)",
            listOf("high blood pressure", "hypertension", "high bp", "raised blood pressure"),
            listOf("hypertension", "essential hypertension"),
            listOf(listOf("hypertension")),
            100
        ),
        Route(
            "Type 2 diabetes",
            listOf("type 2 diabetes", "diabetes type 2", "t2 diabetes", "t2d", "diabetes"),
            listOf("type 2 diabetes mellitus", "type 2 diabetes"),
            listOf(listOf("type 2", "diabetes")),
            100
        ),
        Route(
            "Type 1 diabetes",
            listOf("type 1 diabetes", "diabetes type 1", "t1 diabetes", "t1d"),
            listOf("type 1 diabetes mellitus", "type 1 diabetes"),
            listOf(listOf("type 1", "diabetes")),
            96
        ),
        Route(
            "Asthma",
            listOf("asthma", "bronchial asthma"),
            listOf("asthma"),
            listOf(listOf("asthma")),
            100
        ),
        Route(
            "COPD",
            listOf("copd", "chronic obstructive pulmonary disease", "emphysema"),
            listOf("chronic obstructive pulmonary disease", "emphysema"),
            listOf(listOf("chronic obstructive pulmonary"), listOf("emphysema")),
            98
        ),
        Route(
            "Acid reflux (GORD/GERD)",
            listOf("acid reflux", "gerd", "gord", "reflux", "heartburn", "gastroesophageal reflux", "gastro-oesophageal reflux"),
            listOf("gastro-oesophageal reflux", "gastroesophageal reflux", "reflux disease"),
            listOf(listOf("reflux", "gastro")),
            100
        ),
        Route(
            "Irritable bowel syndrome (IBS)",
            listOf("ibs", "irritable bowel", "irritable bowel syndrome"),
            listOf("irritable bowel syndrome"),
            listOf(listOf("irritable bowel")),
            100
        ),
        Route(
            "Coeliac disease",
            listOf("coeliac", "celiac", "coeliac disease", "celiac disease", "gluten disease"),
            listOf("coeliac disease", "celiac disease"),
            listOf(listOf("coeliac"), listOf("celiac")),
            92
        ),
        Route(
            "Crohn disease",
            listOf("crohns", "crohn's", "crohn disease", "crohn's disease"),
            listOf("crohn disease", "crohn"),
            listOf(listOf("crohn")),
            92
        ),
        Route(
            "Ulcerative colitis",
            listOf("ulcerative colitis", "uc"),
            listOf("ulcerative colitis"),
            listOf(listOf("ulcerative colitis")),
            91
        ),
        Route(
            "Depression",
            listOf("depression", "depressive disorder", "major depression", "major depressive disorder", "mdd"),
            listOf("depressive disorder", "depression"),
            listOf(listOf("depress")),
            100
        ),
        Route(
            "Generalized anxiety disorder (GAD)",
            listOf("anxiety", "generalized anxiety", "generalised anxiety", "gad", "anxiety disorder"),
            listOf("generalized anxiety disorder", "generalised anxiety disorder", "anxiety disorder"),
            listOf(listOf("anxiety")),
            100
        ),
        Route(
            "ADHD",
            listOf("adhd", "attention deficit hyperactivity disorder", "attention deficit disorder", "add"),
            listOf("attention deficit hyperactivity disorder"),
            listOf(listOf("attention deficit", "hyperactivity")),
            99
        ),
        Route(
            "Migraine",
            listOf("migraine", "migraines"),
            listOf("migraine"),
            listOf(listOf("migraine")),
            99
        ),
        Route(
            "Underactive thyroid (Hypothyroidism)",
            listOf("underactive thyroid", "hypothyroidism", "low thyroid"),
            listOf("hypothyroidism"),
            listOf(listOf("hypothyroid")),
            96
        ),
        Route(
            "Overactive thyroid (Hyperthyroidism)",
            listOf("overactive thyroid", "hyperthyroidism", "high thyroid"),
            listOf("hyperthyroidism"),
            listOf(listOf("hyperthyroid")),
            94
        ),
        Route(
            "High cholesterol",
            listOf("high cholesterol", "hypercholesterolemia", "hypercholesterolaemia", "high lipids", "high cholesterol levels"),
            listOf("hypercholester", "lipoprotein metabolism", "hyperlipidaemia", "hyperlipidemia"),
            listOf(listOf("hypercholester"), listOf("lipoprotein", "metabolism"), listOf("hyperlip")),
            96
        ),
        Route(
            "Atrial fibrillation (AFib)",
            listOf("atrial fibrillation", "afib", "a fib", "af"),
            listOf("atrial fibrillation"),
            listOf(listOf("atrial fibrillation")),
            95
        ),
        Route(
            "Heart failure",
            listOf("heart failure", "cardiac failure", "congestive heart failure", "chf"),
            listOf("heart failure"),
            listOf(listOf("heart failure")),
            95
        ),
        Route(
            "Coronary heart disease",
            listOf("coronary heart disease", "coronary artery disease", "cad", "ischemic heart disease", "ischaemic heart disease"),
            listOf("ischaemic heart disease", "ischemic heart disease", "coronary"),
            listOf(listOf("ischaemic", "heart"), listOf("ischemic", "heart"), listOf("coronary")),
            94
        ),
        Route(
            "Chronic kidney disease (CKD)",
            listOf("chronic kidney disease", "ckd", "chronic renal disease", "kidney disease"),
            listOf("chronic kidney disease", "chronic renal"),
            listOf(listOf("chronic", "kidney"), listOf("chronic", "renal")),
            94
        ),
        Route(
            "Osteoarthritis",
            listOf("osteoarthritis", "wear and tear arthritis", "degenerative arthritis"),
            listOf("osteoarthritis"),
            listOf(listOf("osteoarthritis")),
            96
        ),
        Route(
            "Rheumatoid arthritis",
            listOf("rheumatoid arthritis", "ra arthritis", "ra"),
            listOf("rheumatoid arthritis"),
            listOf(listOf("rheumatoid", "arthritis")),
            95
        ),
        Route(
            "Psoriasis",
            listOf("psoriasis"),
            listOf("psoriasis"),
            listOf(listOf("psoriasis")),
            92
        ),
        Route(
            "Fibromyalgia",
            listOf("fibromyalgia", "fibro"),
            listOf("fibromyalgia"),
            listOf(listOf("fibromyalgia")),
            90
        ),
        Route(
            "Multiple sclerosis (MS)",
            listOf("multiple sclerosis", "ms"),
            listOf("multiple sclerosis"),
            listOf(listOf("multiple sclerosis")),
            91
        ),
        Route(
            "Parkinson disease",
            listOf("parkinsons", "parkinson's", "parkinson disease", "parkinson's disease"),
            listOf("parkinson disease", "parkinson"),
            listOf(listOf("parkinson")),
            90
        ),
        Route(
            "Obesity",
            listOf("obesity", "obese"),
            listOf("obesity"),
            listOf(listOf("obesity")),
            95
        ),
        Route(
            "Endometriosis",
            listOf("endometriosis"),
            listOf("endometriosis"),
            listOf(listOf("endometriosis")),
            90
        ),
        Route(
            "Polycystic ovary syndrome (PCOS)",
            listOf("pcos", "polycystic ovary syndrome", "polycystic ovarian syndrome"),
            listOf("polycystic ovary", "polycystic ovarian"),
            listOf(listOf("polycystic", "ovar")),
            92
        )
    )

    private val tokenSynonyms = mapOf(
        "cancer" to listOf("malignant neoplasm", "malignancy"),
        "tumor" to listOf("neoplasm", "tumour"),
        "tumour" to listOf("neoplasm", "tumor"),
        "bp" to listOf("blood pressure", "hypertension"),
        "reflux" to listOf("gastro-oesophageal reflux", "gastroesophageal reflux"),
        "kidney" to listOf("renal"),
        "renal" to listOf("kidney"),
        "heart" to listOf("cardiac"),
        "cardiac" to listOf("heart"),
        "bowel" to listOf("intestine", "colon", "intestinal"),
        "thyroid" to listOf("hypothyroidism", "hyperthyroidism")
    )

    private val lowInformationWords = setOf(
        "the", "of", "and", "or", "a", "an", "with", "without", "disease", "disorder", "condition"
    )

    private val obscurePhrases = listOf(
        "other specified", "unspecified", "secondary to", "due to", "in diseases classified elsewhere",
        "with complication", "without complication", "not elsewhere classified"
    )

    fun search(vault: ClinicalConditionVault, rawQuery: String, limit: Int = 30): List<ClinicalConditionSearchResult> {
        val query = normalize(rawQuery)
        if (query.length < 2) return emptyList()

        val matchedRoutes = routes.filter { route ->
            route.aliases.any { alias -> aliasMatchesQuery(normalize(alias), query) } ||
                normalize(route.commonName).let { aliasMatchesQuery(it, query) }
        }

        val searchTerms = linkedSetOf<String>()
        searchTerms += query

        val queryTokens = tokens(query)
        queryTokens.forEach { token ->
            searchTerms += token
            tokenSynonyms[token]?.forEach { searchTerms += it }
        }

        if ("cancer" in queryTokens) {
            val site = queryTokens.filterNot { it == "cancer" || it in lowInformationWords }.joinToString(" ")
            if (site.isNotBlank()) {
                searchTerms += "malignant neoplasm $site"
                searchTerms += site
            }
        }

        matchedRoutes.forEach { route ->
            route.searchHints.forEach { searchTerms += normalize(it) }
        }

        val candidates = linkedMapOf<String, ClinicalCondition>()
        searchTerms.filter { it.length >= 2 }.take(14).forEachIndexed { index, term ->
            val perTerm = if (index == 0) 100 else 60
            vault.search(term, perTerm).forEach { candidate ->
                candidates.putIfAbsent(candidate.id.ifBlank { candidate.code + candidate.title }, candidate)
            }
        }

        return candidates.values
            .map { candidate -> rank(candidate, query, queryTokens, matchedRoutes) }
            .sortedWith(compareByDescending<ClinicalConditionSearchResult> { it.score }
                .thenBy { it.displayTitle.length }
                .thenBy { it.displayTitle.lowercase(Locale.ROOT) })
            .take(limit)
    }

    fun friendlyTitle(officialTitle: String): String {
        val official = normalize(officialTitle)
        routeForOfficial(official)?.let { return it.commonName }

        val malignantPrefix = "malignant neoplasm of "
        if (official.startsWith(malignantPrefix)) {
            val site = officialTitle.substring(malignantPrefix.length).trim()
            if (site.isNotBlank()) {
                return site.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } + " cancer"
            }
        }

        return officialTitle
    }

    private fun rank(
        condition: ClinicalCondition,
        query: String,
        queryTokens: List<String>,
        matchedRoutes: List<Route>
    ): ClinicalConditionSearchResult {
        val official = normalize(condition.title)
        val display = friendlyTitle(condition.title)
        val displayNorm = normalize(display)
        val code = normalize(condition.code)
        val candidateRoute = routeForOfficial(official)
        var score = 0
        var label = "ICD match"

        if (displayNorm == query) {
            score += 2400
            label = "Common name"
        } else if (displayNorm.startsWith(query)) {
            score += 2050
            label = "Common name"
        } else if (official == query || code == query) {
            score += 1900
            label = if (code == query) "ICD-11 code" else "Exact medical name"
        } else if (official.startsWith(query)) {
            score += 1600
            label = "Medical name"
        }

        if (candidateRoute != null) {
            score += 420 + candidateRoute.popularity
            if (matchedRoutes.any { it.commonName == candidateRoute.commonName }) {
                score += 1200
                label = "Best match"
            }
            if (candidateRoute.aliases.any { normalize(it) == query }) {
                score += 700
                label = "Everyday name"
            }
        }

        val searchable = "$displayNorm $official $code"
        val meaningfulTokens = queryTokens.filterNot { it in lowInformationWords }
        val hitCount = meaningfulTokens.count { token ->
            searchable.contains(token) || tokenSynonyms[token].orEmpty().any { searchable.contains(normalize(it)) }
        }
        score += hitCount * 110
        if (meaningfulTokens.isNotEmpty() && hitCount == meaningfulTokens.size) score += 350

        if (query.contains("cancer") && official.contains("malignant") && official.contains("neoplasm")) {
            score += 220
        }

        if (obscurePhrases.any { official.contains(it) }) score -= 260
        if (official.length > 80) score -= 90
        if (official.length > 130) score -= 120

        return ClinicalConditionSearchResult(
            condition = condition,
            displayTitle = display,
            officialTitle = condition.title,
            score = score,
            matchLabel = label
        )
    }

    private fun routeForOfficial(official: String): Route? = routes
        .filter { route ->
            route.officialSignals.any { signalGroup ->
                signalGroup.all { signal -> official.contains(normalize(signal)) }
            }
        }
        .maxByOrNull { it.popularity }

    private fun aliasMatchesQuery(alias: String, query: String): Boolean {
        if (alias == query) return true
        if (query.length >= 4 && alias.startsWith(query)) return true
        if (alias.length >= 4 && query.startsWith(alias)) return true
        return false
    }

    private fun tokens(value: String): List<String> = normalize(value)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.length >= 2 }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()
}
