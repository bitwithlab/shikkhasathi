import { create } from 'zustand';
import bn from './bn.json';

export type Locale = 'bn' | 'en';
type Dict = Record<string, string>;
// Bangla ships in the main bundle (default); English is a lazy chunk loaded on demand.
const dicts: Record<Locale, Dict | undefined> = { bn: bn as Dict, en: undefined };
let enLoading: Promise<void> | null = null;
const pendingEn: Dict[] = [];
function loadEn() {
  enLoading ??= import('./en.json').then((m) => {
    dicts.en = Object.assign({}, m.default as Dict, ...pendingEn);
  });
  return enLoading;
}

/**
 * Feature dictionaries: each feature folder has its own i18n/{bn,en}.json and calls this at module load,
 * so its copy ships inside that feature's lazy chunk instead of the main bundle.
 */
export function registerMessages(m: { bn: Dict; en: Dict }) {
  Object.assign(dicts.bn!, m.bn);
  if (dicts.en) Object.assign(dicts.en, m.en);
  else pendingEn.push(m.en);
}

function initialLocale(): Locale {
  try {
    return localStorage.getItem('ss-locale') === 'en' ? 'en' : 'bn';
  } catch {
    return 'bn';
  }
}

interface I18nState {
  locale: Locale;
  setLocale: (l: Locale) => void;
}

export const useI18nStore = create<I18nState>((set) => ({
  locale: 'bn',
  setLocale: (locale) => {
    try {
      localStorage.setItem('ss-locale', locale);
    } catch {
      /* private mode */
    }
    document.documentElement.lang = locale;
    if (locale === 'en' && !dicts.en) void loadEn().then(() => set({ locale }));
    else set({ locale });
  },
}));
if (initialLocale() === 'en') useI18nStore.getState().setLocale('en');

/** Translate `key`, interpolating `{name}` placeholders. Missing keys fall back to bn, then the key itself. */
export function translate(locale: Locale, key: string, vars?: Record<string, string | number>): string {
  let s = dicts[locale]?.[key] ?? dicts.bn![key] ?? key;
  if (vars) for (const [k, v] of Object.entries(vars)) s = s.replaceAll(`{${k}}`, typeof v === 'number' ? formatNumber(v, locale) : v);
  return s;
}

export function useT() {
  const locale = useI18nStore((s) => s.locale);
  return Object.assign((key: string, vars?: Record<string, string | number>) => translate(locale, key, vars), { locale });
}

export const t = (key: string, vars?: Record<string, string | number>) => translate(useI18nStore.getState().locale, key, vars);

// ---------------------------------------------------------------- formatting (Asia/Dhaka, Bangla digits)

const BN_DIGITS = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
export const toBnDigits = (s: string | number) => String(s).replace(/\d/g, (d) => BN_DIGITS[Number(d)]!);
export const localDigits = (s: string | number, locale: Locale) => (locale === 'bn' ? toBnDigits(s) : String(s));

export function formatNumber(n: number, locale: Locale) {
  const s = new Intl.NumberFormat('en-IN').format(n);
  return localDigits(s, locale);
}

/** Paisa → "৳২৯৯" (bn) / "৳299" (en). */
export function formatTaka(paisa: number, locale: Locale) {
  const taka = paisa / 100;
  const s = new Intl.NumberFormat('en-IN', { maximumFractionDigits: taka % 1 === 0 ? 0 : 2 }).format(taka);
  return `৳${localDigits(s, locale)}`;
}

const TZ = 'Asia/Dhaka';
export function formatDate(iso: string | Date, locale: Locale, opts: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short' }) {
  return new Intl.DateTimeFormat(locale === 'bn' ? 'bn-BD' : 'en-GB', { timeZone: TZ, ...opts }).format(new Date(iso));
}
export function formatTime(iso: string | Date, locale: Locale) {
  return new Intl.DateTimeFormat(locale === 'bn' ? 'bn-BD' : 'en-GB', { timeZone: TZ, hour: 'numeric', minute: '2-digit', hour12: true }).format(new Date(iso));
}

/** Calendar-day difference in Asia/Dhaka (0 = today, 1 = tomorrow, -1 = yesterday). */
export function dayDiff(iso: string | Date, now: Date = new Date()) {
  const key = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);
  const a = Date.parse(key(new Date(iso)));
  const b = Date.parse(key(now));
  return Math.round((a - b) / 86_400_000);
}

export function relativeTime(iso: string | Date, locale: Locale, now: Date = new Date()) {
  const sec = Math.round((now.getTime() - new Date(iso).getTime()) / 1000);
  const fmt = (n: number, unitKey: string) => translate(locale, `time.${unitKey}`, { n });
  if (sec < 45) return translate(locale, 'time.justNow');
  if (sec < 3600) return fmt(Math.round(sec / 60), 'minutesAgo');
  if (sec < 86400) return fmt(Math.round(sec / 3600), 'hoursAgo');
  if (sec < 7 * 86400) return fmt(Math.round(sec / 86400), 'daysAgo');
  return formatDate(iso, locale);
}

const BN_ORD: Record<number, string> = { 1: '১ম', 2: '২য়', 3: '৩য়', 4: '৪র্থ', 5: '৫ম', 6: '৬ষ্ঠ', 7: '৭ম', 8: '৮ম', 9: '৯ম', 10: '১০ম', 11: '১১তম', 12: '১২তম' };
/** 5 → "৫ম" (bn) / "5th" (en). */
export function ordinal(n: number, locale: Locale) {
  if (locale === 'bn') return BN_ORD[n] ?? `${toBnDigits(n)}তম`;
  const s = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]!);
}

/** Resolve once the dictionary for `l` is loaded (English is lazy). */
export function ensureLocale(l: Locale): Promise<void> {
  return l === 'en' && !dicts.en ? loadEn() : Promise.resolve();
}
