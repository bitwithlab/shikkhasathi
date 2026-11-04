import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Captions,
  Gauge,
  Maximize,
  Minimize,
  Pause,
  Play,
  RotateCcw,
  RotateCw,
  Settings2,
  Volume2,
  VolumeX,
} from 'lucide-react';
import type { Playback } from '@/api';
import { Button } from '@/components';
import { localDigits, useT } from '@/lib/i18n';
import { cn } from '@/lib/cn';
import { fmtTime } from './time';

export { fmtTime };

export const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 1.75, 2] as const;
const LOW_DATA_KEY = 'ss.lowData';
const SPEED_KEY = 'ss.speed';

const read = (k: string) => {
  try {
    return localStorage.getItem(k);
  } catch {
    return null;
  }
};
const write = (k: string, v: string) => {
  try {
    localStorage.setItem(k, v);
  } catch {
    /* storage unavailable */
  }
};

interface Level {
  index: number;
  height: number;
}

export interface VideoPlayerHandle {
  time: () => number;
  seek: (sec: number) => void;
}

/**
 * Custom player shell (spec §6.8): HLS via hls.js (lazy) or native, speed, quality + low-data (360p), captions,
 * resume, keyboard shortcuts, moving watermark, 15 s heartbeat that only counts real playback time.
 */
export function VideoPlayer({
  pb,
  onHeartbeat,
  onEnded,
  onTakeover,
  handleRef,
}: {
  pb: Playback;
  /** called ~every 15 s while playing, and on pause/end; return false when the stream was taken over */
  onHeartbeat: (positionSec: number, deltaSec: number) => Promise<boolean>;
  onEnded: () => void;
  onTakeover: () => void;
  handleRef?: React.MutableRefObject<VideoPlayerHandle | null>;
}) {
  const t = useT();
  const wrap = useRef<HTMLDivElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<{ destroy: () => void; currentLevel: number; levels: { height: number }[] } | null>(
    null,
  );
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [dur, setDur] = useState(0);
  const [buffered, setBuffered] = useState(0);
  const [muted, setMuted] = useState(false);
  const [speed, setSpeed] = useState(() => Number(read(SPEED_KEY)) || 1);
  const [levels, setLevels] = useState<Level[]>([]);
  const [level, setLevel] = useState(-1);
  const [lowData, setLowData] = useState(() => read(LOW_DATA_KEY) === '1');
  const [tracks, setTracks] = useState<TextTrack[]>([]);
  const [track, setTrack] = useState(-1);
  const [menu, setMenu] = useState(false);
  const [fs, setFs] = useState(false);
  const [error, setError] = useState(false);
  const [resumed, setResumed] = useState<number | null>(null);
  const [idle, setIdle] = useState(false);
  const [wm, setWm] = useState({ x: 8, y: 10 });
  const acc = useRef(0); // seconds played since last heartbeat
  const lastT = useRef<number | null>(null);

  // ---- source
  useEffect(() => {
    const v = video.current;
    if (!v) return;
    let cancelled = false;
    setError(false);
    const start = () => {
      if (pb.startAtSec > 5) {
        v.currentTime = pb.startAtSec;
        setResumed(pb.startAtSec);
      }
    };
    if (pb.kind === 'hls' && !v.canPlayType('application/vnd.apple.mpegurl')) {
      void import('hls.js/dist/hls.light.mjs').then(({ default: Hls }) => {
        if (cancelled) return;
        if (!Hls.isSupported()) return setError(true);
        const hls = new Hls({
          capLevelToPlayerSize: true,
          startPosition: pb.startAtSec > 5 ? pb.startAtSec : -1,
        });
        hlsRef.current = hls as unknown as typeof hlsRef.current;
        hls.loadSource(pb.url);
        hls.attachMedia(v);
        hls.on(Hls.Events.MANIFEST_PARSED, (_e, d) => {
          const ls = d.levels.map((l, i) => ({ index: i, height: l.height })).filter((l) => l.height);
          setLevels(ls);
          if (read(LOW_DATA_KEY) === '1' && ls.length) {
            const low = ls.reduce((a, b) => (Math.abs(b.height - 360) < Math.abs(a.height - 360) ? b : a));
            hls.currentLevel = low.index;
            setLevel(low.index);
          }
          if (pb.startAtSec > 5) setResumed(pb.startAtSec);
        });
        hls.on(Hls.Events.ERROR, (_e, d) => {
          if (d.fatal) setError(true);
        });
      });
    } else {
      v.src = pb.url;
      v.addEventListener('loadedmetadata', start, { once: true });
    }
    return () => {
      cancelled = true;
      hlsRef.current?.destroy();
      hlsRef.current = null;
      v.removeAttribute('src');
    };
  }, [pb.url, pb.kind, pb.startAtSec]);

  useEffect(() => {
    if (video.current) video.current.playbackRate = speed;
  }, [speed, pb.url]);

  if (handleRef)
    handleRef.current = {
      time: () => video.current?.currentTime ?? 0,
      seek: (s) => video.current && (video.current.currentTime = s),
    };

  // ---- heartbeat (counts only real playback; 15 s)
  const flush = useCallback(async () => {
    const v = video.current;
    if (!v) return;
    const delta = Math.round(acc.current);
    acc.current = 0;
    const ok = await onHeartbeat(Math.round(v.currentTime), Math.min(60, delta));
    if (!ok) {
      v.pause();
      onTakeover();
    }
  }, [onHeartbeat, onTakeover]);

  useEffect(() => {
    if (!playing) return;
    const id = window.setInterval(() => void flush(), 15_000);
    return () => window.clearInterval(id);
  }, [playing, flush]);

  // ---- moving watermark
  useEffect(() => {
    const id = window.setInterval(
      () => setWm({ x: 5 + Math.random() * 60, y: 6 + Math.random() * 70 }),
      20_000,
    );
    return () => window.clearInterval(id);
  }, []);

  // ---- auto-hide controls
  const poke = useCallback(() => {
    setIdle(false);
  }, []);
  useEffect(() => {
    if (!playing || menu || idle) return;
    const id = window.setTimeout(() => setIdle(true), 2500);
    return () => window.clearTimeout(id);
  }, [playing, menu, idle, time]);

  useEffect(() => {
    const on = () => setFs(document.fullscreenElement === wrap.current);
    document.addEventListener('fullscreenchange', on);
    return () => document.removeEventListener('fullscreenchange', on);
  }, []);

  const toggle = () => {
    const v = video.current;
    if (!v) return;
    if (v.paused) void v.play().catch(() => undefined);
    else v.pause();
  };
  const seekBy = (d: number) => {
    const v = video.current;
    if (v) v.currentTime = Math.max(0, Math.min(v.duration || Infinity, v.currentTime + d));
  };
  const setRate = (r: number) => {
    setSpeed(r);
    write(SPEED_KEY, String(r));
  };
  const chooseLevel = (i: number) => {
    if (hlsRef.current) hlsRef.current.currentLevel = i;
    setLevel(i);
  };
  const toggleLowData = () => {
    const next = !lowData;
    setLowData(next);
    write(LOW_DATA_KEY, next ? '1' : '0');
    if (next && levels.length)
      chooseLevel(
        levels.reduce((a, b) => (Math.abs(b.height - 360) < Math.abs(a.height - 360) ? b : a)).index,
      );
    if (!next) chooseLevel(-1);
  };
  const chooseTrack = (i: number) => {
    tracks.forEach((tr, j) => (tr.mode = j === i ? 'showing' : 'disabled'));
    setTrack(i);
  };
  const toggleFs = () => {
    if (document.fullscreenElement) void document.exitFullscreen();
    else void wrap.current?.requestFullscreen?.().catch(() => undefined);
  };

  const onKey = (e: React.KeyboardEvent) => {
    if ((e.target as HTMLElement).closest('input,textarea,select,[role="menu"]')) return;
    const k = e.key;
    const map: Record<string, () => void> = {
      ' ': toggle,
      k: toggle,
      ArrowLeft: () => seekBy(-5),
      ArrowRight: () => seekBy(5),
      j: () => seekBy(-10),
      l: () => seekBy(10),
      m: () => {
        if (video.current) video.current.muted = !video.current.muted;
      },
      f: toggleFs,
      '>': () => setRate(SPEEDS[Math.min(SPEEDS.length - 1, SPEEDS.indexOf(speed as 1) + 1)] ?? speed),
      '<': () => setRate(SPEEDS[Math.max(0, SPEEDS.indexOf(speed as 1) - 1)] ?? speed),
    };
    const fn = map[k] ?? map[k.toLowerCase()];
    if (fn) {
      e.preventDefault();
      fn();
      poke();
    }
  };

  const pct = dur ? (time / dur) * 100 : 0;
  const showControls = !error && (!playing || !idle || menu);

  return (
    <div
      ref={wrap}
      className={cn(
        'group relative aspect-video w-full overflow-hidden bg-black text-white outline-none',
        fs ? 'h-full' : 'rounded-none sm:rounded-card',
        !showControls && 'cursor-none',
      )}
      tabIndex={0}
      onKeyDown={onKey}
      onMouseMove={poke}
      onTouchStart={poke}
      aria-label={t('vp.shortcuts')}
    >
      <video
        ref={video}
        className="size-full"
        playsInline
        preload="metadata"
        onClick={toggle}
        onDoubleClick={toggleFs}
        onPlay={() => {
          setPlaying(true);
          lastT.current = video.current?.currentTime ?? 0;
        }}
        onPause={() => {
          setPlaying(false);
          void flush();
        }}
        onEnded={() => {
          setPlaying(false);
          void flush().then(onEnded);
        }}
        onTimeUpdate={(e) => {
          const v = e.currentTarget;
          const prev = lastT.current;
          // count forward progress only (seeks don't count), scaled to real time at higher speeds
          if (prev !== null && !v.paused) {
            const d = v.currentTime - prev;
            if (d > 0 && d < 2 * Math.max(1, speed)) acc.current += d / speed;
          }
          lastT.current = v.currentTime;
          setTime(v.currentTime);
          if (v.buffered.length) setBuffered(v.buffered.end(v.buffered.length - 1));
        }}
        onSeeked={(e) => (lastT.current = e.currentTarget.currentTime)}
        onDurationChange={(e) => setDur(e.currentTarget.duration || 0)}
        onVolumeChange={(e) => setMuted(e.currentTarget.muted)}
        onLoadedMetadata={(e) => {
          const list = Array.from(e.currentTarget.textTracks).filter(
            (x) => x.kind === 'subtitles' || x.kind === 'captions',
          );
          setTracks(list);
        }}
        onError={() => setError(true)}
      />

      {/* moving watermark — deters screen recording, never blocks clicks */}
      <span
        aria-hidden
        className="pointer-events-none absolute select-none whitespace-nowrap text-[11px] font-semibold text-white/35 transition-[left,top] duration-[1500ms] ease-in-out [text-shadow:0_1px_2px_rgba(0,0,0,.4)] sm:text-xs"
        style={{ left: `${wm.x}%`, top: `${wm.y}%` }}
      >
        {pb.watermark}
      </span>

      {resumed !== null && !error && (
        <div className="absolute left-3 top-3 flex items-center gap-2 rounded-full bg-black/70 py-1 pl-3 pr-1 text-xs">
          <span>{t('vp.resumed', { time: localDigits(fmtTime(resumed), t.locale) })}</span>
          <button
            type="button"
            className="rounded-full bg-white/15 px-2.5 py-1 font-semibold hover:bg-white/25"
            onClick={() => {
              if (video.current) video.current.currentTime = 0;
              setResumed(null);
            }}
          >
            {t('vp.startOver')}
          </button>
          <button
            type="button"
            className="grid size-6 place-items-center rounded-full hover:bg-white/15"
            onClick={() => setResumed(null)}
            aria-label={t('common.close')}
          >
            ×
          </button>
        </div>
      )}

      {error && (
        <div className="absolute inset-0 grid place-items-center bg-black/80 p-6 text-center">
          <div>
            <p className="font-semibold">{t('vp.error')}</p>
            <p className="mt-1 text-sm text-white/70">{t('vp.errorBody')}</p>
            <Button className="mt-4" size="sm" variant="secondary" onClick={() => window.location.reload()}>
              {t('vp.retry')}
            </Button>
          </div>
        </div>
      )}

      {!playing && !error && (
        <button
          type="button"
          onClick={toggle}
          className="absolute left-1/2 top-1/2 grid size-16 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-primary/90 shadow-e2 transition-transform duration-150 hover:scale-105"
          aria-label={t('vp.play')}
        >
          <Play className="ml-1 size-7 fill-current" aria-hidden />
        </button>
      )}

      {/* controls */}
      <div
        className={cn(
          'absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 via-black/40 to-transparent px-3 pb-2 pt-10 transition-opacity duration-200',
          showControls ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
      >
        <div className="relative h-4">
          <div className="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-white/25">
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-white/35"
              style={{ width: `${dur ? (buffered / dur) * 100 : 0}%` }}
            />
            <div className="absolute inset-y-0 left-0 rounded-full bg-primary" style={{ width: `${pct}%` }} />
          </div>
          <input
            type="range"
            min={0}
            max={dur || 0}
            step={1}
            value={time}
            onChange={(e) => {
              if (video.current) video.current.currentTime = Number(e.target.value);
            }}
            aria-label={t('vp.seek')}
            aria-valuetext={`${fmtTime(time)} / ${fmtTime(dur)}`}
            className="absolute inset-0 w-full cursor-pointer appearance-none bg-transparent opacity-0 focus-visible:opacity-100 [&::-webkit-slider-thumb]:size-3.5 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white"
          />
        </div>
        <div className="mt-1 flex items-center gap-1">
          <CtrlBtn label={playing ? t('vp.pause') : t('vp.play')} onClick={toggle}>
            {playing ? <Pause className="size-5 fill-current" /> : <Play className="size-5 fill-current" />}
          </CtrlBtn>
          <CtrlBtn label={t('vp.back10')} onClick={() => seekBy(-10)}>
            <RotateCcw className="size-5" />
          </CtrlBtn>
          <CtrlBtn label={t('vp.fwd10')} onClick={() => seekBy(10)}>
            <RotateCw className="size-5" />
          </CtrlBtn>
          <CtrlBtn
            label={muted ? t('vp.unmute') : t('vp.mute')}
            onClick={() => video.current && (video.current.muted = !video.current.muted)}
            className="hidden sm:grid"
          >
            {muted ? <VolumeX className="size-5" /> : <Volume2 className="size-5" />}
          </CtrlBtn>
          <span className="latin ml-1 text-xs tabular-nums text-white/85">
            {fmtTime(time)} / {fmtTime(dur)}
          </span>
          <span className="flex-1" />
          {speed !== 1 && (
            <span className="latin rounded bg-white/15 px-1.5 text-xs font-semibold">{speed}×</span>
          )}
          <div className="relative">
            <CtrlBtn
              label={t('vp.speed')}
              onClick={() => setMenu((m) => !m)}
              aria-expanded={menu}
              aria-haspopup="menu"
            >
              <Settings2 className="size-5" />
            </CtrlBtn>
            {menu && (
              <div
                role="menu"
                className="absolute bottom-11 right-0 z-10 w-60 rounded-card border border-white/10 bg-slate-900/95 p-2 text-sm shadow-e3"
                onKeyDown={(e) => e.key === 'Escape' && setMenu(false)}
              >
                <MenuHead icon={<Gauge className="size-4" />}>{t('vp.speed')}</MenuHead>
                <div className="grid grid-cols-4 gap-1 px-1 pb-2">
                  {SPEEDS.map((s) => (
                    <button
                      key={s}
                      type="button"
                      role="menuitemradio"
                      aria-checked={speed === s}
                      onClick={() => setRate(s)}
                      className={cn(
                        'latin h-8 rounded-control text-xs font-semibold',
                        speed === s ? 'bg-primary text-white' : 'hover:bg-white/10',
                      )}
                    >
                      {s}×
                    </button>
                  ))}
                </div>
                {levels.length > 0 && (
                  <>
                    <MenuHead icon={<Settings2 className="size-4" />}>{t('vp.quality')}</MenuHead>
                    <div className="flex flex-wrap gap-1 px-1 pb-2">
                      <QBtn active={level === -1} onClick={() => chooseLevel(-1)}>
                        {t('vp.auto')}
                      </QBtn>
                      {[...levels]
                        .sort((a, b) => b.height - a.height)
                        .map((l) => (
                          <QBtn key={l.index} active={level === l.index} onClick={() => chooseLevel(l.index)}>
                            {l.height}p
                          </QBtn>
                        ))}
                    </div>
                  </>
                )}
                <label className="flex cursor-pointer items-center justify-between gap-2 rounded-control px-2 py-2 hover:bg-white/10">
                  <span>{t('vp.lowData')}</span>
                  <input
                    type="checkbox"
                    checked={lowData}
                    onChange={toggleLowData}
                    className="size-4 accent-[var(--color-primary)]"
                  />
                </label>
                {tracks.length > 0 && (
                  <>
                    <MenuHead icon={<Captions className="size-4" />}>{t('vp.captions')}</MenuHead>
                    <div className="flex flex-wrap gap-1 px-1 pb-1">
                      <QBtn active={track === -1} onClick={() => chooseTrack(-1)}>
                        {t('vp.captionsOff')}
                      </QBtn>
                      {tracks.map((tr, i) => (
                        <QBtn key={i} active={track === i} onClick={() => chooseTrack(i)}>
                          {tr.label || tr.language || String(i + 1)}
                        </QBtn>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
          <CtrlBtn label={fs ? t('vp.exitFullscreen') : t('vp.fullscreen')} onClick={toggleFs}>
            {fs ? <Minimize className="size-5" /> : <Maximize className="size-5" />}
          </CtrlBtn>
        </div>
      </div>
    </div>
  );
}

function CtrlBtn({
  label,
  children,
  className,
  ...rest
}: React.ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      className={cn(
        'grid size-10 place-items-center rounded-full transition-colors hover:bg-white/15 focus-visible:outline-2 focus-visible:outline-white',
        className,
      )}
      {...rest}
    >
      <span aria-hidden className="contents">
        {children}
      </span>
    </button>
  );
}
function MenuHead({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <p className="flex items-center gap-1.5 px-2 pb-1.5 pt-1 text-xs font-semibold text-white/60">
      {icon}
      {children}
    </p>
  );
}
function QBtn({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      role="menuitemradio"
      aria-checked={active}
      onClick={onClick}
      className={cn(
        'latin h-8 rounded-control px-2.5 text-xs font-semibold',
        active ? 'bg-primary text-white' : 'hover:bg-white/10',
      )}
    >
      {children}
    </button>
  );
}

/** YouTube / external embeds: same frame + watermark; progress comes from manual completion. */
export function EmbedPlayer({ pb }: { pb: Playback }) {
  return (
    <div className="relative aspect-video w-full overflow-hidden bg-black sm:rounded-card">
      {pb.kind === 'youtube' ? (
        <iframe
          src={pb.url}
          title="video"
          className="size-full"
          allow="accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen"
          allowFullScreen
          referrerPolicy="strict-origin-when-cross-origin"
        />
      ) : (
        <video src={pb.url} controls playsInline className="size-full" />
      )}
      <span
        aria-hidden
        className="pointer-events-none absolute right-3 top-3 select-none text-xs font-semibold text-white/40 [text-shadow:0_1px_2px_rgba(0,0,0,.5)]"
      >
        {pb.watermark}
      </span>
    </div>
  );
}
