import { useEffect, useRef, useState } from 'react';
import { BadgeCheck, Box, Clock } from 'lucide-react';
import SocialBar from './SocialBar.jsx';
import { safeHttpUrl } from '../../utils/format.js';

/**
 * Restaurant header: cover with a gentle parallax (or an ambient gradient when no banner), floating
 * logo, serif title with the verified atelier badge, tagline, service details, bio, and the
 * swipeable social / contact bar directly beneath.
 */
export default function MenuHero({ restaurant, dishCount = 0, arCount = 0, menuMode }) {
  const [bioOpen, setBioOpen] = useState(false);
  const cover = useRef(null);
  const banner = safeHttpUrl(restaurant.bannerUrl);
  const logo = safeHttpUrl(restaurant.logoUrl);

  // Parallax: the cover drifts at ~35% of the scroll speed (disabled for reduced motion).
  useEffect(() => {
    const el = cover.current;
    if (!el || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined;
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const y = Math.min(window.scrollY, 600);
        el.style.transform = `translate3d(0, ${y * 0.35}px, 0) scale(${1 + y / 4000})`;
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('scroll', onScroll);
    };
  }, []);

  return (
    <header className="relative">
      <div className="relative h-[17rem] overflow-hidden sm:h-80 lg:h-[26rem]">
        <div ref={cover} className="absolute inset-0 will-change-transform">
          {banner ? (
            <img src={banner} alt="" className="h-full w-full object-cover" fetchpriority="high" decoding="async" />
          ) : (
            <div className="ambient absolute inset-0" />
          )}
        </div>
        <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-black/10 to-[#0a0a0c]" />
        <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-[#0a0a0c] to-transparent" />
      </div>

      <div className="relative mx-auto -mt-20 max-w-3xl px-4 sm:-mt-24 sm:px-6">
        <div className="flex items-end gap-4">
          <div className="grid h-24 w-24 shrink-0 place-items-center overflow-hidden rounded-[26px] border border-white/15 bg-[#121214] shadow-[0_20px_50px_-12px_rgba(0,0,0,0.9)] ring-1 ring-accent-400/30 sm:h-28 sm:w-28">
            {logo ? (
              <img src={logo} alt={`${restaurant.name} logo`} className="h-full w-full object-cover" />
            ) : (
              <span className="font-display text-5xl font-semibold italic text-accent-300">{restaurant.name.charAt(0)}</span>
            )}
          </div>
          <div className="min-w-0 pb-1">
            <span className="mb-2 inline-flex items-center gap-1.5 rounded-full border border-accent-400/30 bg-black/40 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.2em] text-accent-300 backdrop-blur-md">
              <BadgeCheck size={13} aria-hidden="true" /> Verified Atelier
            </span>
            <h1 className="font-display text-[40px] font-semibold leading-[0.95] tracking-[-0.01em] text-[#f6efdf] sm:text-6xl">{restaurant.name}</h1>
          </div>
        </div>

        {restaurant.tagline && <p className="mt-3 font-display text-xl italic text-accent-300/90">{restaurant.tagline}</p>}

        <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-[12px] font-medium uppercase tracking-[0.16em] text-zinc-400">
          {restaurant.openingHours && (
            <span className="flex items-center gap-1.5 normal-case tracking-normal text-[13px]">
              <Clock size={14} className="text-accent-400" aria-hidden="true" /> {restaurant.openingHours}
            </span>
          )}
          {dishCount > 0 && <span>{dishCount} {dishCount === 1 ? 'dish' : 'dishes'}</span>}
          {arCount > 0 && (
            <span className="flex items-center gap-1.5 text-accent-300">
              <Box size={13} aria-hidden="true" /> {arCount} {menuMode==='THREE_D'?'in 3D':'in 3D & AR'}
            </span>
          )}
          <span>Prices in {restaurant.currencyCode || 'USD'}</span>
        </div>

        {restaurant.bio && (
          <div className="mt-4 max-w-2xl">
            <p className={bioOpen ? 'text-[15px] leading-relaxed text-zinc-400' : 'line-clamp-3 text-[15px] leading-relaxed text-zinc-400'}>
              {restaurant.bio}
            </p>
            {restaurant.bio.length > 180 && (
              <button type="button" onClick={() => setBioOpen(!bioOpen)} className="mt-1 text-sm font-semibold text-accent-300">
                {bioOpen ? 'Less' : 'Read more'}
              </button>
            )}
          </div>
        )}

        <div className="mt-5">
          <SocialBar restaurant={restaurant} />
        </div>
      </div>
    </header>
  );
}
