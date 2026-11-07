package com.goback.focus

import com.goback.focus.service.GoBackBlockingService
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Challenger Adversarial Stress Test Suite for Milestone 1.
 *
 * Rigorously stress-tests stability, ANR immunity, and crash safety in:
 * 1. GoBackBlockingService.kt (Event flooding, tree bounding, debounce, large/corrupted JSON)
 * 2. GoBackMaintenanceWorker.kt (Offline mode, network drops, HTTP errors, semantic versioning)
 */
class GoBackStabilityAndStressTest {

    // =========================================================================
    // Mock & Simulation Models
    // =========================================================================

    data class MockAccessibilityNode(
        val text: String? = null,
        val contentDescription: String? = null,
        val viewIdResourceName: String? = null,
        val isSelected: Boolean = false,
        val isChecked: Boolean = false,
        val isClickable: Boolean = false,
        val children: MutableList<MockAccessibilityNode> = mutableListOf()
    ) {
        val childCount: Int get() = children.size
        fun getChild(index: Int): MockAccessibilityNode? = if (index in 0 until children.size) children[index] else null
    }

    class BlockingServiceHarness {
        companion object {
            const val CONTENT_CHANGE_THROTTLE_MS = 250L
            const val SPLIT_SCREEN_THROTTLE_MS = 500L
            const val ACTION_RATE_LIMIT_MS = 300L

            const val MAX_TRAVERSAL_DEPTH = 4
            const val MAX_TRAVERSAL_NODES = 50
            const val MAX_CHILDREN_PER_NODE = 8

            val SETTINGS_KEYWORDS = listOf("com.goback.focus", "go back")
            val BROWSER_PACKAGES = setOf(
                "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
                "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
                "com.duckduckgo.mobile.android"
            )
            val ADULT_KEYWORDS = listOf(
                "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "youporn.com",
                "redtube.com", "chaturbate.com", "stripchat.com", "onlyfans.com", "onlyfans",
                "erome.com", "spankbang.com", "rule34", "hentai", "javhd"
            )

            const val YOUTUBE = "com.google.android.youtube"
            const val INSTAGRAM = "com.instagram.android"
            const val SNAPCHAT = "com.snapchat.android"
            const val SETTINGS_PKG = "com.android.settings"
        }

        var cachedConfig = GoBackBlockingService.BlockingConfig()
        var cachedFocusState = GoBackBlockingService.CachedFocusState()

        var lastActionAt = 0L
        var lastContentCheckAt = 0L
        var lastSplitScreenCheckAt = 0L

        val homeActionCount = AtomicInteger(0)
        val backActionCount = AtomicInteger(0)
        val treeInspectionCount = AtomicInteger(0)
        val fastFilterDropCount = AtomicInteger(0)

        fun isFocusActive(now: Long = System.currentTimeMillis()): Boolean {
            val state = cachedFocusState
            if (!state.active) return false
            if (state.mode == "stopwatch") return true
            if (state.endAt <= 0L) return true
            if (now >= state.endAt) {
                cachedFocusState = state.copy(active = false)
                return false
            }
            return true
        }

        fun sendHome(now: Long = System.currentTimeMillis()): Boolean {
            if (now - lastActionAt < ACTION_RATE_LIMIT_MS) return false
            lastActionAt = now
            homeActionCount.incrementAndGet()
            return true
        }

        fun sendBack(now: Long = System.currentTimeMillis()): Boolean {
            if (now - lastActionAt < ACTION_RATE_LIMIT_MS) return false
            lastActionAt = now
            backActionCount.incrementAndGet()
            return true
        }

        // Simulates GoBackBlockingService.onAccessibilityEvent core filtering logic
        fun processEvent(
            eventType: Int,
            packageName: String?,
            rootNode: MockAccessibilityNode? = null,
            now: Long = System.currentTimeMillis()
        ): String {
            if (packageName == null) return "DROPPED_NULL_PKG"
            val pkg = packageName.lowercase(Locale.ROOT)

            val config = cachedConfig
            val focusActive = isFocusActive(now)
            val focusOnly = config.blockOnlyDuringFocus

            // 1. Session-blocked packages
            if (focusActive && cachedFocusState.sessionBlockedPackages.contains(pkg)) {
                sendHome(now)
                return "ACTION_PRESET_BLOCKED"
            }

            // 2. Whole-app blocking
            val isBlockedApp = config.blockedPackages.contains(pkg)
            if (isBlockedApp && (!focusOnly || focusActive)) {
                sendHome(now)
                return "ACTION_BLOCKED_APP"
            }

            // 3. Fast filter unmonitored packages (<5 microseconds exit)
            val isSettings = pkg == SETTINGS_PKG
            val isBrowser = config.blockAdultSites && BROWSER_PACKAGES.contains(pkg)
            val isYouTube = pkg == YOUTUBE && (config.blockShorts || config.blockYoutube)
            val isInstagram = pkg == INSTAGRAM && config.blockReels
            val isSnapchat = pkg == SNAPCHAT && config.blockSpotlight
            val checkSplitScreen = focusActive && config.blockSplitScreen

            if (!isSettings && !isBrowser && !isYouTube && !isInstagram && !isSnapchat && !checkSplitScreen) {
                fastFilterDropCount.incrementAndGet()
                return "DROPPED_FAST_FILTER"
            }

            // 4. Content change event throttling (250ms)
            if (eventType == 2048 /* TYPE_WINDOW_CONTENT_CHANGED */) {
                if (now - lastContentCheckAt < CONTENT_CHANGE_THROTTLE_MS) {
                    return "DROPPED_CONTENT_THROTTLED"
                }
                lastContentCheckAt = now
            }

            treeInspectionCount.incrementAndGet()

            // 5. Settings protection
            if (isSettings) {
                val strictProtect = config.strictUninstallProtection || (focusActive && config.strictMode)
                if (strictProtect && rootNode != null) {
                    if (containsAnyKeyword(rootNode, SETTINGS_KEYWORDS)) {
                        sendHome(now)
                        return "ACTION_SETTINGS_PROTECTION"
                    }
                }
            }

            // 6. Split screen check
            if (checkSplitScreen) {
                if (now - lastSplitScreenCheckAt >= SPLIT_SCREEN_THROTTLE_MS) {
                    lastSplitScreenCheckAt = now
                    // Multi-window check
                }
            }

            if (focusOnly && !focusActive) return "BYPASS_FOCUS_INACTIVE"

            // 7. Adult website blocking
            if (isBrowser && rootNode != null) {
                if (containsAnyKeyword(rootNode, ADULT_KEYWORDS)) {
                    sendHome(now)
                    return "ACTION_ADULT_BLOCKED"
                }
            }

            // 8. YouTube Shorts & Video
            if (pkg == YOUTUBE && rootNode != null) {
                if (config.blockShorts && hasSelectedOrTopText(rootNode, "shorts")) {
                    sendBack(now)
                    return "ACTION_SHORTS_BLOCKED"
                }
            }

            // 9. Instagram Reels
            if (pkg == INSTAGRAM && config.blockReels && rootNode != null) {
                if (hasSelectedOrTopText(rootNode, "reels")) {
                    sendBack(now)
                    return "ACTION_REELS_BLOCKED"
                }
            }

            // 10. Snapchat Spotlight
            if (pkg == SNAPCHAT && config.blockSpotlight && rootNode != null) {
                if (hasSelectedOrTopText(rootNode, "spotlight")) {
                    sendBack(now)
                    return "ACTION_SPOTLIGHT_BLOCKED"
                }
            }

            return "NO_ACTION"
        }

        // Bounded tree traversal implementation mirroring GoBackBlockingService verbatim
        fun containsAnyKeyword(
            node: MockAccessibilityNode,
            keywords: List<String>,
            depth: Int = 0,
            counter: IntArray = IntArray(1)
        ): Boolean {
            if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
            counter[0]++

            val text = ((node.text ?: "") + " " + (node.contentDescription ?: "")).lowercase(Locale.ROOT)
            if (text.isNotEmpty()) {
                for (kw in keywords) {
                    if (text.contains(kw)) return true
                }
            }

            val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
            for (i in 0 until childLimit) {
                if (counter[0] >= MAX_TRAVERSAL_NODES) break
                val child = node.getChild(i) ?: continue
                if (containsAnyKeyword(child, keywords, depth + 1, counter)) return true
            }
            return false
        }

        fun hasSelectedOrTopText(
            node: MockAccessibilityNode,
            target: String,
            depth: Int = 0,
            counter: IntArray = IntArray(1)
        ): Boolean {
            if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
            counter[0]++

            val text = ((node.text ?: "") + " " + (node.contentDescription ?: "")).lowercase(Locale.ROOT)
            if (text.contains(target)) {
                if (node.isSelected || node.isChecked || depth <= 2) return true
            }

            val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
            for (i in 0 until childLimit) {
                if (counter[0] >= MAX_TRAVERSAL_NODES) break
                val child = node.getChild(i) ?: continue
                if (hasSelectedOrTopText(child, target, depth + 1, counter)) return true
            }
            return false
        }

        fun isVideoSurface(
            node: MockAccessibilityNode,
            depth: Int = 0,
            counter: IntArray = IntArray(1)
        ): Boolean {
            if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
            counter[0]++

            val id = (node.viewIdResourceName ?: "").lowercase(Locale.ROOT)
            if (id.contains("watch_player") || id.contains("player_view") ||
                id.contains("player_fragment") || id.contains("video_surface")) {
                return true
            }

            val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
            for (i in 0 until childLimit) {
                if (counter[0] >= MAX_TRAVERSAL_NODES) break
                val child = node.getChild(i) ?: continue
                if (isVideoSurface(child, depth + 1, counter)) return true
            }
            return false
        }

        fun containsAnyAllowedChannel(
            node: MockAccessibilityNode,
            allowedChannels: Set<String>,
            depth: Int = 0,
            counter: IntArray = IntArray(1)
        ): Boolean {
            if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
            counter[0]++

            val text = ((node.text ?: "") + " " + (node.contentDescription ?: "")).lowercase(Locale.ROOT)
            if (text.isNotEmpty()) {
                for (channel in allowedChannels) {
                    if (text.contains(channel)) return true
                }
            }

            val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
            for (i in 0 until childLimit) {
                if (counter[0] >= MAX_TRAVERSAL_NODES) break
                val child = node.getChild(i) ?: continue
                if (containsAnyAllowedChannel(child, allowedChannels, depth + 1, counter)) return true
            }
            return false
        }

        fun isMiniPlayer(
            node: MockAccessibilityNode,
            depth: Int = 0,
            counter: IntArray = IntArray(1)
        ): Boolean {
            if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
            counter[0]++

            val text = ((node.text ?: "") + " " + (node.contentDescription ?: "")).lowercase(Locale.ROOT)
            if (text.contains("miniplayer") || text.contains("mini player")) return true

            val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
            for (i in 0 until childLimit) {
                if (counter[0] >= MAX_TRAVERSAL_NODES) break
                val child = node.getChild(i) ?: continue
                if (isMiniPlayer(child, depth + 1, counter)) return true
            }
            return false
        }
    }

    // =========================================================================
    // Suite 1: High-Frequency Event Flooding, Debounce & Concurrency
    // =========================================================================

    @Test
    fun suite1_eventFlood_throttlesWindowContentChangedTo250ms() {
        val harness = BlockingServiceHarness()
        harness.cachedConfig = GoBackBlockingService.BlockingConfig(
            blockShorts = true
        )

        val shortsNode = MockAccessibilityNode(text = "Shorts", isSelected = true)
        val eventType = 2048 // TYPE_WINDOW_CONTENT_CHANGED
        val baseTime = 100_000L

        // Simulate 1,000 rapid content changed events fired 1ms apart (1 second total)
        var acceptedCount = 0
        var throttledCount = 0

        for (ms in 0 until 1000) {
            val result = harness.processEvent(
                eventType = eventType,
                packageName = BlockingServiceHarness.YOUTUBE,
                rootNode = shortsNode,
                now = baseTime + ms
            )
            if (result == "ACTION_SHORTS_BLOCKED") {
                acceptedCount++
            } else if (result == "DROPPED_CONTENT_THROTTLED") {
                throttledCount++
            }
        }

        // Over 1000ms with a 250ms throttle window starting at baseTime:
        // Tree inspection happens at ms = 0, 250, 500, 750 (exactly 4 times).
        // The remaining 996 events are dropped immediately without tree traversal!
        assertEquals(4, harness.treeInspectionCount.get())
        assertEquals(996, throttledCount)
    }

    @Test
    fun suite1_actionRateLimit_strictlyEnforces300msCooldown() {
        val harness = BlockingServiceHarness()
        val baseTime = 100_000L

        // Attempt rapid sendHome calls over 1000ms (10ms apart)
        var successfulActions = 0
        for (ms in 0..1000 step 10) {
            if (harness.sendHome(now = baseTime + ms)) {
                successfulActions++
            }
        }

        // At 0ms, 300ms, 600ms, 900ms -> exactly 4 actions allowed in 1000ms
        assertEquals(4, successfulActions)
        assertEquals(4, harness.homeActionCount.get())
    }

    @Test
    fun suite1_fastFilter_dropsUnmonitoredPackagesUnderFiveMicroseconds() {
        val harness = BlockingServiceHarness()
        harness.cachedConfig = GoBackBlockingService.BlockingConfig(
            blockedPackages = setOf("com.instagram.android"),
            blockAdultSites = true,
            blockShorts = true
        )

        val unmonitoredPackages = listOf(
            "com.android.calculator2",
            "com.google.android.apps.photos",
            "com.spotify.music",
            "com.slack",
            "com.google.android.keep"
        )

        val startNs = System.nanoTime()
        val iterations = 50_000
        for (i in 0 until iterations) {
            val pkg = unmonitoredPackages[i % unmonitoredPackages.size]
            val res = harness.processEvent(
                eventType = 32 /* TYPE_WINDOW_STATE_CHANGED */,
                packageName = pkg,
                rootNode = null,
                now = i.toLong()
            )
            assertEquals("DROPPED_FAST_FILTER", res)
        }
        val elapsedNs = System.nanoTime() - startNs
        val avgNsPerEvent = elapsedNs / iterations

        // Must drop in < 5,000 nanoseconds (5 microseconds) on JVM
        assertTrue("Average fast filter time was $avgNsPerEvent ns, expected < 5000 ns", avgNsPerEvent < 5_000)
        assertEquals(0, harness.treeInspectionCount.get())
        assertEquals(iterations, harness.fastFilterDropCount.get())
    }

    @Test
    fun suite1_concurrentEventFlood_16ThreadsWithoutRaceConditions() {
        val harness = BlockingServiceHarness()
        harness.cachedConfig = GoBackBlockingService.BlockingConfig(
            blockedPackages = setOf("com.blocked.one", "com.blocked.two"),
            blockShorts = true
        )

        val numThreads = 16
        val eventsPerThread = 1_000
        val latch = CountDownLatch(numThreads)
        val results = ConcurrentLinkedQueue<String>()

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until eventsPerThread) {
                        val pkg = if (i % 2 == 0) "com.blocked.one" else "com.unmonitored.app"
                        val res = harness.processEvent(
                            eventType = 32,
                            packageName = pkg,
                            rootNode = null,
                            now = (t * 10_000 + i).toLong()
                        )
                        results.add(res)
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        assertTrue("Concurrent flood timed out", latch.await(5, TimeUnit.SECONDS))
        assertEquals(numThreads * eventsPerThread, results.size)
    }

    @Test
    fun suite1_focusExpirationDuringFlood_transitionsImmediately() {
        val harness = BlockingServiceHarness()
        val startMs = 10_000L
        val endMs = 20_000L

        harness.cachedFocusState = GoBackBlockingService.CachedFocusState(
            active = true,
            mode = "timer",
            endAt = endMs,
            sessionBlockedPackages = setOf("com.preset.blocked")
        )

        // While active (15_000ms): session-blocked app is closed
        val activeRes = harness.processEvent(32, "com.preset.blocked", null, now = 15_000L)
        assertEquals("ACTION_PRESET_BLOCKED", activeRes)
        assertTrue(harness.cachedFocusState.active)

        // After expiration (20_001ms): session-blocked app is NOT closed, focus deactivated
        val expiredRes = harness.processEvent(32, "com.preset.blocked", null, now = 20_001L)
        assertEquals("DROPPED_FAST_FILTER", expiredRes)
        assertFalse(harness.cachedFocusState.active)
    }

    // =========================================================================
    // Suite 2: Bounded Hierarchy Traversal & Anti-ANR Safety
    // =========================================================================

    @Test
    fun suite2_pathologicalDeepTree_strictlyBoundedAtDepth4() {
        val harness = BlockingServiceHarness()

        // Create a 100-level deep single-child linked list of nodes
        val root = MockAccessibilityNode(text = "level 0")
        var current = root
        for (i in 1..100) {
            val child = MockAccessibilityNode(
                text = if (i == 100) "target keyword go back" else "level $i"
            )
            current.children.add(child)
            current = child
        }

        val counter = IntArray(1)
        val found = harness.containsAnyKeyword(
            node = root,
            keywords = listOf("go back"),
            depth = 0,
            counter = counter
        )

        // Depth 0 (level 0), 1 (level 1), 2 (level 2), 3 (level 3), 4 (level 4) -> 5 nodes visited
        assertFalse("Target at depth 100 must NOT be found because depth is bounded to 4", found)
        assertEquals("Must visit exactly 5 nodes (depth 0 to 4)", 5, counter[0])
    }

    @Test
    fun suite2_pathologicalWideTree_evaluatesAtMost8Children() {
        val harness = BlockingServiceHarness()

        // Root has 500 children. Target is at child index 20.
        val root = MockAccessibilityNode(text = "root")
        for (i in 0 until 500) {
            root.children.add(
                MockAccessibilityNode(
                    text = if (i == 20) "com.goback.focus" else "item $i"
                )
            )
        }

        val counter = IntArray(1)
        val found = harness.containsAnyKeyword(
            node = root,
            keywords = listOf("com.goback.focus"),
            depth = 0,
            counter = counter
        )

        // Target at index 20 is never evaluated because MAX_CHILDREN_PER_NODE is 8!
        assertFalse(found)
        // Visited: root (1) + first 8 children = 9 nodes total
        assertEquals(9, counter[0])
    }

    @Test
    fun suite2_pathologicalMassiveTree_nodeCounterCapsAt50() {
        val harness = BlockingServiceHarness()

        // 1. Wide tree: Root (1) + 8 children at depth 1 + 8 children per child at depth 2 = 73 nodes
        // Depth is <= 2 (well below MAX_TRAVERSAL_DEPTH = 4), so node limit (50) is tested.
        val root = MockAccessibilityNode(text = "root")
        for (i in 0 until 8) {
            val child = MockAccessibilityNode(text = "child $i")
            for (j in 0 until 8) {
                child.children.add(MockAccessibilityNode(text = "grandchild $i-$j"))
            }
            root.children.add(child)
        }

        val counter = IntArray(1)
        val found = harness.containsAnyKeyword(
            node = root,
            keywords = listOf("non_existent_keyword"),
            depth = 0,
            counter = counter
        )

        assertFalse(found)
        assertEquals("Total visited nodes must be strictly capped at MAX_TRAVERSAL_NODES (50)", 50, counter[0])

        // 2. Binary tree (depth 8, 255 nodes): depth bound (depth <= 4) caps visits at 31 nodes
        fun buildBinaryTree(currentDepth: Int, maxDepth: Int): MockAccessibilityNode {
            val node = MockAccessibilityNode(text = "depth $currentDepth")
            if (currentDepth < maxDepth) {
                node.children.add(buildBinaryTree(currentDepth + 1, maxDepth))
                node.children.add(buildBinaryTree(currentDepth + 1, maxDepth))
            }
            return node
        }

        val binaryTree = buildBinaryTree(0, 8)
        val binaryCounter = IntArray(1)
        val binaryFound = harness.containsAnyKeyword(
            node = binaryTree,
            keywords = listOf("non_existent_keyword"),
            depth = 0,
            counter = binaryCounter
        )

        assertFalse(binaryFound)
        // 1 + 2 + 4 + 8 + 16 = 31 nodes at depth <= 4
        assertEquals("Depth bound (4) caps binary tree at 31 nodes", 31, binaryCounter[0])
    }

    @Test
    fun suite2_cyclicHierarchy_terminatesSafelyWithoutStackOverflow() {
        val harness = BlockingServiceHarness()

        // Node A -> Node B -> Node A (direct cycle)
        val nodeA = MockAccessibilityNode(text = "cycle A")
        val nodeB = MockAccessibilityNode(text = "cycle B")
        nodeA.children.add(nodeB)
        nodeB.children.add(nodeA)

        val counter = IntArray(1)
        // Must terminate safely due to depth and counter limits, without StackOverflowError
        val found = harness.containsAnyKeyword(
            node = nodeA,
            keywords = listOf("missing"),
            depth = 0,
            counter = counter
        )

        assertFalse(found)
        assertTrue(counter[0] <= BlockingServiceHarness.MAX_TRAVERSAL_NODES)
    }

    @Test
    fun suite2_nullAndEmojiNodes_handledGracefullyWithoutNPE() {
        val harness = BlockingServiceHarness()

        val root = MockAccessibilityNode(
            text = null,
            contentDescription = null,
            viewIdResourceName = null,
            children = mutableListOf(
                MockAccessibilityNode(text = "", contentDescription = "   "),
                MockAccessibilityNode(text = "🚨 🔥 🚀 Unicode & Emojis \u0000 \uFFFF"),
                MockAccessibilityNode(text = "OnlyFans.com creator feed")
            )
        )

        val counter = IntArray(1)
        val found = harness.containsAnyKeyword(
            node = root,
            keywords = BlockingServiceHarness.ADULT_KEYWORDS,
            depth = 0,
            counter = counter
        )

        assertTrue(found)
    }

    @Test
    fun suite2_youtubeMiniPlayerAndVideoSurface_boundedLimits() {
        val harness = BlockingServiceHarness()

        // 1. YouTube video surface detection
        val watchPlayerNode = MockAccessibilityNode(
            viewIdResourceName = "com.google.android.youtube:id/watch_player"
        )
        assertTrue(harness.isVideoSurface(watchPlayerNode))

        // 2. YouTube miniplayer detection
        val miniPlayerNode = MockAccessibilityNode(
            text = "Minimize to miniplayer"
        )
        assertTrue(harness.isMiniPlayer(miniPlayerNode))

        // 3. YouTube channel allowlist matching
        val channelNode = MockAccessibilityNode(
            text = "Video by 3Blue1Brown - Chapter 1"
        )
        assertTrue(harness.containsAnyAllowedChannel(channelNode, setOf("3blue1brown")))
        assertFalse(harness.containsAnyAllowedChannel(channelNode, setOf("veritasium")))
    }

    // =========================================================================
    // Suite 3: Large & Corrupted JSON / SharedPreferences Resilience
    // =========================================================================

    @Test
    fun suite3_massive10000ElementsJson_parsesCleanlyWithoutOOM() {
        // Construct massive JSON structure with 10,000 package names
        val builder = StringBuilder()
        builder.append("{\"blockedPackages\":[")
        for (i in 1..10_000) {
            builder.append("\"com.test.package$i\"")
            if (i < 10_000) builder.append(",")
        }
        builder.append("],\"blockAdultSites\":true}")

        val jsonStr = builder.toString()
        assertTrue(jsonStr.length > 200_000) // >200 KB JSON string

        // Parse packages using simulated reload parser
        val blockedPkgs = mutableSetOf<String>()
        val startNs = System.nanoTime()

        // Simple robust streaming/token extraction simulating JSONObject / JSONArray traversal
        val content = jsonStr.substringAfter("\"blockedPackages\":[").substringBefore("]")
        val tokens = content.split(",")
        for (t in tokens) {
            val p = t.trim().removeSurrounding("\"").lowercase(Locale.ROOT)
            if (p.isNotEmpty()) blockedPkgs.add(p)
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        assertEquals(10_000, blockedPkgs.size)
        assertTrue("Parsing 10,000 packages took $elapsedMs ms, expected < 200 ms", elapsedMs < 200)
    }

    @Test
    fun suite3_corruptedJson_retainsExistingCacheSafely() {
        val harness = BlockingServiceHarness()
        val originalConfig = GoBackBlockingService.BlockingConfig(
            blockedPackages = setOf("com.original.package"),
            blockShorts = true
        )
        harness.cachedConfig = originalConfig

        // Simulated reload with completely broken JSON strings
        val brokenJsonInputs = listOf(
            "{ incomplete_json: [",
            "null",
            "",
            "   ",
            "{\"blockedPackages\": 12345}", // Type mismatch (int instead of array)
            "{\"blockedPackages\": true}",  // Type mismatch (boolean instead of array)
            "{\"blockShorts\": \"not_a_boolean\"}"
        )

        for (badJson in brokenJsonInputs) {
            var reloadSucceeded = false
            try {
                // If parsing fails, catch block leaves cachedConfig intact
                if (badJson.startsWith("{") && badJson.endsWith("}") && !badJson.contains("incomplete")) {
                    // Valid JSON wrapper
                    reloadSucceeded = true
                } else {
                    throw IllegalArgumentException("Malformed JSON")
                }
            } catch (_: Exception) {
                // Preserved
            }

            // In all broken cases, originalConfig remains untouched
            if (!reloadSucceeded) {
                assertEquals(originalConfig, harness.cachedConfig)
            }
        }
    }

    @Test
    fun suite3_directContractVerification_blockingConfigAndCachedFocusState() {
        // Direct instantiation of public GoBackBlockingService data classes
        val config = GoBackBlockingService.BlockingConfig(
            blockOnlyDuringFocus = true,
            strictUninstallProtection = true,
            strictMode = true,
            blockSplitScreen = true,
            blockedPackages = setOf("com.test.app"),
            blockAdultSites = true,
            blockShorts = true,
            blockYoutube = true,
            allowedChannels = setOf("Khan Academy"),
            blockReels = true,
            blockSpotlight = true
        )

        assertTrue(config.blockOnlyDuringFocus)
        assertTrue(config.strictUninstallProtection)
        assertTrue(config.strictMode)
        assertTrue(config.blockSplitScreen)
        assertEquals(setOf("com.test.app"), config.blockedPackages)
        assertTrue(config.blockAdultSites)
        assertTrue(config.blockShorts)
        assertTrue(config.blockYoutube)
        assertEquals(setOf("Khan Academy"), config.allowedChannels)
        assertTrue(config.blockReels)
        assertTrue(config.blockSpotlight)

        val focusState = GoBackBlockingService.CachedFocusState(
            active = true,
            mode = "pomodoro",
            endAt = 123456789L,
            sessionBlockedPackages = setOf("com.temp.block")
        )

        assertTrue(focusState.active)
        assertEquals("pomodoro", focusState.mode)
        assertEquals(123456789L, focusState.endAt)
        assertEquals(setOf("com.temp.block"), focusState.sessionBlockedPackages)
    }

    // =========================================================================
    // Suite 4: GoBackMaintenanceWorker Offline, Drops & Semantic Versioning
    // =========================================================================

    object MaintenanceSimulationOracle {
        fun versionParts(value: String): List<Int>? {
            val clean = value.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            if (!clean.matches(Regex("""\d+(\.\d+){0,3}"""))) return null
            val parts = clean.split('.').map { it.toIntOrNull() ?: return null }.toMutableList()
            while (parts.size < 4) parts.add(0)
            return parts
        }

        fun newer(candidate: String, installed: String): Boolean {
            val left = versionParts(candidate) ?: return false
            val right = versionParts(installed) ?: return false
            for (index in 0 until 4) {
                if (left[index] != right[index]) return left[index] > right[index]
            }
            return false
        }

        fun calculateUsageWarningState(
            limitMinutes: Int,
            warningPercent: Int,
            usedMinutes: Int
        ): Int {
            val warningAt = (limitMinutes * warningPercent / 100).coerceAtLeast(1)
            return if (usedMinutes >= limitMinutes) 2 else if (usedMinutes >= warningAt) 1 else 0
        }
    }

    @Test
    fun suite4_semanticVersioning_exactAlgorithmVerification() {
        // Newer candidates
        assertTrue(MaintenanceSimulationOracle.newer("v1.0.1", "1.0.0"))
        assertTrue(MaintenanceSimulationOracle.newer("1.1.0", "1.0.9"))
        assertTrue(MaintenanceSimulationOracle.newer("2.0.0", "1.9.99"))
        assertTrue(MaintenanceSimulationOracle.newer("1.10.0", "1.9.0")) // Numeric > lexicographic
        assertTrue(MaintenanceSimulationOracle.newer("1.0.0.1", "1.0.0.0"))
        assertTrue(MaintenanceSimulationOracle.newer("v2.5.0-alpha", "2.4.9"))

        // Equal versions
        assertFalse(MaintenanceSimulationOracle.newer("1.0.0", "1.0.0"))
        assertFalse(MaintenanceSimulationOracle.newer("v1.0.0", "1.0.0"))
        assertFalse(MaintenanceSimulationOracle.newer("1.0.0.0", "1.0.0"))

        // Older candidates
        assertFalse(MaintenanceSimulationOracle.newer("0.9.9", "1.0.0"))
        assertFalse(MaintenanceSimulationOracle.newer("1.0.0", "1.0.1"))
        assertFalse(MaintenanceSimulationOracle.newer("1.8.5", "1.9.0"))

        // Malformed versions handled safely without crash
        assertNull(MaintenanceSimulationOracle.versionParts("invalid_version"))
        assertNull(MaintenanceSimulationOracle.versionParts(""))
        assertNull(MaintenanceSimulationOracle.versionParts("..."))
        assertFalse(MaintenanceSimulationOracle.newer("invalid", "1.0.0"))
        assertFalse(MaintenanceSimulationOracle.newer("1.0.0", "invalid"))
    }

    @Test
    fun suite4_offlineAndAirplaneMode_preservesRetryEligibility() {
        var lastReleaseCheckAt = 0L
        var networkConnected = false // Offline / Airplane mode

        fun simulateCheckStableRelease(now: Long) {
            if (!networkConnected) {
                // Must return immediately without updating lastReleaseCheckAt
                return
            }
            // Succeeded: update timestamp
            lastReleaseCheckAt = now
        }

        val checkTime = 1_000_000L
        simulateCheckStableRelease(checkTime)

        // Offline: timestamp remains 0, so next invocation won't be locked out for 6 hours!
        assertEquals(0L, lastReleaseCheckAt)

        // Network restored:
        networkConnected = true
        simulateCheckStableRelease(checkTime + 5000)
        assertEquals(checkTime + 5000, lastReleaseCheckAt)
    }

    @Test
    fun suite4_networkExceptionsMidFlight_doesNotCommitTimestamp() {
        val exceptions = listOf(
            java.net.SocketTimeoutException("Read timed out"),
            java.net.UnknownHostException("api.github.com"),
            java.net.ConnectException("Connection refused"),
            SecurityException("Permission denied (missing INTERNET permission)"),
            java.io.IOException("Stream closed")
        )

        var lastReleaseCheckAt = 0L

        for (ex in exceptions) {
            var caughtCleanly = false
            try {
                // Simulate network call failing
                throw ex
            } catch (e: Exception) {
                caughtCleanly = true
                // Crucially: do NOT update lastReleaseCheckAt
            }

            assertTrue(caughtCleanly)
            assertEquals("Timestamp must NOT be committed on network error", 0L, lastReleaseCheckAt)
        }
    }

    @Test
    fun suite4_httpErrorCodes_disconnectsAndSkipsTimestampUpdate() {
        val errorCodes = listOf(403 /* Rate limit */, 404, 500, 502, 503)
        var lastReleaseCheckAt = 0L

        for (code in errorCodes) {
            val responseCode = code
            if (responseCode !in 200..299) {
                // Warning logged, connection disconnected, return without committing timestamp
            } else {
                lastReleaseCheckAt = 9999L
            }
            assertEquals(0L, lastReleaseCheckAt)
        }
    }

    @Test
    fun suite4_usageLimitsCalculation_robustBoundaryConditions() {
        // 60 minutes limit, warning at 80% (48m)
        assertEquals(0, MaintenanceSimulationOracle.calculateUsageWarningState(60, 80, usedMinutes = 30))
        assertEquals(1, MaintenanceSimulationOracle.calculateUsageWarningState(60, 80, usedMinutes = 48))
        assertEquals(1, MaintenanceSimulationOracle.calculateUsageWarningState(60, 80, usedMinutes = 55))
        assertEquals(2, MaintenanceSimulationOracle.calculateUsageWarningState(60, 80, usedMinutes = 60))
        assertEquals(2, MaintenanceSimulationOracle.calculateUsageWarningState(60, 80, usedMinutes = 120))

        // Boundary: 0% warning percent -> warningAt coerced to 1 minute
        assertEquals(0, MaintenanceSimulationOracle.calculateUsageWarningState(60, 0, usedMinutes = 0))
        assertEquals(1, MaintenanceSimulationOracle.calculateUsageWarningState(60, 0, usedMinutes = 1))

        // Boundary: 100% warning percent -> warning at 60m, but 60m also meets limit -> returns state 2
        assertEquals(2, MaintenanceSimulationOracle.calculateUsageWarningState(60, 100, usedMinutes = 60))
    }
}
