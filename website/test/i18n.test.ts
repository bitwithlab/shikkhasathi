import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { dayDiff, ensureLocale, formatTaka, ordinal, relativeTime, toBnDigits, translate } from '@/lib/i18n';

function dictDirs(dir: string, out: string[] = []) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) dictDirs(p, out);
    else if (f === 'bn.json') out.push(dir);
  }
  return out;
}

describe('i18n dictionaries', () => {
  const dirs = dictDirs(resolve(__dirname, '../src'));
  it('finds core + feature dictionaries', () => expect(dirs.length).toBeGreaterThanOrEqual(5));
  for (const d of dirs) {
    it(`bn/en have identical keys and placeholders: ${d.split('src/')[1]}`, () => {
      const bn = JSON.parse(readFileSync(join(d, 'bn.json'), 'utf8')) as Record<string, string>;
      const en = JSON.parse(readFileSync(join(d, 'en.json'), 'utf8')) as Record<string, string>;
      expect(Object.keys(bn).sort()).toEqual(Object.keys(en).sort());
      for (const k of Object.keys(bn)) {
        const ph = (s: string) => (s.match(/\{\w+\}/g) ?? []).sort();
        expect(ph(bn[k]!), k).toEqual(ph(en[k]!));
        expect(bn[k]!.trim(), k).not.toBe('');
      }
    });
  }
  it('never uses guarantee language in Bangla copy', () => {
    for (const d of dirs) {
      const bn = readFileSync(join(d, 'bn.json'), 'utf8');
      expect(bn).not.toMatch(/পরীক্ষায় আসবেই|এটাই পরীক্ষায় আসবে|ফুল মার্কস নিশ্চিত/);
    }
  });
});

describe('formatting', () => {
  it('Bangla digits and taka', () => {
    expect(toBnDigits('CSE-2203')).toBe('CSE-২২০৩');
    expect(formatTaka(29900, 'bn')).toBe('৳২৯৯');
    expect(formatTaka(125000, 'en')).toBe('৳1,250');
    expect(formatTaka(12550, 'en')).toBe('৳125.5');
  });
  it('ordinals', () => {
    expect(ordinal(5, 'bn')).toBe('৫ম');
    expect(ordinal(2, 'bn')).toBe('২য়');
    expect(ordinal(4, 'bn')).toBe('৪র্থ');
    expect(ordinal(3, 'en')).toBe('3rd');
    expect(ordinal(11, 'en')).toBe('11th');
  });
  it('day difference in Asia/Dhaka', () => {
    const now = new Date('2026-11-03T17:30:00Z'); // 23:30 Dhaka
    expect(dayDiff('2026-11-03T18:30:00Z', now)).toBe(1); // 00:30 next day in Dhaka
    expect(dayDiff('2026-11-03T10:00:00Z', now)).toBe(0);
    expect(dayDiff('2026-11-02T10:00:00Z', now)).toBe(-1);
  });
  it('relative time + interpolation', async () => {
    await ensureLocale('en');
    const now = new Date('2026-11-03T12:00:00Z');
    expect(relativeTime('2026-11-03T11:58:00Z', 'bn', now)).toBe('২ মিনিট আগে');
    expect(translate('en', 'home.greeting', { name: 'Rahim' })).toBe('Assalamu alaikum, Rahim');
    expect(translate('bn', 'missing.key')).toBe('missing.key');
  });
});
