import './i18n';
import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ArrowLeft, BellRing, CheckCircle2, ShieldCheck, Smartphone } from 'lucide-react';
import { BdPhone, Email, Password, type AuthTokens } from '@shikkhasathi/shared';
import { Button, Field, Input, Logo, Badge } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useAuth, homePath } from '@/lib/auth';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { LangToggle } from '@/app/shells/Prefs';
import { OtpInput } from './OtpInput';
import { GoogleButton } from './GoogleButton';

type Mode = 'phone' | 'otp' | 'email' | 'register' | 'forgot' | 'reset';
type Role = 'student' | 'teacher';

function deviceName() {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser';
  const os = /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS' : /Windows/.test(ua) ? 'Windows' : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : '';
  return `${browser}${os ? ` · ${os}` : ''}`;
}

export default function AuthPage() {
  const t = useT();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const signedIn = useAuth((s) => s.signedIn);
  const [mode, setMode] = useState<Mode>(params.get('mode') === 'register' ? 'register' : 'phone');
  const [role, setRole] = useState<Role>(params.get('role') === 'teacher' ? 'teacher' : 'student');
  const [phone, setPhone] = useState('');
  const [normalized, setNormalized] = useState('');
  const [code, setCode] = useState('');
  const [devCode, setDevCode] = useState<string | null>(null);
  const [resendAt, setResendAt] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (mode !== 'otp') return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [mode]);

  const done = useCallback(
    (r: AuthTokens) => {
      signedIn(r);
      const next = params.get('next');
      if (role === 'teacher' && !r.user.roles.includes('seller')) return nav('/teach-apply', { replace: true });
      nav(next && next.startsWith('/') && !next.startsWith('//') ? next : homePath(r.user), { replace: true });
    },
    [nav, params, role, signedIn],
  );

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : t('state.errorBody'));
    } finally {
      setBusy(false);
    }
  };

  const device = { platform: 'web' as const, deviceName: deviceName() };

  const requestOtp = (e?: FormEvent) => {
    e?.preventDefault();
    const p = BdPhone.safeParse(phone);
    if (!p.success) return setError(p.error.issues[0]?.message ?? '');
    void run(async () => {
      const r = await api(contracts.auth.otpRequest, { body: { phone: p.data } });
      setNormalized(p.data);
      setDevCode(r.devCode ?? null);
      setResendAt(Date.now() + r.resendInSec * 1000);
      setCode('');
      setMode('otp');
    });
  };

  const verifyOtp = (value = code) => {
    if (value.length !== 6) return;
    void run(async () => done(await api(contracts.auth.otpVerify, { body: { phone: normalized, code: value, ...device } })));
  };

  const emailLogin = (e: FormEvent) => {
    e.preventDefault();
    const em = Email.safeParse(email);
    if (!em.success) return setError(em.error.issues[0]?.message ?? '');
    void run(async () => done(await api(contracts.auth.login, { body: { email: em.data, password, ...device } })));
  };

  const register = (e: FormEvent) => {
    e.preventDefault();
    const em = Email.safeParse(email);
    const pw = Password.safeParse(password);
    if (name.trim().length < 2) return setError(t('auth.nameRequired'));
    if (!em.success) return setError(em.error.issues[0]?.message ?? '');
    if (!pw.success) return setError(pw.error.issues[0]?.message ?? '');
    void run(async () => done(await api(contracts.auth.register, { body: { name: name.trim(), email: em.data, password: pw.data, asSeller: role === 'teacher', ...device } })));
  };

  const forgot = (e: FormEvent) => {
    e.preventDefault();
    const em = Email.safeParse(email);
    if (!em.success) return setError(em.error.issues[0]?.message ?? '');
    void run(async () => {
      await api(contracts.auth.emailOtp, { body: { email: em.data, purpose: 'password_reset' } });
      setNotice(t('auth.resetCodeSent'));
      setCode('');
      setMode('reset');
    });
  };

  const reset = (e: FormEvent) => {
    e.preventDefault();
    const pw = Password.safeParse(password);
    if (!pw.success) return setError(pw.error.issues[0]?.message ?? '');
    void run(async () => {
      await api(contracts.auth.passwordReset, { body: { email, code, password: pw.data } });
      setNotice(t('auth.resetDone'));
      setPassword('');
      setMode('email');
    });
  };

  const google = useCallback(
    (idToken: string) => {
      void (async () => {
        setBusy(true);
        setError(null);
        try {
          done(await api(contracts.auth.google, { body: { idToken, platform: 'web', deviceName: deviceName() } }));
        } catch (e) {
          setError(e instanceof ApiError ? e.message : t('state.errorBody'));
        } finally {
          setBusy(false);
        }
      })();
    },
    [done, t],
  );

  const resendIn = Math.max(0, Math.ceil((resendAt - now) / 1000));
  const switchMode = (m: Mode) => {
    setMode(m);
    setError(null);
    setNotice(null);
  };

  let body: ReactNode;
  if (mode === 'otp') {
    body = (
      <form
        onSubmit={(e) => {
          e.preventDefault();
          verifyOtp();
        }}
        className="flex flex-col gap-5"
      >
        <button type="button" onClick={() => switchMode('phone')} className="-ml-2 inline-flex w-fit items-center gap-1 rounded-control px-2 py-1 text-sm text-muted hover:text-text">
          <ArrowLeft className="size-4" aria-hidden /> {t('auth.changeNumber')}
        </button>
        <div>
          <h1 className="text-2xl font-bold">{t('auth.otpTitle')}</h1>
          <p className="mt-1 text-[15px] text-muted">{t('auth.otpSent', { phone: localDigits(normalized.replace('+88', ''), t.locale) })}</p>
        </div>
        <OtpInput value={code} onChange={setCode} onComplete={verifyOtp} disabled={busy} invalid={!!error} />
        {devCode && (
          <p className="rounded-control bg-warning-soft px-3 py-2 text-sm text-warning-fg">
            {t('auth.devCode', { code: devCode })}
          </p>
        )}
        <Button type="submit" size="lg" block loading={busy} disabled={code.length !== 6}>
          {busy ? t('auth.verifying') : t('auth.verify')}
        </Button>
        <p className="text-center text-sm text-muted">
          {resendIn > 0 ? (
            <span className="tnum">{t('auth.resendIn', { s: localDigits(resendIn, t.locale) })}</span>
          ) : (
            <button type="button" className="font-semibold text-primary-fg hover:underline" onClick={() => requestOtp()}>
              {t('auth.resend')}
            </button>
          )}
        </p>
      </form>
    );
  } else if (mode === 'phone') {
    body = (
      <form onSubmit={requestOtp} className="flex flex-col gap-4" noValidate>
        <div>
          <h1 className="text-2xl font-bold">{t('auth.title')}</h1>
          <p className="mt-1 text-[15px] text-muted">{t('auth.subtitle')}</p>
        </div>
        <Field label={t('auth.phone')}>
          {(p) => (
            <div className="relative">
              <span className="latin pointer-events-none absolute inset-y-0 left-3.5 flex items-center text-[15px] text-muted">+88</span>
              <Input {...p} type="tel" inputMode="tel" autoComplete="tel-national" placeholder={t('auth.phonePlaceholder')} value={phone} onChange={(e) => setPhone(e.target.value)} className="latin tnum pl-12 text-[17px] tracking-wide" autoFocus />
            </div>
          )}
        </Field>
        <Button type="submit" size="lg" block loading={busy}>
          {busy ? t('auth.sending') : t('auth.sendCode')}
        </Button>
        <Divider label={t('common.or')} />
        <GoogleButton onCredential={google} disabled={busy} />
        <Button variant="ghost" block onClick={() => switchMode('email')}>
          {t('auth.withEmail')}
        </Button>
      </form>
    );
  } else if (mode === 'email') {
    body = (
      <form onSubmit={emailLogin} className="flex flex-col gap-4" noValidate>
        <div>
          <h1 className="text-2xl font-bold">{t('auth.withEmail')}</h1>
        </div>
        <Field label={t('auth.email')}>{(p) => <Input {...p} type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />}</Field>
        <Field
          label={t('auth.password')}
          trailing={
            <button type="button" className="text-sm font-medium text-primary-fg hover:underline" onClick={() => switchMode('forgot')}>
              {t('auth.forgot')}
            </button>
          }
        >
          {(p) => <Input {...p} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
        </Field>
        <Button type="submit" size="lg" block loading={busy}>
          {t('auth.login')}
        </Button>
        <p className="text-center text-sm text-muted">
          {t('auth.noAccount')}{' '}
          <button type="button" className="font-semibold text-primary-fg hover:underline" onClick={() => switchMode('register')}>
            {t('auth.register')}
          </button>
        </p>
        <Button variant="ghost" block onClick={() => switchMode('phone')} icon={<Smartphone className="size-4" aria-hidden />}>
          {t('auth.withPhone')}
        </Button>
      </form>
    );
  } else if (mode === 'register') {
    body = (
      <form onSubmit={register} className="flex flex-col gap-4" noValidate>
        <h1 className="text-2xl font-bold">{t('auth.register')}</h1>
        <Field label={t('auth.name')}>{(p) => <Input {...p} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} autoFocus />}</Field>
        <Field label={t('auth.email')}>{(p) => <Input {...p} type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />}</Field>
        <Field label={t('auth.password')} hint={t('auth.passwordHint')}>
          {(p) => <Input {...p} type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
        </Field>
        <Button type="submit" size="lg" block loading={busy}>
          {t('auth.register')}
        </Button>
        <Divider label={t('common.or')} />
        <GoogleButton onCredential={google} disabled={busy} />
        <p className="text-center text-sm text-muted">
          {t('auth.haveAccount')}{' '}
          <button type="button" className="font-semibold text-primary-fg hover:underline" onClick={() => switchMode('phone')}>
            {t('auth.login')}
          </button>
        </p>
      </form>
    );
  } else if (mode === 'forgot') {
    body = (
      <form onSubmit={forgot} className="flex flex-col gap-4" noValidate>
        <button type="button" onClick={() => switchMode('email')} className="-ml-2 inline-flex w-fit items-center gap-1 rounded-control px-2 py-1 text-sm text-muted hover:text-text">
          <ArrowLeft className="size-4" aria-hidden /> {t('common.back')}
        </button>
        <h1 className="text-2xl font-bold">{t('auth.resetTitle')}</h1>
        <Field label={t('auth.email')}>{(p) => <Input {...p} type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} autoFocus />}</Field>
        <Button type="submit" size="lg" block loading={busy}>
          {t('auth.resetSend')}
        </Button>
      </form>
    );
  } else {
    body = (
      <form onSubmit={reset} className="flex flex-col gap-4" noValidate>
        <h1 className="text-2xl font-bold">{t('auth.resetTitle')}</h1>
        <OtpInput value={code} onChange={setCode} disabled={busy} />
        <Field label={t('auth.newPassword')} hint={t('auth.passwordHint')}>
          {(p) => <Input {...p} type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
        </Field>
        <Button type="submit" size="lg" block loading={busy} disabled={code.length !== 6}>
          {t('common.save')}
        </Button>
      </form>
    );
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1fr)_minmax(0,1.05fr)]">
      <AuthAside />
      <main id="main" className="flex flex-col px-4 py-5 sm:px-8">
        <div className="flex items-center justify-between">
          <Link to="/" aria-label={t('brand.name')} className="lg:invisible">
            <Logo />
          </Link>
          <LangToggle />
        </div>
        <div className="mx-auto flex w-full max-w-[400px] flex-1 flex-col justify-center py-8">
          {mode !== 'otp' && mode !== 'reset' && (
            <div role="radiogroup" aria-label={t('auth.roleLabel')} className="mb-6 grid grid-cols-2 rounded-control border border-border bg-surface-2 p-1">
              {(['student', 'teacher'] as const).map((r) => (
                <button key={r} type="button" role="radio" aria-checked={role === r} onClick={() => setRole(r)} className={cn('h-10 rounded-[10px] text-sm font-semibold transition-colors', role === r ? 'bg-surface text-text shadow-e1' : 'text-muted hover:text-text')}>
                  {t(r === 'student' ? 'auth.roleStudent' : 'auth.roleTeacher')}
                </button>
              ))}
            </div>
          )}
          {role === 'teacher' && mode !== 'otp' && <p className="mb-5 rounded-control border border-primary/20 bg-primary-soft px-3 py-2.5 text-sm text-primary-fg">{t('auth.teacherNote')}</p>}
          {notice && (
            <p role="status" className="mb-4 flex items-start gap-2 rounded-control bg-success-soft px-3 py-2.5 text-sm text-success-fg">
              <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden /> {notice}
            </p>
          )}
          {error && (
            <p role="alert" className="mb-4 rounded-control bg-danger-soft px-3 py-2.5 text-sm text-danger-fg">
              {error}
            </p>
          )}
          {body}
          <p className="mt-8 text-center text-xs leading-relaxed text-muted">
            {t('auth.termsPre')}{' '}
            <Link to="/terms" className="underline hover:text-text">
              {t('footer.terms')}
            </Link>{' '}
            {t('auth.termsAnd')}{' '}
            <Link to="/privacy" className="underline hover:text-text">
              {t('footer.privacy')}
            </Link>{' '}
            {t('auth.termsPost')}
          </p>
        </div>
      </main>
    </div>
  );
}

function Divider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 text-xs text-muted" aria-hidden>
      <span className="h-px flex-1 bg-border" />
      {label}
      <span className="h-px flex-1 bg-border" />
    </div>
  );
}

/** Desktop side panel: a calm preview of the dashboard instead of a stock illustration. */
function AuthAside() {
  const t = useT();
  const rows = [
    { code: 'CSE-2204', title: 'BST Deletion Lab', due: t('auth.preview.today'), tone: 'danger' as const },
    { code: 'CSE-2205', title: 'OOP Quiz 2 — Multithreading', due: t('auth.preview.tomorrow'), tone: 'warning' as const },
    { code: 'MAT-2101', title: 'Eigenvalues worksheet', due: t('auth.preview.thursday'), tone: 'neutral' as const },
  ];
  return (
    <aside className="relative hidden overflow-hidden border-r border-border bg-surface lg:flex lg:flex-col lg:justify-between lg:p-10">
      <Link to="/" aria-label={t('brand.name')}>
        <Logo />
      </Link>
      <div className="max-w-md">
        <h2 className="text-[28px] font-bold leading-snug">{t('auth.sideTitle')}</h2>
        <p className="mt-3 text-muted">{t('auth.sideBody')}</p>
        <div className="mt-8 rounded-card border border-border bg-bg p-4 shadow-e2">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-sm font-semibold">{t('auth.preview.title')}</p>
            <Badge tone="success" icon={<CheckCircle2 className="size-3.5" aria-hidden />}>
              {t('auth.preview.synced')}
            </Badge>
          </div>
          <ul className="flex flex-col gap-2">
            {rows.map((r) => (
              <li key={r.code} className="flex items-center gap-3 rounded-control border border-border bg-surface px-3 py-2.5">
                <span className={cn('h-8 w-1 rounded-full', r.tone === 'danger' ? 'bg-danger' : r.tone === 'warning' ? 'bg-warning' : 'bg-border-strong')} aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="latin block text-[11px] font-semibold text-muted">{r.code}</span>
                  <span className="latin block truncate text-sm font-medium">{r.title}</span>
                </span>
                <Badge tone={r.tone}>{r.due}</Badge>
              </li>
            ))}
          </ul>
        </div>
        <ul className="mt-8 flex flex-col gap-3 text-[15px]">
          {[
            [BellRing, 'auth.sidePoint1'],
            [CheckCircle2, 'auth.sidePoint2'],
            [ShieldCheck, 'auth.sidePoint3'],
          ].map(([Icon, k]) => {
            const I = Icon as typeof BellRing;
            return (
              <li key={k as string} className="flex items-start gap-3">
                <I className="mt-1 size-[18px] shrink-0 text-primary" strokeWidth={1.75} aria-hidden />
                <span>{t(k as string)}</span>
              </li>
            );
          })}
        </ul>
      </div>
      <p className="text-xs text-muted">
        {t('brand.disclaimerUni')} · {t('brand.disclaimerGoogle')}
      </p>
    </aside>
  );
}
