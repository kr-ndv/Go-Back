import * as SQLite from 'expo-sqlite';

let database: SQLite.SQLiteDatabase | null = null;

export async function getDatabase() {
  if (database) return database;
  database = await SQLite.openDatabaseAsync('goback.db');
  await database.execAsync(`
    PRAGMA journal_mode = WAL;
    PRAGMA foreign_keys = ON;
    CREATE TABLE IF NOT EXISTS tasks (
      id TEXT PRIMARY KEY NOT NULL,
      title TEXT NOT NULL,
      completed INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL,
      priority TEXT NOT NULL DEFAULT 'normal',
      due_date TEXT
    );
    CREATE TABLE IF NOT EXISTS notes (
      id TEXT PRIMARY KEY NOT NULL,
      title TEXT NOT NULL,
      body TEXT NOT NULL DEFAULT '',
      updated_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS subjects (
      id TEXT PRIMARY KEY NOT NULL,
      name TEXT NOT NULL,
      chapters_json TEXT NOT NULL DEFAULT '[]',
      updated_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS focus_sessions (
      id TEXT PRIMARY KEY NOT NULL,
      started_at TEXT NOT NULL,
      duration_seconds INTEGER NOT NULL,
      completed INTEGER NOT NULL DEFAULT 0,
      session_type TEXT NOT NULL DEFAULT 'focus',
      mode TEXT NOT NULL DEFAULT 'timer',
      tag TEXT NOT NULL DEFAULT '',
      notes TEXT NOT NULL DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS exams (
      id TEXT PRIMARY KEY NOT NULL,
      title TEXT NOT NULL,
      exam_date TEXT NOT NULL,
      details TEXT NOT NULL DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS goals (
      id TEXT PRIMARY KEY NOT NULL,
      title TEXT NOT NULL,
      target_date TEXT,
      progress INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL
    );
    CREATE TABLE IF NOT EXISTS habits (
      id TEXT PRIMARY KEY NOT NULL,
      title TEXT NOT NULL,
      created_at TEXT NOT NULL,
      archived INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS habit_logs (
      id TEXT PRIMARY KEY NOT NULL,
      habit_id TEXT NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
      log_date TEXT NOT NULL,
      completed INTEGER NOT NULL DEFAULT 1,
      UNIQUE(habit_id, log_date)
    );
    CREATE TABLE IF NOT EXISTS settings (
      key TEXT PRIMARY KEY NOT NULL,
      value TEXT NOT NULL
    );
  `);

  // Additive migrations preserve records stored by already-installed beta builds.
  const focusColumns = await database.getAllAsync<{ name: string }>('PRAGMA table_info(focus_sessions)');
  if (!focusColumns.some(column => column.name === 'session_type')) {
    await database.execAsync("ALTER TABLE focus_sessions ADD COLUMN session_type TEXT NOT NULL DEFAULT 'focus'");
  }
  if (!focusColumns.some(column => column.name === 'mode')) {
    await database.execAsync("ALTER TABLE focus_sessions ADD COLUMN mode TEXT NOT NULL DEFAULT 'timer'");
  }
  if (!focusColumns.some(column => column.name === 'tag')) {
    await database.execAsync("ALTER TABLE focus_sessions ADD COLUMN tag TEXT NOT NULL DEFAULT ''");
  }
  if (!focusColumns.some(column => column.name === 'notes')) {
    await database.execAsync("ALTER TABLE focus_sessions ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
  }

  const taskColumns = await database.getAllAsync<{ name: string }>('PRAGMA table_info(tasks)');
  if (!taskColumns.some(column => column.name === 'priority')) {
    await database.execAsync("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'normal'");
  }
  if (!taskColumns.some(column => column.name === 'due_date')) {
    await database.execAsync('ALTER TABLE tasks ADD COLUMN due_date TEXT');
  }
  return database;
}

export async function getSetting<T>(key: string, fallback: T): Promise<T> {
  const db = await getDatabase();
  const row = await db.getFirstAsync<{ value: string }>(
    'SELECT value FROM settings WHERE key = ?',
    key
  );
  if (!row) return fallback;
  try { return JSON.parse(row.value) as T; } catch { return fallback; }
}

export async function setSetting(key: string, value: unknown) {
  const db = await getDatabase();
  await db.runAsync(
    'INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value',
    key,
    JSON.stringify(value)
  );
}
