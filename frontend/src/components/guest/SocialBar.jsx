import { useEffect, useRef, useState } from 'react';
import { Globe, MapPin, Phone } from 'lucide-react';
import { FacebookGlyph, InstagramGlyph, TikTokGlyph, WhatsAppGlyph } from './SocialGlyphs.jsx';
import { buzz, cx, safeHttpUrl, whatsappLink } from '../../utils/format.js';

const handle = (h) => String(h || '').replace(/^@/, '');

/** Builds the chips for the links the restaurant configured (unset links are skipped). */
export function socialLinks(r) {
  return [
    r.instagramHandle && {
      key: 'instagram',
      label: `@${handle(r.instagramHandle)}`,
      title: 'Instagram',
      icon: InstagramGlyph,
      href: `https://instagram.com/${encodeURIComponent(handle(r.instagramHandle))}`,
    },
    r.tiktokHandle && {
      key: 'tiktok',
      label: `@${handle(r.tiktokHandle)}`,
      title: 'TikTok',
      icon: TikTokGlyph,
      href: `https://www.tiktok.com/@${encodeURIComponent(handle(r.tiktokHandle))}`,
    },
    safeHttpUrl(r.googleMapsUrl) && { key: 'maps', label: 'Directions', title: 'Directions', icon: MapPin, href: r.googleMapsUrl },
    r.whatsappNumber && {
      key: 'whatsapp',
      label: 'WhatsApp',
      title: 'WhatsApp concierge',
      icon: WhatsAppGlyph,
      href: whatsappLink(r.whatsappNumber, `Hello ${r.name}, I'd like to make a reservation.`),
      primary: true,
    },
    r.phoneNumber && { key: 'call', label: 'Call', title: 'Call', icon: Phone, href: `tel:${r.phoneNumber.replace(/[^0-9+]/g, '')}`, native: true },
    safeHttpUrl(r.facebookUrl) && { key: 'facebook', label: 'Facebook', title: 'Facebook', icon: FacebookGlyph, href: r.facebookUrl },
    safeHttpUrl(r.websiteUrl) && { key: 'website', label: 'Website', title: 'Website', icon: Globe, href: r.websiteUrl },
  ].filter(Boolean);
}

/**
 * Swipeable contact bar under the restaurant header: momentum scrolling, snap points, hidden
 * scrollbar and fade edges that appear only when there is more to scroll in that direction.
 */
export default function SocialBar({ restaurant }) {
  const rail = useRef(null);
  const [edges, setEdges] = useState({ left: false, right: false });
  const links = socialLinks(restaurant);

  useEffect(() => {
    const el = rail.current;
    if (!el) return undefined;
    const update = () => {
      setEdges({ left: el.scrollLeft > 4, right: el.scrollLeft + el.clientWidth < el.scrollWidth - 4 });
    };
    update();
    el.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      el.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
    };
  }, [links.length]);

  if (links.length === 0) return null;
  const mask =
    edges.left && edges.right
      ? 'linear-gradient(90deg, transparent, #000 28px, #000 calc(100% - 28px), transparent)'
      : edges.left
        ? 'linear-gradient(90deg, transparent, #000 28px)'
        : edges.right
          ? 'linear-gradient(90deg, #000 calc(100% - 28px), transparent)'
          : 'none';

  return (
    <div className="relative" data-testid="social-bar">
      <div
        ref={rail}
        className="no-scrollbar momentum -mx-4 flex snap-x snap-mandatory gap-2 overflow-x-auto scroll-px-4 px-4 py-1 sm:mx-0 sm:px-0"
        style={{ WebkitMaskImage: mask, maskImage: mask }}
        role="list"
        aria-label="Contact and social links"
      >
        {links.map(({ key, label, title, icon: Icon, href, primary, native }) => (
          <a
            key={key}
            role="listitem"
            href={href}
            target={native ? undefined : '_blank'}
            rel={native ? undefined : 'noopener noreferrer'}
            onClick={() => buzz()}
            title={title}
            data-chip={key}
            className={cx(
              'tap flex h-11 shrink-0 snap-start items-center gap-2 rounded-full border px-4 text-[13px] font-semibold backdrop-blur-md transition-colors',
              primary
                ? 'border-accent-400/60 bg-accent-500/15 text-accent-300 hover:bg-accent-500/25'
                : 'border-white/10 bg-white/[0.04] text-zinc-200 hover:border-white/25 hover:bg-white/[0.08]',
            )}
          >
            <Icon size={16} />
            <span className="whitespace-nowrap">{label}</span>
          </a>
        ))}
      </div>
    </div>
  );
}
