/** Bangla / English / transliteration / abbreviation expansion for search ("ডাটা স্ট্রাকচার" = "data structure" = "DS"). */
const GROUPS: string[][] = [
  ['data structures', 'data structure', 'ds', 'dsa', 'ডাটা স্ট্রাকচার', 'ডেটা স্ট্রাকচার', 'ডাটা স্ট্রাকচার্স'],
  ['algorithms', 'algorithm', 'daa', 'অ্যালগরিদম', 'এলগরিদম'],
  ['database management systems', 'database', 'dbms', 'ডাটাবেস', 'ডেটাবেস'],
  ['object oriented programming', 'oop', 'java', 'অবজেক্ট ওরিয়েন্টেড'],
  ['digital logic design', 'dld', 'digital logic', 'ডিজিটাল লজিক'],
  ['structured programming', 'spl', 'c programming', 'programming language', 'সি প্রোগ্রামিং'],
  ['discrete mathematics', 'discrete math', 'ডিসক্রিট ম্যাথ'],
  ['computer networks', 'networking', 'cn', 'নেটওয়ার্ক'],
  ['operating systems', 'os', 'অপারেটিং সিস্টেম'],
  ['linear algebra', 'matrix', 'লিনিয়ার অ্যালজেব্রা'],
  ['electrical circuits', 'circuit', 'সার্কিট'],
  ['microeconomics', 'micro', 'ইকোনমিক্স', 'অর্থনীতি'],
  ['midterm', 'mid', 'মিডটার্ম', 'মিড'],
  ['final', 'ফাইনাল'],
  ['quiz', 'কুইজ'],
  ['metropolitan university', 'mu', 'মেট্রোপলিটন'],
  ['north south university', 'nsu', 'নর্থ সাউথ'],
  ['brac university', 'bracu', 'brac', 'ব্র্যাক'],
];

export const EXAM_WORDS: Record<string, 'midterm' | 'final' | 'quiz'> = { midterm: 'midterm', mid: 'midterm', 'মিডটার্ম': 'midterm', 'মিড': 'midterm', final: 'final', 'ফাইনাল': 'final', quiz: 'quiz', 'কুইজ': 'quiz' };

const norm = (s: string) => s.toLowerCase().normalize('NFC').replace(/\s+/g, ' ').trim();

/** Course codes: "cse 2203", "CSE2203", "cse-2203" → "CSE2203". */
export const normalizeCode = (s: string) => s.toUpperCase().replace(/[^A-Z0-9]/g, '');
export const looksLikeCode = (s: string) => /^[a-z]{2,4}[\s-]?\d{3,4}$/i.test(s.trim());

export interface ExpandedQuery {
  terms: string[]; // all text variants to match (lowercased)
  code: string | null; // normalized course code if the query is one
  examType: 'midterm' | 'final' | 'quiz' | null;
  interpreted: { term: string; meaning: string }[];
}

export function expandQuery(q: string): ExpandedQuery {
  const raw = norm(q);
  const out: ExpandedQuery = { terms: [], code: null, examType: null, interpreted: [] };
  if (!raw) return out;
  const codeMatch = raw.match(/\b([a-z]{2,4})[\s-]?(\d{3,4})\b/i);
  if (codeMatch) out.code = normalizeCode(codeMatch[0]);
  let rest = raw;
  for (const [w, e] of Object.entries(EXAM_WORDS)) {
    const re = new RegExp(`(^|\\s)${w}(?=\\s|$)`, 'i');
    if (re.test(rest)) {
      out.examType = e;
      rest = rest.replace(re, ' ').trim();
    }
  }
  if (out.code) rest = rest.replace(codeMatch![0].toLowerCase(), ' ').trim();
  const add = (t: string) => {
    if (t && !out.terms.includes(t)) out.terms.push(t);
  };
  if (rest) add(rest);
  for (const g of GROUPS) {
    const hit = g.find((v) => rest === v || (v.length > 3 && rest.includes(v)) || rest.split(' ').includes(v));
    if (hit) {
      g.forEach(add);
      if (hit !== g[0] && !EXAM_WORDS[hit]) out.interpreted.push({ term: hit, meaning: g[0]!.replace(/\b\w/g, (c) => c.toUpperCase()) });
    }
  }
  return out;
}
