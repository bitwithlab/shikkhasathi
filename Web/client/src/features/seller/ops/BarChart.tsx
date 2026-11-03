import { useId, useState, type KeyboardEvent } from 'react';
import { cn } from '@/lib/cn';

export interface BarDatum {
  label: string;
  value: number;
}

/** "Nice" ceiling for the y-axis: 1, 2, 2.5 or 5 × 10^n. */
export function niceMax(v: number) {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  const n = v / p;
  return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 2.5 ? 2.5 : n <= 5 ? 5 : 10) * p;
}

/**
 * Lightweight single-series bar chart (inline SVG, no chart library). One hue from the tokens, recessive
 * grid, rounded data-ends, hover/focus tooltip and arrow-key navigation, plus a visually hidden table.
 */
export function BarChart({
  data,
  format,
  axisFormat = format,
  color = 'var(--ss-primary)',
  title,
  tableLabels,
  height = 180,
}: {
  data: BarDatum[];
  format: (v: number) => string;
  axisFormat?: (v: number) => string;
  color?: string;
  title: string;
  tableLabels: [string, string];
  height?: number;
}) {
  const [active, setActive] = useState<number | null>(null);
  const id = useId();
  const max = niceMax(Math.max(0, ...data.map((d) => d.value)));
  const n = Math.max(1, data.length);
  const slot = 100 / n;
  const gap = n > 45 ? 0.15 : n > 20 ? 0.25 : 0.3;
  const bw = slot * (1 - gap);
  const key = (e: KeyboardEvent) => {
    if (!data.length) return;
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
      e.preventDefault();
      setActive((a) =>
        Math.min(
          data.length - 1,
          Math.max(0, (a ?? (e.key === 'ArrowRight' ? -1 : data.length)) + (e.key === 'ArrowRight' ? 1 : -1)),
        ),
      );
    } else if (e.key === 'Escape') setActive(null);
  };
  const cur = active != null ? data[active] : null;
  const ticks = [0, 0.5, 1];
  const mid = Math.floor((data.length - 1) / 2);

  return (
    <figure className="m-0">
      <div className="flex gap-2">
        {/* y-axis labels */}
        <div
          className="latin flex shrink-0 flex-col justify-between text-right text-[11px] text-muted tnum"
          style={{ height }}
          aria-hidden
        >
          {[...ticks].reverse().map((tk) => (
            <span key={tk} className="-my-1.5 leading-3">
              {axisFormat(max * tk)}
            </span>
          ))}
        </div>
        <div className="min-w-0 flex-1">
          <div
            className="relative rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-primary"
            style={{ height }}
            tabIndex={0}
            role="group"
            aria-label={title}
            aria-describedby={`${id}-tip`}
            onKeyDown={key}
            onMouseLeave={() => setActive(null)}
            onBlur={() => setActive(null)}
          >
            <svg
              viewBox="0 0 100 100"
              preserveAspectRatio="none"
              className="absolute inset-0 size-full overflow-visible"
              aria-hidden
            >
              {ticks.map((tk) => (
                <line
                  key={tk}
                  x1="0"
                  x2="100"
                  y1={100 - tk * 100}
                  y2={100 - tk * 100}
                  stroke="var(--ss-border)"
                  strokeWidth="1"
                  vectorEffect="non-scaling-stroke"
                  strokeDasharray={tk === 0 ? undefined : '3 3'}
                />
              ))}
              {data.map((d, i) => {
                const h = (d.value / max) * 100;
                const x = i * slot + (slot - bw) / 2;
                return (
                  <g key={i}>
                    {d.value > 0 && (
                      <rect
                        x={x}
                        y={100 - h}
                        width={bw}
                        height={h}
                        fill={color}
                        opacity={active == null || active === i ? 1 : 0.45}
                        rx={Math.min(bw / 2, 0.8)}
                        className="transition-opacity duration-150"
                      />
                    )}
                    <rect
                      x={i * slot}
                      y="0"
                      width={slot}
                      height="100"
                      fill="transparent"
                      onMouseEnter={() => setActive(i)}
                    />
                  </g>
                );
              })}
            </svg>
            {cur && active != null && (
              <div
                className={cn(
                  'pointer-events-none absolute -top-2 z-10 -translate-y-full whitespace-nowrap rounded-control border border-border bg-surface px-2.5 py-1.5 text-xs shadow-e2',
                  active > n * 0.66 ? '-translate-x-full' : active > n * 0.33 ? '-translate-x-1/2' : '',
                )}
                style={{ left: `${(active + 0.5) * slot}%` }}
              >
                <span className="block text-muted">{cur.label}</span>
                <span className="latin block font-semibold tnum">{format(cur.value)}</span>
              </div>
            )}
            <span id={`${id}-tip`} className="sr-only" aria-live="polite">
              {cur ? `${cur.label}: ${format(cur.value)}` : ''}
            </span>
          </div>
          {data.length > 0 && (
            <div className="latin mt-1.5 flex justify-between text-[11px] text-muted" aria-hidden>
              <span>{data[0]!.label}</span>
              {data.length > 2 && <span>{data[mid]!.label}</span>}
              <span>{data[data.length - 1]!.label}</span>
            </div>
          )}
        </div>
      </div>
      <table className="sr-only">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th scope="col">{tableLabels[0]}</th>
            <th scope="col">{tableLabels[1]}</th>
          </tr>
        </thead>
        <tbody>
          {data.map((d, i) => (
            <tr key={i}>
              <td>{d.label}</td>
              <td>{format(d.value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}
