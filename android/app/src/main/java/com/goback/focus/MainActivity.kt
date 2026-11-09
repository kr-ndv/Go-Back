package com.goback.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goback.focus.ui.components.FloatingNavigationBar
import com.goback.focus.ui.components.NavTab
import com.goback.focus.ui.screens.*
import com.goback.focus.ui.theme.GoBackTheme

data class NavigationTarget(val route: String, val timestamp: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {

    private val targetRoute = mutableStateOf<NavigationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startRoute = intent?.getStringExtra("route")
        if (!startRoute.isNullOrEmpty()) {
            targetRoute.value = NavigationTarget(startRoute)
        }

        setContent {
            GoBackTheme {
                GoBackMainApp(navigationTarget = targetRoute.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = intent.getStringExtra("route")
        if (!route.isNullOrEmpty()) {
            targetRoute.value = NavigationTarget(route)
        }
    }
}

@Composable
fun GoBackMainApp(navigationTarget: NavigationTarget? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavTab.Home.route
    val isTopLevelDestination = NavTab.items.any { it.route == currentRoute }

    LaunchedEffect(navigationTarget) {
        val route = navigationTarget?.route
        if (!route.isNullOrEmpty() && route != NavTab.Home.route) {
            navController.navigate(route) {
                popUpTo(NavTab.Home.route) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelDestination) {
                FloatingNavigationBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        if (currentRoute != tab.route) {
                            navController.navigate(tab.route) {
                                popUpTo(NavTab.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavTab.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavTab.Home.route) {
                TodayScreen(
                    onNavigateToFocus = { navController.navigate(NavTab.Focus.route) },
                    onNavigateToSyllabus = { navController.navigate("syllabus") },
                    onNavigateToExams = { navController.navigate("exams") },
                    onNavigateToAnalytics = { navController.navigate(NavTab.Analytics.route) },
                    onNavigateToBlocking = { navController.navigate("blocking") },
                    onNavigateToUsageLimits = { navController.navigate("usage_limits") },
                    onNavigateToSchedules = { navController.navigate("schedules") },
                    onNavigateToNotes = { navController.navigate(NavTab.Notes.route) },
                    onNavigateToSettings = { navController.navigate(NavTab.Settings.route) }
                )
            }
            composable(NavTab.Focus.route) {
                FocusScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPresets = { navController.navigate("focus_presets") }
                )
            }
            composable("focus_presets") {
                FocusPresetsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onApplyPreset = { navController.popBackStack() }
                )
            }
            composable("backup_restore") {
                BackupRestoreScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(NavTab.Analytics.route) {
                AnalyticsScreen()
            }
            composable(NavTab.Notes.route) {
                NotesScreen(
                    onNavigateToEditor = { noteId ->
                        navController.navigate("note_editor/$noteId")
                    }
                )
            }
            composable(
                route = "note_editor/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId").orEmpty()
                NoteEditorScreen(
                    noteId = noteId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(NavTab.Settings.route) {
                SettingsScreen(
                    onNavigateToBlocking = { navController.navigate("blocking") },
                    onNavigateToUsageLimits = { navController.navigate("usage_limits") },
                    onNavigateToSchedules = { navController.navigate("schedules") },
                    onNavigateToExams = { navController.navigate("exams") },
                    onNavigateToFocusPresets = { navController.navigate("focus_presets") },
                    onNavigateToBackupRestore = { navController.navigate("backup_restore") }
                )
            }
            composable("syllabus") {
                SyllabusScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("exams") {
                ExamsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("schedules") {
                SchedulesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("blocking") {
                BlockingScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("usage_limits") {
                UsageLimitsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
