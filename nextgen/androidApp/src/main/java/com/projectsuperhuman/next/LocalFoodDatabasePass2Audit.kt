package com.projectsuperhuman.next

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

/**
 * Pass 2 is intentionally not part of normal food search/bootstrap. It is a development/diagnostic
 * audit that interrogates the fully materialised SQLite database and writes compact artifacts.
 */
internal object LocalFoodDatabasePass2Auditor {
    private const val SAMPLE_TARGET = 400

    internal enum class Confidence { VERIFIED, HIGH, MEDIUM, LOW, NEEDS_REVIEW }

    data class Row(
        val id: String,
        val name: String,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fibre: Double,
        val sugar: Double,
        val saturatedFat: Double,
        val sodiumMg: Double,
        val saltG: Double,
        val proteinKnown: Boolean,
        val carbsKnown: Boolean,
        val fatKnown: Boolean,
        val fibreKnown: Boolean,
        val sugarKnown: Boolean,
        val saturatedFatKnown: Boolean,
        val sodiumKnown: Boolean,
        val saltKnown: Boolean,
        val unit: String,
        val source: String,
        val sourceRecordId: String,
        val micronutrientsJson: String,
        val unknownMicronutrientsJson: String,
        val tags: Set<FoodTag>,
        val preparationState: FoodPreparationState,
        val servingQuantity: Double?,
        val servingUnit: String,
        val servingLabel: String
    ) {
        val micronutrientCount: Int
            get() = runCatching { JSONObject(micronutrientsJson).length() }.getOrDefault(0)

        val profileFingerprint: String
            get() = listOf(
                kcal, protein, carbs, fat, fibre, sugar, saturatedFat, sodiumMg,
                micronutrientsJson
            ).joinToString("|")

        val isUsda: Boolean get() = id.startsWith("usda:") || id.startsWith("core:usda:")
    }

    data class SourceConflict(
        val coreId: String,
        val rawId: String,
        val sourceRecordId: String,
        val field: String,
        val coreValue: String,
        val rawValue: String
    )

    data class GoldenSpec(
        val name: String,
        val tokenGroups: List<List<String>>,
        val requiredPreparation: FoodPreparationState? = null,
        val kcalRange: ClosedFloatingPointRange<Double>,
        val proteinRange: ClosedFloatingPointRange<Double>? = null,
        val carbsRange: ClosedFloatingPointRange<Double>? = null,
        val fatRange: ClosedFloatingPointRange<Double>? = null,
        val requiredTags: Set<FoodTag> = emptySet()
    )

    data class GoldenResult(
        val spec: GoldenSpec,
        val matched: Row?,
        val failures: List<String>
    )

    data class Result(
        val generatedEpochMs: Long,
        val totalRecords: Int,
        val totalUsdaRecords: Int,
        val totalCoreRecords: Int,
        val uniqueCanonicalFoods: Int,
        val duplicateCandidateGroups: Int,
        val foundationCount: Int,
        val fnddsCount: Int,
        val srLegacyCount: Int,
        val nonUsdaLocalCount: Int,
        val validFdcIdCount: Int,
        val duplicateUsdaSourceRecordIds: Int,
        val databaseFingerprintSha256: String,
        val caloriesKnownCount: Int,
        val proteinKnownCount: Int,
        val carbsKnownCount: Int,
        val fatKnownCount: Int,
        val fibreKnownCount: Int,
        val sodiumKnownCount: Int,
        val plantTaggedCount: Int,
        val lowMicronutrientCoverageCount: Int,
        val averageKnownNutrientCount: Double,
        val medianKnownNutrientCount: Double,
        val categoryCounts: Map<String, Int>,
        val preparationCounts: Map<String, Int>,
        val deepSampleCount: Int,
        val verifiedCount: Int,
        val highCount: Int,
        val mediumCount: Int,
        val lowCount: Int,
        val needsReviewCount: Int,
        val coreSnapshotPairs: Int,
        val coreSnapshotConflictCount: Int,
        val goldenMatched: Int,
        val goldenFailures: Int,
        val sample: List<Pair<Row, Confidence>>,
        val sourceConflicts: List<SourceConflict>,
        val baseline: LocalFoodDatabaseAuditReport,
        val golden: List<GoldenResult>
    )

    fun audit(context: Context): Result =
        LargeFoodDb(context.applicationContext).use { audit(it.readableDatabase) }

    fun writeReports(context: Context): List<File> {
        val result = audit(context)
        val dir = File(context.filesDir, "nutrition_audits").apply { mkdirs() }
        val files = listOf(
            File(dir, "food_audit_pass2_summary.json"),
            File(dir, "food_audit_pass2_sample.csv"),
            File(dir, "food_audit_pass2_anomalies.csv"),
            File(dir, "food_audit_pass2_duplicates.csv"),
            File(dir, "food_audit_pass2_source_conflicts.csv"),
            File(dir, "food_audit_pass2_taxonomy_issues.csv"),
            File(dir, "food_audit_pass2_missing_nutrients.csv")
        )
        files[0].writeText(summaryJson(result).toString(2))
        files[1].writeText(sampleCsv(result))
        files[2].writeText(findingsCsv(result.baseline.findings))
        files[3].writeText(duplicatesCsv(result.baseline.duplicateExamples))
        files[4].writeText(sourceConflictsCsv(result.sourceConflicts))
        files[5].writeText(
            findingsCsv(
                result.baseline.findings.filter {
                    it.code.startsWith("taxonomy_") ||
                        it.code.startsWith("plant_") ||
                        it.code.startsWith("preparation_")
                }
            )
        )
        files[6].writeText(missingNutrientsCsv(result.baseline.nutrientCoverage))
        return files
    }

    internal fun audit(db: SQLiteDatabase): Result {
        val rows = readRows(db)
        val baseline = LocalFoodDatabaseAuditor.audit(db)
        val conflictResult = compareCoreSnapshots(rows)
        val findingsById = baseline.findings.groupBy { it.foodId }
        val sampleRows = stratifiedSample(rows, baseline)
        val sample = sampleRows.map { row ->
            row to confidenceFor(row, findingsById[row.id].orEmpty(), conflictResult.second[row.id].orEmpty())
        }
        val golden = goldenSpecs().map { validateGolden(it, rows) }
        val knownCounts = rows.map { row ->
            listOf(
                row.kcalKnown(),
                row.proteinKnown,
                row.carbsKnown,
                row.fatKnown,
                row.fibreKnown,
                row.sugarKnown,
                row.saturatedFatKnown,
                row.sodiumKnown,
                row.saltKnown
            ).count { it } + row.micronutrientCount
        }.sorted()
        val averageKnown = if (knownCounts.isEmpty()) 0.0 else knownCounts.average()
        val medianKnown = when {
            knownCounts.isEmpty() -> 0.0
            knownCounts.size % 2 == 1 -> knownCounts[knownCounts.size / 2].toDouble()
            else -> {
                val hi = knownCounts.size / 2
                (knownCounts[hi - 1] + knownCounts[hi]) / 2.0
            }
        }
        val categoryCounts = FoodTag.entries.associate { tag ->
            tag.name to rows.count { tag in it.tags }
        }.filterValues { it > 0 }
        val preparationCounts = FoodPreparationState.entries.associate { prep ->
            prep.name to rows.count { it.preparationState == prep }
        }.filterValues { it > 0 }
        val duplicateUsdaSourceRecordIds = rows.asSequence()
            .filter { it.id.startsWith("usda:") }
            .map { it.sourceRecordId }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .count { (_, count) -> count > 1 }
        val databaseFingerprintSha256 = databaseFingerprint(rows)

        return Result(
            generatedEpochMs = System.currentTimeMillis(),
            totalRecords = rows.size,
            totalUsdaRecords = rows.count { it.id.startsWith("usda:") },
            totalCoreRecords = rows.count { it.id.startsWith("core:") },
            uniqueCanonicalFoods = baseline.uniqueCanonicalFoods,
            duplicateCandidateGroups = baseline.duplicateCandidateGroups,
            foundationCount = rows.count { it.source.contains("Foundation", ignoreCase = true) && it.id.startsWith("usda:") },
            fnddsCount = rows.count { it.source.contains("FNDDS", ignoreCase = true) && it.id.startsWith("usda:") },
            srLegacyCount = rows.count { it.source.contains("SR Legacy", ignoreCase = true) && it.id.startsWith("usda:") },
            nonUsdaLocalCount = rows.count { !it.isUsda },
            validFdcIdCount = rows.count { it.isUsda && it.sourceRecordId.toLongOrNull() != null },
            duplicateUsdaSourceRecordIds = duplicateUsdaSourceRecordIds,
            databaseFingerprintSha256 = databaseFingerprintSha256,
            caloriesKnownCount = rows.count { it.kcalKnown() },
            proteinKnownCount = rows.count { it.proteinKnown },
            carbsKnownCount = rows.count { it.carbsKnown },
            fatKnownCount = rows.count { it.fatKnown },
            fibreKnownCount = rows.count { it.fibreKnown },
            sodiumKnownCount = rows.count { it.sodiumKnown },
            plantTaggedCount = rows.count { FoodTag.PLANT in it.tags },
            lowMicronutrientCoverageCount = rows.count { it.micronutrientCount < 4 },
            averageKnownNutrientCount = averageKnown,
            medianKnownNutrientCount = medianKnown,
            categoryCounts = categoryCounts,
            preparationCounts = preparationCounts,
            deepSampleCount = sample.size,
            // This audit deliberately never promotes a row to VERIFIED merely because it passed
            // software checks. VERIFIED is reserved for a separately documented direct source check.
            verifiedCount = sample.count { it.second == Confidence.VERIFIED },
            highCount = sample.count { it.second == Confidence.HIGH },
            mediumCount = sample.count { it.second == Confidence.MEDIUM },
            lowCount = sample.count { it.second == Confidence.LOW },
            needsReviewCount = sample.count { it.second == Confidence.NEEDS_REVIEW },
            coreSnapshotPairs = conflictResult.first,
            coreSnapshotConflictCount = conflictResult.second.values.sumOf { it.size },
            goldenMatched = golden.count { it.matched != null },
            goldenFailures = golden.sumOf { it.failures.size },
            sample = sample,
            sourceConflicts = conflictResult.second.values.flatten(),
            baseline = baseline,
            golden = golden
        )
    }

    /**
     * Stable digest of the persisted food database. The digest deliberately excludes timestamps and
     * row order, so two imports from identical source data must produce the same value. Any nutrition,
     * knownness, provenance, taxonomy, preparation or serving change changes the digest.
     */
    internal fun databaseFingerprint(rows: List<Row>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        rows.sortedBy { it.id }.forEach { row ->
            val canonical = buildString {
                append(row.id).append('\u001f')
                append(row.name).append('\u001f')
                append(row.kcal).append('|').append(row.protein).append('|').append(row.carbs)
                    .append('|').append(row.fat).append('|').append(row.fibre).append('|')
                    .append(row.sugar).append('|').append(row.saturatedFat).append('|')
                    .append(row.sodiumMg).append('|').append(row.saltG).append('\u001f')
                append(row.proteinKnown).append('|').append(row.carbsKnown).append('|')
                    .append(row.fatKnown).append('|').append(row.fibreKnown).append('|')
                    .append(row.sugarKnown).append('|').append(row.saturatedFatKnown).append('|')
                    .append(row.sodiumKnown).append('|').append(row.saltKnown).append('\u001f')
                append(row.unit).append('\u001f').append(row.source).append('\u001f')
                    .append(row.sourceRecordId).append('\u001f')
                append(row.micronutrientsJson).append('\u001f')
                    .append(row.unknownMicronutrientsJson).append('\u001f')
                append(row.tags.map { it.name }.sorted().joinToString("|")).append('\u001f')
                append(row.preparationState.name).append('\u001f')
                append(row.servingQuantity?.toString().orEmpty()).append('\u001f')
                append(row.servingUnit).append('\u001f').append(row.servingLabel)
            }
            digest.update(canonical.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun confidenceFor(
        row: Row,
        findings: List<LocalFoodAuditFinding>,
        sourceConflicts: List<SourceConflict>
    ): Confidence {
        if (findings.any { it.severity != LocalFoodAuditSeverity.INFO } || sourceConflicts.isNotEmpty()) {
            return Confidence.NEEDS_REVIEW
        }
        if (row.isUsda && row.sourceRecordId.isNotBlank() &&
            row.proteinKnown && row.carbsKnown && row.fatKnown && row.micronutrientCount >= 8
        ) return Confidence.HIGH
        if (row.sourceRecordId.isNotBlank() && row.micronutrientCount >= 4) return Confidence.MEDIUM
        return Confidence.LOW
    }

    /**
     * Curated, deterministic, non-random sample. It deliberately spans source families, taxonomy,
     * preparation states, profile richness, duplicate/copy-error candidates and outliers.
     */
    private fun stratifiedSample(
        rows: List<Row>,
        baseline: LocalFoodDatabaseAuditReport
    ): List<Row> {
        val chosen = linkedMapOf<String, Row>()
        fun add(items: Sequence<Row>, count: Int) {
            items.sortedBy { it.id }.take(count).forEach { chosen.putIfAbsent(it.id, it) }
        }

        listOf(
            "Foundation" to 35,
            "FNDDS" to 35,
            "SR Legacy" to 35
        ).forEach { (label, count) ->
            add(rows.asSequence().filter { it.id.startsWith("usda:") && it.source.contains(label, ignoreCase = true) }, count)
        }
        add(rows.asSequence().filter { !it.isUsda }, 25)
        add(rows.asSequence().filter { it.id.startsWith("core:usda:") }, 30)

        val taxonomyStrata = listOf(
            FoodTag.FRUIT, FoodTag.VEGETABLE, FoodTag.LEAFY_GREEN, FoodTag.LEGUME, FoodTag.GRAIN,
            FoodTag.WHOLE_GRAIN, FoodTag.NUT, FoodTag.SEED, FoodTag.HERB, FoodTag.SPICE,
            FoodTag.MUSHROOM, FoodTag.SEAWEED, FoodTag.DAIRY, FoodTag.EGG, FoodTag.POULTRY,
            FoodTag.BEEF, FoodTag.PORK, FoodTag.LAMB, FoodTag.GAME_MEAT, FoodTag.FISH,
            FoodTag.SHELLFISH, FoodTag.OIL, FoodTag.BREAD, FoodTag.BAKERY,
            FoodTag.BAKING_INGREDIENT, FoodTag.FERMENTED, FoodTag.SAUCE, FoodTag.PREPARED_FOOD
        )
        taxonomyStrata.forEach { tag -> add(rows.asSequence().filter { tag in it.tags }, 6) }

        listOf(
            FoodPreparationState.RAW, FoodPreparationState.COOKED, FoodPreparationState.BOILED,
            FoodPreparationState.GRILLED, FoodPreparationState.ROASTED, FoodPreparationState.BAKED,
            FoodPreparationState.FRIED, FoodPreparationState.CANNED, FoodPreparationState.FROZEN,
            FoodPreparationState.DRIED
        ).forEach { prep -> add(rows.asSequence().filter { it.preparationState == prep }, 7) }

        add(rows.asSequence().sortedBy { it.micronutrientCount }, 30)
        add(rows.asSequence().sortedByDescending { it.micronutrientCount }, 30)
        add(rows.asSequence().sortedByDescending { abs(it.kcal - (it.protein * 4.0 + it.carbs * 4.0 + it.fat * 9.0)) }, 30)

        val duplicateIds = baseline.duplicateExamples.flatten().mapNotNull(::idFromExample).toSet()
        add(rows.asSequence().filter { it.id in duplicateIds }, 35)
        val suspiciousIds = baseline.suspiciousProfileExamples.flatten().mapNotNull(::idFromExample).toSet()
        add(rows.asSequence().filter { it.id in suspiciousIds }, 35)

        // If strata overlap heavily, fill deterministically from underrepresented source-backed rows.
        val remaining = (SAMPLE_TARGET - chosen.size).coerceAtLeast(0)
        if (remaining > 0) add(rows.asSequence().filter { it.isUsda }, remaining)
        return chosen.values.take(SAMPLE_TARGET)
    }

    private fun idFromExample(value: String): String? {
        val open = value.lastIndexOf('[')
        val close = value.lastIndexOf(']')
        return if (open >= 0 && close > open) value.substring(open + 1, close) else null
    }

    /**
     * A core:usda row is a curated/ranking snapshot. Nutrition and evidence-bearing metadata must
     * remain byte-for-byte/numerically identical to usda:<FDC_ID>.
     */
    private fun compareCoreSnapshots(rows: List<Row>): Pair<Int, Map<String, List<SourceConflict>>> {
        val rawBySource = rows.filter { it.id.startsWith("usda:") }
            .associateBy { it.sourceRecordId.ifBlank { it.id.removePrefix("usda:") } }
        var pairs = 0
        val conflicts = linkedMapOf<String, MutableList<SourceConflict>>()

        fun compare(core: Row, raw: Row, field: String, a: Any?, b: Any?) {
            if (a == b) return
            conflicts.getOrPut(core.id) { mutableListOf() } += SourceConflict(
                core.id, raw.id, core.sourceRecordId, field, a.toString(), b.toString()
            )
        }

        rows.asSequence().filter { it.id.startsWith("core:usda:") }.forEach { core ->
            val raw = rawBySource[core.sourceRecordId]
            if (raw == null) {
                conflicts.getOrPut(core.id) { mutableListOf() } += SourceConflict(
                    core.id,
                    "usda:" + core.sourceRecordId,
                    core.sourceRecordId,
                    "source_record",
                    "present core",
                    "missing raw source"
                )
                return@forEach
            }
            pairs++
            compare(core, raw, "kcal", core.kcal, raw.kcal)
            compare(core, raw, "protein", core.protein, raw.protein)
            compare(core, raw, "carbs", core.carbs, raw.carbs)
            compare(core, raw, "fat", core.fat, raw.fat)
            compare(core, raw, "fibre", core.fibre, raw.fibre)
            compare(core, raw, "sugar", core.sugar, raw.sugar)
            compare(core, raw, "saturated_fat", core.saturatedFat, raw.saturatedFat)
            compare(core, raw, "sodium_mg", core.sodiumMg, raw.sodiumMg)
            compare(core, raw, "salt_g", core.saltG, raw.saltG)
            compare(core, raw, "protein_known", core.proteinKnown, raw.proteinKnown)
            compare(core, raw, "carbs_known", core.carbsKnown, raw.carbsKnown)
            compare(core, raw, "fat_known", core.fatKnown, raw.fatKnown)
            compare(core, raw, "fibre_known", core.fibreKnown, raw.fibreKnown)
            compare(core, raw, "sugar_known", core.sugarKnown, raw.sugarKnown)
            compare(core, raw, "saturated_fat_known", core.saturatedFatKnown, raw.saturatedFatKnown)
            compare(core, raw, "sodium_known", core.sodiumKnown, raw.sodiumKnown)
            compare(core, raw, "salt_known", core.saltKnown, raw.saltKnown)
            compare(core, raw, "unit", core.unit, raw.unit)
            compare(core, raw, "micronutrients_json", core.micronutrientsJson, raw.micronutrientsJson)
            compare(core, raw, "unknown_micronutrients_json", core.unknownMicronutrientsJson, raw.unknownMicronutrientsJson)
            compare(core, raw, "preparation_state", core.preparationState, raw.preparationState)
            compare(core, raw, "serving_quantity", core.servingQuantity, raw.servingQuantity)
            compare(core, raw, "serving_unit", core.servingUnit, raw.servingUnit)
            compare(core, raw, "serving_label", core.servingLabel, raw.servingLabel)
        }
        return pairs to conflicts
    }

    private fun validateGolden(spec: GoldenSpec, rows: List<Row>): GoldenResult {
        fun normalized(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        val candidates = rows.asSequence()
            .filter { it.isUsda }
            .filter { row ->
                val n = normalized(row.name)
                spec.tokenGroups.all { alternatives -> alternatives.any { normalized(it) in n } }
            }
            .filter { spec.requiredPreparation == null || it.preparationState == spec.requiredPreparation }
            .sortedWith(
                compareBy<Row> {
                    when {
                        it.id.startsWith("core:usda:") -> 0
                        it.source.contains("Foundation", ignoreCase = true) -> 1
                        it.source.contains("SR Legacy", ignoreCase = true) -> 2
                        else -> 3
                    }
                }.thenByDescending { it.micronutrientCount }
            )
            .toList()
        val match = candidates.firstOrNull()
        if (match == null) return GoldenResult(spec, null, listOf("missing_match"))

        val failures = mutableListOf<String>()
        if (match.sourceRecordId.isBlank() || match.sourceRecordId.any { !it.isDigit() }) failures += "invalid_source_id"
        if (!match.kcalKnown()) failures += "energy_unknown"
        if (match.kcal !in spec.kcalRange) failures += "kcal_out_of_range"
        spec.proteinRange?.let { if (!match.proteinKnown || match.protein !in it) failures += "protein_out_of_range" }
        spec.carbsRange?.let { if (!match.carbsKnown || match.carbs !in it) failures += "carbs_out_of_range" }
        spec.fatRange?.let { if (!match.fatKnown || match.fat !in it) failures += "fat_out_of_range" }
        if (!match.tags.containsAll(spec.requiredTags)) failures += "required_tag_missing"
        return GoldenResult(spec, match, failures)
    }

    private fun Row.kcalKnown(): Boolean = kcal.isFinite() && kcal >= 0.0

    /**
     * Wide ranges are diagnostic guardrails, never replacement nutrition values. They are
     * intentionally broad enough to tolerate legitimate varieties while catching importer/unit/
     * preparation catastrophes.
     */
    private fun goldenSpecs(): List<GoldenSpec> = listOf(
        GoldenSpec("apple raw", listOf(listOf("apple"), listOf("raw")), FoodPreparationState.RAW, 25.0..90.0, carbsRange = 5.0..25.0, requiredTags = setOf(FoodTag.FRUIT)),
        GoldenSpec("banana raw", listOf(listOf("banana"), listOf("raw")), FoodPreparationState.RAW, 50.0..140.0, carbsRange = 10.0..35.0, requiredTags = setOf(FoodTag.FRUIT)),
        GoldenSpec("orange raw", listOf(listOf("orange"), listOf("raw")), FoodPreparationState.RAW, 20.0..90.0, requiredTags = setOf(FoodTag.FRUIT)),
        GoldenSpec("kiwi raw", listOf(listOf("kiwi", "kiwifruit"), listOf("raw")), FoodPreparationState.RAW, 30.0..100.0, requiredTags = setOf(FoodTag.FRUIT)),
        GoldenSpec("strawberry raw", listOf(listOf("strawberry", "strawberries"), listOf("raw")), FoodPreparationState.RAW, 15.0..70.0, requiredTags = setOf(FoodTag.FRUIT)),
        GoldenSpec("spinach raw", listOf(listOf("spinach"), listOf("raw")), FoodPreparationState.RAW, 10.0..60.0, requiredTags = setOf(FoodTag.VEGETABLE, FoodTag.LEAFY_GREEN)),
        GoldenSpec("rocket arugula raw", listOf(listOf("arugula", "rocket"), listOf("raw")), FoodPreparationState.RAW, 10.0..70.0, requiredTags = setOf(FoodTag.VEGETABLE, FoodTag.LEAFY_GREEN)),
        GoldenSpec("broccoli raw", listOf(listOf("broccoli"), listOf("raw")), FoodPreparationState.RAW, 10.0..80.0, requiredTags = setOf(FoodTag.VEGETABLE)),
        GoldenSpec("carrot raw", listOf(listOf("carrot"), listOf("raw")), FoodPreparationState.RAW, 15.0..90.0, requiredTags = setOf(FoodTag.VEGETABLE)),
        GoldenSpec("tomato raw", listOf(listOf("tomato"), listOf("raw")), FoodPreparationState.RAW, 5.0..60.0, requiredTags = setOf(FoodTag.VEGETABLE)),
        GoldenSpec("potato raw", listOf(listOf("potato"), listOf("raw")), FoodPreparationState.RAW, 40.0..130.0, carbsRange = 8.0..30.0, requiredTags = setOf(FoodTag.POTATO)),
        GoldenSpec("potato boiled", listOf(listOf("potato"), listOf("boiled")), FoodPreparationState.BOILED, 40.0..140.0, requiredTags = setOf(FoodTag.POTATO)),
        GoldenSpec("white rice dry", listOf(listOf("rice"), listOf("white"), listOf("raw", "dry", "uncooked")), null, 300.0..420.0, carbsRange = 65.0..90.0, requiredTags = setOf(FoodTag.RICE)),
        GoldenSpec("white rice cooked", listOf(listOf("rice"), listOf("white"), listOf("cooked")), FoodPreparationState.COOKED, 80.0..180.0, carbsRange = 15.0..40.0, requiredTags = setOf(FoodTag.RICE)),
        GoldenSpec("brown rice cooked", listOf(listOf("brown rice"), listOf("cooked")), FoodPreparationState.COOKED, 80.0..190.0, requiredTags = setOf(FoodTag.RICE, FoodTag.WHOLE_GRAIN)),
        GoldenSpec("oats", listOf(listOf("oat", "oats")), null, 250.0..450.0, requiredTags = setOf(FoodTag.GRAIN)),
        GoldenSpec("lentils dry", listOf(listOf("lentil"), listOf("dry", "raw")), null, 250.0..400.0, proteinRange = 15.0..35.0, requiredTags = setOf(FoodTag.LEGUME)),
        GoldenSpec("lentils cooked", listOf(listOf("lentil"), listOf("cooked", "boiled")), null, 70.0..180.0, requiredTags = setOf(FoodTag.LEGUME)),
        GoldenSpec("chickpeas", listOf(listOf("chickpea", "garbanzo")), null, 80.0..400.0, requiredTags = setOf(FoodTag.LEGUME)),
        GoldenSpec("kidney beans", listOf(listOf("kidney bean")), null, 70.0..380.0, requiredTags = setOf(FoodTag.LEGUME)),
        GoldenSpec("walnuts", listOf(listOf("walnut")), null, 500.0..750.0, fatRange = 45.0..75.0, requiredTags = setOf(FoodTag.NUT)),
        GoldenSpec("almonds", listOf(listOf("almond")), null, 450.0..700.0, requiredTags = setOf(FoodTag.NUT)),
        GoldenSpec("flaxseed", listOf(listOf("flax", "linseed")), null, 400.0..650.0, requiredTags = setOf(FoodTag.SEED)),
        GoldenSpec("chia seed", listOf(listOf("chia")), null, 350.0..600.0, requiredTags = setOf(FoodTag.SEED)),
        GoldenSpec("pumpkin seed", listOf(listOf("pumpkin seed")), null, 400.0..700.0, requiredTags = setOf(FoodTag.SEED)),
        GoldenSpec("olive oil", listOf(listOf("olive oil")), null, 800.0..930.0, fatRange = 90.0..105.0, requiredTags = setOf(FoodTag.OIL)),
        GoldenSpec("butter", listOf(listOf("butter")), null, 600.0..800.0, fatRange = 70.0..90.0, requiredTags = setOf(FoodTag.DAIRY, FoodTag.BUTTER)),
        GoldenSpec("chicken breast raw", listOf(listOf("chicken"), listOf("breast"), listOf("raw")), FoodPreparationState.RAW, 90.0..180.0, proteinRange = 15.0..30.0, requiredTags = setOf(FoodTag.CHICKEN_BREAST)),
        GoldenSpec("chicken breast grilled", listOf(listOf("chicken"), listOf("breast"), listOf("grilled", "broiled")), FoodPreparationState.GRILLED, 120.0..230.0, proteinRange = 20.0..40.0, requiredTags = setOf(FoodTag.CHICKEN_BREAST)),
        GoldenSpec("chicken thigh", listOf(listOf("chicken"), listOf("thigh")), null, 100.0..280.0, requiredTags = setOf(FoodTag.CHICKEN_LEG)),
        GoldenSpec("beef", listOf(listOf("beef")), null, 100.0..450.0, proteinRange = 10.0..40.0, requiredTags = setOf(FoodTag.BEEF)),
        GoldenSpec("pork", listOf(listOf("pork")), null, 100.0..450.0, proteinRange = 10.0..40.0, requiredTags = setOf(FoodTag.PORK)),
        GoldenSpec("liver", listOf(listOf("liver")), null, 90.0..250.0, proteinRange = 10.0..35.0, requiredTags = setOf(FoodTag.MEAT)),
        GoldenSpec("salmon", listOf(listOf("salmon")), null, 80.0..300.0, proteinRange = 15.0..35.0, requiredTags = setOf(FoodTag.FISH)),
        GoldenSpec("cod", listOf(listOf("cod")), null, 50.0..180.0, proteinRange = 12.0..30.0, requiredTags = setOf(FoodTag.FISH)),
        GoldenSpec("tuna", listOf(listOf("tuna")), null, 70.0..300.0, requiredTags = setOf(FoodTag.FISH)),
        GoldenSpec("sardines", listOf(listOf("sardine")), null, 100.0..350.0, requiredTags = setOf(FoodTag.FISH)),
        GoldenSpec("egg raw", listOf(listOf("egg"), listOf("raw")), FoodPreparationState.RAW, 110.0..190.0, proteinRange = 9.0..16.0, requiredTags = setOf(FoodTag.EGG)),
        GoldenSpec("egg boiled", listOf(listOf("egg"), listOf("boiled")), FoodPreparationState.BOILED, 110.0..200.0, requiredTags = setOf(FoodTag.EGG)),
        GoldenSpec("whole milk", listOf(listOf("milk"), listOf("whole")), null, 45.0..90.0, proteinRange = 2.0..5.0, requiredTags = setOf(FoodTag.MILK)),
        GoldenSpec("semi skimmed milk", listOf(listOf("milk"), listOf("2%", "reduced fat", "semi skimmed")), null, 35.0..70.0, requiredTags = setOf(FoodTag.MILK)),
        GoldenSpec("greek yogurt", listOf(listOf("greek"), listOf("yogurt", "yoghurt")), null, 40.0..160.0, requiredTags = setOf(FoodTag.YOGURT)),
        GoldenSpec("kefir", listOf(listOf("kefir")), null, 30.0..120.0, requiredTags = setOf(FoodTag.YOGURT, FoodTag.FERMENTED)),
        GoldenSpec("cheddar", listOf(listOf("cheddar")), null, 300.0..500.0, proteinRange = 18.0..32.0, requiredTags = setOf(FoodTag.CHEESE)),
        GoldenSpec("sourdough bread", listOf(listOf("sourdough")), null, 180.0..350.0, requiredTags = setOf(FoodTag.BREAD, FoodTag.FERMENTED)),
        GoldenSpec("wholemeal bread", listOf(listOf("whole wheat", "wholemeal", "whole grain"), listOf("bread")), null, 180.0..350.0, requiredTags = setOf(FoodTag.BREAD, FoodTag.WHOLE_GRAIN)),
        GoldenSpec("sugar", listOf(listOf("sugar")), null, 350.0..410.0, carbsRange = 90.0..105.0, requiredTags = setOf(FoodTag.SWEETENER)),
        GoldenSpec("salt", listOf(listOf("salt")), null, 0.0..10.0)
    )

    private fun readRows(db: SQLiteDatabase): List<Row> {
        val rows = mutableListOf<Row>()
        db.rawQuery(
            """
            SELECT id, name, kcal, protein, carbs, fat, fibre, sugar, saturated_fat,
                   sodium_mg, salt_g, protein_known, carbs_known, fat_known, fibre_known,
                   sugar_known, saturated_fat_known, sodium_known, salt_known, unit, source,
                   source_record_id, micronutrients_json, unknown_micronutrients_json,
                   food_tags_json, preparation_state, serving_quantity, serving_unit, serving_label
            FROM food_reference
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                rows += Row(
                    id = c.getString(0),
                    name = c.getString(1),
                    kcal = c.getDouble(2),
                    protein = c.getDouble(3),
                    carbs = c.getDouble(4),
                    fat = c.getDouble(5),
                    fibre = c.getDouble(6),
                    sugar = c.getDouble(7),
                    saturatedFat = c.getDouble(8),
                    sodiumMg = c.getDouble(9),
                    saltG = c.getDouble(10),
                    proteinKnown = c.getInt(11) != 0,
                    carbsKnown = c.getInt(12) != 0,
                    fatKnown = c.getInt(13) != 0,
                    fibreKnown = c.getInt(14) != 0,
                    sugarKnown = c.getInt(15) != 0,
                    saturatedFatKnown = c.getInt(16) != 0,
                    sodiumKnown = c.getInt(17) != 0,
                    saltKnown = c.getInt(18) != 0,
                    unit = c.getString(19),
                    source = c.getString(20),
                    sourceRecordId = c.getString(21),
                    micronutrientsJson = c.getString(22),
                    unknownMicronutrientsJson = c.getString(23),
                    tags = decodeTags(c.getString(24)),
                    preparationState = runCatching { FoodPreparationState.valueOf(c.getString(25)) }
                        .getOrDefault(FoodPreparationState.UNSPECIFIED),
                    servingQuantity = if (c.isNull(26)) null else c.getDouble(26),
                    servingUnit = c.getString(27),
                    servingLabel = c.getString(28)
                )
            }
        }
        return rows
    }

    private fun decodeTags(raw: String): Set<FoodTag> = runCatching {
        val a = JSONArray(raw)
        buildSet {
            for (i in 0 until a.length()) {
                runCatching { FoodTag.valueOf(a.optString(i)) }.getOrNull()?.let(::add)
            }
        }
    }.getOrDefault(emptySet())

    private fun summaryJson(r: Result): JSONObject = JSONObject().apply {
        put("generatedEpochMs", r.generatedEpochMs)
        put("database", JSONObject().apply {
            put("totalRecords", r.totalRecords)
            put("totalUsdaRecords", r.totalUsdaRecords)
            put("totalCoreRecords", r.totalCoreRecords)
            put("uniqueCanonicalFoodIdentities", r.uniqueCanonicalFoods)
            put("duplicateCandidateGroups", r.duplicateCandidateGroups)
            put("duplicateUsdaSourceRecordIds", r.duplicateUsdaSourceRecordIds)
            put("fingerprintSha256", r.databaseFingerprintSha256)
        })
        put("sources", JSONObject().apply {
            put("foundation", r.foundationCount)
            put("fndds", r.fnddsCount)
            put("srLegacy", r.srLegacyCount)
            put("nonUsdaLocalPriority", r.nonUsdaLocalCount)
            put("validFdcIds", r.validFdcIdCount)
        })
        put("coverage", JSONObject().apply {
            put("caloriesKnown", r.caloriesKnownCount)
            put("proteinKnown", r.proteinKnownCount)
            put("carbohydrateKnown", r.carbsKnownCount)
            put("fatKnown", r.fatKnownCount)
            put("fibreKnown", r.fibreKnownCount)
            put("sodiumKnown", r.sodiumKnownCount)
            put("plantTagged", r.plantTaggedCount)
            put("foodsWithFewerThan4Micronutrients", r.lowMicronutrientCoverageCount)
            put("averageKnownNutrientCount", r.averageKnownNutrientCount)
            put("medianKnownNutrientCount", r.medianKnownNutrientCount)
        })
        put("categoryCounts", JSONObject().apply {
            r.categoryCounts.toSortedMap().forEach { (tag, count) -> put(tag, count) }
        })
        put("preparationCounts", JSONObject().apply {
            r.preparationCounts.toSortedMap().forEach { (prep, count) -> put(prep, count) }
        })
        put("verification", JSONObject().apply {
            put("deepSampleCount", r.deepSampleCount)
            put("VERIFIED", r.verifiedCount)
            put("HIGH", r.highCount)
            put("MEDIUM", r.mediumCount)
            put("LOW", r.lowCount)
            put("NEEDS_REVIEW", r.needsReviewCount)
            put("note", "VERIFIED is intentionally not assigned by software-only checks")
            put("externalSourceRowSampleCount", 0)
            put("externalSourceRowStatus", "REQUIRES_POPULATED_DB_EXPORT_OR_SOURCE_ARCHIVE_COMPARISON")
        })
        put("coreSnapshotIntegrity", JSONObject().apply {
            put("pairs", r.coreSnapshotPairs)
            put("conflicts", r.coreSnapshotConflictCount)
        })
        put("goldenReference", JSONObject().apply {
            put("matched", r.goldenMatched)
            put("failures", r.goldenFailures)
            put("results", JSONArray().apply {
                r.golden.forEach { g ->
                    put(JSONObject().apply {
                        put("name", g.spec.name)
                        put("matchedId", g.matched?.id ?: JSONObject.NULL)
                        put("sourceRecordId", g.matched?.sourceRecordId ?: JSONObject.NULL)
                        put("failures", JSONArray(g.failures))
                    })
                }
            })
        })
        put("errors", JSONObject().apply {
            put("allFindings", r.baseline.findings.size)
            put("recordsNeedingReview", r.baseline.recordsNeedingReview)
            put("coreSnapshotConflicts", r.coreSnapshotConflictCount)
        })
        put("nutrientCoverage", JSONArray().apply {
            r.baseline.nutrientCoverage.forEach { n ->
                put(JSONObject().apply {
                    put("nutrient", n.nutrient)
                    put("valid", n.validCount)
                    put("missing", n.missingCount)
                    put("zero", n.zeroCount)
                    put("suspiciousZero", n.suspiciousZeroCount)
                    put("coveragePercent", if (r.totalRecords == 0) 0.0 else n.validCount * 100.0 / r.totalRecords)
                })
            }
        })
    }

    private fun sampleCsv(r: Result): String = buildString {
        appendLine("id,name,source,source_record_id,preparation,confidence,kcal,protein,carbs,fat,fibre,sugar,sodium_mg,micronutrient_count,tags")
        r.sample.forEach { (row, confidence) ->
            appendCsvRow(
                row.id, row.name, row.source, row.sourceRecordId, row.preparationState.name,
                confidence.name, row.kcal, row.protein, row.carbs, row.fat, row.fibre, row.sugar,
                row.sodiumMg, row.micronutrientCount, row.tags.joinToString("|") { it.name }
            )
        }
    }

    private fun findingsCsv(findings: List<LocalFoodAuditFinding>): String = buildString {
        appendLine("food_id,food_name,code,severity,message")
        findings.forEach { appendCsvRow(it.foodId, it.foodName, it.code, it.severity.name, it.message) }
    }

    private fun duplicatesCsv(groups: List<List<String>>): String = buildString {
        appendLine("group_index,member")
        groups.forEachIndexed { index, group -> group.forEach { appendCsvRow(index + 1, it) } }
    }

    private fun sourceConflictsCsv(conflicts: List<SourceConflict>): String = buildString {
        appendLine("core_id,raw_id,source_record_id,field,core_value,raw_value")
        conflicts.forEach { appendCsvRow(it.coreId, it.rawId, it.sourceRecordId, it.field, it.coreValue, it.rawValue) }
    }

    private fun missingNutrientsCsv(coverage: List<LocalFoodNutrientCoverage>): String = buildString {
        appendLine("nutrient,known_count,missing_count,zero_count,suspicious_zero_count")
        coverage.forEach { appendCsvRow(it.nutrient, it.validCount, it.missingCount, it.zeroCount, it.suspiciousZeroCount) }
    }

    private fun StringBuilder.appendCsvRow(vararg fields: Any?) {
        append(fields.joinToString(",") { csv(it?.toString().orEmpty()) })
        append('\n')
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
    }
}
