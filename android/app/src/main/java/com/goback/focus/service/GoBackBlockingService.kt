package com.goback.focus.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class GoBackBlockingService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastActionAt = 0L
    private var lastContentCheckAt = 0L
    private var lastSplitScreenCheckAt = 0L

    // In-memory configuration cache
    @Volatile
    private var cachedConfig = BlockingConfig()

    @Volatile
    private var cachedFocusState = CachedFocusState()

    // Strong reference to prevent garbage collection by SharedPreferencesImpl
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        reloadConfiguration(prefs)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Optimized accessibility service configuration:
        // 1. Remove TYPE_VIEW_SCROLLED to avoid high-frequency gesture event floods
        // 2. Remove FLAG_INCLUDE_NOT_IMPORTANT_VIEWS to eliminate empty/decorative layout nodes
        // 3. Set notificationTimeout to 120ms to batch rapid window updates
        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 120
        }
        serviceInfo = info

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        reloadConfiguration(prefs)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        val rawPkg = event.packageName?.toString() ?: return
        val pkg = rawPkg.lowercase(Locale.ROOT)

        val config = cachedConfig
        val focusActive = isFocusActive()
        val focusOnly = config.blockOnlyDuringFocus

        // 0. Usage limit exceeded apps (immediate termination at 100% daily limit)
        if (config.usageLimitBlockedPackages.contains(pkg)) {
            recordAction("daily usage limit exceeded: $rawPkg")
            sendHome()
            return
        }

        // 1. Session-blocked packages (preset rules) - active during focus
        if (focusActive && cachedFocusState.sessionBlockedPackages.contains(pkg)) {
            recordAction("preset app closed: $rawPkg")
            sendHome()
            return
        }

        // 2. Whole-app blocking (active during focus or when scheduled focus block is active)
        val blockingActive = focusActive || config.scheduledBlockingActive
        val isBlockedApp = config.blockedPackages.contains(pkg)
        if (isBlockedApp && (!focusOnly || blockingActive)) {
            recordAction("blocked app closed: $rawPkg")
            sendHome()
            return
        }

        // 3. Fast filter for unmonitored packages:
        // If the package is not Settings, not a supported browser, and not a targeted social app,
        // and split screen protection does not need to check, exit immediately (<5 microseconds).
        val isSettings = pkg == SETTINGS_PKG
        val isBrowser = config.blockAdultSites && BROWSER_PACKAGES.contains(pkg)
        val isYouTube = pkg == YOUTUBE && (config.blockShorts || config.blockYoutube)
        val isInstagram = pkg == INSTAGRAM && config.blockReels
        val isSnapchat = pkg == SNAPCHAT && config.blockSpotlight
        val checkSplitScreen = focusActive && config.blockSplitScreen

        if (!isSettings && !isBrowser && !isYouTube && !isInstagram && !isSnapchat && !checkSplitScreen) {
            return
        }

        val now = System.currentTimeMillis()

        // 4. Content change event debouncing / throttling (limit tree traversals to max once every 250ms per active app)
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (now - lastContentCheckAt < CONTENT_CHANGE_THROTTLE_MS) {
                return
            }
            lastContentCheckAt = now
        }

        // 5. Settings / Uninstall protection
        if (isSettings) {
            val strictProtect = config.strictUninstallProtection || (focusActive && config.strictMode)
            if (strictProtect) {
                val root = rootInActiveWindow
                if (root != null && containsAnyKeyword(root, SETTINGS_KEYWORDS)) {
                    recordAction("Settings protection active: blocked access to Go Back controls")
                    sendHome()
                    return
                }
            }
        }

        // 6. Split-screen / Floating window protection (throttled to at most once per 500ms)
        if (checkSplitScreen && Build.VERSION.SDK_INT >= 24) {
            if (now - lastSplitScreenCheckAt >= SPLIT_SCREEN_THROTTLE_MS) {
                lastSplitScreenCheckAt = now
                val allWindows = windows
                if (allWindows != null) {
                    val appWindows = if (Build.VERSION.SDK_INT >= 21) {
                        allWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                    } else {
                        allWindows
                    }
                    if (appWindows.size > 1) {
                        val myPkg = packageName.lowercase(Locale.ROOT)
                        for (win in appWindows) {
                            val winRoot = win.root ?: continue
                            val winPkg = winRoot.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""
                            if (winPkg.isNotEmpty() && winPkg != myPkg) {
                                if (config.blockedPackages.contains(winPkg) ||
                                    winPkg == YOUTUBE || winPkg == INSTAGRAM || winPkg == SNAPCHAT) {
                                    recordAction("split-screen blocked app closed: $winPkg")
                                    sendHome()
                                    return
                                }
                            }
                        }
                    }
                }
            }
        }

        // If rules apply only during focus and focus is inactive, exit before deeper feature blocking
        if (focusOnly && !focusActive) return

        // 7. Adult website blocking in supported browsers
        if (isBrowser) {
            val root = rootInActiveWindow ?: return
            if (containsAnyKeyword(root, ADULT_KEYWORDS)) {
                recordAction("Adult website blocked")
                sendHome()
                return
            }
        }

        if (pkg != YOUTUBE && pkg != INSTAGRAM && pkg != SNAPCHAT) return
        val root = rootInActiveWindow ?: return

        // 8. YouTube Shorts & Video allowlist
        if (pkg == YOUTUBE) {
            if (config.blockShorts && hasSelectedOrTopText(root, "shorts")) {
                recordAction("YouTube Shorts closed")
                sendBack()
                return
            }
            if (config.blockYoutube && isVideoSurface(root)) {
                if (!matchesAllowedChannel(root, config.allowedChannels)) {
                    recordAction("disallowed YouTube video closed")
                    closeDisallowedVideo()
                    return
                }
            }
        }

        // 9. Instagram Reels
        if (pkg == INSTAGRAM && config.blockReels && hasSelectedOrTopText(root, "reels")) {
            recordAction("Instagram Reels closed")
            sendBack()
            return
        }

        // 10. Snapchat Spotlight
        if (pkg == SNAPCHAT && config.blockSpotlight && hasSelectedOrTopText(root, "spotlight")) {
            recordAction("Snapchat Spotlight closed")
            sendBack()
            return
        }
    }

    override fun onInterrupt() {}

    /**
     * Checks in-memory if a focus session is active and valid, without reading disk or parsing JSON.
     * If an active countdown timer has expired, updates in-memory state immediately and
     * schedules persistent disk cleanup asynchronously via Handler.
     */
    private fun isFocusActive(): Boolean {
        val state = cachedFocusState
        if (!state.active) return false
        if (state.mode == "stopwatch") return true
        if (state.endAt <= 0L) return true
        val now = System.currentTimeMillis()
        if (now >= state.endAt) {
            cachedFocusState = state.copy(active = false)
            handler.post {
                try {
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putBoolean("focus_active", false)
                        .remove("session_blocked_packages")
                        .apply()
                } catch (_: Exception) {}
            }
            return false
        }
        return true
    }

    /**
     * Reloads configuration from SharedPreferences into immutable in-memory caches.
     * Supports both the unified JSON "config" format and individual SharedPreferences keys.
     */
    private fun reloadConfiguration(prefs: SharedPreferences) {
        try {
            val configJsonStr = prefs.getString("config", null)
            val configObj = if (!configJsonStr.isNullOrEmpty()) {
                try { JSONObject(configJsonStr) } catch (_: Exception) { null }
            } else null

            val blockedPkgs = mutableSetOf<String>()
            configObj?.optJSONArray("blockedPackages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i).trim()
                    if (p.isNotEmpty()) blockedPkgs.add(p.lowercase(Locale.ROOT))
                }
            }
            prefs.getStringSet("blocked_packages", null)?.let { set ->
                for (p in set) {
                    val trimmed = p.trim()
                    if (trimmed.isNotEmpty()) blockedPkgs.add(trimmed.lowercase(Locale.ROOT))
                }
            }

            val allowedChs = mutableSetOf<String>()
            configObj?.optJSONArray("allowedChannels")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val ch = arr.optString(i).trim()
                    if (ch.isNotEmpty()) allowedChs.add(ch.lowercase(Locale.ROOT))
                }
            }
            prefs.getStringSet("youtube_allowed_channels", null)?.let { set ->
                for (ch in set) {
                    val trimmed = ch.trim()
                    if (trimmed.isNotEmpty()) allowedChs.add(trimmed.lowercase(Locale.ROOT))
                }
            }

            val usageLimitBlockedPkgs = mutableSetOf<String>()
            prefs.getStringSet("usage_limit_blocked_packages", null)?.let { set ->
                for (p in set) {
                    val trimmed = p.trim()
                    if (trimmed.isNotEmpty()) usageLimitBlockedPkgs.add(trimmed.lowercase(Locale.ROOT))
                }
            }

            cachedConfig = BlockingConfig(
                blockOnlyDuringFocus = configObj?.optBoolean("blockOnlyDuringFocus", false)
                    ?: prefs.getBoolean("block_only_during_focus", false),
                strictUninstallProtection = configObj?.optBoolean("strictUninstallProtection", false)
                    ?: prefs.getBoolean("strict_uninstall_protection", false),
                strictMode = configObj?.optBoolean("strictMode", false)
                    ?: prefs.getBoolean("strict_mode_active", false),
                blockSplitScreen = configObj?.optBoolean("blockSplitScreen", false)
                    ?: prefs.getBoolean("block_split_screen", false),
                blockedPackages = blockedPkgs,
                usageLimitBlockedPackages = usageLimitBlockedPkgs,
                scheduledBlockingActive = prefs.getBoolean("scheduled_blocking_active", false),
                blockAdultSites = configObj?.optBoolean("blockAdultSites", false)
                    ?: prefs.getBoolean("block_adult", false),
                blockShorts = configObj?.optBoolean("blockShorts", false)
                    ?: prefs.getBoolean("block_shorts", false),
                blockYoutube = configObj?.optBoolean("blockYoutube", false)
                    ?: prefs.getBoolean("youtube_study_mode", false),
                allowedChannels = allowedChs,
                blockReels = configObj?.optBoolean("blockReels", false)
                    ?: prefs.getBoolean("block_reels", false),
                blockSpotlight = configObj?.optBoolean("blockSpotlight", false)
                    ?: prefs.getBoolean("block_spotlight", false)
            )

            val focusActive = prefs.getBoolean("focus_active", false)
            val focusStateStr = prefs.getString("focus_state", "{}") ?: "{}"
            val stateObj = try { JSONObject(focusStateStr) } catch (_: Exception) { JSONObject() }
            val sessionBlocked = mutableSetOf<String>()

            val sessionBlockedRaw = prefs.getString("session_blocked_packages", null)
            if (!sessionBlockedRaw.isNullOrEmpty()) {
                try {
                    val arr = JSONArray(sessionBlockedRaw)
                    for (i in 0 until arr.length()) {
                        val p = arr.optString(i).trim()
                        if (p.isNotEmpty()) sessionBlocked.add(p.lowercase(Locale.ROOT))
                    }
                } catch (_: Exception) {}
            }

            cachedFocusState = CachedFocusState(
                active = focusActive && stateObj.optBoolean("active", false),
                mode = stateObj.optString("mode", "timer"),
                endAt = stateObj.optLong("endAt", 0L),
                sessionBlockedPackages = sessionBlocked
            )
        } catch (_: Exception) {
            // Retain existing cached configuration upon error
        }
    }

    /**
     * Records the action reason asynchronously to avoid enqueuing synchronous disk writes on the UI thread.
     */
    private fun recordAction(reason: String) {
        val now = System.currentTimeMillis()
        handler.post {
            try {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString("last_action_reason", reason)
                    .putLong("last_action_at", now)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    private fun sendHome() {
        val now = System.currentTimeMillis()
        if (now - lastActionAt < ACTION_RATE_LIMIT_MS) return
        lastActionAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun sendBack() {
        val now = System.currentTimeMillis()
        if (now - lastActionAt < ACTION_RATE_LIMIT_MS) return
        lastActionAt = now
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    // =========================================================================
    // Bounded Safe Tree Traversal (depth <= 4, node visit count <= 50, child limit <= 8)
    // =========================================================================

    /**
     * Searches the node hierarchy for any matching keyword.
     * Bounded by maxDepth = 4 and total visited nodes = 50.
     * Short-circuits immediately when a match is found.
     */
    private fun containsAnyKeyword(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        depth: Int = 0,
        counter: IntArray = IntArray(1)
    ): Boolean {
        if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
        counter[0]++

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
        if (text.isNotEmpty()) {
            for (kw in keywords) {
                if (text.contains(kw)) return true
            }
        }

        val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
        for (i in 0 until childLimit) {
            if (counter[0] >= MAX_TRAVERSAL_NODES) break
            val child = node.getChild(i) ?: continue
            val found = containsAnyKeyword(child, keywords, depth + 1, counter)
            if (found) return true
        }
        return false
    }

    /**
     * Detects selected bottom navigation tabs or prominent surface labels (e.g. "shorts", "reels", "spotlight").
     * Bounded by maxDepth = 4 and total visited nodes = 50.
     */
    private fun hasSelectedOrTopText(
        node: AccessibilityNodeInfo,
        target: String,
        depth: Int = 0,
        counter: IntArray = IntArray(1)
    ): Boolean {
        if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
        counter[0]++

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
        if (text.contains(target)) {
            if (node.isSelected || node.isChecked || depth <= 2) return true
        }

        val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
        for (i in 0 until childLimit) {
            if (counter[0] >= MAX_TRAVERSAL_NODES) break
            val child = node.getChild(i) ?: continue
            val found = hasSelectedOrTopText(child, target, depth + 1, counter)
            if (found) return true
        }
        return false
    }

    /**
     * Bounded check for YouTube video playback surface by resource ID.
     */
    private fun isVideoSurface(
        node: AccessibilityNodeInfo,
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

    /**
     * Bounded check matching active YouTube video metadata against allowed channel names.
     */
    private fun matchesAllowedChannel(
        root: AccessibilityNodeInfo,
        allowedChannels: Set<String>
    ): Boolean {
        if (allowedChannels.isEmpty()) return false
        return containsAnyAllowedChannel(root, allowedChannels)
    }

    private fun containsAnyAllowedChannel(
        node: AccessibilityNodeInfo,
        allowedChannels: Set<String>,
        depth: Int = 0,
        counter: IntArray = IntArray(1)
    ): Boolean {
        if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
        counter[0]++

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
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

    /**
     * Closes disallowed YouTube video with safe debounced global actions and bounded close button search.
     */
    private fun closeDisallowedVideo() {
        val now = System.currentTimeMillis()
        if (now - lastActionAt < 1400) return
        lastActionAt = now

        val current = rootInActiveWindow
        if (current != null && clickPlayerClose(current)) return

        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            val afterBack = rootInActiveWindow ?: return@postDelayed
            if (afterBack.packageName?.toString()?.lowercase(Locale.ROOT) != YOUTUBE) return@postDelayed
            if (clickPlayerClose(afterBack)) return@postDelayed
            if (isVideoSurface(afterBack) || isMiniPlayer(afterBack)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({
                    val finalRoot = rootInActiveWindow ?: return@postDelayed
                    if (finalRoot.packageName?.toString()?.lowercase(Locale.ROOT) != YOUTUBE) return@postDelayed
                    if (clickPlayerClose(finalRoot)) return@postDelayed
                    if (isVideoSurface(finalRoot) || isMiniPlayer(finalRoot)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }, 420)
            }
        }, 320)
    }

    /**
     * Bounded search for YouTube miniplayer close button.
     */
    private fun clickPlayerClose(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        counter: IntArray = IntArray(1)
    ): Boolean {
        if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
        counter[0]++

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: ""))
            .lowercase(Locale.ROOT)
            .replace('-', ' ')
        val closeLabel = text.contains("close mini player") ||
                text.contains("close miniplayer") ||
                text.contains("dismiss mini player") ||
                text.contains("dismiss miniplayer") ||
                text == "close" ||
                text.contains("close player")

        if (closeLabel && node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
        for (i in 0 until childLimit) {
            if (counter[0] >= MAX_TRAVERSAL_NODES) break
            val child = node.getChild(i) ?: continue
            if (clickPlayerClose(child, depth + 1, counter)) return true
        }
        return false
    }

    /**
     * Bounded check for YouTube miniplayer container.
     */
    private fun isMiniPlayer(
        node: AccessibilityNodeInfo,
        depth: Int = 0,
        counter: IntArray = IntArray(1)
    ): Boolean {
        if (depth > MAX_TRAVERSAL_DEPTH || counter[0] >= MAX_TRAVERSAL_NODES) return false
        counter[0]++

        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
        if (text.contains("miniplayer") || text.contains("mini player")) return true

        val childLimit = Math.min(node.childCount, MAX_CHILDREN_PER_NODE)
        for (i in 0 until childLimit) {
            if (counter[0] >= MAX_TRAVERSAL_NODES) break
            val child = node.getChild(i) ?: continue
            if (isMiniPlayer(child, depth + 1, counter)) return true
        }
        return false
    }

    data class BlockingConfig(
        val blockOnlyDuringFocus: Boolean = false,
        val strictUninstallProtection: Boolean = false,
        val strictMode: Boolean = false,
        val blockSplitScreen: Boolean = false,
        val blockedPackages: Set<String> = emptySet(),
        val usageLimitBlockedPackages: Set<String> = emptySet(),
        val scheduledBlockingActive: Boolean = false,
        val blockAdultSites: Boolean = false,
        val blockShorts: Boolean = false,
        val blockYoutube: Boolean = false,
        val allowedChannels: Set<String> = emptySet(),
        val blockReels: Boolean = false,
        val blockSpotlight: Boolean = false
    )

    data class CachedFocusState(
        val active: Boolean = false,
        val mode: String = "timer",
        val endAt: Long = 0L,
        val sessionBlockedPackages: Set<String> = emptySet()
    )

    companion object {
        private const val PREFS_NAME = "go_back_blocking"
        private const val YOUTUBE = "com.google.android.youtube"
        private const val INSTAGRAM = "com.instagram.android"
        private const val SNAPCHAT = "com.snapchat.android"
        private const val SETTINGS_PKG = "com.android.settings"

        private const val CONTENT_CHANGE_THROTTLE_MS = 250L
        private const val SPLIT_SCREEN_THROTTLE_MS = 500L
        private const val ACTION_RATE_LIMIT_MS = 300L

        private const val MAX_TRAVERSAL_DEPTH = 4
        private const val MAX_TRAVERSAL_NODES = 50
        private const val MAX_CHILDREN_PER_NODE = 8

        private val SETTINGS_KEYWORDS = listOf("com.goback.focus")

        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
            "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android"
        )

        private val ADULT_KEYWORDS = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "youporn.com",
            "redtube.com", "chaturbate.com", "stripchat.com", "onlyfans.com", "onlyfans",
            "erome.com", "spankbang.com", "rule34", "hentai", "javhd"
        )
    }
}
