import { describe, expect, it } from 'vitest';
import { decrypt, encrypt, mask } from '../src/common/crypto';

describe('AES-256-GCM', () => {
  it('round-trips and uses a fresh IV each time', () => {
    const a = encrypt('xkeysib-secret-1234');
    const b = encrypt('xkeysib-secret-1234');
    expect(a).not.toEqual(b);
    expect(decrypt(a)).toBe('xkeysib-secret-1234');
  });
  it('detects tampering', () => {
    const c = encrypt('hello');
    const parts = c.split(':');
    parts[3] = Buffer.from('jello').toString('base64');
    expect(() => decrypt(parts.join(':'))).toThrow();
  });
  it('masks secrets', () => {
    expect(mask('xkeysib-abcdef-1234')).toBe('••••1234');
    expect(mask('short')).toBe('••••');
    expect(mask(null)).toBeNull();
  });
});
