import { useState } from 'react';
import { ExternalLink, LogOut, QrCode, ShieldAlert } from 'lucide-react';
import RestaurantQrModal from '../RestaurantQrModal.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { api } from '../../api/client.js';
import { useToast } from '../Toast.jsx';
import { STATUS_STYLE, buzz, cx, fmtBytes } from '../../utils/format.js';

/** Restaurant badge, status, usage, link to the public menu, impersonation banner. */
export default function StudioTopBar({ settings, onSettingsChange }) {
  const toast = useToast();
  const { restaurant, impersonatedBy, logout } = useAuth();
  const branding = settings?.branding;
  const name = branding?.name || restaurant.name;
  const logo = branding?.logoUrl;
  const status = settings?.status || restaurant.status;
  const [qrOpen, setQrOpen] = useState(false);
  const [isMenuLive, setIsMenuLive] = useState(settings?.isMenuLive !== false);
  const [togglingMenu, setTogglingMenu] = useState(false);

  const toggleMenuLive = async () => {
    if (togglingMenu) return;
    const next = !isMenuLive;
    setIsMenuLive(next);
    setTogglingMenu(true);
    buzz(15);
    try {
      await api.setMenuStatus(next);
      onSettingsChange?.({ ...settings, isMenuLive: next });
      toast(next ? 'Menu is now live for all guests' : 'Menu set to private (drafting mode)');
    } catch (err) {
      setIsMenuLive(!next);
      toast(err.message, 'error');
    } finally {
      setTogglingMenu(false);
    }
  };

  return (
    <header className="glass pt-safe sticky top-0 z-40 border-b hairline">
      {impersonatedBy && (
        <div className="flex items-center justify-center gap-2 bg-amber-500 px-4 py-2 text-center text-[13px] font-semibold text-zinc-950">
          <ShieldAlert size={16} aria-hidden="true" />
          Admin session ({impersonatedBy}): you are editing {name} as its owner.
          <button type="button" onClick={logout} className="ml-2 rounded-full bg-zinc-950/15 px-3 py-0.5 font-bold hover:bg-zinc-950/25">
            End session
          </button>
        </div>
      )}
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <div className="flex min-w-0 items-center gap-3">
          <div className="grid h-11 w-11 shrink-0 place-items-center overflow-hidden rounded-2xl border border-amber-500/40 bg-onyx">
            {logo ? (
              <img src={logo} alt="" className="h-full w-full object-cover" />
            ) : (
              <span className="font-display text-2xl font-semibold italic text-amber-300">{name.charAt(0).toUpperCase()}</span>
            )}
          </div>
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="truncate text-[13px] font-bold uppercase tracking-[0.22em] text-zinc-100">{name}</span>
              {status && (
                <span className={cx('shrink-0 rounded-full border px-2 py-0.5 text-[9px] font-bold uppercase tracking-[0.16em]', STATUS_STYLE[status])}>
                  {status}
                </span>
              )}
            </div>
            <div className="truncate font-mono text-[10px] text-zinc-500">
              Studio · /r/{restaurant.slug}
              {settings ? ` · ${settings.dishCount} dishes · ${settings.model3dCount} in 3D · ${fmtBytes(settings.storageUsedBytes)}` : ''}
            </div>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {/* Public vs Private Menu Toggle */}
          <button
            type="button"
            disabled={togglingMenu}
            onClick={toggleMenuLive}
            title={isMenuLive ? 'Menu is publicly live. Click to make private.' : 'Menu is private. Click to make live for guests.'}
            className={cx(
              'tap hidden h-11 items-center gap-2 rounded-full border px-3.5 text-xs font-semibold transition-all lg:flex',
              isMenuLive
                ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300 hover:bg-emerald-500/20'
                : 'border-amber-500/40 bg-amber-500/10 text-amber-300 hover:bg-amber-500/20',
            )}
          >
            <span className={cx('h-2 w-2 rounded-full', isMenuLive ? 'bg-emerald-400 animate-pulse' : 'bg-amber-400')} />
            <span>{isMenuLive ? 'Menu: Live for Guests' : 'Menu: Private (Draft)'}</span>
          </button>

          <button
            type="button"
            onClick={() => {
              buzz();
              setQrOpen(true);
            }}
            className="tap flex h-11 min-w-11 items-center justify-center gap-2 rounded-full bg-gradient-to-b from-amber-400 to-amber-600 px-3 text-[13px] font-bold text-zinc-950 shadow-fab md:px-4"
            aria-label="Restaurant QR code"
          >
            <QrCode size={16} aria-hidden="true" />
            <span className="hidden md:inline">Restaurant QR Code</span>
          </button>
          <a
            href={`/r/${restaurant.slug}?preview=true`}
            target="_blank"
            rel="noreferrer"
            aria-label="View menu preview"
            className="tap flex h-11 min-w-11 items-center justify-center gap-2 rounded-full border border-amber-500/30 px-3 text-[13px] font-semibold text-amber-200 hover:border-amber-400/60 sm:px-4"
          >
            <ExternalLink size={15} aria-hidden="true" />
            <span className="hidden sm:inline">{isMenuLive ? 'View guest menu' : 'Preview menu'}</span>
          </a>
          <button
            type="button"
            onClick={() => {
              buzz();
              logout();
            }}
            aria-label="Sign out"
            className="tap grid h-11 w-11 place-items-center rounded-full border hairline text-zinc-300 hover:border-zinc-600"
          >
            <LogOut size={16} aria-hidden="true" />
          </button>
        </div>
      </div>
      {qrOpen && <RestaurantQrModal restaurant={{ name, slug: restaurant.slug }} onClose={() => setQrOpen(false)} />}
    </header>
  );
}

