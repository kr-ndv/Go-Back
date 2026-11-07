# Dispatch for E2E Test Writer: Test Infra & Requirements-Driven Test Suite

## 2026-11-07T14:45:26Z

You are the E2E Test Writer for the Go-Back project.
Working directory: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\test_writer_e2e\
Master Project Document: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md
Original Request path: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md
Dispatch file: c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\test_writer_e2e\DISPATCH.md

You MUST read `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\ORIGINAL_REQUEST.md` and `c:\Users\visha\Desktop\Projects\Go-Back\.agents\teamwork\orchestrator\PROJECT.md` first before starting work.

Your objective:
Establish the E2E Testing Track for Go-Back native Android app:
1. Create `TEST_INFRA.md` in `.agents/teamwork/test_writer_e2e/TEST_INFRA.md` following the 4-tier methodology:
   - Tier 1: Feature Coverage (>=5 per feature)
   - Tier 2: Boundary & Corner Cases (>=5 per feature)
   - Tier 3: Cross-Feature Combinations (pairwise interactions)
   - Tier 4: Real-World Application Scenarios
2. Create automated Gradle / Kotlin unit tests in `android/app/src/test/java/com/goback/focus/` covering:
   - Focus Timer & Pomodoro math, phase transitions, and recovery
   - Streak tiers calculation (Spark to Centurion) and 1-day shield logic
   - Blocking configuration filtering, allowlists, and domain matching
   - Schedule recurring times and 3-stage exam reminder triggers
3. Verify test execution via `./gradlew testDebugUnitTest` and publish `TEST_READY.md` in your working directory and at `.agents/teamwork/TEST_READY.md` summarizing coverage across all 4 tiers.

Regularly update `progress.md` with timestamp headers in your working directory.
When finished, write your handoff report to `handoff.md` and notify the orchestrator.
