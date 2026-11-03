import { create } from 'zustand';
import type { AuthTokens, Me } from '@shikkhasathi/shared';
import { api, contracts, refreshSession, tokenStore } from './api';
import { queryClient } from './query';
import { useI18nStore } from './i18n';
import { useThemeStore } from './theme';

type Status = 'loading' | 'authed' | 'anon';

interface AuthState {
  status: Status;
  user: Me | null;
  bootstrap: () => Promise<void>;
  signedIn: (t: AuthTokens) => void;
  setUser: (u: Me) => void;
  logout: () => Promise<void>;
}

function applyPrefs(u: Me) {
  if (u.locale !== useI18nStore.getState().locale) useI18nStore.getState().setLocale(u.locale);
  if (u.theme !== useThemeStore.getState().theme) useThemeStore.getState().setTheme(u.theme);
}

export const useAuth = create<AuthState>((set, get) => ({
  status: 'loading',
  user: null,
  bootstrap: async () => {
    const r = await refreshSession();
    if (r) {
      applyPrefs(r.user);
      set({ status: 'authed', user: r.user });
    } else set({ status: 'anon', user: null });
  },
  signedIn: (t) => {
    tokenStore.set(t.accessToken);
    applyPrefs(t.user);
    set({ status: 'authed', user: t.user });
  },
  setUser: (user) => set({ user }),
  logout: async () => {
    await api(contracts.auth.logout, { body: {} }).catch(() => undefined);
    tokenStore.set(null);
    queryClient.clear();
    set({ status: 'anon', user: null });
    void get;
  },
}));

tokenStore.subscribe((t) => {
  if (!t) useAuth.setState({ status: 'anon', user: null });
  else useAuth.setState({ status: 'authed', user: t.user });
});

export const hasRole = (u: Me | null, r: 'student' | 'seller' | 'admin') => !!u?.roles.includes(r);

/** Where a signed-in user lands. */
export function homePath(u: Me | null) {
  if (!u) return '/auth';
  if (!u.onboardingCompleted && !u.roles.includes('admin')) return '/onboarding';
  return '/home';
}
