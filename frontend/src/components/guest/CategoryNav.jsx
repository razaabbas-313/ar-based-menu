import { useLayoutEffect, useRef, useState } from 'react';
import { Box } from 'lucide-react';
import { CATEGORY_LABEL, buzz, cx } from '../../utils/format.js';

/**
 * Sticky glass sub-header with horizontal category pills, a sliding active indicator, mobile
 * auto-scrolling to keep the active pill in view, and a "3D Only" toggle.
 */
export default function CategoryNav({ categories, active, onSelect, restaurantName }) {
  const rail = useRef(null);
  const [indicator, setIndicator] = useState(null);

  // Measure the active pill for the sliding indicator and keep it centred in the rail.
  useLayoutEffect(() => {
    const r = rail.current;
    const el = r?.querySelector(`[data-cat="${active}"]`);
    if (!r || !el) {
      setIndicator(null);
      return undefined;
    }
    const measure = () => setIndicator({ left: el.offsetLeft, width: el.offsetWidth });
    measure();
    const left = el.offsetLeft - r.clientWidth / 2 + el.clientWidth / 2;
    r.scrollTo({ left: Math.max(0, left), behavior: 'smooth' });
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [active, categories.length]);

  if (!categories || categories.length === 0) return null;
  return (
    <nav className="pt-safe sticky top-0 z-30 border-b border-white/10 bg-[#0a0a0c]/75 backdrop-blur-xl backdrop-saturate-150" aria-label="Menu sections">
      <div className="mx-auto max-w-3xl px-4 sm:px-6">
        <div className="flex h-10 items-center justify-between pt-2 text-[10px] font-semibold uppercase tracking-[0.3em] text-zinc-500">
          <span className="truncate">{restaurantName}</span>
          <span className="shrink-0 text-accent-400/90">La Carte</span>
        </div>
        <div ref={rail} className="no-scrollbar momentum relative -mx-1 flex gap-1.5 overflow-x-auto px-1 pb-3 pt-1">
          {indicator && (
            <span
              aria-hidden="true"
              className="pointer-events-none absolute left-0 top-1 h-10 rounded-full bg-accent-500 shadow-[0_8px_24px_-8px_rgb(var(--accent-500)/0.7)] transition-[transform,width] duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]"
              style={{ width: indicator.width, transform: `translateX(${indicator.left}px)` }}
              data-testid="cat-indicator"
            />
          )}
          {categories.map((c) => (
            <button
              key={c.key}
              data-cat={c.key}
              type="button"
              onClick={() => {
                buzz();
                onSelect(c.key);
              }}
              aria-current={active === c.key ? 'true' : undefined}
              className={cx(
                'tap relative z-10 flex h-10 shrink-0 items-center rounded-full px-4 text-[13px] font-semibold transition-colors duration-300 whitespace-nowrap',
                active === c.key ? 'text-zinc-950 font-bold' : 'text-zinc-400 hover:text-zinc-100',
              )}
            >
              {c.label || CATEGORY_LABEL[c.key] || c.key}
            </button>
          ))}
        </div>
      </div>
    </nav>
  );
}
