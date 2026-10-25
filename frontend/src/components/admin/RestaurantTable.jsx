import { ArrowDown, ArrowUp, Eye, MoreHorizontal, Pause, Pencil, Play, RotateCcw, Trash2 } from 'lucide-react';
import { STATUS_STYLE, cx, fmtBytes, fmtDate } from '../../utils/format.js';

const COLUMNS = [
  { key: 'name', label: 'Restaurant', sortable: true },
  { key: 'status', label: 'Status', sortable: true },
  { key: 'dishes', label: 'Dishes', sortable: true },
  { key: 'models', label: '3D', sortable: true },
  { key: 'storage', label: 'Storage', sortable: true },
  { key: 'createdAt', label: 'Joined', sortable: true },
];

/** Restaurants table (desktop) and cards (phones). */
export default function RestaurantTable({ rows, sort, direction, onSort, actions }) {
  return (
    <>
      <div className="hidden overflow-hidden rounded-3xl border hairline lg:block">
        <table className="w-full text-left text-sm">
          <thead className="bg-black/40 text-[10px] uppercase tracking-[0.2em] text-zinc-500">
            <tr>
              {COLUMNS.map((c) => (
                <th key={c.key} className="px-4 py-3 font-semibold">
                  <button type="button" onClick={() => onSort(c.key)} className={cx('flex items-center gap-1 uppercase', sort === c.key && 'text-amber-300')}>
                    {c.label}
                    {sort === c.key && (direction === 'asc' ? <ArrowUp size={12} aria-hidden="true" /> : <ArrowDown size={12} aria-hidden="true" />)}
                  </button>
                </th>
              ))}
              <th className="px-4 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800/80">
            {rows.map((r) => (
              <tr key={r.id} className="hover:bg-white/[0.02]" data-testid="restaurant-row">
                <td className="px-4 py-3">
                  <button type="button" onClick={() => actions.open(r)} className="flex items-center gap-3 text-left">
                    <Logo r={r} />
                    <span className="min-w-0">
                      <span className="block truncate font-semibold text-zinc-100 hover:text-amber-200">{r.name}</span>
                      <span className="block truncate font-mono text-[11px] text-zinc-500">/r/{r.slug} · {r.email}</span>
                    </span>
                  </button>
                </td>
                <td className="px-4 py-3">
                  <StatusBadge r={r} />
                </td>
                <td className="px-4 py-3 font-mono text-zinc-300">{r.dishCount}</td>
                <td className="px-4 py-3 font-mono text-zinc-300">
                  <div className="flex items-center gap-1.5">
                    <span>{r.model3dCount}</span>
                    {r.canUpload3d ? (
                      <span className="rounded border border-emerald-500/30 bg-emerald-500/10 px-1.5 py-0.5 text-[9px] font-sans font-semibold text-emerald-300" title="Direct 3D model uploads enabled in studio">
                        Direct 3D
                      </span>
                    ) : (
                      <span className="rounded border border-zinc-700 bg-zinc-800/60 px-1.5 py-0.5 text-[9px] font-sans text-zinc-400" title="Concierge request only">
                        Concierge
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="font-mono text-zinc-200">{fmtBytes(r.storageUsedBytes)}</div>
                  <div className="text-[10px] uppercase tracking-wider text-zinc-500">{r.bandwidthTier}</div>
                </td>
                <td className="px-4 py-3 text-xs text-zinc-500">{fmtDate(r.createdAt)}</td>
                <td className="px-4 py-3">
                  <Actions r={r} actions={actions} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="grid gap-3 lg:hidden">
        {rows.map((r) => (
          <article key={r.id} className="rounded-3xl border hairline bg-onyx p-4" data-testid="restaurant-card">
            <button type="button" onClick={() => actions.open(r)} className="flex w-full items-center gap-3 text-left">
              <Logo r={r} />
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold text-zinc-100">{r.name}</span>
                <span className="block truncate font-mono text-[11px] text-zinc-500">/r/{r.slug}</span>
              </span>
              <StatusBadge r={r} />
            </button>
            <div className="mt-3 grid grid-cols-3 gap-2 text-center">
              <Mini label="Dishes" value={r.dishCount} />
              <Mini label="3D" value={r.model3dCount} />
              <Mini label="Storage" value={fmtBytes(r.storageUsedBytes)} />
            </div>
            <div className="mt-3 border-t hairline pt-3">
              <Actions r={r} actions={actions} />
            </div>
          </article>
        ))}
      </div>
    </>
  );
}

function Logo({ r }) {
  return (
    <span className="grid h-10 w-10 shrink-0 place-items-center overflow-hidden rounded-xl border border-amber-500/30 bg-black">
      {r.logoUrl ? <img src={r.logoUrl} alt="" className="h-full w-full object-cover" /> : <span className="font-display text-lg italic text-amber-300">{r.name.charAt(0)}</span>}
    </span>
  );
}

function StatusBadge({ r }) {
  const s = r.deletedAt ? 'DELETED' : r.status;
  return (
    <span className="inline-flex shrink-0 flex-col items-start gap-1">
      <span className={cx('inline-flex rounded-full border px-2 py-0.5 text-[9px] font-bold uppercase tracking-[0.16em]', STATUS_STYLE[s])}>{s}</span>
      {r.mustChangePassword && !r.deletedAt && (
        <span className="text-[9px] font-semibold uppercase tracking-[0.12em] text-amber-300/80" title="The owner still uses the temporary password">
          Temp password
        </span>
      )}
    </span>
  );
}

function Mini({ label, value }) {
  return (
    <div className="rounded-2xl bg-black/30 px-2 py-2">
      <div className="text-[9px] uppercase tracking-[0.2em] text-zinc-500">{label}</div>
      <div className="font-mono text-sm text-zinc-200">{value}</div>
    </div>
  );
}

function Actions({ r, actions }) {
  const btn = 'tap grid h-10 w-10 place-items-center rounded-full border hairline text-zinc-300 hover:border-amber-500/40 hover:text-amber-300';
  if (r.deletedAt) {
    return (
      <div className="flex items-center justify-end gap-1.5">
        <button type="button" onClick={() => actions.restore(r)} className="tap flex h-10 items-center gap-1.5 rounded-full border hairline px-3 text-xs font-semibold text-zinc-200">
          <RotateCcw size={14} aria-hidden="true" /> Restore
        </button>
        <button type="button" onClick={() => actions.remove(r)} className={cx(btn, 'hover:border-red-500/40 hover:text-red-300')} aria-label={`Delete ${r.name}`} title="Delete permanently">
          <Trash2 size={15} aria-hidden="true" />
        </button>
      </div>
    );
  }
  const suspended = r.status === 'SUSPENDED';
  return (
    <div className="flex items-center justify-end gap-1.5">
      <button type="button" onClick={() => actions.edit(r)} className={btn} aria-label={`Edit ${r.name}`} title="Edit">
        <Pencil size={15} aria-hidden="true" />
      </button>
      <button
        type="button"
        onClick={() => actions.status(r, suspended ? 'ACTIVE' : 'SUSPENDED')}
        className={cx(btn, suspended ? 'text-emerald-300' : '')}
        aria-label={suspended ? `Activate ${r.name}` : `Suspend ${r.name}`}
        title={suspended ? 'Activate' : 'Suspend'}
      >
        {suspended ? <Play size={15} aria-hidden="true" /> : <Pause size={15} aria-hidden="true" />}
      </button>
      <a href={`/r/${r.slug}`} target="_blank" rel="noreferrer" className={btn} aria-label={`View ${r.name} menu`} title="View menu">
        <Eye size={15} aria-hidden="true" />
      </a>
      <button type="button" onClick={() => actions.open(r)} className={btn} aria-label={`Details for ${r.name}`} title="Deep dive">
        <MoreHorizontal size={15} aria-hidden="true" />
      </button>
      <button type="button" onClick={() => actions.remove(r)} className={cx(btn, 'hover:border-red-500/40 hover:text-red-300')} aria-label={`Delete ${r.name}`} title="Delete">
        <Trash2 size={15} aria-hidden="true" />
      </button>
    </div>
  );
}
