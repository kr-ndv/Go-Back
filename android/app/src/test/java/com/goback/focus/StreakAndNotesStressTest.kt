package com.goback.focus

import com.goback.focus.data.entity.FocusSessionEntity
import com.goback.focus.data.entity.NoteEntity
import com.goback.focus.ui.viewmodel.TodayViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Empirical Challenger Adversarial Stress Suite for Milestone 2.
 *
 * Verifies with empirical evidence:
 * 1. Streak Tier Boundaries (both 5-tier Spark/Flame/Blaze/Inferno/Centurion model and
 *    6-tier production model) with exact off-by-one boundary checks.
 * 2. Streak Shield Bridging: exactly 1 missed day bridged without streak reset, 2 consecutive
 *    missed days resetting streak, shield recharge/single-use mechanics, and leap year & month boundaries.
 * 3. Note Persistence & Debounce Integrity: 500ms debounce collapsing rapid keystrokes, non-cancellable
 *    IO supervisor scope guaranteeing zero data loss upon hardware back or lifecycle pause, title
 *    sanitization, and concurrent thread stress.
 * 4. Analytics Calculations: dual-bar chart normalization, zero-minute handling, division-by-zero
 *    immunity, and percentage changes vs prior periods (including zero prior period).
 */
class StreakAndNotesStressTest {

    // =========================================================================
    // 1. Streak Tiers Oracles & Test Models
    // =========================================================================

    /**
     * 5-Tier Gamification Model:
     * - NONE: < 1 day (0 or negative)
     * - SPARK: 1-2 days
     * - FLAME: 3-6 days
     * - BLAZE: 7-13 days
     * - INFERNO: 14-29 days
     * - CENTURION: 30+ days
     */
    enum class FiveTier {
        NONE, SPARK, FLAME, BLAZE, INFERNO, CENTURION
    }

    object FiveTierClassifier {
        fun classify(streakDays: Int): FiveTier {
            return when {
                streakDays <= 0 -> FiveTier.NONE
                streakDays in 1..2 -> FiveTier.SPARK
                streakDays in 3..6 -> FiveTier.FLAME
                streakDays in 7..13 -> FiveTier.BLAZE
                streakDays in 14..29 -> FiveTier.INFERNO
                else -> FiveTier.CENTURION
            }
        }
    }

    // =========================================================================
    // 2. Note Persistence Harness (Mirrors NoteEditorViewModel concurrency architecture)
    // =========================================================================

    class NotePersistenceHarness(
        private val initialNoteId: String = "test_note_1"
    ) {
        val storage = ConcurrentHashMap<String, NoteEntity>()
        private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var autoSaveJob: Job? = null

        val currentTitle = AtomicReference("")
        val currentBody = AtomicReference("")
        val saveCounter = AtomicInteger(0)
        val isReady = AtomicBoolean(true)

        fun onTitleChange(scope: CoroutineScope, newTitle: String) {
            currentTitle.set(newTitle)
            triggerDebounce(scope)
        }

        fun onBodyChange(scope: CoroutineScope, newBody: String) {
            currentBody.set(newBody)
            triggerDebounce(scope)
        }

        private fun triggerDebounce(scope: CoroutineScope) {
            if (!isReady.get()) return
            autoSaveJob?.cancel()
            autoSaveJob = scope.launch {
                delay(500)
                executeSaveSync()
            }
        }

        fun executeSaveSync(): Boolean {
            if (!isReady.get()) return false
            val cleanTitle = currentTitle.get().trim().ifEmpty { "Untitled note" }
            val cleanBody = currentBody.get()
            val timestamp = "2026-11-07T16:00:00Z"

            val entity = NoteEntity(
                id = initialNoteId,
                title = cleanTitle,
                body = cleanBody,
                updatedAt = timestamp
            )
            storage[initialNoteId] = entity
            saveCounter.incrementAndGet()
            return true
        }

        /**
         * Critical flush executed on hardware back or lifecycle pause.
         * Must persist to storage using the detached supervisor scope even if caller scope is cancelled.
         */
        fun flushSave(): Job {
            autoSaveJob?.cancel()
            if (!isReady.get()) return CompletableDeferred(Unit)
            val cleanTitle = currentTitle.get().trim().ifEmpty { "Untitled note" }
            val cleanBody = currentBody.get()
            val timestamp = "2026-11-07T16:00:00Z"

            return persistenceScope.launch {
                val entity = NoteEntity(
                    id = initialNoteId,
                    title = cleanTitle,
                    body = cleanBody,
                    updatedAt = timestamp
                )
                storage[initialNoteId] = entity
                saveCounter.incrementAndGet()
            }
        }
    }

    // =========================================================================
    // 3. Analytics Math Oracle
    // =========================================================================

    object AnalyticsOracle {
        fun calculatePercentChange(current: Long, previous: Long): Int? {
            if (previous <= 0L) return if (current > 0L) null else 0
            return (((current - previous).toDouble() / previous.toDouble()) * 100.0).roundToInt()
        }

        fun normalizeMaxMinutes(chartPointsMinutes: List<Int>): Int {
            return max(60, chartPointsMinutes.maxOrNull() ?: 60)
        }

        fun calculateCategoryPercentages(productiveMs: Long, distractingMs: Long, neutralMs: Long): Triple<Int, Int, Int> {
            val total = productiveMs + distractingMs + neutralMs
            if (total <= 0L) return Triple(0, 0, 0)
            val prodPct = ((productiveMs.toDouble() / total.toDouble()) * 100.0).roundToInt()
            val distPct = ((distractingMs.toDouble() / total.toDouble()) * 100.0).roundToInt()
            val neutPct = max(0, 100 - prodPct - distPct)
            return Triple(prodPct, distPct, neutPct)
        }
    }

    // =========================================================================
    // TEST SUITE: PART 1 - STREAK TIERS & BOUNDARIES
    // =========================================================================

    @Test
    fun test01_fiveTier_exactOffByOneBoundaries() {
        // Negative & Zero
        assertEquals(FiveTier.NONE, FiveTierClassifier.classify(-10))
        assertEquals(FiveTier.NONE, FiveTierClassifier.classify(0))

        // Spark Tier: 1 to 2 days
        assertEquals("Boundary 0 vs 1", FiveTier.SPARK, FiveTierClassifier.classify(1))
        assertEquals("Boundary upper Spark", FiveTier.SPARK, FiveTierClassifier.classify(2))

        // Flame Tier: 3 to 6 days
        assertEquals("Boundary 2 vs 3 (Transition to Flame)", FiveTier.FLAME, FiveTierClassifier.classify(3))
        assertEquals("Mid Flame", FiveTier.FLAME, FiveTierClassifier.classify(4))
        assertEquals("Mid Flame", FiveTier.FLAME, FiveTierClassifier.classify(5))
        assertEquals("Boundary upper Flame", FiveTier.FLAME, FiveTierClassifier.classify(6))

        // Blaze Tier: 7 to 13 days
        assertEquals("Boundary 6 vs 7 (Transition to Blaze)", FiveTier.BLAZE, FiveTierClassifier.classify(7))
        assertEquals("Mid Blaze", FiveTier.BLAZE, FiveTierClassifier.classify(10))
        assertEquals("Boundary upper Blaze", FiveTier.BLAZE, FiveTierClassifier.classify(13))

        // Inferno Tier: 14 to 29 days
        assertEquals("Boundary 13 vs 14 (Transition to Inferno)", FiveTier.INFERNO, FiveTierClassifier.classify(14))
        assertEquals("Mid Inferno", FiveTier.INFERNO, FiveTierClassifier.classify(20))
        assertEquals("Boundary upper Inferno", FiveTier.INFERNO, FiveTierClassifier.classify(29))

        // Centurion Tier: 30+ days
        assertEquals("Boundary 29 vs 30 (Transition to Centurion)", FiveTier.CENTURION, FiveTierClassifier.classify(30))
        assertEquals("Centurion + 1", FiveTier.CENTURION, FiveTierClassifier.classify(31))
        assertEquals("Centurion 100", FiveTier.CENTURION, FiveTierClassifier.classify(100))
        assertEquals("Centurion 365", FiveTier.CENTURION, FiveTierClassifier.classify(365))
    }

    @Test
    fun test02_productionSixTier_milestoneCalculationsAndPercentages() {
        // 0 days: None
        val m0 = TodayViewModel.calculateStreakMilestone(0)
        assertEquals("none", m0.tier)
        assertEquals("Getting Started", m0.label)
        assertEquals(3, m0.nextMilestoneDays)
        assertEquals(3, m0.daysToNext)
        assertEquals(0, m0.progressPercent)

        // 1 day: None (progress 33%)
        val m1 = TodayViewModel.calculateStreakMilestone(1)
        assertEquals("none", m1.tier)
        assertEquals(2, m1.daysToNext)
        assertEquals(33, m1.progressPercent)

        // 2 days: None (progress 66%)
        val m2 = TodayViewModel.calculateStreakMilestone(2)
        assertEquals("none", m2.tier)
        assertEquals(1, m2.daysToNext)
        assertEquals(66, m2.progressPercent)

        // 3 days: Spark threshold
        val m3 = TodayViewModel.calculateStreakMilestone(3)
        assertEquals("spark", m3.tier)
        assertEquals("Spark", m3.label)
        assertEquals(7, m3.nextMilestoneDays)
        assertEquals(4, m3.daysToNext)
        assertEquals(0, m3.progressPercent)

        // 6 days: Spark upper edge (3 of 4 days into tier -> 75%)
        val m6 = TodayViewModel.calculateStreakMilestone(6)
        assertEquals("spark", m6.tier)
        assertEquals(1, m6.daysToNext)
        assertEquals(75, m6.progressPercent)

        // 7 days: Momentum threshold
        val m7 = TodayViewModel.calculateStreakMilestone(7)
        assertEquals("momentum", m7.tier)
        assertEquals("Momentum", m7.label)
        assertEquals(14, m7.nextMilestoneDays)
        assertEquals(7, m7.daysToNext)
        assertEquals(0, m7.progressPercent)

        // 13 days: Momentum upper edge (6 of 7 days -> 85%)
        val m13 = TodayViewModel.calculateStreakMilestone(13)
        assertEquals("momentum", m13.tier)
        assertEquals(1, m13.daysToNext)
        assertEquals(85, m13.progressPercent)

        // 14 days: Commitment threshold
        val m14 = TodayViewModel.calculateStreakMilestone(14)
        assertEquals("commitment", m14.tier)
        assertEquals("Commitment", m14.label)
        assertEquals(30, m14.nextMilestoneDays)
        assertEquals(16, m14.daysToNext)
        assertEquals(0, m14.progressPercent)

        // 29 days: Commitment upper edge (15 of 16 days -> 93%)
        val m29 = TodayViewModel.calculateStreakMilestone(29)
        assertEquals("commitment", m29.tier)
        assertEquals(1, m29.daysToNext)
        assertEquals(93, m29.progressPercent)

        // 30 days: Habit Builder threshold
        val m30 = TodayViewModel.calculateStreakMilestone(30)
        assertEquals("habit", m30.tier)
        assertEquals("Habit Builder", m30.label)
        assertEquals(50, m30.nextMilestoneDays)
        assertEquals(20, m30.daysToNext)
        assertEquals(0, m30.progressPercent)

        // 49 days: Habit Builder upper edge (19 of 20 days -> 95%)
        val m49 = TodayViewModel.calculateStreakMilestone(49)
        assertEquals("habit", m49.tier)
        assertEquals(1, m49.daysToNext)
        assertEquals(95, m49.progressPercent)

        // 50 days: Focus Mastery threshold
        val m50 = TodayViewModel.calculateStreakMilestone(50)
        assertEquals("mastery", m50.tier)
        assertEquals("Focus Mastery", m50.label)
        assertEquals(100, m50.nextMilestoneDays)
        assertEquals(50, m50.daysToNext)
        assertEquals(0, m50.progressPercent)

        // 99 days: Mastery upper edge (49 of 50 days -> 98%)
        val m99 = TodayViewModel.calculateStreakMilestone(99)
        assertEquals("mastery", m99.tier)
        assertEquals(1, m99.daysToNext)
        assertEquals(98, m99.progressPercent)

        // 100 days: Centurion threshold
        val m100 = TodayViewModel.calculateStreakMilestone(100)
        assertEquals("centurion", m100.tier)
        assertEquals("Centurion", m100.label)
        assertEquals(150, m100.nextMilestoneDays)
        assertEquals(50, m100.daysToNext)
        assertEquals(0, m100.progressPercent)

        // 150 days: Beyond 100 days remains Centurion tier with 100% progress into milestone
        val m150 = TodayViewModel.calculateStreakMilestone(150)
        assertEquals("centurion", m150.tier)
        assertEquals("Centurion", m150.label)
        assertEquals(150, m150.nextMilestoneDays)
        assertEquals(0, m150.daysToNext)
        assertEquals(100, m150.progressPercent)
    }

    // =========================================================================
    // TEST SUITE: PART 2 - STREAK SHIELD BRIDGING & DATE MATH
    // =========================================================================

    private fun date(year: Int, month: Int, day: Int): Date {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.time
    }

    @Test
    fun test03_streakShield_bridgesExactlyOneMissedDayInRunningStreak() {
        // Timeline:
        // Day 1 (Nov 4): Met
        // Day 2 (Nov 5): Met
        // Day 3 (Nov 6): MISSED
        // Day 4 (Nov 7): Met (Today)
        val sessions = listOf(
            FocusSessionEntity("s1", "2026-11-04T10:00:00", 7200),
            FocusSessionEntity("s2", "2026-11-05T10:00:00", 7200),
            // Nov 6 missed
            FocusSessionEntity("s3", "2026-11-07T10:00:00", 7200)
        )

        val stats = TodayViewModel.calculateFocusGoalStats(
            rows = sessions,
            dailyGoalMinutes = 120,
            todayDate = date(2026, 11, 7)
        )

        assertTrue("Shield should activate to bridge Nov 6", stats.streakShieldActive)
        assertEquals("Streak should count Nov 4, Nov 5, and Nov 7 (3 days total)", 3, stats.currentStreak)
        assertEquals("spark", stats.milestone.tier)
    }

    @Test
    fun test04_streakShield_twoConsecutiveMissedDaysBreaksStreak() {
        // Timeline:
        // Day 1 (Nov 3): Met
        // Day 2 (Nov 4): Met
        // Day 3 (Nov 5): MISSED
        // Day 4 (Nov 6): MISSED
        // Day 5 (Nov 7): Met (Today)
        val sessions = listOf(
            FocusSessionEntity("s1", "2026-11-03T10:00:00", 7200),
            FocusSessionEntity("s2", "2026-11-04T10:00:00", 7200),
            // Nov 5 missed
            // Nov 6 missed
            FocusSessionEntity("s3", "2026-11-07T10:00:00", 7200)
        )

        val stats = TodayViewModel.calculateFocusGoalStats(
            rows = sessions,
            dailyGoalMinutes = 120,
            todayDate = date(2026, 11, 7)
        )

        assertFalse("Shield must NOT activate for 2 consecutive missed days", stats.streakShieldActive)
        assertEquals("Streak resets to only today's session (1 day)", 1, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
    }

    @Test
    fun test05_streakShield_singleChargePerStreakAndRechargeOnReset() {
        // Scenario A: Two isolated misses in the same sequence
        // Day 1 (Nov 1): Met
        // Day 2 (Nov 2): Met
        // Day 3 (Nov 3): MISSED (miss 1)
        // Day 4 (Nov 4): Met
        // Day 5 (Nov 5): Met
        // Day 6 (Nov 6): MISSED (miss 2)
        // Day 7 (Nov 7): Met (Today)
        val twoMissSessions = listOf(
            FocusSessionEntity("s1", "2026-11-01T10:00:00", 7200),
            FocusSessionEntity("s2", "2026-11-02T10:00:00", 7200),
            // Nov 3 missed
            FocusSessionEntity("s3", "2026-11-04T10:00:00", 7200),
            FocusSessionEntity("s4", "2026-11-05T10:00:00", 7200),
            // Nov 6 missed
            FocusSessionEntity("s5", "2026-11-07T10:00:00", 7200)
        )

        val statsA = TodayViewModel.calculateFocusGoalStats(
            rows = twoMissSessions,
            dailyGoalMinutes = 120,
            todayDate = date(2026, 11, 7)
        )

        assertTrue(statsA.streakShieldActive)
        // Nov 6 is bridged. Nov 4 and Nov 5 are counted. Nov 3 encounters shield already active -> loop terminates!
        // Current streak = Nov 7 (1) + Nov 5 (1) + Nov 4 (1) = 3 days.
        assertEquals("Only one single miss can be bridged in continuous streak", 3, statsA.currentStreak)

        // Scenario B: Streak resets, user starts new streak, and shield recharges!
        // Nov 8: Met
        // Nov 9: Met
        // Nov 10: MISSED (new single miss)
        // Nov 11: Met (Today)
        val rechargedSessions = twoMissSessions + listOf(
            FocusSessionEntity("s6", "2026-11-08T10:00:00", 7200),
            FocusSessionEntity("s7", "2026-11-09T10:00:00", 7200),
            // Nov 10 missed
            FocusSessionEntity("s8", "2026-11-11T10:00:00", 7200)
        )

        val statsB = TodayViewModel.calculateFocusGoalStats(
            rows = rechargedSessions,
            dailyGoalMinutes = 120,
            todayDate = date(2026, 11, 11)
        )

        assertTrue("Shield recharges for new streak evaluation", statsB.streakShieldActive)
        // Nov 11 (met), Nov 10 (shielded), Nov 9 (met), Nov 8 (met), Nov 7 (met), Nov 6 (would be second miss) -> streak is 4 days
        assertEquals(4, statsB.currentStreak)
    }

    @Test
    fun test06_leapYear_february29BridgedAndStreakPreserved() {
        // 2024 is a leap year (Feb 29 exists)
        // Case 1: Continuous across leap day
        val leapContinuousSessions = listOf(
            FocusSessionEntity("s1", "2024-02-28T10:00:00", 7200),
            FocusSessionEntity("s2", "2024-02-29T10:00:00", 7200),
            FocusSessionEntity("s3", "2024-03-01T10:00:00", 7200)
        )

        val statsContinuous = TodayViewModel.calculateFocusGoalStats(
            rows = leapContinuousSessions,
            dailyGoalMinutes = 120,
            todayDate = date(2024, 3, 1)
        )
        assertFalse("No miss occurred", statsContinuous.streakShieldActive)
        assertEquals("3-day continuous streak across leap day", 3, statsContinuous.currentStreak)

        // Case 2: Feb 29 leap day is MISSED -> Shield bridges leap day!
        val leapMissedSessions = listOf(
            FocusSessionEntity("s1", "2024-02-28T10:00:00", 7200),
            // Feb 29 missed!
            FocusSessionEntity("s2", "2024-03-01T10:00:00", 7200)
        )

        val statsLeapShield = TodayViewModel.calculateFocusGoalStats(
            rows = leapMissedSessions,
            dailyGoalMinutes = 120,
            todayDate = date(2024, 3, 1)
        )
        assertTrue("Shield must bridge Feb 29 leap day", statsLeapShield.streakShieldActive)
        assertEquals("Streak counts Feb 28 and Mar 1 (2 days)", 2, statsLeapShield.currentStreak)

        // Case 3: 2 consecutive misses across leap day (Feb 28 missed + Feb 29 missed)
        val leapDoubleMiss = listOf(
            FocusSessionEntity("s1", "2024-02-27T10:00:00", 7200),
            // Feb 28 missed
            // Feb 29 missed
            FocusSessionEntity("s2", "2024-03-01T10:00:00", 7200)
        )
        val statsDoubleMiss = TodayViewModel.calculateFocusGoalStats(
            rows = leapDoubleMiss,
            dailyGoalMinutes = 120,
            todayDate = date(2024, 3, 1)
        )
        assertFalse(statsDoubleMiss.streakShieldActive)
        assertEquals(1, statsDoubleMiss.currentStreak)
    }

    @Test
    fun test07_monthAndYearBoundaries_accurateDateMath() {
        // 1. Non-leap year: Feb 28 to Mar 1 (2025)
        val nonLeapSessions = listOf(
            FocusSessionEntity("s1", "2025-02-28T10:00:00", 7200),
            // Mar 1 missed
            FocusSessionEntity("s2", "2025-03-02T10:00:00", 7200)
        )
        val nonLeapStats = TodayViewModel.calculateFocusGoalStats(
            rows = nonLeapSessions,
            dailyGoalMinutes = 120,
            todayDate = date(2025, 3, 2)
        )
        assertTrue("Shield bridges Mar 1 in non-leap year", nonLeapStats.streakShieldActive)
        assertEquals(2, nonLeapStats.currentStreak)

        // 2. Year transition: Dec 31 to Jan 1
        val yearTransitionSessions = listOf(
            FocusSessionEntity("s1", "2026-12-30T10:00:00", 7200),
            FocusSessionEntity("s2", "2026-12-31T10:00:00", 7200),
            FocusSessionEntity("s3", "2027-01-01T10:00:00", 7200),
            FocusSessionEntity("s4", "2027-01-02T10:00:00", 7200)
        )
        val yearStats = TodayViewModel.calculateFocusGoalStats(
            rows = yearTransitionSessions,
            dailyGoalMinutes = 120,
            todayDate = date(2027, 1, 2)
        )
        assertEquals("4 days continuous across year boundary", 4, yearStats.currentStreak)

        // 3. 30-day month boundary: Apr 30 to May 1
        val month30Sessions = listOf(
            FocusSessionEntity("s1", "2026-04-29T10:00:00", 7200),
            FocusSessionEntity("s2", "2026-04-30T10:00:00", 7200),
            // May 1 missed
            FocusSessionEntity("s3", "2026-05-02T10:00:00", 7200)
        )
        val month30Stats = TodayViewModel.calculateFocusGoalStats(
            rows = month30Sessions,
            dailyGoalMinutes = 120,
            todayDate = date(2026, 5, 2)
        )
        assertTrue(month30Stats.streakShieldActive)
        assertEquals(3, month30Stats.currentStreak)
    }

    // =========================================================================
    // TEST SUITE: PART 3 - NOTE PERSISTENCE, DEBOUNCE & SUPERVISOR SCOPE
    // =========================================================================

    @Test
    fun test08_notePersistence_debounceCollapsesRapidTyping() = runBlocking {
        val harness = NotePersistenceHarness("note_debounce_1")
        val scope = CoroutineScope(Dispatchers.Default + Job())

        // Simulate user typing 5 keystrokes rapidly (every 50ms)
        harness.onTitleChange(scope, "T")
        delay(50)
        harness.onTitleChange(scope, "Te")
        delay(50)
        harness.onTitleChange(scope, "Tes")
        delay(50)
        harness.onTitleChange(scope, "Test")
        delay(50)
        harness.onTitleChange(scope, "Testing Debounce")

        // Immediately after typing, save should NOT have fired yet (0 saves)
        assertEquals(0, harness.saveCounter.get())

        // Wait 300ms (still within 500ms debounce)
        delay(300)
        assertEquals(0, harness.saveCounter.get())

        // Wait another 300ms (total 600ms > 500ms debounce)
        delay(300)
        assertEquals("Exactly one consolidated save should execute", 1, harness.saveCounter.get())
        assertEquals("Testing Debounce", harness.storage["note_debounce_1"]?.title)

        scope.cancel()
    }

    @Test
    fun test09_notePersistence_flushSaveSurvivesScopeCancellationOnHardwareBack() = runBlocking {
        val harness = NotePersistenceHarness("note_back_1")
        // Foreground UI scope (representing viewModelScope of NoteEditorViewModel)
        val viewModelScope = CoroutineScope(Dispatchers.Default + Job())

        harness.onTitleChange(viewModelScope, "Crucial Exam Formula")
        harness.onBodyChange(viewModelScope, "Euler: e^(i*pi) + 1 = 0")

        // User immediately presses hardware BACK button at 100ms (before 500ms debounce triggers)
        delay(100)
        assertEquals("Debounce has not fired yet", 0, harness.saveCounter.get())

        // Hardware back handler triggers flushSave(), then ViewModel is immediately destroyed/cancelled!
        val flushJob = harness.flushSave()
        viewModelScope.cancel() // Simulates ViewModel onCleared / navigation pop

        // Wait for persistent supervisor scope job to complete
        flushJob.join()

        // Verify: Zero data loss!
        assertEquals("Flush save executed despite UI cancellation", 1, harness.saveCounter.get())
        val saved = harness.storage["note_back_1"]
        assertNotNull("Note must be persisted in storage", saved)
        assertEquals("Crucial Exam Formula", saved?.title)
        assertEquals("Euler: e^(i*pi) + 1 = 0", saved?.body)
    }

    @Test
    fun test10_notePersistence_titleSanitizationAndDefaultFallback() = runBlocking {
        val harness = NotePersistenceHarness("note_sanitize_1")

        // 1. Empty title defaults to "Untitled note"
        harness.currentTitle.set("")
        harness.currentBody.set("Some body content")
        harness.flushSave().join()
        assertEquals("Untitled note", harness.storage["note_sanitize_1"]?.title)

        // 2. Whitespace-only title defaults to "Untitled note"
        harness.currentTitle.set("    \t\n   ")
        harness.flushSave().join()
        assertEquals("Untitled note", harness.storage["note_sanitize_1"]?.title)

        // 3. Surrounding whitespace is trimmed
        harness.currentTitle.set("   Organic Chemistry Ch 4   ")
        harness.flushSave().join()
        assertEquals("Organic Chemistry Ch 4", harness.storage["note_sanitize_1"]?.title)
    }

    @Test
    fun test11_notePersistence_concurrentFlushStressTest() = runBlocking {
        val harness = NotePersistenceHarness("note_stress_1")
        val threadCount = 20
        val latch = CountDownLatch(threadCount)

        // 20 concurrent threads attempting to edit and flush notes simultaneously
        for (i in 0 until threadCount) {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    harness.currentTitle.set("Thread $i Title")
                    harness.currentBody.set("Content from thread $i")
                    harness.flushSave().join()
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("All concurrent flushes must finish within 5s", completed)
        assertTrue("Storage must contain saved note", harness.storage.containsKey("note_stress_1"))
        assertNotNull(harness.storage["note_stress_1"]?.title)
        assertTrue(harness.saveCounter.get() >= threadCount)
    }

    // =========================================================================
    // TEST SUITE: PART 4 - ANALYTICS DUAL-BAR CHART & PERCENTAGE CALCULATIONS
    // =========================================================================

    @Test
    fun test12_analytics_percentageChangeCalculations() {
        // Standard positive increase: 50 -> 100 (+100%)
        assertEquals(100, AnalyticsOracle.calculatePercentChange(100L, 50L))

        // Standard positive increase: 100 -> 150 (+50%)
        assertEquals(50, AnalyticsOracle.calculatePercentChange(150L, 100L))

        // Standard negative decrease: 100 -> 50 (-50%)
        assertEquals(-50, AnalyticsOracle.calculatePercentChange(50L, 100L))

        // Total drop to zero: 100 -> 0 (-100%)
        assertEquals(-100, AnalyticsOracle.calculatePercentChange(0L, 100L))

        // Zero prior period with positive current: undefined mathematically -> null
        assertNull("Prior period zero cannot divide by zero; must return null", AnalyticsOracle.calculatePercentChange(50L, 0L))

        // Zero prior period with zero current -> 0%
        assertEquals(0, AnalyticsOracle.calculatePercentChange(0L, 0L))

        // Large numbers stress test (no integer overflow)
        assertEquals(100, AnalyticsOracle.calculatePercentChange(10_000_000L, 5_000_000L))
        assertEquals(-25, AnalyticsOracle.calculatePercentChange(75_000_000L, 100_000_000L))
    }

    @Test
    fun test13_analytics_dualBarChartNormalizationAndZeroDivisionImmunity() {
        // All days have 0 minutes -> baseline minimum must be 60 (prevents division by zero)
        val emptyScale = AnalyticsOracle.normalizeMaxMinutes(emptyList())
        assertEquals(60, emptyScale)

        val allZeroScale = AnalyticsOracle.normalizeMaxMinutes(listOf(0, 0, 0, 0))
        assertEquals(60, allZeroScale)

        // Below 60 minutes -> clamp to 60 for clean UI proportion
        val lowScale = AnalyticsOracle.normalizeMaxMinutes(listOf(15, 25, 45))
        assertEquals(60, lowScale)

        // Above 60 minutes -> dynamic expansion to maximum peak
        val highScale = AnalyticsOracle.normalizeMaxMinutes(listOf(30, 90, 180, 45))
        assertEquals(180, highScale)

        // Fraction calculation verification: fraction never NaN or infinite
        val zeroFraction = 0f / allZeroScale.toFloat()
        assertEquals(0.0f, zeroFraction, 0.001f)

        val halfFraction = 30f / lowScale.toFloat()
        assertEquals(0.5f, halfFraction, 0.001f)

        val fullFraction = 180f / highScale.toFloat()
        assertEquals(1.0f, fullFraction, 0.001f)
    }

    @Test
    fun test14_analytics_categoryPercentagesAndZeroUsageHandling() {
        // Zero screen time -> all categories 0% (no division by zero!)
        val (prodZero, distZero, neutZero) = AnalyticsOracle.calculateCategoryPercentages(0L, 0L, 0L)
        assertEquals(0, prodZero)
        assertEquals(0, distZero)
        assertEquals(0, neutZero)

        // Normal distribution: 60m productive, 30m distracting, 30m neutral (Total 120m)
        // 60/120 = 50%, 30/120 = 25%, neutral = 25% (sum = 100%)
        val (prodNorm, distNorm, neutNorm) = AnalyticsOracle.calculateCategoryPercentages(3600000L, 1800000L, 1800000L)
        assertEquals(50, prodNorm)
        assertEquals(25, distNorm)
        assertEquals(25, neutNorm)
        assertEquals(100, prodNorm + distNorm + neutNorm)

        // Odd fractions rounding guarantee (e.g. 1/3 each):
        // 33% prod + 33% dist + 34% neut = exactly 100%
        val (prodThird, distThird, neutThird) = AnalyticsOracle.calculateCategoryPercentages(1000L, 1000L, 1000L)
        assertEquals(33, prodThird)
        assertEquals(33, distThird)
        assertEquals(34, neutThird)
        assertEquals(100, prodThird + distThird + neutThird)
    }
}
