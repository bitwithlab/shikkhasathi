import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { SellerProfile } from '@shikkhasathi/shared';
import { Avatar, Button, Card, EmptyState, ErrorState, Field, Input, ProgressBar, Skeleton, Textarea, toast } from '@/components';
import { api, ApiError, contracts } from '@/lib/api';
import { useT } from '@/lib/i18n';
import { ACCEPT, BadgeCheck, Copy, Counter, ExternalLink, FileDrop, ImagePlus, isSlug, MAX, Trash2, uploadCover } from '../parts';

export default function SellerStorefrontPage() {
  const t = useT();
  const q = useQuery({ queryKey: ['seller', 'profile'], queryFn: () => api(contracts.sellerApply.profile) });
  return (
    <div className="mx-auto max-w-[1200px] px-4 pb-12 pt-6 lg:px-8">
      <header className="mb-6">
        <h1 className="text-2xl font-bold">{t('seller.store.title')}</h1>
        <p className="mt-1 max-w-2xl text-sm text-muted">{t('seller.store.subtitle')}</p>
      </header>
      {q.isLoading ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
          <Skeleton className="h-[520px] rounded-card" />
          <Skeleton className="h-72 rounded-card" />
        </div>
      ) : q.isError ? (
        <Card>
          <ErrorState onRetry={() => void q.refetch()} />
        </Card>
      ) : !q.data?.profile ? (
        <Card>
          <EmptyState
            title={t('seller.store.noProfile')}
            body={t('seller.store.noProfileBody')}
            action={
              <Button asChild variant="soft">
                <Link to="/teach/verification">{t('seller.ver.apply')}</Link>
              </Button>
            }
          />
        </Card>
      ) : (
        <StorefrontForm p={q.data.profile} />
      )}
    </div>
  );
}

function StorefrontForm({ p }: { p: SellerProfile }) {
  const t = useT();
  const qc = useQueryClient();
  const [displayName, setDisplayName] = useState(p.displayName);
  const [slug, setSlug] = useState(p.slug);
  const [headline, setHeadline] = useState(p.headline ?? '');
  const [bio, setBio] = useState(p.bio ?? '');
  const [cover, setCover] = useState<string | null>(p.coverUrl);
  const [progress, setProgress] = useState<number | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const dirty = displayName !== p.displayName || slug !== p.slug || headline !== (p.headline ?? '') || bio !== (p.bio ?? '') || cover !== p.coverUrl;
  const url = `shikkhasathi.com/t/${p.slug}`;

  const save = useMutation({
    mutationFn: () => api(contracts.sellerApply.updateStorefront, { body: { displayName: displayName.trim(), slug, headline: headline.trim() || null, bio: bio.trim() || null, coverUrl: cover } }),
    onSuccess: (np) => {
      qc.setQueryData(['seller', 'profile'], { profile: np });
      toast.success(t('common.saved'));
    },
    onError: (e) => {
      if (e instanceof ApiError && e.status === 409) setErrors({ slug: e.message });
      else toast.error(e instanceof ApiError ? e.message : t('state.errorBody'));
    },
  });

  const onCover = async (f: File) => {
    if (f.size > MAX.cover) return toast.error(t('seller.apply.fileTooBig', { mb: '5' }));
    setProgress(0);
    try {
      setCover(await uploadCover(f, setProgress));
    } catch (e) {
      toast.error(e instanceof ApiError ? e.message : t('seller.upload.failed'));
    } finally {
      setProgress(null);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(`https://${url}`);
      toast.success(t('seller.store.copied'));
    } catch {
      toast.error(t('state.errorBody'));
    }
  };

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px] lg:items-start">
      <form
        noValidate
        className="surface-card flex flex-col gap-5 p-5 sm:p-6"
        onSubmit={(e) => {
          e.preventDefault();
          const errs: Record<string, string> = {};
          if (displayName.trim().length < 2) errs.displayName = t('seller.apply.err.name');
          if (!isSlug(slug)) errs.slug = t('seller.apply.err.slug');
          setErrors(errs);
          if (!Object.keys(errs).length) save.mutate();
        }}
      >
        <div className="flex flex-wrap items-center gap-2 rounded-control bg-surface-2 p-2 pl-3">
          <span className="latin min-w-0 flex-1 truncate text-sm font-medium">{url}</span>
          <Button variant="ghost" size="sm" className="h-11" onClick={() => void copy()} icon={<Copy className="size-4" aria-hidden />}>
            {t('seller.store.copy')}
          </Button>
          <Button asChild variant="secondary" size="sm" className="h-11" icon={<ExternalLink className="size-4" aria-hidden />}>
            <Link to={`/t/${p.slug}`}>{t('seller.store.open')}</Link>
          </Button>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Field label={t('seller.apply.displayName')} error={errors.displayName}>
            {(f) => <Input {...f} value={displayName} maxLength={80} onChange={(e) => setDisplayName(e.target.value)} />}
          </Field>
          <Field label={t('seller.apply.slug')} error={errors.slug} hint={slug !== p.slug ? t('seller.store.slugWarn') : <span className="latin">shikkhasathi.com/t/{slug}</span>}>
            {(f) => <Input {...f} value={slug} maxLength={40} className="latin" spellCheck={false} autoCapitalize="none" onChange={(e) => setSlug(e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, ''))} />}
          </Field>
        </div>
        <Field label={t('seller.apply.headline')} trailing={<Counter n={headline.length} max={120} />}>
          {(f) => <Input {...f} value={headline} maxLength={120} placeholder={t('seller.apply.headlinePh')} onChange={(e) => setHeadline(e.target.value)} />}
        </Field>
        <Field label={t('seller.apply.bio')} trailing={<Counter n={bio.length} max={2000} />}>
          {(f) => <Textarea {...f} value={bio} rows={6} maxLength={2000} placeholder={t('seller.apply.bioPh')} onChange={(e) => setBio(e.target.value)} />}
        </Field>
        <div className="flex flex-col gap-2">
          <span className="text-sm font-medium">{t('seller.store.cover')}</span>
          {cover ? (
            <div className="flex items-center gap-3">
              <img src={cover} alt="" width={160} height={54} className="h-14 w-40 rounded-control border border-border object-cover" />
              <FileDrop compact accept={ACCEPT.image} onFile={(f) => void onCover(f)} disabled={progress !== null} title={t('seller.upload.replace')} className="flex-1" icon={<ImagePlus className="size-5" strokeWidth={1.75} aria-hidden />} />
              <button type="button" onClick={() => setCover(null)} className="flex size-11 items-center justify-center rounded-control text-muted hover:bg-danger-soft hover:text-danger-fg" aria-label={t('seller.store.removeCover')}>
                <Trash2 className="size-[18px]" aria-hidden />
              </button>
            </div>
          ) : (
            <FileDrop accept={ACCEPT.image} onFile={(f) => void onCover(f)} disabled={progress !== null} title={t('seller.store.coverUpload')} hint={t('seller.store.coverHint')} icon={<ImagePlus className="size-5" strokeWidth={1.75} aria-hidden />} />
          )}
          {progress !== null && <ProgressBar value={progress} tone="primary" label={t('seller.upload.progress')} />}
        </div>
        <div className="flex justify-end border-t border-border pt-4">
          <Button type="submit" disabled={!dirty} loading={save.isPending}>
            {t('common.save')}
          </Button>
        </div>
      </form>

      <aside aria-label={t('seller.store.preview')} className="lg:sticky lg:top-24">
        <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">{t('seller.store.preview')}</p>
        <div className="surface-card overflow-hidden">
          <div className="h-24 bg-primary-soft">{cover && <img src={cover} alt="" width={380} height={96} className="size-full object-cover" />}</div>
          <div className="px-5 pb-5">
            <div className="-mt-8 rounded-full border-4 border-surface bg-surface" style={{ width: 72 }}>
              <Avatar name={displayName || p.displayName} size={64} />
            </div>
            <p className="mt-2 flex items-center gap-1.5 text-lg font-bold">
              <span className="truncate">{displayName || p.displayName}</span>
              {p.verificationStatus === 'verified' && <BadgeCheck className="size-5 shrink-0 text-success" aria-label={t('course.verified')} />}
            </p>
            {headline && <p className="text-sm text-muted">{headline}</p>}
            {p.institutionName && <p className="latin mt-2 text-xs font-medium text-muted">{p.institutionName}</p>}
            {bio && <p className="mt-3 line-clamp-4 text-sm">{bio}</p>}
          </div>
        </div>
      </aside>
    </div>
  );
}
