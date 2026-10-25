import { Crown } from 'lucide-react';
import { CATEGORY_LABEL, CATEGORY_ORDER, buzz, cx } from '../../utils/format.js';

/** Shared dish metadata fields (new dish + edit). */
export default function DishFields({ form, set, currencyLabel, showPlacement, categories }) {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-[1fr_9rem]">
        <label className="block">
          <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Dish title</span>
          <input
            value={form.title}
            onChange={(e) => set('title', e.target.value)}
            maxLength={160}
            placeholder="e.g. Hokkaido Scallop Crudo"
            className="h-12 w-full rounded-2xl border hairline bg-black/40 px-4 font-display text-xl text-zinc-50 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
          />
        </label>
        <label className="block">
          <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Price ({currencyLabel})</span>
          <input
            value={form.price}
            onChange={(e) => set('price', e.target.value.replace(/[^0-9.]/g, '').replace(/(\..*)\./g, '$1'))}
            inputMode="decimal"
            placeholder="0.00"
            className="h-12 w-full rounded-2xl border hairline bg-black/40 px-4 font-mono text-lg text-amber-200 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
          />
        </label>
      </div>

      <div>
        <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Category</span>
        <div className="flex flex-wrap gap-2">
          {categories && categories.length > 0 ? (
            categories.map((cat) => (
              <button
                key={cat.id}
                type="button"
                onClick={() => {
                  buzz();
                  set('categoryId', cat.id);
                  set('category', cat.name);
                }}
                className={cx(
                  'tap h-10 rounded-full border px-4 text-xs font-semibold transition-all',
                  form.categoryId === cat.id || (!form.categoryId && form.category === cat.name)
                    ? 'border-amber-400/60 bg-amber-500/15 text-amber-200 shadow-sm'
                    : 'hairline text-zinc-400 hover:border-zinc-600 hover:text-zinc-200',
                )}
              >
                {cat.name}
              </button>
            ))
          ) : (
            CATEGORY_ORDER.map((key) => (
              <button
                key={key}
                type="button"
                onClick={() => {
                  buzz();
                  set('category', key);
                }}
                className={cx(
                  'tap h-10 rounded-full border px-4 text-xs font-semibold transition-all',
                  form.category === key
                    ? 'border-amber-400/60 bg-amber-500/15 text-amber-200 shadow-sm'
                    : 'hairline text-zinc-400 hover:border-zinc-600 hover:text-zinc-200',
                )}
              >
                {CATEGORY_LABEL[key] || key}
              </button>
            ))
          )}
        </div>
      </div>

      <label className="block">
        <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Description &amp; ingredients</span>
        <textarea
          value={form.description}
          onChange={(e) => set('description', e.target.value)}
          maxLength={2000}
          rows={3}
          placeholder="Hokkaido scallop, yuzu kosho, finger lime, shiso."
          className="w-full rounded-2xl border hairline bg-black/40 px-4 py-3 text-[15px] text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
        />
      </label>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Toggle on={form.available} onChange={(v) => set('available', v)} label={form.available ? 'In stock' : 'Out of stock'} />
        <Toggle on={form.special} onChange={(v) => set('special', v)} label="Feature as Daily Special" icon={Crown} />
        {showPlacement && (
          <div className="sm:col-span-2">
            <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">AR placement</span>
            <div className="grid grid-cols-2 gap-1 rounded-2xl border hairline bg-black/30 p-1">
              {[
                ['table', 'Tabletop', 'True-to-size'],
                ['floor', 'Pedestal', 'Scalable showpiece'],
              ].map(([key, label, hint]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => set('placement', key)}
                  className={cx(
                    'tap flex min-h-12 flex-col items-center justify-center rounded-xl px-2 py-1',
                    form.placement === key ? 'bg-zinc-800 text-amber-200' : 'text-zinc-400 hover:text-zinc-200',
                  )}
                >
                  <span className="text-sm font-semibold">{label}</span>
                  <span className="text-[10px] text-zinc-500">{hint}</span>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export function Toggle({ on, onChange, label, icon: Icon }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      onClick={() => {
        buzz();
        onChange(!on);
      }}
      className={cx('tap flex min-h-12 items-center justify-between gap-3 rounded-2xl border px-4 py-3 text-left', on ? 'border-amber-400/50 bg-amber-500/10' : 'hairline')}
    >
      <span className="flex items-center gap-2 text-sm font-semibold text-zinc-200">
        {Icon && <Icon size={17} className="text-amber-400" aria-hidden="true" />}
        {label}
      </span>
      <span className={cx('relative h-6 w-11 shrink-0 rounded-full transition-colors', on ? 'bg-amber-500' : 'bg-zinc-700')}>
        <span className={cx('absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all', on ? 'left-[22px]' : 'left-0.5')} />
      </span>
    </button>
  );
}

export function dishToForm(item, categories = []) {
  const initialCategory = item?.categoryId
    ? (categories.find((c) => c.id === item.categoryId)?.name || item?.dishCategory || '')
    : (item?.dishCategory || categories?.[0]?.name || 'MAINS');
  return {
    title: item?.dishTitle || '',
    price: item ? String(item.priceTag) : '',
    category: initialCategory,
    categoryId: item?.categoryId || (categories?.[0]?.id ?? null),
    description: item?.description || '',
    dietaryTags: item?.dietaryTags || [],
    available: item ? item.available : true,
    special: item ? item.dailySpecial : false,
    placement: item?.arPlacement || 'table',
  };
}

export function formIsValid(form) {
  const price = Number(form.price);
  return form.title.trim().length > 0 && form.price !== '' && Number.isFinite(price) && price >= 0;
}
