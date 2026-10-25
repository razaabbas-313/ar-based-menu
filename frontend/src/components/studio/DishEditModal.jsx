import { useState } from 'react';
import { LoaderCircle, Save, TriangleAlert, X } from 'lucide-react';
import DishFields, { dishToForm, formIsValid } from './DishFields.jsx';
import VisualAssetReplacer from './VisualAssetReplacer.jsx';
import { IconButton, Overlay } from '../ui.jsx';
import { api } from '../../api/client.js';
import { buzz, cx } from '../../utils/format.js';

/**
 * Edit title, price, category, description, dietary tags, stock and special flag, and replace the
 * dish's photo or 3D model in place ("Update Visual Asset").
 */
export default function DishEditModal({
  dish: initialDish,
  currencyLabel,
  categories = [],
  canDirectUpload = false,
  onClose,
  onSaved,
  onAssetReplaced,
}) {
  const [dish, setDish] = useState(initialDish);
  const [form, setForm] = useState(() => dishToForm(initialDish, categories));
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const valid = formIsValid(form);

  async function save() {
    if (!valid) return;
    setBusy(true);
    setError('');
    const legacyCategory = (() => {
      if (!form.category) return 'MAINS';
      const norm = String(form.category).toUpperCase().replace(/[-\s]/g, '_').replace(/'/g, '');
      if (['STARTERS', 'MAINS', 'CHEF_SPECIALS', 'DESSERTS', 'DRINKS'].includes(norm)) return norm;
      if (norm.includes('CHEF')) return 'CHEF_SPECIALS';
      if (norm.includes('START') || norm.includes('APPETIZ')) return 'STARTERS';
      if (norm.includes('DESSERT') || norm.includes('SWEET')) return 'DESSERTS';
      if (norm.includes('DRINK') || norm.includes('BEVERAG')) return 'DRINKS';
      return 'MAINS';
    })();
    try {
      const saved = await api.updateDish(dish.id, {
        dishTitle: form.title.trim(),
        priceTag: Number(Number(form.price).toFixed(2)),
        dishCategory: legacyCategory,
        categoryId: form.categoryId || null,
        description: form.description.trim() || null,
        dietaryTags: form.dietaryTags,
        available: form.available,
        arPlacement: dish.is3d ? form.placement : null,
        dailySpecial: form.special,
      });
      buzz(20);
      onSaved(saved);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Overlay onClose={busy || uploading ? undefined : onClose} align="bottom" label={`Edit ${dish.dishTitle}`}>
      <div className="sheet pb-safe relative z-10 flex max-h-[94vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-[30px] border hairline bg-onyx sm:rounded-[30px]">
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div className="flex min-w-0 items-center gap-3">
            {dish.imageUrl && <img src={dish.imageUrl} alt="" className="h-12 w-12 shrink-0 rounded-xl object-cover" />}
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">Edit dish</span>
                {dish.dishCode && (
                  <span className="shrink-0 font-mono text-[10px] font-bold text-amber-400/90 border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.5 rounded">
                    {dish.dishCode}
                  </span>
                )}
              </div>
              <h2 className="truncate font-display text-2xl font-semibold">{dish.dishTitle}</h2>
            </div>
          </div>
          <IconButton icon={X} label="Close" onClick={() => !busy && !uploading && onClose()} />
        </div>
        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          {dish.dishCode && (
            <div className="flex items-center justify-between rounded-2xl border hairline bg-black/40 px-4 py-2.5 text-xs">
              <span className="text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                Dish Reference ID <span className="text-zinc-600">(Unmodifiable)</span>
              </span>
              <span className="font-mono font-bold text-amber-300">{dish.dishCode}</span>
            </div>
          )}
          <VisualAssetReplacer
            dish={dish}
            canDirectUpload={canDirectUpload}
            onBusyChange={setUploading}
            onReplaced={(updated, message) => {
              setDish(updated);
              onAssetReplaced?.(updated, message);
            }}
          />
          <DishFields form={form} set={set} currencyLabel={currencyLabel} categories={categories} showPlacement={dish.is3d} />
          {error && (
            <div className="mt-4 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
              <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}
        </div>
        <div className="border-t hairline bg-black/30 px-5 py-4">
          <button
            type="button"
            disabled={!valid || busy || uploading}
            onClick={save}
            className={cx(
              'tap flex h-14 w-full items-center justify-center gap-2 rounded-2xl text-base font-bold',
              valid ? 'bg-gradient-to-b from-amber-400 to-amber-600 text-zinc-950' : 'bg-zinc-800 text-zinc-500',
            )}
          >
            {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <Save size={19} aria-hidden="true" />}
            {busy ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </div>
    </Overlay>
  );
}
