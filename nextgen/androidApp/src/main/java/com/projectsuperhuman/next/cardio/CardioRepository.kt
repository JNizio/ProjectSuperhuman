package com.projectsuperhuman.next

import com.projectsuperhuman.next.core.HealthDomain
import com.projectsuperhuman.next.core.HealthValue

internal data class CardioWriteResult(
    val success: Boolean,
    val message: String,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val deduplicated: Int = 0
)

internal interface CardioRepository {
    suspend fun save(session: CardioSession): CardioWriteResult
    suspend fun update(session: CardioSession): CardioWriteResult
    suspend fun delete(sessionId: String): Boolean
    suspend fun sessionById(sessionId: String): CardioSession?
    suspend fun recentSessions(limit: Int = 250): List<CardioSession>
    suspend fun sessionsBetween(fromEpochMs: Long, toEpochMs: Long): List<CardioSession>
    suspend fun pageHistory(limit: Int = 250, offset: Int = 0): List<CardioSession>
}

internal class DataVaultCardioRepository(
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) : CardioRepository {
    private val data = NativeDomainData.forDomain(HealthDomain.EXERCISE)

    override suspend fun save(session: CardioSession): CardioWriteResult {
        val validation = CardioValidation.validate(session, nowEpochMs())
        if (validation.isNotEmpty()) {
            return CardioWriteResult(
                success = false,
                message = validation.joinToString("; ") { it.message },
                rejected = 1
            )
        }

        return try {
            val result = NativeDataHub.ingestValues(listOf(session.toHealthValue()))
            val rejectedIssue = result.issues.firstOrNull { it.rejected }
            val success = result.accepted == 1 && result.rejected == 0 && rejectedIssue == null
            CardioWriteResult(
                success = success,
                message = if (success) {
                    "Cardio session persisted"
                } else {
                    rejectedIssue?.message ?: "Cardio session was not accepted by the Data Vault"
                },
                accepted = result.accepted,
                rejected = result.rejected,
                deduplicated = result.deduplicated
            )
        } catch (t: Throwable) {
            CardioWriteResult(
                success = false,
                message = t.message ?: "Cardio persistence failed"
            )
        }
    }

    /**
     * Updates reuse the stable sourceRecordId cardio:<sessionId>. The Data Vault has a unique
     * (source, source_record_id) index and INSERT OR REPLACE, so the logical row is replaced in
     * one SQL transaction instead of delete-then-write.
     */
    override suspend fun update(session: CardioSession): CardioWriteResult = save(session)

    override suspend fun delete(sessionId: String): Boolean {
        val row = rowBySessionId(sessionId) ?: return false
        return try {
            NativeDataHub.deleteValue(row)
            true
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun sessionById(sessionId: String): CardioSession? =
        rowBySessionId(sessionId)?.let(::cardioSessionFromValue)

    override suspend fun recentSessions(limit: Int): List<CardioSession> =
        data.metricHistory(
            metric = CARDIO_METRIC,
            limit = limit.coerceIn(1, MAX_PAGE_SIZE),
            offset = 0
        ).map(::cardioSessionFromValue)

    override suspend fun sessionsBetween(fromEpochMs: Long, toEpochMs: Long): List<CardioSession> =
        data.between(CARDIO_METRIC, fromEpochMs, toEpochMs)
            .map(::cardioSessionFromValue)
            .sortedByDescending { it.endedAt }

    override suspend fun pageHistory(limit: Int, offset: Int): List<CardioSession> =
        data.metricHistory(
            metric = CARDIO_METRIC,
            limit = limit.coerceIn(1, MAX_PAGE_SIZE),
            offset = offset.coerceAtLeast(0)
        ).map(::cardioSessionFromValue)

    private suspend fun rowBySessionId(sessionId: String): HealthValue? {
        var offset = 0
        repeat(MAX_LOOKUP_PAGES) {
            val page = data.metricHistory(CARDIO_METRIC, LOOKUP_PAGE_SIZE, offset)
            page.firstOrNull { it.metadata["sessionId"] == sessionId }?.let { return it }
            if (page.size < LOOKUP_PAGE_SIZE) return null
            offset += page.size
        }
        return null
    }

    private companion object {
        const val CARDIO_METRIC = "cardio_session"
        const val LOOKUP_PAGE_SIZE = 250
        const val MAX_LOOKUP_PAGES = 40
        const val MAX_PAGE_SIZE = 1_000
    }
}
