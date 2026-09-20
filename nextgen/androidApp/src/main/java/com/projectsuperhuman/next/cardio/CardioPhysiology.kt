package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue
import kotlin.math.roundToInt

internal const val CARDIO_PHYSIOLOGY_STORAGE_METRIC = "cardio_physiology_profile_revision"
internal const val CARDIO_PHYSIOLOGY_ALGORITHM_VERSION = "cardio-physiology-v1"

internal object CardioZoneEngine {
    fun createProfile(
        revisionId: String,
        effectiveFromEpochMs: Long,
        hrMaxBpm: Int?,
        hrMaxSource: CardioHrMaxSource?,
        restingHrBpm: Int?,
        lactateThresholdHrBpm: Int?,
        zoneModel: CardioZoneModel,
        sport: CardioActivityType? = null,
        customZones: List<CardioZoneBoundary> = emptyList(),
        sportSpecificSettings: Map<String, String> = emptyMap(),
        createdAtEpochMs: Long = System.currentTimeMillis()
    ): CardioPhysiologyProfile {
        require(revisionId.isNotBlank()) { "revisionId is required" }
        require(effectiveFromEpochMs > 0L) { "effectiveFromEpochMs must be positive" }
        hrMaxBpm?.let { require(it in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM) { "HRmax is out of supported storage range" } }
        restingHrBpm?.let { require(it in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM) { "Resting HR is out of supported storage range" } }
        lactateThresholdHrBpm?.let { require(it in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM) { "LTHR is out of supported storage range" } }
        if (hrMaxBpm == null) require(hrMaxSource == null) { "HRmax source requires HRmax" }
        if (hrMaxBpm != null) require(hrMaxSource != null) { "HRmax must retain its source" }

        val zones = buildZones(
            model = zoneModel,
            hrMaxBpm = hrMaxBpm,
            restingHrBpm = restingHrBpm,
            lthrBpm = lactateThresholdHrBpm,
            customZones = customZones
        )
        return CardioPhysiologyProfile(
            revisionId = revisionId,
            effectiveFromEpochMs = effectiveFromEpochMs,
            hrMaxBpm = hrMaxBpm,
            hrMaxSource = hrMaxSource,
            restingHrBpm = restingHrBpm,
            lactateThresholdHrBpm = lactateThresholdHrBpm,
            zoneModel = zoneModel,
            zones = zones,
            sport = sport,
            sportSpecificSettings = sportSpecificSettings,
            createdAtEpochMs = createdAtEpochMs,
            algorithmVersion = CARDIO_PHYSIOLOGY_ALGORITHM_VERSION
        )
    }

    fun buildZones(
        model: CardioZoneModel,
        hrMaxBpm: Int?,
        restingHrBpm: Int?,
        lthrBpm: Int?,
        customZones: List<CardioZoneBoundary> = emptyList()
    ): List<CardioZoneBoundary> = when (model) {
        CardioZoneModel.HR_MAX -> {
            val max = requireNotNull(hrMaxBpm) { "%HRmax zones require HRmax" }
            fiveZonesFromBoundaries(
                maxBpm = max,
                boundaries = listOf(
                    (max * 0.60).roundToInt(),
                    (max * 0.70).roundToInt(),
                    (max * 0.80).roundToInt(),
                    (max * 0.90).roundToInt()
                )
            )
        }
        CardioZoneModel.HRR -> {
            val max = requireNotNull(hrMaxBpm) { "%HRR zones require HRmax" }
            val resting = requireNotNull(restingHrBpm) { "%HRR zones require resting HR" }
            require(resting < max) { "Resting HR must be below HRmax" }
            val reserve = max - resting
            fiveZonesFromBoundaries(
                maxBpm = max,
                boundaries = listOf(0.60, 0.70, 0.80, 0.90).map {
                    (resting + reserve * it).roundToInt()
                }
            )
        }
        CardioZoneModel.LTHR -> {
            val threshold = requireNotNull(lthrBpm) { "LTHR zones require LTHR" }
            val top = (hrMaxBpm ?: (threshold * 1.15).roundToInt()).coerceAtMost(CARDIO_HR_MAX_BPM)
            fiveZonesFromBoundaries(
                maxBpm = maxOf(top, threshold + 1),
                boundaries = listOf(0.85, 0.90, 0.95, 1.00).map {
                    (threshold * it).roundToInt()
                }
            )
        }
        CardioZoneModel.THREE_ZONE -> {
            val threshold = lthrBpm
            if (threshold != null) {
                val top = (hrMaxBpm ?: (threshold * 1.15).roundToInt()).coerceAtMost(CARDIO_HR_MAX_BPM)
                threeZonesFromBoundaries(
                    maxBpm = maxOf(top, threshold + 1),
                    firstCut = (threshold * 0.90).roundToInt(),
                    secondCut = threshold
                )
            } else {
                val max = requireNotNull(hrMaxBpm) { "Three-zone model requires LTHR or HRmax" }
                threeZonesFromBoundaries(
                    maxBpm = max,
                    firstCut = (max * 0.70).roundToInt(),
                    secondCut = (max * 0.85).roundToInt()
                )
            }
        }
        CardioZoneModel.MANUAL -> validateManual(customZones)
    }

    private fun fiveZonesFromBoundaries(
        maxBpm: Int,
        boundaries: List<Int>
    ): List<CardioZoneBoundary> {
        require(boundaries.size == 4)
        val cuts = boundaries.map { it.coerceIn(CARDIO_HR_MIN_BPM + 1, maxBpm - 1) }
        require(cuts.zipWithNext().all { (a, b) -> a < b }) { "Zone boundaries must be strictly increasing" }
        val names = listOf("Zone 1", "Zone 2", "Zone 3", "Zone 4", "Zone 5")
        val purposes = listOf(
            "Lowest configured intensity band",
            "Low-intensity aerobic band",
            "Moderate-intensity band",
            "High-intensity band",
            "Highest configured intensity band"
        )
        val mins = listOf(CARDIO_HR_MIN_BPM, cuts[0], cuts[1], cuts[2], cuts[3])
        val maxes = listOf(cuts[0] - 1, cuts[1] - 1, cuts[2] - 1, cuts[3] - 1, maxBpm)
        return names.indices.map { index ->
            CardioZoneBoundary(index + 1, names[index], mins[index], maxes[index], purposes[index])
        }
    }

    private fun threeZonesFromBoundaries(
        maxBpm: Int,
        firstCut: Int,
        secondCut: Int
    ): List<CardioZoneBoundary> {
        val first = firstCut.coerceIn(CARDIO_HR_MIN_BPM + 1, maxBpm - 2)
        val second = secondCut.coerceIn(first + 1, maxBpm - 1)
        return listOf(
            CardioZoneBoundary(1, "Zone 1", CARDIO_HR_MIN_BPM, first - 1, "Below first configured threshold"),
            CardioZoneBoundary(2, "Zone 2", first, second - 1, "Between configured thresholds"),
            CardioZoneBoundary(3, "Zone 3", second, maxBpm, "Above second configured threshold")
        )
    }

    private fun validateManual(zones: List<CardioZoneBoundary>): List<CardioZoneBoundary> {
        require(zones.isNotEmpty()) { "Manual zones require at least one boundary" }
        val ordered = zones.sortedBy { it.zone }
        require(ordered.map { it.zone }.distinct().size == ordered.size) { "Manual zone numbers must be unique" }
        require(ordered.all { it.minBpmInclusive in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM }) {
            "Manual zone minimum is out of bounds"
        }
        require(ordered.all { it.maxBpmInclusive in CARDIO_HR_MIN_BPM..CARDIO_HR_MAX_BPM }) {
            "Manual zone maximum is out of bounds"
        }
        require(ordered.all { it.minBpmInclusive <= it.maxBpmInclusive }) {
            "Manual zone minimum must not exceed maximum"
        }
        require(ordered.zipWithNext().all { (a, b) -> a.maxBpmInclusive < b.minBpmInclusive }) {
            "Manual zones must not overlap"
        }
        return ordered
    }
}

internal object CardioPhysiologyCodec {
    fun toHealthValue(profile: CardioPhysiologyProfile): HealthValue {
        val metadata = buildMap {
            put("sourceRecordId", "cardio-physiology:" + profile.revisionId)
            put("revisionId", profile.revisionId)
            put("effectiveFromEpochMs", profile.effectiveFromEpochMs.toString())
            put("zoneModel", profile.zoneModel.name)
            put("createdAtEpochMs", profile.createdAtEpochMs.toString())
            put("algorithmVersion", profile.algorithmVersion)
            profile.hrMaxBpm?.let { put("hrMaxBpm", it.toString()) }
            profile.hrMaxSource?.let { put("hrMaxSource", it.name) }
            profile.restingHrBpm?.let { put("restingHrBpm", it.toString()) }
            profile.lactateThresholdHrBpm?.let { put("lactateThresholdHrBpm", it.toString()) }
            profile.sport?.let { put("sport", it.name) }
            put("zoneCount", profile.zones.size.toString())
            profile.zones.forEachIndexed { index, zone ->
                val prefix = "zone." + index + "."
                put(prefix + "number", zone.zone.toString())
                put(prefix + "name", zone.name)
                put(prefix + "minBpm", zone.minBpmInclusive.toString())
                put(prefix + "maxBpm", zone.maxBpmInclusive.toString())
                put(prefix + "purpose", zone.purpose)
            }
            profile.sportSpecificSettings.forEach { (key, value) ->
                put("sportSetting." + key, value)
            }
        }
        val markerValue = profile.hrMaxBpm?.toDouble()
            ?: profile.lactateThresholdHrBpm?.toDouble()
            ?: profile.restingHrBpm?.toDouble()
            ?: 0.0
        return HealthValue(
            domain = HealthDomain.EXERCISE,
            metric = CARDIO_PHYSIOLOGY_STORAGE_METRIC,
            value = markerValue,
            unit = "revision",
            timestampEpochMs = profile.effectiveFromEpochMs,
            source = "cardio-physiology",
            metadata = metadata
        )
    }

    fun fromHealthValue(row: HealthValue): CardioPhysiologyProfile? {
        if (row.domain != HealthDomain.EXERCISE || row.metric != CARDIO_PHYSIOLOGY_STORAGE_METRIC) return null
        val metadata = row.metadata
        val revisionId = metadata["revisionId"]?.takeIf { it.isNotBlank() } ?: return null
        val effective = metadata["effectiveFromEpochMs"]?.toLongOrNull() ?: row.timestampEpochMs
        val model = metadata["zoneModel"]
            ?.let { raw -> CardioZoneModel.entries.firstOrNull { it.name == raw } }
            ?: return null
        val zoneCount = metadata["zoneCount"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val zones = (0 until zoneCount).mapNotNull { index ->
            val prefix = "zone." + index + "."
            val number = metadata[prefix + "number"]?.toIntOrNull() ?: return@mapNotNull null
            val min = metadata[prefix + "minBpm"]?.toIntOrNull() ?: return@mapNotNull null
            val max = metadata[prefix + "maxBpm"]?.toIntOrNull() ?: return@mapNotNull null
            CardioZoneBoundary(
                zone = number,
                name = metadata[prefix + "name"].orEmpty().ifBlank { "Zone " + number },
                minBpmInclusive = min,
                maxBpmInclusive = max,
                purpose = metadata[prefix + "purpose"].orEmpty()
            )
        }
        if (zones.size != zoneCount) return null
        val sport = metadata["sport"]?.let { raw ->
            CardioActivityType.entries.firstOrNull { it.name == raw }
        }
        val settings = metadata
            .filterKeys { it.startsWith("sportSetting.") }
            .mapKeys { it.key.removePrefix("sportSetting.") }

        return CardioPhysiologyProfile(
            revisionId = revisionId,
            effectiveFromEpochMs = effective,
            hrMaxBpm = metadata["hrMaxBpm"]?.toIntOrNull(),
            hrMaxSource = metadata["hrMaxSource"]?.let { raw ->
                CardioHrMaxSource.entries.firstOrNull { it.name == raw }
            },
            restingHrBpm = metadata["restingHrBpm"]?.toIntOrNull(),
            lactateThresholdHrBpm = metadata["lactateThresholdHrBpm"]?.toIntOrNull(),
            zoneModel = model,
            zones = zones,
            sport = sport,
            sportSpecificSettings = settings,
            createdAtEpochMs = metadata["createdAtEpochMs"]?.toLongOrNull() ?: effective,
            algorithmVersion = metadata["algorithmVersion"] ?: CARDIO_PHYSIOLOGY_ALGORITHM_VERSION
        )
    }
}

internal class CardioPhysiologyRepository(
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    suspend fun save(profile: CardioPhysiologyProfile): CardioWriteResult = try {
        val result = NativeDataHub.ingestValues(listOf(CardioPhysiologyCodec.toHealthValue(profile)))
        CardioWriteResult(
            success = result.rejected == 0,
            message = if (result.rejected == 0) "Saved physiology revision" else "Physiology revision was rejected",
            accepted = result.accepted,
            rejected = result.rejected,
            deduplicated = result.deduplicated
        )
    } catch (t: Throwable) {
        CardioWriteResult(false, t.message ?: "Could not save physiology revision")
    }

    suspend fun revisionById(revisionId: String): CardioPhysiologyProfile? =
        loadRecent().firstOrNull { it.revisionId == revisionId }

    suspend fun latestForSport(
        sport: CardioActivityType? = null,
        atEpochMs: Long = nowEpochMs()
    ): CardioPhysiologyProfile? {
        val candidates = loadRecent()
            .filter { it.effectiveFromEpochMs <= atEpochMs }
            .filter { profile ->
                if (sport == null) profile.sport == null
                else profile.sport == null || profile.sport == sport
            }
        return candidates.sortedWith(
            compareByDescending<CardioPhysiologyProfile> { sport != null && it.sport == sport }
                .thenByDescending { it.effectiveFromEpochMs }
        ).firstOrNull()
    }

    private suspend fun loadRecent(): List<CardioPhysiologyProfile> =
        NativeDomainData.forDomain(HealthDomain.EXERCISE)
            .metricHistory(CARDIO_PHYSIOLOGY_STORAGE_METRIC, limit = 500)
            .mapNotNull(CardioPhysiologyCodec::fromHealthValue)
            .sortedByDescending { it.effectiveFromEpochMs }
}

internal enum class CardioZoneAnalysisMode {
    ORIGINAL,
    CURRENT
}

internal object CardioZoneAnalysisResolver {
    fun resolve(
        session: CardioSession,
        originalProfile: CardioPhysiologyProfile?,
        currentProfile: CardioPhysiologyProfile?,
        mode: CardioZoneAnalysisMode
    ): CardioPhysiologyProfile? = when (mode) {
        CardioZoneAnalysisMode.ORIGINAL -> {
            val revision = session.physiologyRevisionId ?: return null
            originalProfile?.takeIf { it.revisionId == revision }
        }
        CardioZoneAnalysisMode.CURRENT -> currentProfile
    }
}
