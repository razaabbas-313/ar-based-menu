import { useState } from 'react';
import { Box, Crown } from 'lucide-react';
import { buzz, cx, fmtPrice } from '../../utils/format.js';

/** "Chef's Special" applies to the daily special and the Chef Specials section. */
export function isChefSpecial(item) {
  return Boolean(item.dailySpecial || item.dishCategory === 'CHEF_SPECIALS');
}

/** Progressive photo: shimmer skeleton until decoded, then a soft fade-in. */
function DishPhoto({ item, eager }) {
  const [state, setState] = useState(item.imageUrl ? 'loading' : 'none');
  return (
    <>
      {state === 'loading' && <div className="skeleton absolute inset-0" aria-hidden="true" />}
      {item.imageUrl && state !== 'error' && (
        <img
          src={item.imageUrl}
          alt={item.dishTitle}
          loading={eager ? 'eager' : 'lazy'}
          decoding="async"
          width={item.widthPx || undefined}
          height={item.heightPx || undefined}
          onLoad={() => setState('loaded')}
          onError={() => setState('error')}
          className={cx(
            'absolute inset-0 h-full w-full object-cover transition-[opacity,transform] duration-700 ease-out group-hover:scale-[1.045]',
            state === 'loaded' ? 'opacity-100' : 'opacity-0',
          )}
        />
      )}
      {(state === 'none' || state === 'error') && (
        <div className="ambient absolute inset-0 grid place-items-center">
          <span className="font-display text-6xl italic text-accent-500/40">{item.dishTitle.charAt(0)}</span>
        </div>
      )}
    </>
  );
}

/** Sleek, minimalist "3D" pill for interactive dishes. */
export function ArBadge({ className = '' }) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 rounded-full border border-white/10 bg-black/60 px-2 py-0.5 text-[10px] font-medium tracking-wide text-accent-300 backdrop-blur-md',
        className,
      )}
      title="3D interactive view available"
    >
      <Box size={11} aria-hidden="true" />
      <span>3D</span>
    </span>
  );
}

/** Lightweight dish card: photo only on the feed (the 3D model loads in the inspector). */
export default function GuestDishCard({ item, layout, currency, currencySymbol, onOpen, index }) {
  const price = fmtPrice(item.priceTag, currency, currencySymbol);
  const open = () => {
    buzz();
    onOpen(item);
  };
  const special = isChefSpecial(item);

  if (layout === 'compact') {
    return (
      <button
        type="button"
        onClick={open}
        className="tap group flex w-full items-center gap-4 rounded-[22px] border border-white/10 bg-white/[0.02] p-3 text-left transition-[transform,border-color,background-color] duration-300 hover:-translate-y-0.5 hover:border-accent-400/30 hover:bg-white/[0.04]"
        data-testid="guest-dish"
      >
        <div className="relative h-24 w-24 shrink-0 overflow-hidden rounded-2xl bg-zinc-900">
          <DishPhoto item={item} eager={index < 6} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-3">
            <h3 className="font-display text-[21px] font-semibold leading-[1.08] text-[#f6efdf]">{item.dishTitle}</h3>
            <span className="shrink-0 pt-0.5 font-sans text-[15px] font-semibold tabular-nums tracking-tight text-accent-300">{price}</span>
          </div>
          {item.description && <p className="mt-1 line-clamp-2 text-[13px] leading-snug text-zinc-400">{item.description}</p>}
          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            {item.is3d && <ArBadge className="bg-transparent px-2 py-0.5" />}
            {special && <Pill accent icon={Crown}>Chef&apos;s Special</Pill>}
          </div>
        </div>
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={open}
      className="tap rise group flex w-full flex-col overflow-hidden rounded-[26px] border border-white/10 bg-[#111113] text-left shadow-[0_1px_0_rgba(255,255,255,0.04)_inset] transition-[transform,box-shadow,border-color] duration-500 ease-[cubic-bezier(0.2,0.8,0.2,1)] hover:-translate-y-1 hover:border-accent-400/30 hover:shadow-[0_28px_60px_-28px_rgb(var(--accent-500)/0.45)]"
      style={{ animationDelay: `${Math.min(index, 8) * 50}ms` }}
      data-testid="guest-dish"
    >
      <div className="relative aspect-[5/4] overflow-hidden bg-zinc-900">
        <DishPhoto item={item} eager={index < 4} />
        <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/70 via-black/0 to-black/10" />
        <div className="absolute left-3 top-3 flex flex-wrap gap-1.5">
          {item.is3d && <ArBadge />}
          {special && (
            <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-black/55 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] text-[#f6efdf] backdrop-blur-md">
              <Crown size={12} className="text-accent-300" aria-hidden="true" /> Chef&apos;s Special
            </span>
          )}
        </div>
        <span className="absolute bottom-3 right-3 rounded-full border border-white/15 bg-black/60 px-3 py-1.5 font-sans text-[15px] font-semibold tabular-nums tracking-tight text-[#f6efdf] backdrop-blur-md">
          {price}
        </span>
      </div>
      <div className="flex flex-1 flex-col px-4 pb-4 pt-3.5">
        <h3 className="font-display text-[25px] font-semibold leading-[1.04] text-[#f6efdf]">{item.dishTitle}</h3>
        {item.description && <p className="mt-1.5 line-clamp-2 text-[14px] leading-snug text-zinc-400">{item.description}</p>}
      </div>
    </button>
  );
}

export function Pill({ children, icon: Icon, accent }) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-semibold tracking-wide',
        accent ? 'border-accent-500/40 bg-accent-500/10 text-accent-300' : 'border-white/10 bg-white/[0.03] text-zinc-300',
      )}
    >
      {Icon && <Icon size={11} aria-hidden="true" />}
      {children}
    </span>
  );
}
