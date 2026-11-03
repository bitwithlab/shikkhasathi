import { badRequest } from './errors';

/** Opaque cursor = base64url(JSON [sortValue, id]). */
export function encodeCursor(sortValue: string | number, id: string): string {
  return Buffer.from(JSON.stringify([sortValue, id])).toString('base64url');
}

export function decodeCursor(cursor: string | undefined): [string | number, string] | null {
  if (!cursor) return null;
  try {
    const v = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'));
    if (Array.isArray(v) && v.length === 2 && typeof v[1] === 'string') return v as [string | number, string];
  } catch {
    /* fallthrough */
  }
  throw badRequest('অবৈধ cursor');
}

/** Given limit+1 rows, return the page and next cursor. */
export function toPage<T>(rows: T[], limit: number, cursorOf: (row: T) => [string | number, string]) {
  const hasMore = rows.length > limit;
  const items = hasMore ? rows.slice(0, limit) : rows;
  const last = items[items.length - 1];
  return { items, nextCursor: hasMore && last ? encodeCursor(...cursorOf(last)) : null };
}
