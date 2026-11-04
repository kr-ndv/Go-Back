jest.mock('expo-sqlite', () => ({
  openDatabaseAsync: jest.fn(),
}));

jest.mock('@/lib/database', () => ({
  getDatabase: jest.fn(),
}));

jest.mock('expo-file-system/legacy', () => ({
  documentDirectory: '/mock/docs/',
  writeAsStringAsync: jest.fn(),
  readAsStringAsync: jest.fn(),
  getInfoAsync: jest.fn(),
  EncodingType: { UTF8: 'utf8' },
}));

import { computeBackupChecksum } from '@/lib/backupV5';

describe('backup checksum', () => {
  it('computes a consistent 8-character hex checksum', () => {
    const data = { notes: [{ id: '1', title: 'Test', body: 'Body' }] };
    const hash1 = computeBackupChecksum(data);
    const hash2 = computeBackupChecksum(data);
    expect(hash1).toHaveLength(8);
    expect(hash1).toBe(hash2);
  });

  it('detects changes in tables content', () => {
    const dataA = { notes: [{ id: '1', title: 'Test', body: 'Body' }] };
    const dataB = { notes: [{ id: '1', title: 'Test Modified', body: 'Body' }] };
    expect(computeBackupChecksum(dataA)).not.toBe(computeBackupChecksum(dataB));
  });
});
