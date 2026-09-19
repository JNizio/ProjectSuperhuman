package com.projectsuperhuman.next

import java.time.ZoneId

internal object CardioHistoryEngine {
    fun filterAndPage(
        sessions: List<CardioSession>,
        filter: CardioHistoryFilter = CardioHistoryFilter(),
        page: Int = 0,
        pageSize: Int = 30,
        nowEpochMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CardioHistoryPage {
        require(page >= 0)
        require(pageSize in 1..200)

        val rangeFiltered = CardioTrendEngine.filterRange(
            sessions = sessions,
            range = filter.range,
            nowEpochMs = nowEpochMs,
            zoneId = zoneId
        )
        val query = filter.query.trim().lowercase()
        val filtered = rangeFiltered.asSequence()
            .filter { filter.activities.isEmpty() || it.activity in filter.activities }
            .filter { filter.workoutTypes.isEmpty() || it.workoutType in filter.workoutTypes }
            .filter { filter.sources.isEmpty() || filter.sources.any { source -> source.equals(it.source, ignoreCase = true) } }
            .filter {
                query.isBlank() ||
                    it.notes.lowercase().contains(query) ||
                    it.activity.displayName.lowercase().contains(query) ||
                    it.workoutType.label.lowercase().contains(query) ||
                    it.source.lowercase().contains(query)
            }
            .sortedByDescending { it.endedAt }
            .toList()

        val from = (page * pageSize).coerceAtMost(filtered.size)
        val to = (from + pageSize).coerceAtMost(filtered.size)
        return CardioHistoryPage(
            items = filtered.subList(from, to),
            page = page,
            pageSize = pageSize,
            totalItems = filtered.size,
            hasMore = to < filtered.size
        )
    }
}
