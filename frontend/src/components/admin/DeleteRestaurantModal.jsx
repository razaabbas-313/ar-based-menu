import { useState } from 'react';
import { Archive, LoaderCircle, Trash2, TriangleAlert, X } from 'lucide-react';
import { IconButton, Overlay, inputClass } from '../ui.jsx';
import { api } from '../../api/client.js';
import { cx, fmtBytes } from '../../utils/format.js';

/** Soft delete (default, reversible) or hard delete (type the slug; S3 assets wiped asynchronously). */
export default function DeleteRestaurantModal({ restaurant, onClose, onDone }) {
  const [mode, setMode] = useState('soft');
  const [confirmText, setConfirmText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const canSubmit = mode === 'soft' || confirmText.trim() === restaurant.slug;

  async function submit() {
    if (!canSubmit) return;
    setBusy(true);
    setError('');
    try {
      const res = await api.adminDeleteRestaurant(restaurant.id, mode === 'hard');
      onDone(
        mode === 'hard'
          ? `${restaurant.name} permanently deleted · ${res?.objectsScheduled ?? 0} S3 objects scheduled for removal`
          : `${restaurant.name} archived. Restore it any time from the table.`,
        mode,
      );
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }

  return (
    <Overlay onClose={busy ? undefined : onClose} align="bottom" label="Delete restaurant">
      <div className="sheet pb-safe relative z-10 w-full max-w-lg rounded-t-[30px] border hairline bg-onyx p-5 sm:rounded-[30px]">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="font-display text-3xl font-semibold">Delete {restaurant.name}?</h2>
          <IconButton icon={X} label="Close" onClick={() => !busy && onClose()} />
        </div>
        <div className="grid gap-2">
          {[
            ['soft', Archive, 'Soft delete', 'Hides the menu and blocks sign-in. Data and S3 files are kept, so it can be restored.'],
            ['hard', Trash2, 'Hard delete', `Removes the account, ${restaurant.dishCount} dishes and ${fmtBytes(restaurant.storageUsedBytes)} of S3 files. Cannot be undone.`],
          ].map(([key, Icon, label, text]) => (
            <button
              key={key}
              type="button"
              onClick={() => setMode(key)}
              aria-pressed={mode === key}
              className={cx(
                'tap flex gap-3 rounded-2xl border p-4 text-left',
                mode === key ? (key === 'hard' ? 'border-red-500/60 bg-red-500/10' : 'border-amber-500/60 bg-amber-500/10') : 'hairline',
              )}
            >
              <Icon size={20} className={key === 'hard' ? 'text-red-300' : 'text-amber-300'} aria-hidden="true" />
              <span>
                <span className="block text-sm font-semibold text-zinc-100">{label}</span>
                <span className="mt-0.5 block text-xs leading-relaxed text-zinc-400">{text}</span>
              </span>
            </button>
          ))}
        </div>
        {mode === 'hard' && (
          <label className="mt-4 block">
            <span className="mb-1.5 block text-xs text-zinc-400">
              Type <span className="font-mono text-red-200">{restaurant.slug}</span> to confirm
            </span>
            <input className={inputClass} value={confirmText} onChange={(e) => setConfirmText(e.target.value)} autoCapitalize="none" spellCheck="false" data-testid="confirm-slug" />
          </label>
        )}
        {error && (
          <div className="mt-4 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
            <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
        <button
          type="button"
          disabled={!canSubmit || busy}
          onClick={submit}
          className={cx(
            'tap mt-5 flex h-14 w-full items-center justify-center gap-2 rounded-2xl text-base font-bold',
            canSubmit ? (mode === 'hard' ? 'bg-red-500 text-white' : 'bg-amber-500 text-zinc-950') : 'bg-zinc-800 text-zinc-500',
          )}
        >
          {busy && <LoaderCircle size={19} className="animate-spin" aria-hidden="true" />}
          {mode === 'hard' ? 'Delete permanently' : 'Archive restaurant'}
        </button>
      </div>
    </Overlay>
  );
}
