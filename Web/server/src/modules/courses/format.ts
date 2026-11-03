const BN = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
const ORD: Record<number, string> = { 1: '১ম', 2: '২য়', 3: '৩য়', 4: '৪র্থ', 5: '৫ম', 6: '৬ষ্ঠ', 7: '৭ম', 8: '৮ম', 9: '৯ম', 10: '১০ম' };
export const bnDigits = (n: number | string) => String(n).replace(/\d/g, (d) => BN[Number(d)]!);
export const ordinalBn = (n: number) => ORD[n] ?? `${bnDigits(n)}তম`;
