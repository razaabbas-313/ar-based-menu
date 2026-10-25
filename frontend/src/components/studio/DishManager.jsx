import { useMemo, useState } from 'react';
import { Box, Camera, ImageOff, LoaderCircle, PackageX, Pencil, Plus, Search, Sparkles, Trash2, UtensilsCrossed } from 'lucide-react';
import DishEditModal from './DishEditModal.jsx';
import Request3dModal from './Request3dModal.jsx';
import ModelModal from './ModelModal.jsx';
import { Toggle } from './DishFields.jsx';
import { useToast } from '../Toast.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { api } from '../../api/client.js';
import { CATEGORY_LABEL, CATEGORY_ORDER, buzz, cx, fmtBytes, fmtDate, fmtPrice } from '../../utils/format.js';

/**
 * Tab 1: every dish of this restaurant (in and out of stock). Desktop gets a table, phones get
 * cards. Stock toggles are optimistic and roll back on failure.
 */
export default function DishManager({
  dishes,
  loading,
  error,
  currency,
  currencySymbol,
  currencyLabel,
  categories = [],
  onDishes,
  onNewDish,
  onReload,
  canUpload3d,
}) {
  const toast = useToast();
  const { restaurant } = useAuth();
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('ALL');
  const [editing, setEditing] = useState(null);
  const [request3dFor, setRequest3dFor] = useState(null);
  const [modelModalFor, setModelModalFor] = useState(null);
  const [busyIds, setBusyIds] = useState(() => new Set());

  const canDirectUpload = canUpload3d !== undefined ? Boolean(canUpload3d) : Boolean(restaurant?.canUpload3d);

  const setBusy = (id, on) =>
    setBusyIds((s) => {
      const next = new Set(s);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  const replace = (dish) => onDishes((list) => list.map((d) => (d.id === dish.id ? dish : d)));

  const categoryNames = useMemo(() => {
    if (categories && categories.length > 0) {
      const seen = new Set();
      const unique = [];
      for (const c of categories) {
        const norm = (c.name || '').trim();
        const key = norm.toLowerCase();
        if (norm && !seen.has(key)) {
          seen.add(key);
          unique.push(norm);
        }
      }
      return ['ALL', ...unique];
    }
    return ['ALL', ...CATEGORY_ORDER.map((c) => CATEGORY_LABEL[c] || c)];
  }, [categories]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return dishes
      .filter((d) => {
        if (category === 'ALL') return true;
        const dCat = d.categoryName || CATEGORY_LABEL[d.dishCategory] || d.dishCategory;
        return dCat === category;
      })
      .filter((d) => !q || d.dishTitle.toLowerCase().includes(q) || (d.description || '').toLowerCase().includes(q))
      .sort((a, b) => (a.dishTitle || '').localeCompare(b.dishTitle || ''));
  }, [dishes, query, category]);

  const counts = useMemo(() => {
    const c = { ALL: dishes.length };
    dishes.forEach((d) => {
      const dCat = d.categoryName || CATEGORY_LABEL[d.dishCategory] || d.dishCategory;
      c[dCat] = (c[dCat] || 0) + 1;
    });
    return c;
  }, [dishes]);

  async function toggleStock(dish) {
    buzz();
    const next = !dish.available;
    replace({ ...dish, available: next });
    setBusy(dish.id, true);
    try {
      const saved = await api.setAvailability(dish.id, next);
      replace(saved);
      toast(next ? `${dish.dishTitle} is back on the menu` : `${dish.dishTitle} marked out of stock`);
    } catch (e) {
      replace(dish);
      toast(e.message, 'error');
    } finally {
      setBusy(dish.id, false);
    }
  }

  async function remove(dish) {
    if (!window.confirm(`Delete “${dish.dishTitle}”? Its photo${dish.is3d ? ' and 3D model are' : ' is'} removed from S3. This cannot be undone.`)) return;
    setBusy(dish.id, true);
    try {
      await api.deleteDish(dish.id);
      onDishes((list) => list.filter((d) => d.id !== dish.id));
      toast(`${dish.dishTitle} deleted`);
    } catch (e) {
      toast(e.message, 'error');
      setBusy(dish.id, false);
    }
  }

  const actions = {
    toggleStock,
    remove,
    edit: setEditing,
    request3d: (d) => setRequest3dFor(d),
    upload3d: (d) => setModelModalFor(d),
    canDirectUpload,
  };

  if (loading) {
    return (
      <div className="grid place-items-center rounded-[28px] border hairline py-24">
        <LoaderCircle size={28} className="animate-spin text-amber-400" aria-hidden="true" />
      </div>
    );
  }
  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-[28px] border border-red-500/25 bg-red-500/5 px-6 py-14 text-center">
        <p className="max-w-md text-sm text-red-200">{error}</p>
        <button type="button" onClick={onReload} className="tap h-11 rounded-full border hairline px-5 text-sm text-zinc-200">
          Try again
        </button>
      </div>
    );
  }
  if (dishes.length === 0) {
    return (
      <div className="flex flex-col items-center rounded-[32px] border border-dashed border-amber-500/25 bg-gradient-to-b from-amber-500/[0.06] to-transparent px-6 py-20 text-center">
        <div className="grid h-16 w-16 place-items-center rounded-full border border-amber-500/30 bg-onyx">
          <UtensilsCrossed size={26} className="text-amber-300" aria-hidden="true" />
        </div>
        <h3 className="mt-5 font-display text-3xl font-semibold">Your menu is a blank canvas</h3>
        <p className="mt-2 max-w-md text-sm leading-relaxed text-zinc-400">
          Photograph your first creation. Request a white-glove 3D reconstruction to let guests place it on their table in AR.
        </p>
        <button type="button" onClick={onNewDish} className="tap shadow-fab mt-6 flex h-14 items-center gap-2 rounded-full bg-gradient-to-b from-amber-400 to-amber-600 px-6 text-[15px] font-bold text-zinc-950">
          <Plus size={19} aria-hidden="true" /> Create first dish
        </button>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-sm">
          <Search size={16} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-zinc-500" aria-hidden="true" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search dishes"
            aria-label="Search dishes"
            className="h-12 w-full rounded-full border hairline bg-black/40 pl-11 pr-4 text-sm text-zinc-50 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
          />
        </div>
        <button type="button" onClick={onNewDish} className="tap flex h-12 shrink-0 items-center justify-center gap-2 rounded-full bg-amber-500 px-5 text-sm font-bold text-zinc-950">
          <Plus size={17} aria-hidden="true" /> New dish
        </button>
      </div>
      <div className="no-scrollbar -mx-4 mb-5 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:px-0">
        {categoryNames.map((c) => (
          <button
            key={c}
            type="button"
            onClick={() => setCategory(c)}
            className={cx(
              'tap h-10 shrink-0 rounded-full border px-4 text-[13px] font-semibold',
              category === c ? 'border-amber-500 bg-amber-500 text-zinc-950' : 'hairline text-zinc-400 hover:text-zinc-200',
            )}
          >
            {c === 'ALL' ? 'All' : c} <span className="opacity-60">{counts[c] || 0}</span>
          </button>
        ))}
      </div>

      {visible.length === 0 ? (
        <p className="rounded-3xl border hairline px-6 py-12 text-center text-sm text-zinc-500">No dishes match this filter.</p>
      ) : (
        <>
          {/* Desktop table */}
          <div className="hidden overflow-hidden rounded-3xl border hairline md:block">
            <table className="w-full text-left text-sm">
              <thead className="bg-black/40 text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">Dish</th>
                  <th className="px-4 py-3 font-semibold">Category</th>
                  <th className="px-4 py-3 font-semibold">Price</th>
                  <th className="px-4 py-3 font-semibold">3D Status</th>
                  <th className="px-4 py-3 font-semibold">In stock</th>
                  <th className="px-4 py-3 text-right font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/80">
                {visible.map((d) => (
                  <tr key={d.id} className={cx('transition-colors hover:bg-white/[0.02]', !d.available && 'opacity-60')} data-testid="dish-row">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <Thumb dish={d} className="h-12 w-12" />
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 font-semibold text-zinc-100">
                            <span className="truncate">{d.dishTitle}</span>
                            {d.dishCode && (
                              <span className="shrink-0 font-mono text-[10px] font-semibold text-amber-400/90 border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.5 rounded">
                                {d.dishCode}
                              </span>
                            )}
                            {d.dailySpecial && <Sparkles size={13} className="shrink-0 text-amber-300" aria-label="Daily special" />}
                          </div>
                          <div className="truncate text-xs text-zinc-500">
                            {fmtDate(d.createdAt)}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-zinc-400">{d.categoryName || CATEGORY_LABEL[d.dishCategory] || d.dishCategory}</td>
                    <td className="px-4 py-3 font-mono text-zinc-200">{fmtPrice(d.priceTag, currency, currencySymbol)}</td>
                    <td className="px-4 py-3">
                      <MediaBadges dish={d} onRequest3d={actions.request3d} onUpload3d={actions.upload3d} canDirectUpload={canDirectUpload} />
                    </td>
                    <td className="px-4 py-3">
                      <Toggle on={d.available} onChange={() => !busyIds.has(d.id) && toggleStock(d)} label={d.available ? 'In stock' : 'Out of stock'} />
                    </td>
                    <td className="px-4 py-3">
                      <RowActions dish={d} busy={busyIds.has(d.id)} actions={actions} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile cards */}
          <div className="grid gap-3 md:hidden">
            {visible.map((d) => (
              <article key={d.id} className={cx('overflow-hidden rounded-3xl border hairline bg-onyx', !d.available && 'opacity-70')} data-testid="dish-card">
                <div className="flex gap-3 p-3">
                  <Thumb dish={d} className="h-20 w-20" />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <h3 className="line-clamp-2 font-display text-xl font-semibold leading-tight">{d.dishTitle}</h3>
                          {d.dishCode && (
                            <span className="shrink-0 font-mono text-[10px] font-semibold text-amber-400/90 border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.5 rounded">
                              {d.dishCode}
                            </span>
                          )}
                        </div>
                      </div>
                      <span className="shrink-0 font-mono text-sm text-amber-200">{fmtPrice(d.priceTag, currency, currencySymbol)}</span>
                    </div>
                    <div className="mt-1 text-xs text-zinc-500">{d.categoryName || CATEGORY_LABEL[d.dishCategory] || d.dishCategory}</div>
                    <div className="mt-2">
                      <MediaBadges dish={d} onRequest3d={actions.request3d} onUpload3d={actions.upload3d} canDirectUpload={canDirectUpload} />
                    </div>
                  </div>
                </div>
                <div className="flex items-center justify-between gap-2 border-t hairline bg-black/30 px-3 py-2">
                  <Toggle on={d.available} onChange={() => !busyIds.has(d.id) && toggleStock(d)} label={d.available ? 'In stock' : 'Out of stock'} />
                  <RowActions dish={d} busy={busyIds.has(d.id)} actions={actions} />
                </div>
              </article>
            ))}
          </div>
        </>
      )}

      {editing && (
        <DishEditModal
          dish={editing}
          currencyLabel={currencyLabel}
          categories={categories}
          canDirectUpload={canDirectUpload}
          onClose={() => setEditing(null)}
          onSaved={(saved) => {
            replace(saved);
            setEditing(null);
            toast(`${saved.dishTitle} updated`);
          }}
          onAssetReplaced={(saved, message) => {
            replace(saved);
            toast(message);
          }}
        />
      )}
      {request3dFor && (
        <Request3dModal
          dish={request3dFor}
          onClose={() => setRequest3dFor(null)}
          onSuccess={(saved) => {
            replace(saved);
            toast('3D model request submitted! Our culinary team has received your photos.');
          }}
        />
      )}
      {modelModalFor && (
        <ModelModal
          dish={modelModalFor}
          onClose={() => setModelModalFor(null)}
          onSaved={(saved, message) => {
            replace(saved);
            setModelModalFor(null);
            toast(message || '3D model saved');
          }}
        />
      )}
    </div>
  );
}

function Thumb({ dish, className }) {
  return (
    <div className={cx('relative shrink-0 overflow-hidden rounded-2xl bg-black', className)}>
      {dish.imageUrl ? (
        <img src={dish.imageUrl} alt="" loading="lazy" decoding="async" className="h-full w-full object-cover" />
      ) : (
        <div className="grid h-full w-full place-items-center text-zinc-600">
          {dish.is3d ? <Box size={20} aria-hidden="true" /> : <ImageOff size={20} aria-hidden="true" />}
        </div>
      )}
      {!dish.available && (
        <div className="absolute inset-0 grid place-items-center bg-black/60">
          <PackageX size={18} className="text-zinc-300" aria-label="Out of stock" />
        </div>
      )}
    </div>
  );
}

function MediaBadges({ dish, onRequest3d, onUpload3d, canDirectUpload }) {
  const isPending = dish.modelRequestStatus === 'REQUESTED' || dish.modelRequestStatus === 'IN_PRODUCTION' || dish.modelPending;

  if (isPending) {
    return (
      <span
        className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/40 bg-amber-500/10 px-2.5 py-1 text-[11px] font-semibold text-amber-300"
        title="Our 3D culinary team is crafting your model"
      >
        <span className="h-1.5 w-1.5 rounded-full bg-amber-400 animate-ping" />
        3D Twin: In Production
      </span>
    );
  }

  if (dish.is3d) {
    return (
      <div className="flex items-center gap-2">
        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-500/40 bg-emerald-500/10 px-2.5 py-1 text-[11px] font-semibold text-emerald-300">
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400 animate-pulse" />
          3D &amp; AR Live ✨
        </span>
        {canDirectUpload && onUpload3d ? (
          <button
            type="button"
            onClick={() => onUpload3d(dish)}
            className="tap flex items-center gap-1 rounded-full border border-emerald-500/40 bg-emerald-500/10 px-2 py-0.5 text-[10px] font-medium text-emerald-300 hover:bg-emerald-500/20"
            title="Replace 3D model directly (.glb)"
          >
            <Box size={10} />
            Replace 3D
          </button>
        ) : onRequest3d ? (
          <button
            type="button"
            onClick={() => onRequest3d(dish)}
            className="tap flex items-center gap-1 rounded-full border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[10px] font-medium text-amber-300 hover:bg-amber-500/20"
            title="Request replacement 3D model via Concierge"
          >
            <Sparkles size={10} />
            Replace 3D
          </button>
        ) : null}
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <span className="rounded-full border border-zinc-700 bg-zinc-800/40 px-2.5 py-1 text-[11px] text-zinc-400">
        ⚪ Photo Only
      </span>
      {canDirectUpload && onUpload3d && (
        <button
          type="button"
          onClick={() => onUpload3d(dish)}
          className="tap flex items-center gap-1 rounded-full border border-emerald-500/50 bg-emerald-500/10 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-300 hover:bg-emerald-500/20"
          title="Upload .glb model directly"
        >
          <Box size={11} />
          Upload 3D
        </button>
      )}
      {onRequest3d && !canDirectUpload && (
        <button
          type="button"
          onClick={() => onRequest3d(dish)}
          className="tap flex items-center gap-1 rounded-full border border-amber-500/50 bg-amber-500/10 px-2.5 py-0.5 text-[11px] font-semibold text-amber-300 hover:bg-amber-500/20"
          title="Request 3D model from culinary team (5 photos)"
        >
          <Sparkles size={11} />
          Request 3D
        </button>
      )}
    </div>
  );
}

function RowActions({ dish, busy, actions }) {
  const btn = 'tap grid h-10 w-10 place-items-center rounded-full border hairline text-zinc-300 hover:border-amber-500/40 hover:text-amber-300 disabled:opacity-40';
  const isPending = dish.modelRequestStatus === 'REQUESTED' || dish.modelRequestStatus === 'IN_PRODUCTION' || dish.modelPending;
  const canRequest3d = !isPending;
  return (
    <div className="flex items-center justify-end gap-1.5">
      <button type="button" disabled={busy} onClick={() => actions.edit(dish)} className={btn} aria-label={`Edit ${dish.dishTitle}`} title="Edit dish">
        <Pencil size={15} aria-hidden="true" />
      </button>
      {actions.canDirectUpload ? (
        <button
          type="button"
          disabled={busy}
          onClick={() => actions.upload3d(dish)}
          className="tap grid h-10 w-10 place-items-center rounded-full border border-emerald-500/40 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20 disabled:opacity-40"
          aria-label={dish.is3d ? `Replace 3D Model (.glb) for ${dish.dishTitle}` : `Upload 3D Model (.glb) for ${dish.dishTitle}`}
          title={dish.is3d ? 'Replace 3D Model (.glb)' : 'Upload 3D Model (.glb)'}
        >
          <Box size={15} aria-hidden="true" />
        </button>
      ) : canRequest3d ? (
        <button
          type="button"
          disabled={busy}
          onClick={() => actions.request3d(dish)}
          className="tap grid h-10 w-10 place-items-center rounded-full border border-amber-500/40 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20 disabled:opacity-40"
          aria-label={dish.is3d ? `Replace 3D Model for ${dish.dishTitle}` : `Request 3D Model for ${dish.dishTitle}`}
          title={dish.is3d ? 'Replace 3D Model (5 photos)' : 'Request 3D Model (5 photos)'}
        >
          <Camera size={15} aria-hidden="true" />
        </button>
      ) : null}
      <button
        type="button"
        disabled={busy}
        onClick={() => actions.remove(dish)}
        className="tap grid h-10 w-10 place-items-center rounded-full border hairline text-zinc-400 hover:border-red-500/40 hover:text-red-300 disabled:opacity-40"
        aria-label={`Delete ${dish.dishTitle}`}
        title="Delete"
      >
        {busy ? <LoaderCircle size={15} className="animate-spin" aria-hidden="true" /> : <Trash2 size={15} aria-hidden="true" />}
      </button>
    </div>
  );
}
