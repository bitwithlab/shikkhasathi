/** Price helpers. The API stores integer paisa (৳299 = 29900); the UI edits taka. */

const BN = '০১২৩৪৫৬৭৮৯';

/** Bangla digits → ASCII, so "২৯৯" and "299" both parse. */
export function normalizeDigits(s: string) {
  return s.replace(/[০-৯]/g, (d) => String(BN.indexOf(d)));
}

/** "299", "২৯৯", "1,299.50" → paisa. Empty → null; invalid/negative → NaN. */
export function takaToPaisa(input: string): number | null {
  const s = normalizeDigits(input).replace(/[,\s৳]/g, '');
  if (s === '') return null;
  if (!/^\d+(\.\d{0,2})?$/.test(s)) return Number.NaN;
  const [whole, frac = ''] = s.split('.');
  return Number(whole) * 100 + Number((frac + '00').slice(0, 2));
}

/** 29900 → "299", 29950 → "299.50" (editable value, ASCII digits). */
export function paisaToTaka(paisa: number | null | undefined): string {
  if (paisa == null) return '';
  const whole = Math.floor(paisa / 100);
  const frac = paisa % 100;
  return frac ? `${whole}.${String(frac).padStart(2, '0')}` : String(whole);
}

/** Whole-percent discount, 0 when compare-at is missing or not higher. */
export function percentOff(price: number, compareAt: number | null | undefined) {
  if (!compareAt || compareAt <= price) return 0;
  return Math.round((1 - price / compareAt) * 100);
}

/** Seconds → "1:05:09" / "12:04". */
export function formatDuration(sec: number) {
  const s = Math.max(0, Math.round(sec));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  const mm = h ? String(m).padStart(2, '0') : String(m);
  return `${h ? `${h}:` : ''}${mm}:${String(r).padStart(2, '0')}`;
}

/** "12:30" / "১২:৩০" / "45" (minutes) → seconds; invalid → null. */
export function parseDuration(input: string): number | null {
  const s = normalizeDigits(input).trim();
  if (!s) return 0;
  if (/^\d+$/.test(s)) return Number(s) * 60;
  const m = s.match(/^(?:(\d+):)?(\d{1,2}):(\d{2})$/);
  if (!m) return null;
  return Number(m[1] ?? 0) * 3600 + Number(m[2]) * 60 + Number(m[3]);
}
