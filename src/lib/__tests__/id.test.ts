import { createLocalId } from '../id';

describe('createLocalId', () => {
  it('includes the requested prefix and URL-safe characters', () => {
    expect(createLocalId('focus')).toMatch(/^focus_[a-z0-9]+_[a-z0-9]+$/);
  });

  it('creates distinct identifiers for separate records', () => {
    const first = createLocalId('task');
    const second = createLocalId('task');
    expect(first).not.toBe(second);
  });
});
