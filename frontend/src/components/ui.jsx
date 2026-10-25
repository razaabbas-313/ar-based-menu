import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { buzz, cx } from '../utils/format.js';

export function Badge({ children, tone = 'amber', icon: Icon }) {
  const tones = {
    amber: 'bg-amber-500/15 text-amber-300 border-amber-400/30',
    zinc: 'bg-zinc-900/70 text-zinc-300 border-zinc-700/70',
    green: 'bg-emerald-500/10 text-emerald-300 border-emerald-400/25',
  };
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] backdrop-blur-md',
        tones[tone],
      )}
    >
      {Icon && <Icon size={12} aria-hidden="true" />}
      {children}
    </span>
  );
}

export function IconButton({ icon: Icon, label, onClick, className = '', size = 18 }) {
  return (
    <button
      type="button"
      onClick={(e) => {
        buzz();
        onClick?.(e);
      }}
      aria-label={label}
      title={label}
      className={cx(
        'tap grid h-12 w-12 shrink-0 place-items-center rounded-full border hairline glass text-zinc-300 hover:border-amber-500/40 hover:text-amber-300',
        className,
      )}
    >
      <Icon size={size} aria-hidden="true" />
    </button>
  );
}

/**
 * Modal backdrop with Escape-to-close and body scroll lock. Rendered in a portal on <body> so an
 * ancestor with backdrop-filter / transform (sticky glass headers, drawers) can never become the
 * containing block of the fixed overlay and clip it.
 */
export function Overlay({ onClose, children, align = 'center', z = 'z-50', label }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose?.();
    };
    document.addEventListener('keydown', onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  return createPortal(
    <div
      className={cx(
        'fixed inset-0 flex fade',
        z,
        align === 'bottom' ? 'items-end justify-center sm:items-center' : 'items-center justify-center',
      )}
      role="dialog"
      aria-modal="true"
      aria-label={label}
    >
      <div className="absolute inset-0 bg-black/70 backdrop-blur-md" onClick={() => onClose?.()} />
      {children}
    </div>,
    document.body,
  );
}

export function Field({ label, hint, error, children }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">{label}</span>
      {children}
      {error ? (
        <span className="mt-1.5 block text-xs text-red-300">{error}</span>
      ) : hint ? (
        <span className="mt-1.5 block text-xs text-zinc-500">{hint}</span>
      ) : null}
    </label>
  );
}

export const inputClass =
  'h-12 w-full rounded-2xl border hairline bg-black/40 px-4 text-[15px] text-zinc-50 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none focus:ring-2 focus:ring-amber-500/20';

export const primaryButtonClass =
  'tap flex h-14 w-full items-center justify-center gap-2 rounded-2xl bg-gradient-to-b from-amber-400 to-amber-600 text-base font-bold text-zinc-950 shadow-fab hover:from-amber-300 hover:to-amber-500 disabled:from-zinc-800 disabled:to-zinc-800 disabled:text-zinc-500 disabled:shadow-none';

export function Spinner({ className = '' }) {
  return <div className={cx('h-10 w-10 animate-spin rounded-full border border-amber-500/30 border-t-amber-400', className)} />;
}
