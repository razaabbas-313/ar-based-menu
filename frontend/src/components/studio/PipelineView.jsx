import { CircleCheck, LoaderCircle, TriangleAlert } from 'lucide-react';
import { cx } from '../../utils/format.js';

/** Animated multi-stage progress for the upload pipelines. */
export default function PipelineView({ stages, overall, title = 'Publishing to menu' }) {
  return (
    <div className="rounded-3xl border hairline bg-black/30 p-4">
      <div className="mb-4 flex items-center justify-between">
        <div className="font-mono text-[10px] uppercase tracking-[0.22em] text-zinc-500">{title}</div>
        <div className="font-mono text-xs text-amber-300">{Math.round(overall * 100)}%</div>
      </div>
      <div
        className="relative h-1.5 overflow-hidden rounded-full bg-zinc-800"
        role="progressbar"
        aria-valuenow={Math.round(overall * 100)}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <div
          className="h-full rounded-full bg-gradient-to-r from-amber-600 via-amber-400 to-amber-300 transition-[width] duration-300 ease-out"
          style={{ width: `${overall * 100}%` }}
        />
        {overall > 0 && overall < 1 && <div className="shimmer absolute inset-0" />}
      </div>
      <ol className="mt-4 space-y-3">
        {stages.map((s, i) => {
          const Icon = s.status === 'done' ? CircleCheck : s.status === 'active' ? LoaderCircle : s.status === 'error' ? TriangleAlert : s.icon;
          return (
            <li key={s.key} className="flex gap-3">
              <span
                className={cx(
                  'grid h-9 w-9 shrink-0 place-items-center rounded-full border transition-colors',
                  s.status === 'done'
                    ? 'border-amber-400/50 bg-amber-500 text-zinc-950'
                    : s.status === 'active'
                      ? 'border-amber-400/60 text-amber-300'
                      : s.status === 'error'
                        ? 'border-red-500/50 bg-red-500/15 text-red-300'
                        : 'hairline text-zinc-600',
                )}
              >
                <Icon size={16} className={s.status === 'active' ? 'animate-spin' : ''} aria-hidden="true" />
              </span>
              <div className="min-w-0 pt-1">
                <div className={cx('text-sm font-semibold', s.status === 'pending' ? 'text-zinc-500' : 'text-zinc-100')}>
                  {i + 1}. {s.label}
                </div>
                {s.detail && (
                  <div className={cx('mt-0.5 break-words font-mono text-[11px]', s.status === 'error' ? 'text-red-300' : 'text-zinc-500')}>
                    {s.detail}
                  </div>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

/** Helpers to drive a stage list from upload callbacks. */
export function makeStages(defs) {
  return defs.map((d) => ({ ...d, status: 'pending', detail: '' }));
}

export function overallProgress(stages, activeFraction = 0) {
  const done = stages.filter((s) => s.status === 'done').length;
  const streaming = stages.some((s) => s.status === 'active' && s.streams);
  return Math.min(1, (done + (streaming ? activeFraction : 0)) / Math.max(1, stages.length));
}
