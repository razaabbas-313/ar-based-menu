import { useState } from 'react';
import { Box, Crown, Search, SlidersHorizontal, X } from 'lucide-react';
import { IconButton, Overlay } from '../ui.jsx';
import { buzz, cx } from '../../utils/format.js';

/** Sticky bottom search + experience filter drawer. */
export default function FilterBar({ query, onQuery, filters, onFilters, resultCount, menuMode }) {
  const [open, setOpen] = useState(false);
  const activeCount = (filters.chefPicks ? 1 : 0) + (filters.arOnly ? 1 : 0);

  return (
    <>
      <div className="glass pb-safe fixed inset-x-0 bottom-0 z-30 border-t hairline">
        <div className="mx-auto flex max-w-3xl items-center gap-2 px-4 py-3 sm:px-6">
          <label className="flex h-12 flex-1 items-center gap-2 rounded-full border hairline bg-black/40 px-4 focus-within:border-accent-500/60">
            <Search size={17} className="shrink-0 text-zinc-500" aria-hidden="true" />
            <input
              value={query}
              onChange={(e) => onQuery(e.target.value)}
              placeholder="Search dishes, ingredients…"
              className="h-full min-w-0 flex-1 bg-transparent text-[15px] text-zinc-100 placeholder:text-zinc-600 focus:outline-none"
              aria-label="Search the menu"
            />
            {query && (
              <button type="button" onClick={() => onQuery('')} aria-label="Clear search" className="text-zinc-500 hover:text-zinc-200">
                <X size={16} aria-hidden="true" />
              </button>
            )}
          </label>
          <button
            type="button"
            onClick={() => {
              buzz();
              setOpen(true);
            }}
            className={cx(
              'tap relative flex h-12 shrink-0 items-center gap-2 rounded-full border px-4 text-[13px] font-semibold',
              activeCount ? 'border-accent-400/60 bg-accent-500/15 text-accent-300' : 'hairline text-zinc-300',
            )}
          >
            <SlidersHorizontal size={17} aria-hidden="true" />
            Filters
            {activeCount > 0 && (
              <span className="grid h-5 min-w-5 place-items-center rounded-full bg-accent-500 px-1 text-[11px] font-bold text-zinc-950">{activeCount}</span>
            )}
          </button>
        </div>
      </div>

      {open && (
        <Overlay onClose={() => setOpen(false)} align="bottom" label="Filters">
          <div className="sheet pb-safe relative z-10 w-full max-w-lg rounded-t-[28px] border hairline bg-onyx p-5 sm:rounded-[28px]">
            <div className="flex items-center justify-between">
              <h3 className="font-display text-3xl font-semibold">Filters</h3>
              <IconButton icon={X} label="Close filters" onClick={() => setOpen(false)} />
            </div>
            <div className="mt-5 text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Experience</div>
            <div className="mt-2 flex flex-wrap gap-2">
              <Chip active={filters.chefPicks} onClick={() => onFilters({ ...filters, chefPicks: !filters.chefPicks })} icon={Crown}>
                Chef picks
              </Chip>
              {menuMode!=='SIMPLE' && <Chip active={filters.arOnly} onClick={() => onFilters({ ...filters, arOnly: !filters.arOnly })} icon={Box}>
                {menuMode==='THREE_D'?'3D models only':'3D & AR only'}
              </Chip>}
            </div>
            <div className="mt-6 grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => onFilters({ tags: [], chefPicks: false, arOnly: false })}
                className="tap h-12 rounded-2xl border hairline text-sm font-semibold text-zinc-300"
              >
                Clear all
              </button>
              <button type="button" onClick={() => setOpen(false)} className="tap h-12 rounded-2xl bg-accent-500 text-sm font-bold text-zinc-950">
                Show {resultCount} dish{resultCount === 1 ? '' : 'es'}
              </button>
            </div>
          </div>
        </Overlay>
      )}
    </>
  );
}

function Chip({ active, onClick, children, icon: Icon }) {
  return (
    <button
      type="button"
      onClick={() => {
        buzz();
        onClick();
      }}
      aria-pressed={active}
      className={cx(
        'tap flex h-11 items-center gap-2 rounded-full border px-4 text-sm font-semibold',
        active ? 'border-accent-400/70 bg-accent-500/15 text-accent-300' : 'hairline text-zinc-300 hover:border-zinc-600',
      )}
    >
      {Icon && <Icon size={15} aria-hidden="true" />}
      {children}
    </button>
  );
}
