package com.projectsuperhuman.next

import com.projectsuperhuman.next.trudy.TrudyTemporalBoundaryProvider
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Device-local calendar boundaries; no absolute dates are hard-coded into Trudy planning. */
internal class AndroidTrudyTemporalBoundaryProvider(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = { System.currentTimeMillis() }
) : TrudyTemporalBoundaryProvider {
    override fun nowEpochMs(): Long = now().coerceAtLeast(0L)

    override fun startOfTodayEpochMs(): Long = localNow().toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    override fun startOfWeekEpochMs(): Long = localNow().toLocalDate()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    override fun startOfMonthEpochMs(monthsAgo: Int): Long = localNow().toLocalDate()
        .withDayOfMonth(1)
        .minusMonths(monthsAgo.coerceAtLeast(0).toLong())
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()

    private fun localNow() = Instant.ofEpochMilli(nowEpochMs()).atZone(zoneId)
}
