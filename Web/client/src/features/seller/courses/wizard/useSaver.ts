import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '@/lib/api';

export type SaveStatus = 'idle' | 'pending' | 'saving' | 'saved' | 'error';

/**
 * Debounced, serialized autosave. `schedule()` marks the resource dirty; after `delay` ms of quiet the
 * latest `send()` runs (it builds its payload from refs at fire time). Saves never overlap: changes made
 * while one is in flight trigger exactly one follow-up save.
 */
export function useSaver(send: () => Promise<void>, delay = 800) {
  const sendRef = useRef(send);
  sendRef.current = send;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const flying = useRef<Promise<void> | null>(null);
  const dirty = useRef(false);
  const [status, setStatus] = useState<SaveStatus>('idle');
  const [error, setError] = useState<string | null>(null);

  const fire = useCallback((): Promise<void> => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    if (flying.current) return flying.current;
    if (!dirty.current) return Promise.resolve();
    dirty.current = false;
    setStatus('saving');
    const p = sendRef
      .current()
      .then(() => {
        setError(null);
        setStatus(dirty.current ? 'pending' : 'saved');
      })
      .catch((e: unknown) => {
        setError(e instanceof ApiError ? e.message : 'error');
        setStatus('error');
      })
      .finally(() => {
        flying.current = null;
      });
    flying.current = p;
    return p.then(() => {
      if (dirty.current) return fire();
    });
  }, []);

  const schedule = useCallback(() => {
    dirty.current = true;
    setStatus((s) => (s === 'saving' ? s : 'pending'));
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => void fire(), delay);
  }, [delay, fire]);

  /** Save now (and wait for any in-flight save). */
  const flush = useCallback(async () => {
    if (flying.current) await flying.current;
    await fire();
  }, [fire]);

  const retry = useCallback(() => {
    dirty.current = true;
    void fire();
  }, [fire]);

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current);
  }, []);

  return { status, error, schedule, flush, retry };
}

export type Saver = ReturnType<typeof useSaver>;

/** Combine saver states for the header indicator. */
export function overall(savers: Saver[]): SaveStatus {
  if (savers.some((s) => s.status === 'error')) return 'error';
  if (savers.some((s) => s.status === 'saving')) return 'saving';
  if (savers.some((s) => s.status === 'pending')) return 'pending';
  if (savers.some((s) => s.status === 'saved')) return 'saved';
  return 'idle';
}

/** State mirrored in a ref so async code (autosave) always reads the latest value synchronously. */
export function useRefState<T>(initial: T | (() => T)) {
  const [state, setState] = useState<T>(initial);
  const ref = useRef<T>(state);
  const set = useCallback((next: T | ((prev: T) => T)) => {
    ref.current = typeof next === 'function' ? (next as (p: T) => T)(ref.current) : next;
    setState(ref.current);
  }, []);
  return [state, ref, set] as const;
}
