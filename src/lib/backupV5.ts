import * as SQLite from 'expo-sqlite';
import * as FileSystem from 'expo-file-system/legacy';
import { getDatabase } from '@/lib/database';

const TABLES = ['tasks', 'notes', 'subjects', 'focus_sessions', 'focus_schedules', 'focus_presets', 'usage_limits', 'exams', 'goals', 'habits', 'habit_logs', 'settings'] as const;
type TableName = typeof TABLES[number];

const COLUMNS: Record<TableName, string[]> = {
  tasks: ['id', 'title', 'completed', 'created_at', 'priority', 'due_date'], notes: ['id', 'title', 'body', 'updated_at'], subjects: ['id', 'name', 'chapters_json', 'updated_at'],
  focus_sessions: ['id', 'started_at', 'duration_seconds', 'completed', 'session_type', 'mode', 'tag', 'notes'],
  focus_schedules: ['id', 'title', 'days_json', 'start_time', 'duration_minutes', 'enabled', 'created_at', 'reminder_minutes', 'skip_dates_json', 'routine_type', 'timezone'],
  focus_presets: ['id', 'name', 'mode', 'duration_minutes', 'blocked_packages_json', 'created_at'], usage_limits: ['id', 'package_name', 'app_name', 'limit_minutes', 'warning_percent', 'enabled', 'created_at'],
  exams: ['id', 'title', 'exam_date', 'details'], goals: ['id', 'title', 'target_date', 'progress', 'created_at'], habits: ['id', 'title', 'created_at', 'archived'], habit_logs: ['id', 'habit_id', 'log_date', 'completed'], settings: ['key', 'value'],
};

const REQUIRED: Record<TableName, string[]> = {
  tasks: ['id', 'title', 'created_at'], notes: ['id', 'title', 'body', 'updated_at'], subjects: ['id', 'name', 'chapters_json', 'updated_at'], focus_sessions: ['id', 'started_at', 'duration_seconds', 'completed'],
  focus_schedules: ['id', 'title', 'days_json', 'start_time', 'duration_minutes', 'enabled', 'created_at'], focus_presets: ['id', 'name', 'mode', 'duration_minutes', 'blocked_packages_json', 'created_at'],
  usage_limits: ['id', 'package_name', 'app_name', 'limit_minutes', 'warning_percent', 'enabled', 'created_at'], exams: ['id', 'title', 'exam_date', 'details'], goals: ['id', 'title', 'progress', 'created_at'], habits: ['id', 'title', 'created_at', 'archived'], habit_logs: ['id', 'habit_id', 'log_date', 'completed'], settings: ['key', 'value'],
};

type Backup = { app: 'Go Back'; schemaVersion: number; exportedAt: string; includes?: string[]; tables: Record<TableName, Record<string, unknown>[]> };
const RESTORE_POINT_NAME = 'go-back-before-last-restore.json';
const restorePointUri = () => FileSystem.documentDirectory ? FileSystem.documentDirectory + RESTORE_POINT_NAME : null;

export async function createBackupJson(): Promise<string> {
  const db = await getDatabase(); const tables = {} as Backup['tables'];
  for (const table of TABLES) tables[table] = await db.getAllAsync<Record<string, unknown>>(`SELECT * FROM ${table}`);
  return JSON.stringify({ app: 'Go Back', schemaVersion: 5, exportedAt: new Date().toISOString(), includes: ['tasks','notes','subjects','focus history','focus schedules','focus presets','screen-time limits','goals','habits','settings'], tables } satisfies Backup, null, 2);
}

function validateBackup(value: unknown): Backup {
  if (!value || typeof value !== 'object') throw new Error('The selected file is not a valid Go Back backup.');
  const backup = value as Partial<Backup>;
  if (backup.app !== 'Go Back' || !Number.isInteger(backup.schemaVersion) || (backup.schemaVersion ?? 0) < 1 || (backup.schemaVersion ?? 0) > 5 || !backup.tables || typeof backup.tables !== 'object') throw new Error('This backup is not compatible with this version of Go Back.');
  const data = backup.tables as unknown as Record<string, unknown>;
  if ((backup.schemaVersion ?? 0) < 4 && !Array.isArray(data.focus_schedules)) data.focus_schedules = [];
  if ((backup.schemaVersion ?? 0) < 5) { if (!Array.isArray(data.focus_presets)) data.focus_presets = []; if (!Array.isArray(data.usage_limits)) data.usage_limits = []; }
  for (const table of TABLES) { if (!Array.isArray(data[table])) throw new Error(`Backup is missing the ${table} data section.`); if ((data[table] as unknown[]).length > 50000) throw new Error('This backup contains too many records to restore safely.'); }
  return backup as Backup;
}

function cleanRow(table: TableName, value: unknown): Record<string, string | number | null> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`Invalid record in ${table}.`);
  const source = value as Record<string, unknown>; const clean: Record<string, string | number | null> = {};
  for (const column of COLUMNS[table]) { if (!(column in source)) continue; const cell = source[column]; if (cell === null && (column === 'due_date' || column === 'target_date')) clean[column] = null; else if (typeof cell === 'string' || (typeof cell === 'number' && Number.isFinite(cell))) clean[column] = cell; else throw new Error(`Invalid ${column} value in ${table}.`); }
  if (REQUIRED[table].some(key => !(key in clean))) throw new Error(`A record in ${table} is missing required fields.`);
  if (table === 'focus_sessions' && (Number(clean.duration_seconds) < 0 || Number(clean.duration_seconds) > 31_536_000)) throw new Error('A focus session has an invalid duration.');
  if (table === 'focus_schedules' && (Number(clean.duration_minutes) < 5 || Number(clean.duration_minutes) > 180)) throw new Error('A focus schedule has an invalid duration.');
  if (table === 'focus_presets' && (Number(clean.duration_minutes) < 1 || Number(clean.duration_minutes) > 180)) throw new Error('A focus preset has an invalid duration.');
  if (table === 'usage_limits' && (Number(clean.limit_minutes) < 1 || Number(clean.limit_minutes) > 1440)) throw new Error('A screen-time limit has an invalid duration.');
  return clean;
}

async function writeRestorePoint(json: string) { const uri = restorePointUri(); if (uri) await FileSystem.writeAsStringAsync(uri, json, { encoding: FileSystem.EncodingType.UTF8 }); }
export async function hasRestorePoint() { const uri = restorePointUri(); if (!uri) return false; try { return (await FileSystem.getInfoAsync(uri)).exists; } catch { return false; } }
export async function restoreLastRestorePoint() { const uri = restorePointUri(); if (!uri) throw new Error('Local restore-point storage is unavailable.'); const info = await FileSystem.getInfoAsync(uri); if (!info.exists) throw new Error('There is no previous restore point on this device.'); const json = await FileSystem.readAsStringAsync(uri, { encoding: FileSystem.EncodingType.UTF8 }); await restoreBackupJson(json, { createRestorePoint: false }); }

export async function restoreBackupJson(json: string, options: { createRestorePoint?: boolean } = {}) {
  let parsed: unknown; try { parsed = JSON.parse(json); } catch { throw new Error('The selected file is not valid JSON.'); }
  const backup = validateBackup(parsed); const sanitized = {} as Record<TableName, Record<string, string | number | null>[]>;
  for (const table of TABLES) sanitized[table] = backup.tables[table].map(row => cleanRow(table, row));
  for (const table of TABLES) { const keyName = table === 'settings' ? 'key' : 'id'; const seen = new Set<string>(); for (const row of sanitized[table]) { const key = String(row[keyName] ?? ''); if (seen.has(key)) throw new Error(`Backup contains duplicate ${keyName} values in ${table}.`); seen.add(key); } }
  const packages = new Set<string>(); for (const row of sanitized.usage_limits) { const key = String(row.package_name ?? ''); if (packages.has(key)) throw new Error('Backup contains duplicate screen-time limit apps.'); packages.add(key); }
  if (options.createRestorePoint !== false) await writeRestorePoint(await createBackupJson());
  const db: SQLite.SQLiteDatabase = await getDatabase();
  await db.withTransactionAsync(async () => { for (const table of [...TABLES].reverse()) await db.runAsync(`DELETE FROM ${table}`); for (const table of TABLES) for (const row of sanitized[table]) { const columns = COLUMNS[table].filter(column => column in row); const placeholders = columns.map(() => '?').join(', '); const values = columns.map(column => row[column]); await db.runAsync(`INSERT INTO ${table} (${columns.join(', ')}) VALUES (${placeholders})`, ...values); } });
}
