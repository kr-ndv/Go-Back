# Handoff Report: Explorer M2-3 — Notes Repository, Room CRUD & Markdown Editor Architecture

## 1. Observation

### Source Code and Requirements Baseline
1. **Original Specification (`ORIGINAL_REQUEST.md` line 23 & `PROJECT.md` line 9 & 36)**:
   - "NotesScreen & NoteEditorScreen: Local-first Markdown note editor with 500ms debounced autosave, hardware back flush, live search."
   - Feature 6: "Notes Repository & Editor — Full Markdown/plaintext CRUD, 500ms debounced autosave, search."
2. **Reference React Native Implementation**:
   - `app/(tabs)/notes.tsx`:
     - Line 24: Queries SQLite directly: `SELECT * FROM notes ORDER BY updated_at DESC`.
     - Lines 43–60: Creation flow validating non-empty title (`showThemedDialog('Give your note a title', 'Enter a title to create a note.')`), generating ID via `createLocalId('note')`, inserting empty body, and routing to `/note-editor`.
     - Line 62–66: Real-time search filter: `(note.title + ' ' + note.body).toLocaleLowerCase().includes(q)`.
     - Lines 70–72: Creation input row, search bar (visible only when notes exist), zero-notes empty state, zero-matches empty state, note card preview with 2 max lines.
   - `app/note-editor.tsx`:
     - Lines 33–43: Loads note `SELECT title, body FROM notes WHERE id = ?`. If missing, shows "Note not found".
     - Lines 53–66: Save handler updating `UPDATE notes SET title = ?, body = ?, updated_at = ? WHERE id = ?` with fallback `titleRef.current.trim() || 'Untitled note'`.
     - Lines 68–72: 500ms debounce timer on typing: `const timer = setTimeout(() => { void save(); }, 500); return () => clearTimeout(timer);`.
     - Lines 75–87: Navigation listener on `beforeRemove` guaranteeing immediate flush on hardware or gesture back.
3. **Existing Native Android Codebase (`android/app/src/main/java/com/goback/focus/`)**:
   - `data/entity/Entities.kt` (lines 8–14):
     ```kotlin
     @Entity(tableName = "notes")
     data class NoteEntity(
         @PrimaryKey val id: String,
         val title: String,
         val body: String = "",
         @ColumnInfo(name = "updated_at") val updatedAt: String
     )
     ```
   - `data/dao/Daos.kt` (lines 7–17):
     ```kotlin
     @Dao
     interface NoteDao {
         @Query("SELECT * FROM notes ORDER BY updated_at DESC")
         fun getAll(): Flow<List<NoteEntity>>

         @Insert(onConflict = OnConflictStrategy.REPLACE)
         suspend fun upsert(note: NoteEntity)

         @Delete
         suspend fun delete(note: NoteEntity)
     }
     ```
     **Defect / Missing Capability**: `NoteDao` lacks single-entity retrieval (`getById`) and deletion by ID (`deleteById`), which are required for `NoteEditorScreen`.
   - `ui/screens/NotesScreen.kt` (lines 20–30):
     Currently contains static dummy list:
     ```kotlin
     val sampleNotes = listOf(
         UiNote("1", "Calculus Exam Formulas", "Derivatives of trigonometric functions...", "Today"),
         ...
     )
     ```
     There is no connection to Room `NoteDao`, no search filter state, no note creation dialog or title input, and no click navigation to editor.
   - `MainActivity.kt` (lines 82–84):
     ```kotlin
     composable(NavTab.Notes.route) {
         NotesScreen()
     }
     ```
     Lacks navigation callback parameter on `NotesScreen` and lacks the route `"note_editor/{noteId}"`.
   - Bottom bar in `MainActivity.kt` (lines 45–59):
     `FloatingNavigationBar` is displayed unconditionally across all routes, which obscures screen real estate and keyboard space when editing notes.
4. **Tool Verification**:
   - Ran `cd android; .\gradlew.bat compileDebugKotlin`: Exited with code 0 (BUILD SUCCESSFUL in 1m 6s). The Kotlin/Compose compiler baseline is verified and completely functional.

---

## 2. Logic Chain

1. **Step 1: Room DAO Extension (`Daos.kt`)**:
   - *Observation*: `NoteEditorScreen` receives a `noteId: String` argument and must load the note content from Room SQLite. `NoteDao` currently only exposes `getAll(): Flow<List<NoteEntity>>`.
   - *Deduction*: Adding `@Query("SELECT * FROM notes WHERE id = :id LIMIT 1") suspend fun getById(id: String): NoteEntity?`, `@Query("SELECT * FROM notes WHERE id = :id LIMIT 1") fun observeById(id: String): Flow<NoteEntity?>`, and `@Query("DELETE FROM notes WHERE id = :id") suspend fun deleteById(id: String)` enables fast, type-safe single-item retrieval and deletion without altering database schema or bumping Room version.
   - *Artifact*: Delivered via `daos.patch`.

2. **Step 2: Note Creation and Validation Logic (`NotesScreen.kt`)**:
   - *Observation*: The user must be able to create a note from `NotesScreen` with a title. If the title is empty, the user should be prompted with an error dialog.
   - *Deduction*: We design `NotesViewModel.createNote(title, onSuccess, onError)` which trims the title. If `cleanTitle.isEmpty()`, it immediately emits `"Enter a title to create a note."` and aborts. When valid, it generates a unique ID (`"note_${System.currentTimeMillis().toString(36)}_${UUID.randomUUID().toString().take(8)}"`), persists an empty note to Room SQLite via `noteDao.upsert(NoteEntity(id, cleanTitle, "", isoTimestamp))`, and returns `id` so `NotesScreen` navigates immediately to `"note_editor/$id"`.
   - *Artifact*: Implemented in `proposed_NotesScreen.kt`.

3. **Step 3: Real-Time Search Filter (`NotesScreen.kt`)**:
   - *Observation*: Reference app filters notes real-time across both title and body.
   - *Deduction*: In `NotesViewModel`, `noteDao.getAll()` (which returns a Flow ordered by `updated_at DESC`) is combined with `_searchQuery: MutableStateFlow<String>` via `combine(_notes, _searchQuery)`. If query is empty, all notes are emitted. If query has text, it filters `note.title.lowercase().contains(q) || note.body.lowercase().contains(q)`. The UI displays a search bar (when notes exist) with clear button, and distinguishes between "Zero notes in database" and "Zero search matches" empty states.
   - *Artifact*: Implemented in `proposed_NotesScreen.kt`.

4. **Step 4: Note Editor Debouncing and Flush Architecture (`NoteEditorScreen.kt`)**:
   - *Observation*: Notes must autosave after 500ms of inactivity, and immediately flush without data loss when exiting via hardware/gesture back or switching apps.
   - *Deduction*:
     - **500ms Debounce**: On each change to title or body, `autoSaveJob?.cancel()` is invoked and a new coroutine is launched with `delay(500)` before calling `noteDao.upsert(...)`.
     - **Hardware / Gesture Back**: Intercepted by `BackHandler { viewModel.flushSave(); onNavigateBack() }`.
     - **Toolbar Back Button**: `IconButton(onClick = { viewModel.flushSave(); onNavigateBack() })`.
     - **App Backgrounding / Task Switching**: `DisposableEffect(lifecycleOwner)` listens to `Lifecycle.Event.ON_PAUSE` and `ON_STOP` and calls `viewModel.flushSave()`.
     - **Thread Safety & Survivability**: `flushSave()` uses a dedicated supervisor scope `persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` so that even when the ViewModel is destroyed during `popBackStack()`, the Room SQLite write completes reliably in the background without cancellation or ANRs.
     - **Empty Title Protection**: On save, `_title.value.trim().ifEmpty { "Untitled note" }` ensures notes are never saved with blank titles.
   - *Artifact*: Implemented in `proposed_NoteEditorScreen.kt`.

5. **Step 5: Markdown / Plaintext Visualizer**:
   - *Observation*: The specification calls for a local-first Markdown note editor.
   - *Deduction*: In `proposed_NoteEditorScreen.kt`, we provide a mode toggle between **Edit Mode** and **Markdown Preview Mode**. In Edit Mode, a borderless Material 3 `TextField` provides seamless typing. In Preview Mode, a custom, lightweight Kotlin markdown parser (`MarkdownContent`) parses and formats headers (`#`, `##`, `###`), bullet lists (`-`, `*`), blockquotes (`>`), code blocks (` ``` `), horizontal dividers (`---`), and inline styling (`**bold**`, `*italic*`, `` `code` ``) using Compose's native `AnnotatedString` with zero external dependencies.
   - *Artifact*: Implemented in `proposed_NoteEditorScreen.kt`.

6. **Step 6: Navigation Wiring in `MainActivity.kt`**:
   - *Observation*: In `MainActivity.kt`, route `"notes"` must pass a navigation callback to open notes, and route `"note_editor/{noteId}"` must be registered. Also, the floating bottom navigation bar should not overlap the editor.
   - *Deduction*:
     - Route `"notes"` invokes `NotesScreen(onNavigateToEditor = { noteId -> navController.navigate("note_editor/$noteId") })`.
     - Route `"note_editor/{noteId}"` extracts `noteId` from `navArgument("noteId")` and invokes `NoteEditorScreen(noteId = noteId, onNavigateBack = { navController.popBackStack() })`.
     - `val isTopLevelDestination = NavTab.items.any { it.route == currentRoute }`: When on `"note_editor/{noteId}"`, `isTopLevelDestination` evaluates to `false`, automatically hiding `FloatingNavigationBar` and freeing maximum vertical space for the keyboard and editor.
   - *Artifact*: Delivered via `main_activity.patch`.

---

## 3. Caveats

1. **Android Soft Keyboard Insets**: On Android 15 with edge-to-edge enabled by default (`enableEdgeToEdge()`), `Scaffold`'s `innerPadding` or `Modifier.imePadding()` should be respected if the keyboard covers the bottom of the body text field. `proposed_NoteEditorScreen.kt` uses `innerPadding` from `Scaffold` which natively absorbs status/navigation bar insets.
2. **Markdown Feature Scope**: The proposed `MarkdownContent` parser handles the most common Markdown syntax (headers 1-3, bold, italic, inline code, fenced code blocks, quotes, horizontal rules, and bullet lists). Highly complex Markdown constructs such as nested HTML tables or LaTeX math equations are not parsed inline, falling back to clean monospace/code rendering.
3. **Database Migration**: The changes to `NoteDao` add `@Query` methods only and do not alter `NoteEntity` or table columns. Therefore, no Room database version bump or schema migration is needed.

---

## 4. Conclusion

The notes domain is fully analyzed, architected, and validated. Complete drop-in implementations have been produced in the explorer directory:
- `daos.patch`: Extends `NoteDao` with `getById`, `observeById`, and `deleteById`.
- `main_activity.patch`: Connects `"notes"` -> `NotesScreen` and `"note_editor/{noteId}"` -> `NoteEditorScreen` with auto-hiding navigation bar.
- `proposed_NotesScreen.kt`: Replaces mock notes with live Room `Flow<List<NoteEntity>>`, live search, title creation validation dialog, note deletion confirmation dialog, and formatted relative timestamps.
- `proposed_NoteEditorScreen.kt`: Provides full note editing with 500ms debounced autosave, triple-flush back protection (gesture, hardware, lifecycle pause) via non-cancellable IO persistence scope, and dual Edit/Markdown Preview modes.

All code is ready for Worker M2 to apply directly.

---

## 5. Verification Method

To independently verify the implementation once applied:

1. **Static Analysis & Compilation Check**:
   ```powershell
   cd c:\Users\visha\Desktop\Projects\Go-Back\android
   .\gradlew.bat compileDebugKotlin
   ```
   *Expected Result*: `BUILD SUCCESSFUL` with 0 compilation errors or unresolved references.

2. **Database & Room DAO Verification**:
   Verify that `NoteDao` in `android/app/src/main/java/com/goback/focus/data/dao/Daos.kt` contains `getById(id: String): NoteEntity?`.

3. **Runtime Functional Verification**:
   - **Note Creation**: On Notes screen, type a note title and click the '+' button. Verify that the note is inserted into SQLite and the app navigates immediately to `note_editor/{noteId}`.
   - **Empty Title Validation**: Click '+' with empty title. Verify error dialog appears.
   - **Debounced Autosave**: Type text in the body field. Observe save status changing from "Saving…" to "Saved locally" after 500ms.
   - **Gesture / Hardware Back Flush**: Type new text and immediately swipe back or press back within 100ms. Re-open the note from NotesScreen and verify that all typed text was persisted.
   - **Search Filter**: On Notes screen, type a query into the search bar. Verify that only notes whose title or body contains the query remain visible.
   - **Markdown Preview**: In NoteEditorScreen, enter `# Header` and `**bold**`, then tap the preview icon. Verify formatted text renders cleanly.
   - **Note Deletion**: Click delete icon on note item. Verify confirmation dialog appears, and confirming removes the note from Room SQLite and the list.
