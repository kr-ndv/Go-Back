import * as SQLite from 'expo-sqlite';
import { getDatabase } from '@/lib/database';

const TABLES = ['tasks', 'notes', 'subjects', 'focus_sessions', 'exams', 'habits', 'habit_logs', 'settings'] as const;
type TableName = typeof TABLES[number];

const COLUMNS: Record<TableName, string[]> = {
  tasks: ['id', 'title', 'completed', 'created_at'],
  notes: ['id', 'title', 'body', 'updated_at'],
  subjects: ['id', 'name', 'chapters_json', 'updated_at'],
  focus_sessions: ['id', 'started_at', 'duration_seconds', 'completed'],
  exams: ['id', 'title', 'exam_date', 'details'],
  habits: ['id', 'title', 'created_at', 'archived'],
  habit_logs: ['id', 'habit_id', 'log_date', 'completed'],
  settings: ['key', 'value'],
};

type Backup = {
  app: 'Go Back';
  schemaVersion: 1;
  exportedAt: string;
  tables: Record<TableName, Record<string, unknown>[]>;
};

export async function createBackupJson(): Promise<string> {
  const db = await getDatabase();
  const tables = {} as Backup['tables'];
  for (const table of TABLES) {
    tables[table] = await db.getAllAsync<Record<string, unknown>>(`SELECT * FROM ${table}`);
  }
  const backup: Backup = { app: 'Go Back', schemaVersion: 1, exportedAt: new Date().toISOString(), tables };
  return JSON.stringify(backup, null, 2);
}

function validateBackup(value: unknown): Backup {
  if (!value || typeof value !== 'object') throw new Error('The selected file is not a valid Go Back backup.');
  const backup = value as Partial<Backup>;
  if (backup.app !== 'Go Back' || backup.schemaVersion !== 1 || !backup.tables || typeof backup.tables !== 'object') {
    throw new Error('This backup is not compatible with this version of Go Back.');
  }
  for (const table of TABLES) {
    if (!Array.isArray(backup.tables[table])) throw new Error(`Backup is missing the ${table} data section.`);
    if (backup.tables[table]!.length > 50000) throw new Error('This backup contains too many records to restore safely.');
  }
  return backup as Backup;
}

function cleanRow(table: TableName, value: unknown): Record<string, string | number> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`Invalid record in ${table}.`);
  const source = value as Record<string, unknown>;
  const clean: Record<string, string | number> = {};
  for (const column of COLUMNS[table]) {
    if (!(column in source)) continue;
    const cell = source[column];
    if (typeof cell === 'string' || (typeof cell === 'number' && Number.isFinite(cell))) clean[column] = cell;
    else throw new Error(`Invalid ${column} value in ${table}.`);
  }
  const required = table === 'settings' ? ['key', 'value'] : table === 'tasks' ? ['id', 'title', 'created_at'] :
    table === 'notes' ? ['id', 'title', 'body', 'updated_at'] : table === 'subjects' ? ['id', 'name', 'chapters_json', 'updated_at'] :
    table === 'focus_sessions' ? ['id', 'started_at', 'duration_seconds', 'completed'] : table === 'exams' ? ['id', 'title', 'exam_date', 'details'] :
    table === 'habits' ? ['id', 'title', 'created_at', 'archived'] : ['id', 'habit_id', 'log_date', 'completed'];
  if (required.some(key => !(key in clean))) throw new Error(`A record in ${table} is missing required fields.`);
  return clean;
}

export async function restoreBackupJson(json: string): Promise<void> {
  let parsed: unknown;
  try { parsed = JSON.parse(json); } catch { throw new Error('The selected file is not valid JSON.'); }
  const backup = validateBackup(parsed);
  const sanitized = {} as Record<TableName, Record<string, string | number>[]>;
  for (const table of TABLES) sanitized[table] = backup.tables[table].map(row => cleanRow(table, row));

  const db: SQLite.SQLiteDatabase = await getDatabase();
  await db.withTransactionAsync(async () => {
    for (const table of [...TABLES].reverse()) await db.runAsync(`DELETE FROM ${table}`);
    for (const table of TABLES) {
      for (const row of sanitized[table]) {
        const columns = COLUMNS[table].filter(column => column in row);
        const placeholders = columns.map(() => '?').join(', ');
        const values = columns.map(column => row[column]);
        await db.runAsync(`INSERT INTO ${table} (${columns.join(', ')}) VALUES (${placeholders})`, ...values);
      }
    }
  });
}
