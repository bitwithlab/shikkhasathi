import { createContext, useContext } from 'react';
import type { SellerCourse } from '@shikkhasathi/shared';
import type { LessonDraft, Tree } from '../../lib/curriculum';
import type { Basics, Pricing, TargetDraft } from './model';
import type { Saver } from './useSaver';
import { type DocInfo } from '../../parts';

export type Step = 1 | 2 | 3 | 4 | 5;
type Set<T> = (next: T | ((prev: T) => T)) => void;

export interface Wizard {
  id: string;
  /** last server copy (status, review note, revision flag, derived labels) */
  server: SellerCourse;
  /** adopt a fresh server copy (e.g. after submit) */
  accept: (c: SellerCourse) => void;
  basics: Basics;
  setBasics: Set<Basics>;
  pricing: Pricing;
  setPricing: Set<Pricing>;
  targets: TargetDraft[];
  setTargets: Set<TargetDraft[]>;
  tree: Tree;
  setTree: Set<Tree>;
  /** patch a lesson by key from anywhere (uploads finish after the editor may have closed) */
  patchLesson: (key: string, patch: Partial<LessonDraft>) => void;
  documents: DocInfo[];
  addDocument: (d: DocInfo) => void;
  savers: { basics: Saver; pricing: Saver; targets: Saver; curriculum: Saver };
  flushAll: () => Promise<void>;
  goto: (s: Step) => void;
  verified: boolean;
}

export const WizardContext = createContext<Wizard | null>(null);

export function useWizard() {
  const w = useContext(WizardContext);
  if (!w) throw new Error('useWizard outside wizard');
  return w;
}
