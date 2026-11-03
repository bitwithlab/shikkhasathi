import { and, eq } from 'drizzle-orm';
import { contentDb } from '../client';
import { departments, institutionSubjects, institutions, programs, subjectMasters } from '../content/schema';

interface DeptSeed {
  short: string;
  name: string;
  nameBn: string;
  icon: string;
  programs: { name: string; semesters?: number }[];
}
interface InstSeed {
  slug: string;
  name: string;
  nameBn: string;
  short: string;
  type: 'public' | 'private' | 'college' | 'polytechnic';
  city: string;
  website?: string;
  aliases: string[];
  depts: DeptSeed[];
}

const CSE = (programs: DeptSeed['programs'] = [{ name: 'B.Sc. in CSE' }]): DeptSeed => ({ short: 'CSE', name: 'Computer Science and Engineering', nameBn: 'কম্পিউটার সায়েন্স অ্যান্ড ইঞ্জিনিয়ারিং', icon: 'code', programs });
const EEE: DeptSeed = { short: 'EEE', name: 'Electrical and Electronic Engineering', nameBn: 'তড়িৎ ও ইলেকট্রনিক প্রকৌশল', icon: 'zap', programs: [{ name: 'B.Sc. in EEE' }] };
const SWE: DeptSeed = { short: 'SWE', name: 'Software Engineering', nameBn: 'সফটওয়্যার ইঞ্জিনিয়ারিং', icon: 'file-code', programs: [{ name: 'B.Sc. in SWE' }] };
const BBA: DeptSeed = { short: 'BBA', name: 'Business Administration', nameBn: 'ব্যবসায় প্রশাসন', icon: 'bar-chart', programs: [{ name: 'BBA' }, { name: 'MBA', semesters: 4 }] };
const ENG: DeptSeed = { short: 'ENG', name: 'English', nameBn: 'ইংরেজি', icon: 'languages', programs: [{ name: 'BA (Hons) in English' }] };
const LAW: DeptSeed = { short: 'LAW', name: 'Law', nameBn: 'আইন', icon: 'gavel', programs: [{ name: 'LL.B. (Hons)' }] };
const CE: DeptSeed = { short: 'CE', name: 'Civil Engineering', nameBn: 'সিভিল ইঞ্জিনিয়ারিং', icon: 'drafting-compass', programs: [{ name: 'B.Sc. in Civil Engineering' }] };
const ECO: DeptSeed = { short: 'ECO', name: 'Economics', nameBn: 'অর্থনীতি', icon: 'line-chart', programs: [{ name: 'BSS in Economics' }] };

export const INSTITUTIONS: InstSeed[] = [
  {
    slug: 'metropolitan-university',
    name: 'Metropolitan University',
    nameBn: 'মেট্রোপলিটন ইউনিভার্সিটি',
    short: 'MU',
    type: 'private',
    city: 'সিলেট',
    website: 'https://www.metrouni.edu.bd',
    aliases: ['MU', 'Metro Uni', 'MU Sylhet', 'মেট্রোপলিটন'],
    depts: [CSE([{ name: 'B.Sc. in CSE (Day)' }, { name: 'B.Sc. in CSE (Evening)' }, { name: 'Diploma to B.Sc.' }]), EEE, SWE, BBA, ENG, LAW, CE],
  },
  {
    slug: 'north-south-university',
    name: 'North South University',
    nameBn: 'নর্থ সাউথ ইউনিভার্সিটি',
    short: 'NSU',
    type: 'private',
    city: 'ঢাকা',
    website: 'https://www.northsouth.edu',
    aliases: ['NSU', 'North South', 'নর্থ সাউথ'],
    depts: [{ ...CSE([{ name: 'B.Sc. in CSE', semesters: 12 }]), name: 'Electrical and Computer Engineering (CSE)' }, EEE, BBA, ECO, ENG, LAW],
  },
  {
    slug: 'brac-university',
    name: 'BRAC University',
    nameBn: 'ব্র্যাক ইউনিভার্সিটি',
    short: 'BRACU',
    type: 'private',
    city: 'ঢাকা',
    website: 'https://www.bracu.ac.bd',
    aliases: ['BRACU', 'BU', 'BRAC', 'ব্র্যাক'],
    depts: [CSE([{ name: 'B.Sc. in CSE', semesters: 12 }, { name: 'B.Sc. in CS', semesters: 12 }]), EEE, BBA, ECO, ENG],
  },
  { slug: 'aiub', name: 'American International University-Bangladesh', nameBn: 'আমেরিকান ইন্টারন্যাশনাল ইউনিভার্সিটি-বাংলাদেশ', short: 'AIUB', type: 'private', city: 'ঢাকা', aliases: ['AIUB'], depts: [CSE(), EEE, BBA] },
  { slug: 'uiu', name: 'United International University', nameBn: 'ইউনাইটেড ইন্টারন্যাশনাল ইউনিভার্সিটি', short: 'UIU', type: 'private', city: 'ঢাকা', aliases: ['UIU'], depts: [CSE(), EEE, BBA] },
  { slug: 'sust', name: 'Shahjalal University of Science and Technology', nameBn: 'শাহজালাল বিজ্ঞান ও প্রযুক্তি বিশ্ববিদ্যালয়', short: 'SUST', type: 'public', city: 'সিলেট', aliases: ['SUST', 'শাবিপ্রবি'], depts: [CSE(), EEE, ECO, CE] },
  { slug: 'university-of-dhaka', name: 'University of Dhaka', nameBn: 'ঢাকা বিশ্ববিদ্যালয়', short: 'DU', type: 'public', city: 'ঢাকা', aliases: ['DU', 'ঢাবি', 'Dhaka University'], depts: [CSE(), ECO, ENG, LAW] },
  { slug: 'buet', name: 'Bangladesh University of Engineering and Technology', nameBn: 'বাংলাদেশ প্রকৌশল বিশ্ববিদ্যালয়', short: 'BUET', type: 'public', city: 'ঢাকা', aliases: ['BUET', 'বুয়েট'], depts: [CSE(), EEE, CE] },
  { slug: 'daffodil-international-university', name: 'Daffodil International University', nameBn: 'ড্যাফোডিল ইন্টারন্যাশনাল ইউনিভার্সিটি', short: 'DIU', type: 'private', city: 'ঢাকা', aliases: ['DIU', 'Daffodil'], depts: [CSE(), SWE, BBA] },
  { slug: 'east-west-university', name: 'East West University', nameBn: 'ইস্ট ওয়েস্ট ইউনিভার্সিটি', short: 'EWU', type: 'private', city: 'ঢাকা', aliases: ['EWU', 'East West'], depts: [CSE(), EEE, BBA] },
  { slug: 'sylhet-polytechnic-institute', name: 'Sylhet Polytechnic Institute', nameBn: 'সিলেট পলিটেকনিক ইনস্টিটিউট', short: 'SPI', type: 'polytechnic', city: 'সিলেট', aliases: ['SPI'], depts: [{ short: 'CMT', name: 'Computer Technology', nameBn: 'কম্পিউটার টেকনোলজি', icon: 'monitor', programs: [{ name: 'Diploma in Engineering (Computer)' }] }] },
  { slug: 'mc-college', name: 'Murari Chand College', nameBn: 'মুরারিচাঁদ কলেজ', short: 'MC College', type: 'college', city: 'সিলেট', aliases: ['MC College', 'এমসি কলেজ'], depts: [ECO, ENG] },
];

export const SUBJECTS: { slug: string; name: string; nameBn: string; aliases: string[]; category: string }[] = [
  { slug: 'structured-programming', name: 'Structured Programming (C)', nameBn: 'স্ট্রাকচার্ড প্রোগ্রামিং', aliases: ['SPL', 'C Programming', 'Programming Language I'], category: 'cse' },
  { slug: 'oop', name: 'Object Oriented Programming', nameBn: 'অবজেক্ট ওরিয়েন্টেড প্রোগ্রামিং', aliases: ['OOP', 'Java', 'OOP II'], category: 'cse' },
  { slug: 'data-structures', name: 'Data Structures', nameBn: 'ডাটা স্ট্রাকচার', aliases: ['DS', 'DSA', 'Data Structure', 'ডাটা স্ট্রাকচার্স'], category: 'cse' },
  { slug: 'algorithms', name: 'Design and Analysis of Algorithms', nameBn: 'অ্যালগরিদম', aliases: ['Algorithms', 'DAA', 'Algorithm Analysis'], category: 'cse' },
  { slug: 'discrete-mathematics', name: 'Discrete Mathematics', nameBn: 'ডিসক্রিট ম্যাথ', aliases: ['Discrete Math', 'DM'], category: 'math' },
  { slug: 'database-management-systems', name: 'Database Management Systems', nameBn: 'ডাটাবেস ম্যানেজমেন্ট সিস্টেম', aliases: ['DBMS', 'Database'], category: 'cse' },
  { slug: 'digital-logic-design', name: 'Digital Logic Design', nameBn: 'ডিজিটাল লজিক ডিজাইন', aliases: ['DLD', 'Digital Logic', 'Digital Circuits'], category: 'cse' },
  { slug: 'computer-networks', name: 'Computer Networks', nameBn: 'কম্পিউটার নেটওয়ার্ক', aliases: ['Networking', 'CN', 'Data Communication'], category: 'cse' },
  { slug: 'operating-systems', name: 'Operating Systems', nameBn: 'অপারেটিং সিস্টেম', aliases: ['OS'], category: 'cse' },
  { slug: 'computer-architecture', name: 'Computer Architecture', nameBn: 'কম্পিউটার আর্কিটেকচার', aliases: ['COA', 'Computer Organization'], category: 'cse' },
  { slug: 'theory-of-computation', name: 'Theory of Computation', nameBn: 'থিওরি অব কম্পিউটেশন', aliases: ['TOC', 'Automata'], category: 'cse' },
  { slug: 'linear-algebra', name: 'Linear Algebra', nameBn: 'লিনিয়ার অ্যালজেব্রা', aliases: ['Matrices', 'Linear Algebra & Differential Equations'], category: 'math' },
  { slug: 'calculus', name: 'Calculus', nameBn: 'ক্যালকুলাস', aliases: ['Differential Calculus', 'Integral Calculus', 'Math I'], category: 'math' },
  { slug: 'statistics', name: 'Probability and Statistics', nameBn: 'সম্ভাবনা ও পরিসংখ্যান', aliases: ['Statistics', 'Probability'], category: 'math' },
  { slug: 'electrical-circuits', name: 'Electrical Circuits', nameBn: 'ইলেকট্রিক্যাল সার্কিট', aliases: ['Circuit Analysis', 'EC I'], category: 'eee' },
  { slug: 'electronics', name: 'Electronic Devices and Circuits', nameBn: 'ইলেকট্রনিক্স', aliases: ['Electronics I', 'EDC'], category: 'eee' },
  { slug: 'microeconomics', name: 'Microeconomics', nameBn: 'ব্যষ্টিক অর্থনীতি', aliases: ['Micro', 'Principles of Microeconomics'], category: 'business' },
  { slug: 'financial-accounting', name: 'Financial Accounting', nameBn: 'আর্থিক হিসাববিজ্ঞান', aliases: ['Accounting', 'ACT'], category: 'business' },
  { slug: 'english-composition', name: 'English Composition', nameBn: 'ইংলিশ কম্পোজিশন', aliases: ['English I', 'Writing'], category: 'humanities' },
  { slug: 'software-engineering', name: 'Software Engineering', nameBn: 'সফটওয়্যার ইঞ্জিনিয়ারিং', aliases: ['SE', 'SDLC'], category: 'cse' },
];

/** [institution slug, dept short, semester, subject slug, course code] */
export const OFFERINGS: [string, string, number, string, string][] = [
  ['metropolitan-university', 'CSE', 1, 'structured-programming', 'CSE-1101'],
  ['metropolitan-university', 'CSE', 1, 'calculus', 'MAT-1101'],
  ['metropolitan-university', 'CSE', 1, 'english-composition', 'ENG-1101'],
  ['metropolitan-university', 'CSE', 2, 'oop', 'CSE-1201'],
  ['metropolitan-university', 'CSE', 2, 'discrete-mathematics', 'CSE-1203'],
  ['metropolitan-university', 'CSE', 3, 'digital-logic-design', 'CSE-2101'],
  ['metropolitan-university', 'CSE', 3, 'linear-algebra', 'MAT-2101'],
  ['metropolitan-university', 'CSE', 4, 'data-structures', 'CSE-2203'],
  ['metropolitan-university', 'CSE', 4, 'oop', 'CSE-2205'],
  ['metropolitan-university', 'CSE', 5, 'algorithms', 'CSE-2201'],
  ['metropolitan-university', 'CSE', 5, 'database-management-systems', 'CSE-3101'],
  ['metropolitan-university', 'CSE', 5, 'statistics', 'STA-3103'],
  ['metropolitan-university', 'CSE', 6, 'operating-systems', 'CSE-3201'],
  ['metropolitan-university', 'CSE', 6, 'computer-networks', 'CSE-3203'],
  ['metropolitan-university', 'CSE', 7, 'theory-of-computation', 'CSE-4101'],
  ['metropolitan-university', 'CSE', 7, 'software-engineering', 'CSE-4103'],
  ['metropolitan-university', 'EEE', 2, 'electrical-circuits', 'EEE-1201'],
  ['metropolitan-university', 'EEE', 3, 'electronics', 'EEE-2103'],
  ['metropolitan-university', 'BBA', 1, 'financial-accounting', 'ACT-1101'],
  ['metropolitan-university', 'BBA', 2, 'microeconomics', 'ECO-1201'],
  ['north-south-university', 'CSE', 1, 'structured-programming', 'CSE115'],
  ['north-south-university', 'CSE', 2, 'discrete-mathematics', 'CSE173'],
  ['north-south-university', 'CSE', 3, 'oop', 'CSE215'],
  ['north-south-university', 'CSE', 4, 'data-structures', 'CSE225'],
  ['north-south-university', 'CSE', 5, 'digital-logic-design', 'CSE231'],
  ['north-south-university', 'CSE', 6, 'database-management-systems', 'CSE311'],
  ['north-south-university', 'CSE', 7, 'algorithms', 'CSE373'],
  ['north-south-university', 'CSE', 8, 'operating-systems', 'CSE323'],
  ['north-south-university', 'EEE', 2, 'electrical-circuits', 'EEE141'],
  ['north-south-university', 'ECO', 1, 'microeconomics', 'ECO101'],
  ['north-south-university', 'BBA', 1, 'financial-accounting', 'ACT201'],
  ['brac-university', 'CSE', 1, 'structured-programming', 'CSE110'],
  ['brac-university', 'CSE', 2, 'oop', 'CSE111'],
  ['brac-university', 'CSE', 3, 'discrete-mathematics', 'CSE230'],
  ['brac-university', 'CSE', 4, 'data-structures', 'CSE220'],
  ['brac-university', 'CSE', 5, 'algorithms', 'CSE221'],
  ['brac-university', 'CSE', 5, 'digital-logic-design', 'CSE260'],
  ['brac-university', 'CSE', 6, 'database-management-systems', 'CSE370'],
  ['brac-university', 'CSE', 7, 'operating-systems', 'CSE321'],
  ['brac-university', 'CSE', 8, 'computer-networks', 'CSE421'],
  ['brac-university', 'ECO', 1, 'microeconomics', 'ECO101'],
  ['aiub', 'CSE', 4, 'data-structures', 'CSC2106'],
  ['aiub', 'EEE', 3, 'digital-logic-design', 'EEE-2103'],
  ['uiu', 'CSE', 4, 'data-structures', 'CSE 2215'],
  ['sust', 'CSE', 3, 'data-structures', 'CSE 233'],
  ['university-of-dhaka', 'CSE', 3, 'data-structures', 'CSE-2101'],
  ['buet', 'CSE', 3, 'data-structures', 'CSE 203'],
  ['daffodil-international-university', 'CSE', 3, 'data-structures', 'CSE134'],
  ['east-west-university', 'CSE', 4, 'data-structures', 'CSE207'],
];

export interface CatalogIds {
  inst: Record<string, string>;
  dept: Record<string, string>; // `${instSlug}:${short}`
  prog: Record<string, string>; // `${instSlug}:${short}:${index}`
  subj: Record<string, string>;
  instSubj: Record<string, string>; // `${instSlug}:${code}`
}

export async function seedCatalog(): Promise<CatalogIds> {
  const ids: CatalogIds = { inst: {}, dept: {}, prog: {}, subj: {}, instSubj: {} };
  for (const i of INSTITUTIONS) {
    const [row] = await contentDb
      .insert(institutions)
      .values({ slug: i.slug, name: i.name, nameBn: i.nameBn, shortName: i.short, type: i.type, city: i.city, website: i.website ?? null, aliases: i.aliases, status: 'approved' })
      .onConflictDoUpdate({ target: institutions.slug, set: { name: i.name, nameBn: i.nameBn, aliases: i.aliases, city: i.city } })
      .returning({ id: institutions.id });
    ids.inst[i.slug] = row!.id;
    let pos = 0;
    for (const d of i.depts) {
      const [drow] = await contentDb
        .insert(departments)
        .values({ institutionId: row!.id, shortName: d.short, name: d.name, nameBn: d.nameBn, icon: d.icon, position: pos++ })
        .onConflictDoUpdate({ target: [departments.institutionId, departments.shortName], set: { name: d.name, nameBn: d.nameBn, icon: d.icon } })
        .returning({ id: departments.id });
      ids.dept[`${i.slug}:${d.short}`] = drow!.id;
      for (const [pi, p] of d.programs.entries()) {
        const [existing] = await contentDb.select({ id: programs.id }).from(programs).where(and(eq(programs.departmentId, drow!.id), eq(programs.name, p.name)));
        const pid = existing?.id ?? (await contentDb.insert(programs).values({ departmentId: drow!.id, name: p.name, semesters: p.semesters ?? 8 }).returning({ id: programs.id }))[0]!.id;
        ids.prog[`${i.slug}:${d.short}:${pi}`] = pid;
      }
    }
  }
  for (const s of SUBJECTS) {
    const [row] = await contentDb
      .insert(subjectMasters)
      .values(s)
      .onConflictDoUpdate({ target: subjectMasters.slug, set: { name: s.name, nameBn: s.nameBn, aliases: s.aliases, category: s.category } })
      .returning({ id: subjectMasters.id });
    ids.subj[s.slug] = row!.id;
  }
  for (const [inst, dept, sem, subj, code] of OFFERINGS) {
    const institutionId = ids.inst[inst]!;
    const [existing] = await contentDb.select({ id: institutionSubjects.id }).from(institutionSubjects).where(and(eq(institutionSubjects.institutionId, institutionId), eq(institutionSubjects.courseCode, code)));
    const id =
      existing?.id ??
      (await contentDb.insert(institutionSubjects).values({ institutionId, departmentId: ids.dept[`${inst}:${dept}`]!, semester: sem, subjectMasterId: ids.subj[subj]!, courseCode: code }).returning({ id: institutionSubjects.id }))[0]!.id;
    ids.instSubj[`${inst}:${code}`] = id;
  }
  return ids;
}
