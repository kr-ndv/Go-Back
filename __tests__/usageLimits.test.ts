import { evaluateUsageLimits } from '@/lib/usageLimitLogic';

describe('usage limits', () => {
  const limit = { id: '1', package_name: 'app.test', app_name: 'Test', limit_minutes: 60, warning_percent: 80, enabled: 1 };
  test('warns once near a limit', () => {
    const first = evaluateUsageLimits([{ packageName: 'app.test', appName: 'Test', foregroundTimeMs: 50 * 60000 }], [limit], {});
    expect(first.events[0]?.kind).toBe('warning');
    const second = evaluateUsageLimits([{ packageName: 'app.test', appName: 'Test', foregroundTimeMs: 55 * 60000 }], [limit], first.nextSent);
    expect(second.events).toHaveLength(0);
  });
  test('can escalate from warning to exceeded', () => {
    const result = evaluateUsageLimits([{ packageName: 'app.test', appName: 'Test', foregroundTimeMs: 61 * 60000 }], [limit], { 'app.test': ['warning'] });
    expect(result.events[0]?.kind).toBe('exceeded');
  });
});
