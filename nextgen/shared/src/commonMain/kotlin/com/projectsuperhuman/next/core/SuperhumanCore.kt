package com.projectsuperhuman.next.core

/**
 * Shared contracts for the next Project Superhuman architecture.
 *
 * UI code should depend on these contracts rather than on Android, iOS,
 * WebView, SQLite, Health Connect or HealthKit directly.
 */

enum class HealthDomain {
    CLINICAL, BLOOD_PRESSURE, BODY, SLEEP, NUTRITION, HYDRATION, EXERCISE, MINDFULNESS, EMOTIONAL
}

data class HealthValue(
    val domain: HealthDomain,
    val metric: String,
    val value: Double,
    val unit: String,
    val timestampEpochMs: Long,
    val source: String,
    val metadata: Map<String, String> = emptyMap()
)

data class HealthRange(
    val low: Double? = null,
    val high: Double? = null,
    val unit: String? = null
)

enum class InterpretationState { NO_DATA, NORMAL, LOW, HIGH, ATTENTION, UNKNOWN }

data class ScientificInsight(
    val id: String,
    val domain: HealthDomain,
    val state: InterpretationState,
    val title: String,
    val explanation: String,
    val evidenceMetricIds: List<String>,
    val confidence: Double? = null,
    val engineVersion: String
)

/** Large-data storage boundary. Step 2 will provide the SQLite implementation. */
interface HealthRepository {
    suspend fun save(values: List<HealthValue>)
    suspend fun latest(metric: String): HealthValue?
    suspend fun between(metric: String, fromEpochMs: Long, toEpochMs: Long): List<HealthValue>
    suspend fun latestForDomain(domain: HealthDomain): List<HealthValue>
}

/** Scientific interpretation boundary. Existing F#/Rust/Python work can sit behind this. */
interface ScientificEngine {
    suspend fun interpret(values: List<HealthValue>): List<ScientificInsight>
}

/** OS integrations live behind adapters: Health Connect on Android, HealthKit on iOS. */
interface PlatformHealthSource {
    val sourceName: String
    suspend fun isAvailable(): Boolean
    suspend fun requestPermissions(): Boolean
    suspend fun importSince(epochMs: Long): List<HealthValue>
}

/** Camera/barcode/BP scanners become replaceable platform services rather than UI code. */
interface CaptureService {
    suspend fun scanBarcode(): String?
    suspend fun captureBloodPressure(): Triple<Int, Int, Int>?
}

class SuperhumanCore(
    val repository: HealthRepository,
    val science: ScientificEngine,
    val healthSources: List<PlatformHealthSource>,
    val capture: CaptureService
)
