package com.goback.focus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tier 1, Tier 2, and Tier 3 tests for Streak Tiers & 1-Day Streak Shield Gamification.
 * Tests milestone mapping (Spark to Centurion), progress into tier,
 * 1-day shield activation, double-miss break, and goal celebration threshold logic.
 */
class StreakTiersAndShieldTest {

    // --- Domain Models & Logic Oracles ---

    enum class StreakTier {
        NONE, SPARK, MOMENTUM, COMMITMENT, HABIT, MASTERY, CENTURION
    }

    data class StreakMilestone(
        val tier: StreakTier,
        val label: String,
        val badge: String,
        val days: Int,
        val nextMilestoneDays: Int,
        val daysToNext: Int,
        val progressPercent: Int
    )

    data class MilestoneTierDef(
        val days: Int,
        val tier: StreakTier,
        val label: String,
        val badge: String
    )

    data class FocusSessionRecord(
        val startedAt: String, // ISO timestamp or YYYY-MM-DDTHH:MM:SS
        val durationSeconds: Int,
        val sessionType: String = "focus"
    )

    data class FocusGoalStats(
        val currentStreak: Int,
        val bestStreak: Int,
        val goalDaysThisWeek: Int,
        val goalDaysThisMonth: Int,
        val todaySeconds: Int,
        val goalSeconds: Int,
        val todayGoalMet: Boolean,
        val streakMilestone: StreakMilestone,
        val streakShieldActive: Boolean
    )

    object StreakEngine {
        private val MILESTONE_TIERS = listOf(
            MilestoneTierDef(3, StreakTier.SPARK, "Spark", "✨"),
            MilestoneTierDef(7, StreakTier.MOMENTUM, "Momentum", "🔥"),
            MilestoneTierDef(14, StreakTier.COMMITMENT, "Commitment", "⚡"),
            MilestoneTierDef(30, StreakTier.HABIT, "Habit Builder", "🛡️"),
            MilestoneTierDef(50, StreakTier.MASTERY, "Focus Mastery", "💎"),
            MilestoneTierDef(100, StreakTier.CENTURION, "Centurion", "👑")
        )

        fun calculateStreakMilestone(currentStreak: Int): StreakMilestone {
            if (currentStreak < 3) {
                val clamped = max(0, currentStreak)
                val progress = ((clamped.toDouble() / 3.0) * 100.0).roundToInt().coerceIn(0, 100)
                return StreakMilestone(
                    tier = StreakTier.NONE,
                    label = "Getting Started",
                    badge = "🌱",
                    days = clamped,
                    nextMilestoneDays = 3,
                    daysToNext = max(1, 3 - clamped),
                    progressPercent = progress
                )
            }

            var currentTier = MILESTONE_TIERS[0]
            var nextTier = MILESTONE_TIERS[1]

            for (i in MILESTONE_TIERS.indices) {
                if (currentStreak >= MILESTONE_TIERS[i].days) {
                    currentTier = MILESTONE_TIERS[i]
                    nextTier = if (i + 1 < MILESTONE_TIERS.size) {
                        MILESTONE_TIERS[i + 1]
                    } else {
                        MilestoneTierDef(currentTier.days + 50, StreakTier.CENTURION, "Grandmaster", "👑")
                    }
                }
            }

            val range = nextTier.days - currentTier.days
            val progressIntoTier = currentStreak - currentTier.days
            val progressPercent = ((progressIntoTier.toDouble() / range.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)

            return StreakMilestone(
                tier = currentTier.tier,
                label = currentTier.label,
                badge = currentTier.badge,
                days = currentStreak,
                nextMilestoneDays = nextTier.days,
                daysToNext = max(0, nextTier.days - currentStreak),
                progressPercent = progressPercent
            )
        }

        fun aggregateDailyTotals(sessions: List<FocusSessionRecord>): Map<String, Int> {
            val totals = mutableMapOf<String, Int>()
            for (s in sessions) {
                if (s.sessionType != "focus") continue
                if (s.durationSeconds <= 0) continue
                val dateKey = s.startedAt.substring(0, 10) // YYYY-MM-DD
                totals[dateKey] = (totals[dateKey] ?: 0) + s.durationSeconds
            }
            return totals
        }

        fun computeFocusGoalStats(
            sessions: List<FocusSessionRecord>,
            dailyGoalMinutes: Int,
            todayDate: LocalDate = LocalDate.now()
        ): FocusGoalStats {
            val goalSeconds = max(1, dailyGoalMinutes * 60)
            val totals = aggregateDailyTotals(sessions)
            val dtf = DateTimeFormatter.ISO_LOCAL_DATE
            val todayKey = todayDate.format(dtf)
            val todaySeconds = totals[todayKey] ?: 0
            val todayGoalMet = todaySeconds >= goalSeconds

            var currentStreak = 0
            var cursor = todayDate
            var streakShieldActive = false

            // If today is not completed yet, start evaluating from yesterday
            if (!todayGoalMet) {
                cursor = cursor.minusDays(1)
            }

            // Walk backward counting consecutive goal days
            for (guard in 0 until 3650) {
                val key = cursor.format(dtf)
                val daySecs = totals[key] ?: 0
                if (daySecs >= goalSeconds) {
                    currentStreak += 1
                    cursor = cursor.minusDays(1)
                } else if (!streakShieldActive && guard > 0) {
                    // 1-Day Streak Shield: check if the day before met the goal
                    val dayBefore = cursor.minusDays(1)
                    val dayBeforeSecs = totals[dayBefore.format(dtf)] ?: 0
                    if (dayBeforeSecs >= goalSeconds) {
                        streakShieldActive = true
                        cursor = dayBefore
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            // Calculate historical best streak
            val successfulDays = totals.filter { it.value >= goalSeconds }.keys.sorted()
            var bestStreak = 0
            var running = 0
            var prevDate: LocalDate? = null
            for (dStr in successfulDays) {
                val d = LocalDate.parse(dStr, dtf)
                running = if (prevDate != null && prevDate.plusDays(1) == d) running + 1 else 1
                bestStreak = max(bestStreak, running)
                prevDate = d
            }
            bestStreak = max(bestStreak, currentStreak)

            // Weekly and monthly goal day counts
            val mondayOffset = (todayDate.dayOfWeek.value + 6) % 7 // Monday = 0
            val weekStart = todayDate.minusDays(mondayOffset.toLong())
            val monthStart = todayDate.withDayOfMonth(1)

            val weekKey = weekStart.format(dtf)
            val monthKey = monthStart.format(dtf)

            var weekGoalDays = successfulDays.count { it >= weekKey && it <= todayKey }
            var monthGoalDays = successfulDays.count { it >= monthKey && it <= todayKey }

            val milestone = calculateStreakMilestone(currentStreak)

            return FocusGoalStats(
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                goalDaysThisWeek = weekGoalDays,
                goalDaysThisMonth = monthGoalDays,
                todaySeconds = todaySeconds,
                goalSeconds = goalSeconds,
                todayGoalMet = todayGoalMet,
                streakMilestone = milestone,
                streakShieldActive = streakShieldActive
            )
        }

        fun shouldTriggerCelebration(priorSecondsToday: Int, newSecondsToday: Int, goalSeconds: Int): Boolean {
            return priorSecondsToday < goalSeconds && newSecondsToday >= goalSeconds
        }
    }

    // =========================================================================
    // Tier 1: Feature Coverage (>=5 per feature)
    // =========================================================================

    @Test
    fun tier1_streakTiers_allSixMilestonesMapCorrectly() {
        // < 3 days: None
        val m0 = StreakEngine.calculateStreakMilestone(0)
        assertEquals(StreakTier.NONE, m0.tier)
        assertEquals("Getting Started", m0.label)
        assertEquals(3, m0.nextMilestoneDays)

        val m2 = StreakEngine.calculateStreakMilestone(2)
        assertEquals(StreakTier.NONE, m2.tier)
        assertEquals(1, m2.daysToNext)
        assertEquals(67, m2.progressPercent) // 2/3 = 67%

        // 3 days: Spark
        val m3 = StreakEngine.calculateStreakMilestone(3)
        assertEquals(StreakTier.SPARK, m3.tier)
        assertEquals("Spark", m3.label)
        assertEquals("✨", m3.badge)
        assertEquals(7, m3.nextMilestoneDays)
        assertEquals(4, m3.daysToNext)
        assertEquals(0, m3.progressPercent)

        // 7 days: Momentum
        val m7 = StreakEngine.calculateStreakMilestone(7)
        assertEquals(StreakTier.MOMENTUM, m7.tier)
        assertEquals("Momentum", m7.label)
        assertEquals("🔥", m7.badge)
        assertEquals(14, m7.nextMilestoneDays)
        assertEquals(0, m7.progressPercent)

        // 14 days: Commitment
        val m14 = StreakEngine.calculateStreakMilestone(14)
        assertEquals(StreakTier.COMMITMENT, m14.tier)
        assertEquals("Commitment", m14.label)
        assertEquals("⚡", m14.badge)
        assertEquals(30, m14.nextMilestoneDays)

        // 30 days: Habit Builder
        val m30 = StreakEngine.calculateStreakMilestone(30)
        assertEquals(StreakTier.HABIT, m30.tier)
        assertEquals("Habit Builder", m30.label)
        assertEquals("🛡️", m30.badge)
        assertEquals(50, m30.nextMilestoneDays)

        // 50 days: Focus Mastery
        val m50 = StreakEngine.calculateStreakMilestone(50)
        assertEquals(StreakTier.MASTERY, m50.tier)
        assertEquals("Focus Mastery", m50.label)
        assertEquals("💎", m50.badge)
        assertEquals(100, m50.nextMilestoneDays)

        // 100 days: Centurion
        val m100 = StreakEngine.calculateStreakMilestone(100)
        assertEquals(StreakTier.CENTURION, m100.tier)
        assertEquals("Centurion", m100.label)
        assertEquals("👑", m100.badge)
    }

    @Test
    fun tier1_streakShield_bridgesSingleMissedDay() {
        val today = LocalDate.of(2026, 11, 7) // Saturday
        val dtf = DateTimeFormatter.ISO_LOCAL_DATE

        // User studied on Wed, Thu, MISSED Friday, and completed goal on Saturday
        val sessions = listOf(
            FocusSessionRecord("2026-11-04T10:00:00", 7200), // Wed (met)
            FocusSessionRecord("2026-11-05T10:00:00", 7200), // Thu (met)
            // Friday missed!
            FocusSessionRecord("2026-11-07T10:00:00", 7200)  // Sat (met)
        )

        val stats = StreakEngine.computeFocusGoalStats(sessions, dailyGoalMinutes = 120, todayDate = today)

        // Streak shield should activate and connect Saturday to Thursday & Wednesday (3 total streak days)
        assertTrue(stats.streakShieldActive)
        assertEquals(3, stats.currentStreak)
        assertEquals(StreakTier.SPARK, stats.streakMilestone.tier)
    }

    @Test
    fun tier1_doubleMiss_breaksStreak() {
        val today = LocalDate.of(2026, 11, 7) // Saturday

        // User studied on Tue, Wed, MISSED Thursday and Friday, and completed goal on Saturday
        val sessions = listOf(
            FocusSessionRecord("2026-11-03T10:00:00", 7200), // Tue
            FocusSessionRecord("2026-11-04T10:00:00", 7200), // Wed
            // Thu missed
            // Fri missed
            FocusSessionRecord("2026-11-07T10:00:00", 7200)  // Sat
        )

        val stats = StreakEngine.computeFocusGoalStats(sessions, dailyGoalMinutes = 120, todayDate = today)

        // Shield cannot bridge 2 consecutive missed days -> streak is only today (1 day)
        assertFalse(stats.streakShieldActive)
        assertEquals(1, stats.currentStreak)
        assertEquals(StreakTier.NONE, stats.streakMilestone.tier)
    }

    @Test
    fun tier1_todayIncomplete_evaluatesStreakFromYesterday() {
        val today = LocalDate.of(2026, 11, 7)

        // User studied Mon, Tue, Wed, Thu, Fri (5 days). Today (Sat) has only 30m / 120m goal.
        val sessions = listOf(
            FocusSessionRecord("2026-11-02T10:00:00", 7200), // Mon
            FocusSessionRecord("2026-11-03T10:00:00", 7200), // Tue
            FocusSessionRecord("2026-11-04T10:00:00", 7200), // Wed
            FocusSessionRecord("2026-11-05T10:00:00", 7200), // Thu
            FocusSessionRecord("2026-11-06T10:00:00", 7200), // Fri
            FocusSessionRecord("2026-11-07T10:00:00", 1800)  // Sat (only 30m - not met yet)
        )

        val stats = StreakEngine.computeFocusGoalStats(sessions, dailyGoalMinutes = 120, todayDate = today)

        assertFalse(stats.todayGoalMet)
        assertEquals(5, stats.currentStreak) // Preserved 5-day streak pending today's completion
        assertFalse(stats.streakShieldActive)
        assertEquals(StreakTier.SPARK, stats.streakMilestone.tier)
    }

    @Test
    fun tier1_goalCelebrationTrigger_firesExactlyOnceWhenCrossingThreshold() {
        val goalSeconds = 120 * 60 // 7200s

        // 1. Session 1: 0 -> 3600s (below goal) -> no celebration
        assertFalse(StreakEngine.shouldTriggerCelebration(0, 3600, goalSeconds))

        // 2. Session 2: 3600 -> 7200s (reaches goal!) -> triggers celebration!
        assertTrue(StreakEngine.shouldTriggerCelebration(3600, 7200, goalSeconds))

        // 3. Session 3: 7200 -> 9000s (already met prior to session) -> no duplicate celebration!
        assertFalse(StreakEngine.shouldTriggerCelebration(7200, 9000, goalSeconds))
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (>=5 per feature)
    // =========================================================================

    @Test
    fun tier2_boundary_zeroAndNegativeDurationsIgnoredInDailyTotals() {
        val sessions = listOf(
            FocusSessionRecord("2026-11-07T10:00:00", -500),
            FocusSessionRecord("2026-11-07T11:00:00", 0),
            FocusSessionRecord("2026-11-07T12:00:00", 1500)
        )
        val totals = StreakEngine.aggregateDailyTotals(sessions)
        assertEquals(1500, totals["2026-11-07"])
    }

    @Test
    fun tier2_boundary_nonFocusSessionsExcluded() {
        val sessions = listOf(
            FocusSessionRecord("2026-11-07T10:00:00", 3000, sessionType = "focus"),
            FocusSessionRecord("2026-11-07T11:00:00", 1800, sessionType = "short-break"),
            FocusSessionRecord("2026-11-07T12:00:00", 3600, sessionType = "long-break")
        )
        val totals = StreakEngine.aggregateDailyTotals(sessions)
        assertEquals(3000, totals["2026-11-07"])
    }

    @Test
    fun tier2_boundary_emptySessionsProducesZeroStreak() {
        val today = LocalDate.of(2026, 11, 7)
        val stats = StreakEngine.computeFocusGoalStats(emptyList(), dailyGoalMinutes = 120, todayDate = today)

        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.bestStreak)
        assertFalse(stats.todayGoalMet)
        assertFalse(stats.streakShieldActive)
        assertEquals(StreakTier.NONE, stats.streakMilestone.tier)
    }

    @Test
    fun tier2_boundary_progressCalculationIntoMilestone() {
        // At 10 days (between 7d Momentum and 14d Commitment, range is 7 days)
        // Progress = (10 - 7) / 7 = 3 / 7 = 43%
        val m10 = StreakEngine.calculateStreakMilestone(10)
        assertEquals(StreakTier.MOMENTUM, m10.tier)
        assertEquals(43, m10.progressPercent)
        assertEquals(4, m10.daysToNext) // 14 - 10 = 4 days
    }

    @Test
    fun tier2_boundary_monthAndYearTransitionStreak() {
        val today = LocalDate.of(2027, 1, 2)
        // User studied on Dec 30, Dec 31, Jan 1, Jan 2
        val sessions = listOf(
            FocusSessionRecord("2026-12-30T10:00:00", 7200),
            FocusSessionRecord("2026-12-31T10:00:00", 7200),
            FocusSessionRecord("2027-01-01T10:00:00", 7200),
            FocusSessionRecord("2027-01-02T10:00:00", 7200)
        )
        val stats = StreakEngine.computeFocusGoalStats(sessions, dailyGoalMinutes = 120, todayDate = today)

        assertEquals(4, stats.currentStreak)
        assertEquals(StreakTier.SPARK, stats.streakMilestone.tier)
        assertFalse(stats.streakShieldActive)
    }

    // =========================================================================
    // Tier 3: Cross-Feature Interaction
    // =========================================================================

    @Test
    fun tier3_crossFeature_shieldActivationWithThresholdCelebration() {
        val today = LocalDate.of(2026, 11, 7)
        // User had 4-day streak, missed yesterday (Friday), today is completing goal session
        val pastSessions = listOf(
            FocusSessionRecord("2026-11-02T10:00:00", 7200), // Mon (1)
            FocusSessionRecord("2026-11-03T10:00:00", 7200), // Tue (2)
            FocusSessionRecord("2026-11-04T10:00:00", 7200), // Wed (3)
            FocusSessionRecord("2026-11-05T10:00:00", 7200)  // Thu (4)
            // Friday missed
        )

        // Morning: 0 seconds today, and yesterday was missed -> current streak is 0 until today is completed, but best streak is 4
        val morningStats = StreakEngine.computeFocusGoalStats(pastSessions, 120, today)
        assertEquals(0, morningStats.currentStreak)
        assertEquals(4, morningStats.bestStreak)
        assertFalse(morningStats.todayGoalMet)

        // Session 1: 60 minutes (3600s)
        assertFalse(StreakEngine.shouldTriggerCelebration(0, 3600, 7200))

        // Session 2: finishes remaining 60 minutes -> crosses 7200s goal!
        assertTrue(StreakEngine.shouldTriggerCelebration(3600, 7200, 7200))

        // Evening stats: today added
        val allSessions = pastSessions + FocusSessionRecord("2026-11-07T16:00:00", 7200)
        val eveningStats = StreakEngine.computeFocusGoalStats(allSessions, 120, today)

        assertTrue(eveningStats.todayGoalMet)
        assertTrue(eveningStats.streakShieldActive)
        assertEquals(5, eveningStats.currentStreak) // 4 past + 1 today = 5 days preserved!
    }
}
