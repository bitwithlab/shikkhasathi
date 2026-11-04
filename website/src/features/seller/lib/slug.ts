/** Storefront slug: 3–40 chars of a-z, 0-9 and "-" (same rule as the shared `Slug` schema). */
export const SLUG_RE = /^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$/;

export function slugify(s: string) {
  return s
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
    .replace(/-+$/g, '');
}

export const isSlug = (s: string) => SLUG_RE.test(s);
