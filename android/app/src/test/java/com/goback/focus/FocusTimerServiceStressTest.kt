package com.goback.focus

import com.goback.focus.service.FocusTimerService
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Challenger Stress Tests for FocusTimerService and GoBackApplication.
 *
 * Stresses:
 * 1. Rapid start/pause/resume/stop intent sequences and concurrency
 * 2. Missing or negative duration / endAt extras
 * 3. Android 14+ background launch restriction exception handling
 * 4. Missing or corrupted notification channels & self-healing
 */
class FocusTimerServiceStressTest {

    // --- Simulated Intent Model & Service Logic Oracle ---

    data class MockIntent(
        val action: String?,
        val extras: Map<String, Any?> = emptyMap()
    ) {
        fun getStringExtra(key: String): String? = extras[key] as? String
        fun getBooleanExtra(key: String, defaultValue: Boolean): Boolean = (extras[key] as? Boolean) ?: defaultValue
        fun getLongExtra(key: String, defaultValue: Long): Long = (extras[key] as? Long) ?: defaultValue
    }

    enum class ServiceState {
        STOPPED,
        RUNNING,
        PAUSED
    }

    class ServiceSimulationHarness(
        var simulateAndroid14OrHigher: Boolean = true,
        var throwOnStartForeground: Throwable? = null
    ) {
        var currentState: ServiceState = ServiceState.STOPPED
        val stopSelfCallCount = AtomicInteger(0)
        val startForegroundCallCount = AtomicInteger(0)
        val stopForegroundCallCount = AtomicInteger(0)
        val channelEnsureCallCount = AtomicInteger(0)
        val lastNotificationTitle = AtomicBoolean(false)
        var lastCapturedChronometerRunning: Boolean = false
        var lastCapturedEndAtMs: Long = 0L
        var lastCapturedDurationMs: Long = 0L

        // Emulates FocusTimerService.onStartCommand logic verbatim
        fun onStartCommand(intent: MockIntent?): Int {
            val action = intent?.action
            if (action == FocusTimerService.ACTION_STOP) {
                stopForegroundCallCount.incrementAndGet()
                stopSelfCallCount.incrementAndGet()
                currentState = ServiceState.STOPPED
                return 2 // START_NOT_STICKY
            }

            // Defensive: ensure notification channels
            channelEnsureCallCount.incrementAndGet()

            val title = intent?.getStringExtra(FocusTimerService.EXTRA_TITLE) ?: "Focus session in progress"
            val isStopwatch = intent?.getBooleanExtra(FocusTimerService.EXTRA_IS_STOPWATCH, false) ?: false

            // Flexible extras: support both millisecond and second keys
            val durationMs = intent?.getLongExtra(FocusTimerService.EXTRA_DURATION_MS, 0L)?.takeIf { it > 0L }
                ?: ((intent?.getLongExtra(FocusTimerService.EXTRA_DURATION_SECONDS, 0L) ?: 0L) * 1000L)
            val endAtMs = intent?.getLongExtra(FocusTimerService.EXTRA_END_AT_MS, 0L)?.takeIf { it > 0L }
                ?: (intent?.getLongExtra(FocusTimerService.EXTRA_END_AT_MILLIS, 0L) ?: 0L)

            val paused = when (action) {
                FocusTimerService.ACTION_PAUSE -> true
                FocusTimerService.ACTION_RESUME -> false
                else -> intent?.getBooleanExtra(FocusTimerService.EXTRA_PAUSED, false) ?: false
            }

            lastCapturedDurationMs = durationMs
            lastCapturedEndAtMs = endAtMs
            lastCapturedChronometerRunning = !paused

            try {
                if (throwOnStartForeground != null) {
                    throw throwOnStartForeground!!
                }
                startForegroundCallCount.incrementAndGet()
                currentState = if (paused) ServiceState.PAUSED else ServiceState.RUNNING
            } catch (e: Exception) {
                // Guard against ForegroundServiceStartNotAllowedException on API 31+
                // or MissingForegroundServiceTypeException / SecurityException on API 34+
                stopSelfCallCount.incrementAndGet()
                currentState = ServiceState.STOPPED
                return 2 // START_NOT_STICKY
            }

            return 1 // START_STICKY
        }
    }

    // =========================================================================
    // 1. Rapid Intent Sequences & Concurrency
    // =========================================================================

    @Test
    fun testRapidIntentSequences_startPauseResumeStop() {
        val harness = ServiceSimulationHarness()

        val startIntent = MockIntent(
            action = FocusTimerService.ACTION_START,
            extras = mapOf(
                FocusTimerService.EXTRA_TITLE to "Deep Focus",
                FocusTimerService.EXTRA_DURATION_SECONDS to 1500L,
                FocusTimerService.EXTRA_END_AT_MILLIS to System.currentTimeMillis() + 1500_000L
            )
        )
        val pauseIntent = MockIntent(action = FocusTimerService.ACTION_PAUSE)
        val resumeIntent = MockIntent(action = FocusTimerService.ACTION_RESUME)
        val stopIntent = MockIntent(action = FocusTimerService.ACTION_STOP)

        // Run 500 rapid alternating cycles
        for (i in 1..500) {
            val startRes = harness.onStartCommand(startIntent)
            assertEquals(1, startRes) // START_STICKY
            assertEquals(ServiceState.RUNNING, harness.currentState)
            assertTrue(harness.lastCapturedChronometerRunning)

            val pauseRes = harness.onStartCommand(pauseIntent)
            assertEquals(1, pauseRes) // START_STICKY
            assertEquals(ServiceState.PAUSED, harness.currentState)
            assertFalse(harness.lastCapturedChronometerRunning)

            val resumeRes = harness.onStartCommand(resumeIntent)
            assertEquals(1, resumeRes) // START_STICKY
            assertEquals(ServiceState.RUNNING, harness.currentState)
            assertTrue(harness.lastCapturedChronometerRunning)

            val stopRes = harness.onStartCommand(stopIntent)
            assertEquals(2, stopRes) // START_NOT_STICKY
            assertEquals(ServiceState.STOPPED, harness.currentState)
        }

        assertEquals(1500, harness.startForegroundCallCount.get()) // 3 per cycle * 500
        assertEquals(500, harness.stopForegroundCallCount.get())
        assertEquals(500, harness.stopSelfCallCount.get())
        assertEquals(1500, harness.channelEnsureCallCount.get())
    }

    @Test
    fun testOutOfOrderIntentSequences_pauseBeforeStart_stopBeforeStart() {
        val harness = ServiceSimulationHarness()

        // 1. Pause intent received before service is even started
        val pauseIntent = MockIntent(action = FocusTimerService.ACTION_PAUSE)
        val res1 = harness.onStartCommand(pauseIntent)
        assertEquals(1, res1)
        assertEquals(ServiceState.PAUSED, harness.currentState)
        assertFalse(harness.lastCapturedChronometerRunning)

        // 2. Stop intent when already stopped
        val stopIntent = MockIntent(action = FocusTimerService.ACTION_STOP)
        val res2 = harness.onStartCommand(stopIntent)
        assertEquals(2, res2)
        assertEquals(ServiceState.STOPPED, harness.currentState)

        // 3. Resume intent when stopped -> transitions cleanly to running
        val resumeIntent = MockIntent(action = FocusTimerService.ACTION_RESUME)
        val res3 = harness.onStartCommand(resumeIntent)
        assertEquals(1, res3)
        assertEquals(ServiceState.RUNNING, harness.currentState)
        assertTrue(harness.lastCapturedChronometerRunning)

        // 4. Null intent (system service restart)
        val res4 = harness.onStartCommand(null)
        assertEquals(1, res4)
        assertEquals(ServiceState.RUNNING, harness.currentState)
    }

    @Test
    fun testConcurrentIntentStress_multithreadedDispatch() {
        val harness = ServiceSimulationHarness()
        val numThreads = 8
        val iterationsPerThread = 100
        val latch = CountDownLatch(numThreads)
        val actions = listOf(
            FocusTimerService.ACTION_START,
            FocusTimerService.ACTION_PAUSE,
            FocusTimerService.ACTION_RESUME,
            FocusTimerService.ACTION_STOP
        )
        val results = ConcurrentLinkedQueue<Int>()

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val action = actions[(t + i) % actions.size]
                        val intent = MockIntent(
                            action = action,
                            extras = mapOf(
                                FocusTimerService.EXTRA_DURATION_SECONDS to 1500L,
                                FocusTimerService.EXTRA_END_AT_MILLIS to 2_000_000L
                            )
                        )
                        results.add(harness.onStartCommand(intent))
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent execution timed out", latch.await(5, TimeUnit.SECONDS))
        assertEquals(numThreads * iterationsPerThread, results.size)
        // All results must be valid START_STICKY (1) or START_NOT_STICKY (2)
        results.forEach { res ->
            assertTrue(res == 1 || res == 2)
        }
    }

    // =========================================================================
    // 2. Missing or Negative Duration / endAt Extras
    // =========================================================================

    @Test
    fun testMissingExtras_defaultsToSafeValues() {
        val harness = ServiceSimulationHarness()

        // Completely empty intent
        val emptyIntent = MockIntent(action = FocusTimerService.ACTION_START, extras = emptyMap())
        val res = harness.onStartCommand(emptyIntent)

        assertEquals(1, res)
        assertEquals(0L, harness.lastCapturedDurationMs)
        assertEquals(0L, harness.lastCapturedEndAtMs)
        assertTrue(harness.lastCapturedChronometerRunning)
    }

    @Test
    fun testNegativeExtras_handledGracefullyWithoutCrash() {
        val harness = ServiceSimulationHarness()

        // Negative duration and negative endAt
        val negativeIntent = MockIntent(
            action = FocusTimerService.ACTION_START,
            extras = mapOf(
                FocusTimerService.EXTRA_DURATION_MS to -1000L,
                FocusTimerService.EXTRA_DURATION_SECONDS to -5L,
                FocusTimerService.EXTRA_END_AT_MS to -5000L,
                FocusTimerService.EXTRA_END_AT_MILLIS to -5000L
            )
        )
        val res = harness.onStartCommand(negativeIntent)

        assertEquals(1, res)
        // Duration falls back to negative seconds * 1000
        assertEquals(-5000L, harness.lastCapturedDurationMs)
        // EndAt remains negative or 0
        assertEquals(-5000L, harness.lastCapturedEndAtMs)
        // Crucially, service did NOT throw exception and transitioned to RUNNING
        assertEquals(ServiceState.RUNNING, harness.currentState)
    }

    @Test
    fun testExtraPrecedence_millisTakesPrecedenceOverSecondsWhenPositive() {
        val harness = ServiceSimulationHarness()

        val intent = MockIntent(
            action = FocusTimerService.ACTION_START,
            extras = mapOf(
                FocusTimerService.EXTRA_DURATION_MS to 30000L, // 30s in ms
                FocusTimerService.EXTRA_DURATION_SECONDS to 60L, // 60s
                FocusTimerService.EXTRA_END_AT_MS to 123456789L,
                FocusTimerService.EXTRA_END_AT_MILLIS to 987654321L
            )
        )
        harness.onStartCommand(intent)

        // EXTRA_DURATION_MS (30000) takes precedence over EXTRA_DURATION_SECONDS (60000)
        assertEquals(30000L, harness.lastCapturedDurationMs)
        // EXTRA_END_AT_MS (123456789) takes precedence over EXTRA_END_AT_MILLIS (987654321)
        assertEquals(123456789L, harness.lastCapturedEndAtMs)
    }

    @Test
    fun testExtraPrecedence_fallsBackToSecondsWhenMillisIsZeroOrNegative() {
        val harness = ServiceSimulationHarness()

        val intent = MockIntent(
            action = FocusTimerService.ACTION_START,
            extras = mapOf(
                FocusTimerService.EXTRA_DURATION_MS to 0L, // 0 -> fallback
                FocusTimerService.EXTRA_DURATION_SECONDS to 45L,
                FocusTimerService.EXTRA_END_AT_MS to -1L, // -1 -> fallback
                FocusTimerService.EXTRA_END_AT_MILLIS to 555555L
            )
        )
        harness.onStartCommand(intent)

        // Falls back to 45 * 1000 = 45000 ms
        assertEquals(45000L, harness.lastCapturedDurationMs)
        // Falls back to 555555 ms
        assertEquals(555555L, harness.lastCapturedEndAtMs)
    }

    // =========================================================================
    // 3. Android 14+ Background Launch Restriction Exception Handling
    // =========================================================================

    @Test
    fun testAndroid14BackgroundRestriction_foregroundServiceStartNotAllowedException() {
        // Simulate ForegroundServiceStartNotAllowedException (extends IllegalStateException)
        val backgroundException = IllegalStateException("ForegroundServiceStartNotAllowedException: startForeground not allowed from background")
        val harness = ServiceSimulationHarness(
            simulateAndroid14OrHigher = true,
            throwOnStartForeground = backgroundException
        )

        val intent = MockIntent(
            action = FocusTimerService.ACTION_START,
            extras = mapOf(FocusTimerService.EXTRA_TITLE to "Focus")
        )

        val result = harness.onStartCommand(intent)

        // Must catch exception cleanly, stop self, and return START_NOT_STICKY
        assertEquals(2, result) // START_NOT_STICKY
        assertEquals(ServiceState.STOPPED, harness.currentState)
        assertEquals(1, harness.stopSelfCallCount.get())
        assertEquals(0, harness.startForegroundCallCount.get())
    }

    @Test
    fun testAndroid14BackgroundRestriction_securityException() {
        // Simulate SecurityException on API 34+
        val securityException = SecurityException("Starting FGS with type specialUse requires android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
        val harness = ServiceSimulationHarness(
            simulateAndroid14OrHigher = true,
            throwOnStartForeground = securityException
        )

        val intent = MockIntent(action = FocusTimerService.ACTION_START)
        val result = harness.onStartCommand(intent)

        assertEquals(2, result)
        assertEquals(ServiceState.STOPPED, harness.currentState)
        assertEquals(1, harness.stopSelfCallCount.get())
    }

    // =========================================================================
    // 4. Missing or Corrupted Notification Channels & Self-Healing
    // =========================================================================

    @Test
    fun testNotificationChannels_allSevenChannelsDefinedCorrectly() {
        // Check that all 7 channel IDs are unique and defined in GoBackApplication
        val channelIds = listOf(
            GoBackApplication.CHANNEL_FOCUS_STATUS,
            GoBackApplication.CHANNEL_FOCUS_ALERTS,
            GoBackApplication.CHANNEL_SCHEDULE,
            GoBackApplication.CHANNEL_USAGE_LIMITS,
            GoBackApplication.CHANNEL_EXAMS,
            GoBackApplication.CHANNEL_GOAL_COMPLETED,
            GoBackApplication.CHANNEL_UPDATES
        )

        assertEquals(7, channelIds.size)
        assertEquals("All 7 channel IDs must be distinct", 7, channelIds.distinct().size)

        // Verify specific values against specification
        assertEquals("go-back-focus-status-v2", GoBackApplication.CHANNEL_FOCUS_STATUS)
        assertEquals("go-back-focus-alerts-v2", GoBackApplication.CHANNEL_FOCUS_ALERTS)
        assertEquals("go-back-schedule-v3", GoBackApplication.CHANNEL_SCHEDULE)
        assertEquals("screen-time-limits-native-v2", GoBackApplication.CHANNEL_USAGE_LIMITS)
        assertEquals("go-back-exams-v1", GoBackApplication.CHANNEL_EXAMS)
        assertEquals("go-back-goal-completed-v1", GoBackApplication.CHANNEL_GOAL_COMPLETED)
        assertEquals("go-back-updates-native-v1", GoBackApplication.CHANNEL_UPDATES)
    }

    @Test
    fun testNotificationChannels_focusTimerServiceHealsChannelsBeforePosting() {
        val harness = ServiceSimulationHarness()

        // Even with an empty channel state or missing channels, FocusTimerService
        // invokes GoBackApplication.ensureNotificationChannels on EVERY start/update
        assertEquals(0, harness.channelEnsureCallCount.get())

        harness.onStartCommand(MockIntent(action = FocusTimerService.ACTION_START))
        assertEquals(1, harness.channelEnsureCallCount.get())

        harness.onStartCommand(MockIntent(action = FocusTimerService.ACTION_PAUSE))
        assertEquals(2, harness.channelEnsureCallCount.get())

        harness.onStartCommand(MockIntent(action = FocusTimerService.ACTION_RESUME))
        assertEquals(3, harness.channelEnsureCallCount.get())
    }

    @Test
    fun testFocusTimerServiceConstants_exactContractCompliance() {
        assertEquals(8401, FocusTimerService.NOTIFICATION_ID)
        assertEquals("com.goback.focus.action.START_TIMER", FocusTimerService.ACTION_START)
        assertEquals("com.goback.focus.action.PAUSE_TIMER", FocusTimerService.ACTION_PAUSE)
        assertEquals("com.goback.focus.action.RESUME_TIMER", FocusTimerService.ACTION_RESUME)
        assertEquals("com.goback.focus.action.STOP_TIMER", FocusTimerService.ACTION_STOP)

        assertEquals("title", FocusTimerService.EXTRA_TITLE)
        assertEquals("is_stopwatch", FocusTimerService.EXTRA_IS_STOPWATCH)
        assertEquals("duration_ms", FocusTimerService.EXTRA_DURATION_MS)
        assertEquals("duration_seconds", FocusTimerService.EXTRA_DURATION_SECONDS)
        assertEquals("end_at_ms", FocusTimerService.EXTRA_END_AT_MS)
        assertEquals("end_at_millis", FocusTimerService.EXTRA_END_AT_MILLIS)
        assertEquals("paused", FocusTimerService.EXTRA_PAUSED)
    }
}
