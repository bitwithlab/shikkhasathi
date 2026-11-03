import { create } from 'zustand';

export interface UploadState {
  name: string;
  progress: number;
  error?: string;
  abort?: () => void;
}

/** In-flight uploads keyed by lesson/resource key — they keep running when an editor collapses or the step changes. */
export const useUploads = create<{ items: Record<string, UploadState>; put: (key: string, u: UploadState | null) => void; patch: (key: string, p: Partial<UploadState>) => void }>((set) => ({
  items: {},
  put: (key, u) =>
    set((s) => {
      const items = { ...s.items };
      if (u) items[key] = u;
      else delete items[key];
      return { items };
    }),
  patch: (key, p) => set((s) => (s.items[key] ? { items: { ...s.items, [key]: { ...s.items[key]!, ...p } } } : s)),
}));
