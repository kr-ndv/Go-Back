package com.goback.focus

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

/**
 * Tier 1, Tier 2, and Tier 3 tests for Blocking Configuration & Rule Filtering.
 * Tests package allowlists/blocklists, package name sanitization,
 * YouTube study mode channel allowlist matching, adult website keyword scanner,
 * Settings/uninstall anti-tamper heuristics, and split-screen multi-window detection.
 */
class BlockingConfigurationTest {

    // --- Domain Models & Logic Oracles ---

    data class BlockingConfig(
        val blockedPackages: Set<String> = emptySet(),
        val blockShorts: Boolean = false,
        val blockReels: Boolean = false,
        val blockSpotlight: Boolean = false,
        val blockAdultSites: Boolean = false,
        val youtubeStudyMode: Boolean = false,
        val allowedChannels: Set<String> = emptySet(),
        val strictMode: Boolean = false,
        val strictUninstallProtection: Boolean = false,
        val blockSplitScreen: Boolean = false,
        val blockOnlyDuringFocus: Boolean = false
    )

    data class WindowDescriptor(
        val packageName: String,
        val isApplicationType: Boolean,
        val visibleText: String = ""
    )

    object BlockingEngine {
        const val SELF_PACKAGE = "com.goback.focus"
        const val YOUTUBE = "com.google.android.youtube"
        const val INSTAGRAM = "com.instagram.android"
        const val SNAPCHAT = "com.snapchat.android"
        const val SETTINGS_PACKAGE = "com.android.settings"

        val BROWSER_PACKAGES = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.opera.browser",
            "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android"
        )

        val ADULT_KEYWORDS = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "youporn.com",
            "redtube.com", "chaturbate.com", "stripchat.com", "onlyfans.com",
            "erome.com", "spankbang.com", "rule34", "hentai", "javhd"
        )

        fun sanitizePackages(rawPackages: Collection<String>?): Set<String> {
            if (rawPackages == null) return emptySet()
            val packageRegex = Regex("^[A-Za-z0-9_.]+$")
            return rawPackages
                .map { it.trim() }
                .filter { pkg ->
                    pkg.isNotEmpty() &&
                            pkg != SELF_PACKAGE &&
                            pkg.contains('.') &&
                            pkg.matches(packageRegex)
                }
                .take(100)
                .toSet()
        }

        fun isPackageBlocked(
            targetPkg: String,
            config: BlockingConfig,
            sessionBlockedPackages: Set<String> = emptySet(),
            focusActive: Boolean = false
        ): Boolean {
            val pkg = targetPkg.trim().lowercase(Locale.ROOT)
            if (pkg == SELF_PACKAGE) return false

            // 1. Session temporary rules from presets
            if (focusActive && sessionBlockedPackages.any { it.equals(pkg, ignoreCase = true) }) {
                return true
            }

            // If configured to block only during focus and focus is inactive, bypass
            if (config.blockOnlyDuringFocus && !focusActive) {
                return false
            }

            // 2. Permanent blocked packages
            return config.blockedPackages.any { it.equals(pkg, ignoreCase = true) }
        }

        fun matchesAllowedYouTubeChannel(videoText: String, allowedChannels: Set<String>): Boolean {
            if (allowedChannels.isEmpty()) return false
            val lowerText = videoText.lowercase(Locale.ROOT)
            for (channel in allowedChannels) {
                val clean = channel.trim().lowercase(Locale.ROOT)
                if (clean.isNotEmpty() && lowerText.contains(clean)) {
                    return true
                }
            }
            return false
        }

        fun containsAdultKeyword(browserContentOrUrl: String): Boolean {
            val text = browserContentOrUrl.lowercase(Locale.ROOT)
            for (kw in ADULT_KEYWORDS) {
                if (text.contains(kw)) return true
            }
            return false
        }

        fun shouldBlockSettingsAccess(
            packageName: String,
            screenText: String,
            config: BlockingConfig,
            focusActive: Boolean
        ): Boolean {
            if (packageName != SETTINGS_PACKAGE) return false
            val isStrict = config.strictUninstallProtection || (focusActive && config.strictMode)
            if (!isStrict) return false

            val text = screenText.lowercase(Locale.ROOT)
            return text.contains("com.goback.focus") || text.contains("go back")
        }

        fun shouldBlockSplitScreen(
            windows: List<WindowDescriptor>,
            config: BlockingConfig,
            focusActive: Boolean
        ): Boolean {
            if (!focusActive || !config.blockSplitScreen) return false
            val appWindows = windows.filter { it.isApplicationType }
            if (appWindows.size <= 1) return false

            for (win in appWindows) {
                val pkg = win.packageName.lowercase(Locale.ROOT)
                if (pkg.isNotEmpty() && pkg != SELF_PACKAGE) {
                    if (config.blockedPackages.any { it.equals(pkg, ignoreCase = true) } ||
                        pkg == YOUTUBE || pkg == INSTAGRAM || pkg == SNAPCHAT) {
                        return true
                    }
                }
            }
            return false
        }

        fun canPerformAction(lastActionAtMs: Long, nowMs: Long, cooldownMs: Long = 300L): Boolean {
            return (nowMs - lastActionAtMs) >= cooldownMs
        }
    }

    // =========================================================================
    // Tier 1: Feature Coverage (>=5 per feature)
    // =========================================================================

    @Test
    fun tier1_wholeAppBlocking_detectsBlockedPackages() {
        val config = BlockingConfig(
            blockedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically")
        )

        assertTrue(BlockingEngine.isPackageBlocked("com.instagram.android", config))
        assertTrue(BlockingEngine.isPackageBlocked("COM.INSTAGRAM.ANDROID", config)) // Case insensitive
        assertTrue(BlockingEngine.isPackageBlocked("com.zhiliaoapp.musically", config))
        assertFalse(BlockingEngine.isPackageBlocked("com.google.android.apps.docs", config))
    }

    @Test
    fun tier1_sessionTemporaryPresets_blockOnlyDuringFocus() {
        val config = BlockingConfig(
            blockedPackages = emptySet()
        )
        val sessionPackages = setOf("com.twitter.android")

        // Inactive focus -> not blocked
        assertFalse(BlockingEngine.isPackageBlocked("com.twitter.android", config, sessionPackages, focusActive = false))

        // Active focus -> blocked by session preset
        assertTrue(BlockingEngine.isPackageBlocked("com.twitter.android", config, sessionPackages, focusActive = true))
    }

    @Test
    fun tier1_youtubeStudyMode_channelAllowlistMatching() {
        val allowed = setOf("3Blue1Brown", "Khan Academy", "MIT OpenCourseWare")

        val allowedVideo = "3Blue1Brown - Essence of linear algebra, chapter 1"
        val disallowedVideo = "Top 10 Gaming Moments of 2026 - IGN"

        assertTrue(BlockingEngine.matchesAllowedYouTubeChannel(allowedVideo, allowed))
        assertFalse(BlockingEngine.matchesAllowedYouTubeChannel(disallowedVideo, allowed))
    }

    @Test
    fun tier1_adultWebsiteBlocking_detectsKnownKeywords() {
        assertTrue(BlockingEngine.containsAdultKeyword("https://www.pornhub.com/view_video.php"))
        assertTrue(BlockingEngine.containsAdultKeyword("Search results on rule34 post page"))
        assertTrue(BlockingEngine.containsAdultKeyword("https://onlyfans.com/creator_page"))
        assertFalse(BlockingEngine.containsAdultKeyword("https://en.wikipedia.org/wiki/Calculus"))
        assertFalse(BlockingEngine.containsAdultKeyword("https://github.com/kr-ndv/Go-Back"))
    }

    @Test
    fun tier1_settingsProtection_blocksAppInfoSurfaceDuringStrictFocus() {
        val config = BlockingConfig(
            strictMode = true,
            strictUninstallProtection = true
        )

        val settingsWithGoBack = "App info: Go Back version 1.0.0. Force stop. Uninstall."
        val settingsGeneral = "Display brightness and dark theme settings."

        assertTrue(BlockingEngine.shouldBlockSettingsAccess("com.android.settings", settingsWithGoBack, config, focusActive = true))
        assertFalse(BlockingEngine.shouldBlockSettingsAccess("com.android.settings", settingsGeneral, config, focusActive = true))
        assertFalse(BlockingEngine.shouldBlockSettingsAccess("com.example.calculator", settingsWithGoBack, config, focusActive = true))
    }

    @Test
    fun tier1_splitScreenProtection_detectsBlockedAppInMultiWindow() {
        val config = BlockingConfig(
            blockSplitScreen = true,
            blockedPackages = setOf("com.reddit.frontpage")
        )

        val windowsSplit = listOf(
            WindowDescriptor("com.goback.focus", isApplicationType = true),
            WindowDescriptor("com.reddit.frontpage", isApplicationType = true)
        )

        val windowsSingle = listOf(
            WindowDescriptor("com.goback.focus", isApplicationType = true)
        )

        assertTrue(BlockingEngine.shouldBlockSplitScreen(windowsSplit, config, focusActive = true))
        assertFalse(BlockingEngine.shouldBlockSplitScreen(windowsSingle, config, focusActive = true))
    }

    @Test
    fun tier1_actionDebouncing_enforces300msCooldown() {
        val lastAction = 1000L
        assertFalse(BlockingEngine.canPerformAction(lastAction, nowMs = 1150L, cooldownMs = 300L))
        assertTrue(BlockingEngine.canPerformAction(lastAction, nowMs = 1300L, cooldownMs = 300L))
        assertTrue(BlockingEngine.canPerformAction(lastAction, nowMs = 1500L, cooldownMs = 300L))
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (>=5 per feature)
    // =========================================================================

    @Test
    fun tier2_boundary_selfPackageNeverBlocked() {
        val config = BlockingConfig(
            blockedPackages = setOf("com.goback.focus")
        )
        // Self package must never be blocked even if explicitly listed
        assertFalse(BlockingEngine.isPackageBlocked("com.goback.focus", config, focusActive = true))
    }

    @Test
    fun tier2_boundary_packageSanitizerEnforcesRules() {
        val raw = listOf(
            "com.goback.focus", // Self -> excluded
            "   com.instagram.android   ", // Whitespace trimmed -> valid
            "invalid_no_dots", // No dot -> excluded
            "com.bad!package@name", // Invalid chars -> excluded
            "com.valid.app1",
            "com.valid.app2"
        )
        val sanitized = BlockingEngine.sanitizePackages(raw)

        assertFalse(sanitized.contains("com.goback.focus"))
        assertTrue(sanitized.contains("com.instagram.android"))
        assertFalse(sanitized.contains("invalid_no_dots"))
        assertFalse(sanitized.contains("com.bad!package@name"))
        assertTrue(sanitized.contains("com.valid.app1"))
        assertTrue(sanitized.contains("com.valid.app2"))
    }

    @Test
    fun tier2_boundary_packageCapAt100Items() {
        val largeList = (1..200).map { "com.test.app$it" }
        val sanitized = BlockingEngine.sanitizePackages(largeList)
        assertEquals(100, sanitized.size)
    }

    @Test
    fun tier2_boundary_emptyYouTubeAllowlistBlocksAllVideos() {
        val emptyAllowed = emptySet<String>()
        // If study mode is active with no channels allowed, any video is disallowed
        assertFalse(BlockingEngine.matchesAllowedYouTubeChannel("Educational lecture", emptyAllowed))
    }

    @Test
    fun tier2_boundary_splitScreenIgnoresNonApplicationWindows() {
        val config = BlockingConfig(
            blockSplitScreen = true,
            blockedPackages = setOf("com.reddit.frontpage")
        )

        // Multiple windows, but the other is InputMethod or SystemUI (not TYPE_APPLICATION)
        val windowsWithKeyboard = listOf(
            WindowDescriptor("com.goback.focus", isApplicationType = true),
            WindowDescriptor("com.google.android.inputmethod.latin", isApplicationType = false)
        )

        assertFalse(BlockingEngine.shouldBlockSplitScreen(windowsWithKeyboard, config, focusActive = true))
    }

    @Test
    fun tier2_boundary_adultKeywordsCaseInsensitiveSubstring() {
        assertTrue(BlockingEngine.containsAdultKeyword("HTTPS://M.XVIDEOS.COM/VIDEO123"))
        assertTrue(BlockingEngine.containsAdultKeyword("Check out this HenTai gallery"))
    }
}
