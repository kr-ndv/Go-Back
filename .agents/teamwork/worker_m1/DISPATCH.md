# Dispatch for Worker M1: Apply Stability & Crash Fixes

Working directory: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\worker_m1\
Master Project Document: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md
Original Request: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md

You MUST read `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md` and `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md` first before starting work.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## File Ownership
You exclusively own and may edit the following files:
1. `android/app/src/main/AndroidManifest.xml`
2. `android/app/src/main/java/com/goback/focus/service/GoBackMaintenanceWorker.kt`
3. `android/app/src/main/java/com/goback/focus/service/GoBackBlockingService.kt`
4. `android/app/src/main/res/xml/accessibility_service_config.xml`
5. `android/app/src/main/java/com/goback/focus/service/FocusTimerService.kt`
6. `android/app/src/main/java/com/goback/focus/GoBackApplication.kt`

## Inputs & Explorer Findings
Read and apply the exact findings and proposed replacement files / patches authored by the 3 Explorers:
1. Explorer M1-1:
   - Report: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_1\handoff.md`
   - Apply `INTERNET` and `ACCESS_NETWORK_STATE` permissions to `AndroidManifest.xml`.
   - Update `GoBackMaintenanceWorker.kt` with `Dispatchers.IO`, `isNetworkConnected()`, safe timestamp committing, and proper logging.
2. Explorer M1-2:
   - Report: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_2\handoff.md`
   - Reference replacement: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_2\proposed_GoBackBlockingService.kt`
   - Patch: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_2\GoBackBlockingService.patch`
   - Update `GoBackBlockingService.kt` with in-memory caching, debouncing, bounded traversal, fast-exit, and `"onlyfans"` keyword.
   - Update `android/app/src/main/res/xml/accessibility_service_config.xml` (remove `flagIncludeNotImportantViews` and `typeViewScrolled`, notificationTimeout="120").
3. Explorer M1-3:
   - Report: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_3\handoff.md`
   - Reference replacement: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_3\proposed_FocusTimerService.kt`
   - Reference replacement: `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\explorer_m1_3\proposed_GoBackApplication.kt`
   - Update `FocusTimerService.kt` with Android 14+ FGS type, exception handling, and `ACTION_PAUSE`/`ACTION_RESUME`.
   - Update `GoBackApplication.kt` with defensive `ensureNotificationChannels`.

## Verification Requirement
Run the build and tests:
```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```
Both MUST compile cleanly and pass 100%.

Regularly update `progress.md` with timestamp headers in your working directory.
When completed, write your handoff report to `handoff.md` in your working directory and notify the orchestrator.
