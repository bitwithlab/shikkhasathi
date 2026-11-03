import { useState, type ReactNode } from 'react';
import { useMutation } from '@tanstack/react-query';
import { CheckCircle2, FileCheck2, IdCard, Landmark, Link2, Mail, Smartphone, Users } from './icons';
import type { SellerProfile } from '@shikkhasathi/shared';
import { Button, Field, Input, ProgressBar, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { InstitutionPicker, type InstitutionValue } from './InstitutionPicker';
import { FileDrop } from './FileDrop';
import { isSlug, slugify } from '../lib/slug';
import { normalizeDigits } from '../lib/money';
import { ACCEPT, MAX, uploadIdCard } from '../lib/upload';

type Method = 'university_email' | 'id_card' | 'linkedin' | 'reference';
type Payout = 'bkash' | 'nagad' | 'bank';

export interface ApplyResult {
  profile: SellerProfile;
  /** a university-email code typed before the profile existed; the caller confirms it after applying */
  pendingCode: { email: string; code: string } | null;
}

const METHODS: { v: Method; icon: typeof Mail }[] = [
  { v: 'university_email', icon: Mail },
  { v: 'id_card', icon: IdCard },
  { v: 'linkedin', icon: Link2 },
  { v: 'reference', icon: Users },
];
const PAYOUTS: { v: Payout; icon: typeof Mail }[] = [
  { v: 'bkash', icon: Smartphone },
  { v: 'nagad', icon: Smartphone },
  { v: 'bank', icon: Landmark },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const PHONE_RE = /^01[3-9]\d{8}$/;

/** Teacher application (create or update). Used by /teach-apply and /teach/verification. */
export function ApplicationForm({ initial, defaultName, defaultEmail, submitLabel, onDone }: { initial: SellerProfile | null; defaultName: string; defaultEmail?: string | null; submitLabel: string; onDone: (r: ApplyResult) => void | Promise<void> }) {
  const t = useT();
  const [displayName, setDisplayName] = useState(initial?.displayName ?? defaultName);
  const [slug, setSlug] = useState(initial?.slug ?? (slugify(defaultName) || slugify((defaultEmail ?? '').split('@')[0] ?? '')));
  const [slugTouched, setSlugTouched] = useState(!!initial);
  const [headline, setHeadline] = useState(initial?.headline ?? '');
  const [bio, setBio] = useState(initial?.bio ?? '');
  const [inst, setInst] = useState<InstitutionValue | null>(initial?.institutionId ? { id: initial.institutionId, label: initial.institutionName ?? '' } : null);
  const [method, setMethod] = useState<Method>(initial?.verificationMethod ?? 'university_email');
  const [email, setEmail] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [code, setCode] = useState('');
  const [emailVerified, setEmailVerified] = useState(initial?.universityEmailVerified ?? false);
  const [idCard, setIdCard] = useState<{ key: string; name: string } | null>(null);
  const [idProgress, setIdProgress] = useState<number | null>(null);
  const [linkedin, setLinkedin] = useState('');
  const [refName, setRefName] = useState('');
  const [refContact, setRefContact] = useState('');
  const [payout, setPayout] = useState<Payout>(initial?.payoutMethod ?? 'bkash');
  const [account, setAccount] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const sendCode = useMutation({
    mutationFn: () => api(contracts.sellerApply.emailCode, { body: { email: email.trim() } }),
    onSuccess: () => {
      setCodeSent(true);
      toast.success(t('seller.apply.codeSent', { email: email.trim() }));
    },
    onError: (e) => setErrors((x) => ({ ...x, email: e instanceof ApiError ? e.message : t('state.errorBody') })),
  });
  const confirmCode = useMutation({
    mutationFn: () => api(contracts.sellerApply.emailConfirm, { body: { email: email.trim(), code } }),
    onSuccess: () => {
      setEmailVerified(true);
      toast.success(t('seller.apply.emailVerified'));
    },
    onError: (e) => setErrors((x) => ({ ...x, code: e instanceof ApiError ? e.message : t('state.errorBody') })),
  });

  const onIdCard = async (f: File) => {
    if (f.size > MAX.idCard) return setErrors((x) => ({ ...x, idCard: t('seller.apply.fileTooBig', { mb: localDigits(10, t.locale) }) }));
    setErrors((x) => ({ ...x, idCard: '' }));
    setIdProgress(0);
    try {
      const key = await uploadIdCard(f, setIdProgress);
      setIdCard({ key, name: f.name });
    } catch (e) {
      setErrors((x) => ({ ...x, idCard: e instanceof ApiError ? e.message : t('seller.upload.failed') }));
    } finally {
      setIdProgress(null);
    }
  };

  const validate = () => {
    const e: Record<string, string> = {};
    if (displayName.trim().length < 2) e.displayName = t('seller.apply.err.name');
    if (!isSlug(slug)) e.slug = t('seller.apply.err.slug');
    if (method === 'university_email' && !EMAIL_RE.test(email.trim())) e.email = t('seller.apply.err.email');
    if (method === 'id_card' && !idCard) e.idCard = t('seller.apply.err.idCard');
    if (method === 'linkedin' && !/^https:\/\/(www\.)?linkedin\.com\/.+/i.test(linkedin.trim())) e.linkedin = t('seller.apply.err.linkedin');
    if (method === 'reference' && (refName.trim().length < 2 || refContact.trim().length < 5)) e.reference = t('seller.apply.err.reference');
    const acc = normalizeDigits(account).replace(/[\s-]/g, '');
    if (payout !== 'bank' && !PHONE_RE.test(acc)) e.account = t('seller.apply.err.wallet');
    if (payout === 'bank' && account.trim().length < 6) e.account = t('seller.apply.err.bank');
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = useMutation({
    mutationFn: () =>
      api(contracts.sellerApply.apply, {
        body: {
          displayName: displayName.trim(),
          slug,
          headline: headline.trim() || undefined,
          bio: bio.trim() || undefined,
          institutionId: inst?.id ?? null,
          method,
          evidence: {
            ...(method === 'university_email' ? { universityEmail: email.trim() } : {}),
            ...(method === 'id_card' && idCard ? { idCardKey: idCard.key } : {}),
            ...(method === 'linkedin' ? { linkedinUrl: linkedin.trim() } : {}),
            ...(method === 'reference' ? { referenceName: refName.trim(), referenceContact: refContact.trim() } : {}),
          },
          payoutMethod: payout,
          payoutAccount: payout === 'bank' ? account.trim() : normalizeDigits(account).replace(/[\s-]/g, ''),
        },
      }),
    onSuccess: async (profile) => {
      const pendingCode = method === 'university_email' && !emailVerified && /^\d{6}$/.test(code) ? { email: email.trim(), code } : null;
      await onDone({ profile, pendingCode });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 409) setErrors((x) => ({ ...x, slug: e.message }));
      else setFormError(e instanceof ApiError ? e.message : t('state.errorBody'));
    },
  });

  return (
    <form
      noValidate
      className="flex flex-col gap-5"
      onSubmit={(e) => {
        e.preventDefault();
        setFormError(null);
        if (validate()) submit.mutate();
      }}
    >
      <FormBlock n={1} title={t('seller.apply.s1')} body={t('seller.apply.s1Body')}>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label={t('seller.apply.displayName')} error={errors.displayName}>
            {(p) => (
              <Input
                {...p}
                value={displayName}
                maxLength={80}
                autoComplete="name"
                placeholder={t('seller.apply.displayNamePh')}
                onChange={(e) => {
                  setDisplayName(e.target.value);
                  if (!slugTouched) setSlug(slugify(e.target.value) || slug);
                }}
              />
            )}
          </Field>
          <Field label={t('seller.apply.slug')} error={errors.slug} hint={<span className="latin">shikkhasathi.com/t/<b className="text-text">{slug || 'your-name'}</b></span>}>
            {(p) => (
              <Input
                {...p}
                value={slug}
                maxLength={40}
                className="latin"
                autoCapitalize="none"
                spellCheck={false}
                placeholder="rakib-hasan"
                onChange={(e) => {
                  setSlugTouched(true);
                  setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''));
                }}
              />
            )}
          </Field>
          <Field label={t('seller.apply.headline')} className="md:col-span-2" hint={t('seller.apply.headlineHint')}>
            {(p) => <Input {...p} value={headline} maxLength={120} placeholder={t('seller.apply.headlinePh')} onChange={(e) => setHeadline(e.target.value)} />}
          </Field>
          <Field label={t('seller.apply.bio')} className="md:col-span-2" trailing={<Counter n={bio.length} max={2000} />}>
            {(p) => <Textarea {...p} value={bio} maxLength={2000} rows={4} placeholder={t('seller.apply.bioPh')} onChange={(e) => setBio(e.target.value)} />}
          </Field>
          <Field label={t('seller.apply.institution')} className="md:col-span-2" hint={t('seller.apply.institutionHint')}>
            {(p) => <InstitutionPicker {...p} value={inst} onChange={setInst} />}
          </Field>
        </div>
      </FormBlock>

      <FormBlock n={2} title={t('seller.apply.s2')} body={t('seller.apply.s2Body')}>
        <div role="radiogroup" aria-label={t('seller.apply.s2')} className="grid gap-2 sm:grid-cols-2">
          {METHODS.map((m) => (
            <ChoiceCard key={m.v} selected={method === m.v} onClick={() => setMethod(m.v)} icon={<m.icon className="size-5" strokeWidth={1.75} aria-hidden />} title={t(`seller.method.${m.v}`)} body={t(`seller.method.${m.v}.hint`)} />
          ))}
        </div>
        <div className="mt-4 rounded-card bg-surface-2 p-4">
          {method === 'university_email' && (
            <div className="flex flex-col gap-3">
              <Field label={t('seller.apply.uniEmail')} error={errors.email} hint={t('seller.apply.uniEmailHint')}>
                {(p) => (
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Input
                      {...p}
                      type="email"
                      inputMode="email"
                      className="latin"
                      value={email}
                      placeholder="rakib.cse@metrouni.edu.bd"
                      onChange={(e) => {
                        setEmail(e.target.value);
                        setEmailVerified(false);
                        setCodeSent(false);
                      }}
                    />
                    <Button variant="secondary" className="shrink-0" disabled={!EMAIL_RE.test(email.trim()) || emailVerified} loading={sendCode.isPending} onClick={() => sendCode.mutate()}>
                      {codeSent ? t('seller.apply.resend') : t('seller.apply.sendCode')}
                    </Button>
                  </div>
                )}
              </Field>
              {emailVerified ? (
                <p className="flex items-center gap-2 text-sm font-semibold text-success-fg">
                  <CheckCircle2 className="size-4" aria-hidden /> {t('seller.apply.emailVerified')}
                </p>
              ) : (
                codeSent && (
                  <Field label={t('seller.apply.code')} error={errors.code} hint={initial ? undefined : t('seller.apply.codeLater')}>
                    {(p) => (
                      <div className="flex gap-2">
                        <Input {...p} inputMode="numeric" autoComplete="one-time-code" maxLength={6} className="latin tnum max-w-44 text-center text-lg tracking-[0.3em]" value={code} placeholder="••••••" onChange={(e) => setCode(normalizeDigits(e.target.value).replace(/\D/g, '').slice(0, 6))} />
                        {initial && (
                          <Button variant="secondary" disabled={code.length !== 6} loading={confirmCode.isPending} onClick={() => confirmCode.mutate()}>
                            {t('common.confirm')}
                          </Button>
                        )}
                      </div>
                    )}
                  </Field>
                )
              )}
            </div>
          )}
          {method === 'id_card' && (
            <div className="flex flex-col gap-2">
              {idCard ? (
                <p className="flex min-h-12 items-center gap-2 text-sm">
                  <FileCheck2 className="size-5 text-success" aria-hidden />
                  <span className="latin min-w-0 flex-1 truncate font-medium">{idCard.name}</span>
                  <button type="button" className="h-10 rounded-control px-3 text-sm font-semibold text-primary-fg hover:bg-primary-soft" onClick={() => setIdCard(null)}>
                    {t('seller.upload.replace')}
                  </button>
                </p>
              ) : (
                <FileDrop accept={ACCEPT.idCard} onFile={(f) => void onIdCard(f)} disabled={idProgress !== null} title={t('seller.apply.idCard')} hint={t('seller.apply.idCardHint')} icon={<IdCard className="size-5" strokeWidth={1.75} aria-hidden />} />
              )}
              {idProgress !== null && <ProgressBar value={idProgress} tone="primary" label={t('seller.upload.progress')} />}
              {errors.idCard && (
                <p role="alert" className="text-[13px] text-danger-fg">
                  {errors.idCard}
                </p>
              )}
            </div>
          )}
          {method === 'linkedin' && (
            <Field label={t('seller.apply.linkedin')} error={errors.linkedin}>
              {(p) => <Input {...p} type="url" inputMode="url" className="latin" value={linkedin} placeholder="https://www.linkedin.com/in/rakib-hasan" onChange={(e) => setLinkedin(e.target.value)} />}
            </Field>
          )}
          {method === 'reference' && (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label={t('seller.apply.refName')} error={errors.reference}>
                {(p) => <Input {...p} value={refName} maxLength={120} placeholder={t('seller.apply.refNamePh')} onChange={(e) => setRefName(e.target.value)} />}
              </Field>
              <Field label={t('seller.apply.refContact')}>
                {(p) => <Input {...p} value={refContact} maxLength={120} className="latin" placeholder="01712-345678" onChange={(e) => setRefContact(e.target.value)} />}
              </Field>
            </div>
          )}
        </div>
      </FormBlock>

      <FormBlock n={3} title={t('seller.apply.s3')} body={t('seller.apply.s3Body')}>
        <div role="radiogroup" aria-label={t('seller.apply.s3')} className="grid grid-cols-3 gap-2">
          {PAYOUTS.map((m) => (
            <button
              key={m.v}
              type="button"
              role="radio"
              aria-checked={payout === m.v}
              onClick={() => setPayout(m.v)}
              className={cn('flex h-12 items-center justify-center gap-2 rounded-control border text-sm font-semibold transition-colors', payout === m.v ? 'border-primary bg-primary-soft text-primary-fg ring-1 ring-primary' : 'border-border bg-surface hover:border-border-strong')}
            >
              <m.icon className="size-4" strokeWidth={1.75} aria-hidden />
              {t(`seller.payout.${m.v}`)}
            </button>
          ))}
        </div>
        <Field
          className="mt-4"
          label={payout === 'bank' ? t('seller.apply.bankAccount') : t('seller.apply.walletNumber', { m: t(`seller.payout.${payout}`) })}
          error={errors.account}
          hint={initial?.payoutAccountMasked ? t('seller.apply.accountAgain', { masked: initial.payoutAccountMasked }) : t('seller.apply.accountHint')}
        >
          {(p) => <Input {...p} value={account} maxLength={60} inputMode={payout === 'bank' ? 'text' : 'tel'} className="latin tnum" placeholder={payout === 'bank' ? t('seller.apply.bankPh') : '017XXXXXXXX'} onChange={(e) => setAccount(e.target.value)} />}
        </Field>
      </FormBlock>

      {formError && (
        <p role="alert" className="rounded-control border border-danger/30 bg-danger-soft px-4 py-3 text-sm text-danger-fg">
          {formError}
        </p>
      )}
      <div className="flex flex-col-reverse items-stretch gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs text-muted">{t('seller.apply.privacy')}</p>
        <Button type="submit" size="lg" loading={submit.isPending} className="sm:min-w-56">
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

function FormBlock({ n, title, body, children }: { n: number; title: string; body: string; children: ReactNode }) {
  const t = useT();
  return (
    <fieldset className="surface-card p-4 sm:p-6">
      <legend className="sr-only">{title}</legend>
      <div className="mb-4 flex items-start gap-3">
        <span className="latin flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-soft text-sm font-bold text-primary-fg" aria-hidden>
          {localDigits(n, t.locale)}
        </span>
        <div className="min-w-0">
          <h2 className="text-lg font-semibold leading-snug">{title}</h2>
          <p className="text-sm text-muted">{body}</p>
        </div>
      </div>
      {children}
    </fieldset>
  );
}

export function ChoiceCard({ selected, onClick, icon, title, body, disabled }: { selected: boolean; onClick: () => void; icon: ReactNode; title: ReactNode; body?: ReactNode; disabled?: boolean }) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      disabled={disabled}
      onClick={onClick}
      className={cn('flex min-h-16 w-full items-start gap-3 rounded-control border bg-surface p-3 text-left transition-[border-color,box-shadow] duration-150 disabled:opacity-50', selected ? 'border-primary ring-1 ring-primary' : 'border-border hover:border-border-strong')}
    >
      <span className={cn('flex size-9 shrink-0 items-center justify-center rounded-[10px]', selected ? 'bg-primary text-on-primary' : 'bg-primary-soft text-primary')}>{icon}</span>
      <span className="min-w-0">
        <span className="block text-sm font-semibold">{title}</span>
        {body && <span className="block text-xs leading-relaxed text-muted">{body}</span>}
      </span>
    </button>
  );
}

export function Counter({ n, max }: { n: number; max: number }) {
  const t = useT();
  return (
    <span className={cn('latin text-xs tnum', n > max * 0.9 ? 'text-warning-fg' : 'text-muted')} aria-live="polite">
      {localDigits(n, t.locale)}/{localDigits(max, t.locale)}
    </span>
  );
}
