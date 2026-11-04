import { create } from 'zustand';

export type ThemePref = 'system' | 'light' | 'dark';

function read(): ThemePref {
  try {
    const v = localStorage.getItem('ss-theme');
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

export const useThemeStore = create<{ theme: ThemePref; setTheme: (t: ThemePref) => void }>((set) => ({
  theme: read(),
  setTheme: (theme) => {
    try {
      if (theme === 'system') localStorage.removeItem('ss-theme');
      else localStorage.setItem('ss-theme', theme);
    } catch {
      /* ignore */
    }
    if (theme === 'system') delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme = theme;
    set({ theme });
  },
}));
