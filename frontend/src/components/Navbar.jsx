import { ChefHat, LogIn, LogOut } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import { buzz } from '../utils/format.js';
import { linkProps } from '../utils/router.js';

/** Compact platform header used on the studio sign-in and fallback pages. */
export default function Navbar() {
  const { isAuthenticated, restaurant, openAuth, logout } = useAuth();

  return (
    <header className="glass pt-safe sticky top-0 z-40 border-b hairline">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <a {...linkProps('/')} className="flex min-w-0 items-center gap-3" aria-label="Home">
          <div className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl border border-amber-500/40 bg-gradient-to-br from-amber-500/25 to-transparent">
            <span className="font-display text-2xl font-semibold italic text-amber-300">A</span>
          </div>
          <div className="min-w-0">
            <div className="truncate text-[12px] font-bold uppercase tracking-[0.28em] text-zinc-100 sm:text-[13px]">Atelier Culinary Studio</div>
            <div className="truncate text-[10px] font-semibold tracking-[0.3em] text-amber-500/90">3D &amp; AR EXPERIENCE</div>
          </div>
        </a>
        <div className="flex shrink-0 items-center gap-2">
          {isAuthenticated ? (
            <>
              <a
                {...linkProps('/studio')}
                className="tap flex h-11 max-w-[14rem] items-center gap-2 rounded-full border border-amber-500/30 px-4 text-[13px] font-semibold text-amber-200 hover:border-amber-400/60"
              >
                <ChefHat size={16} className="shrink-0" aria-hidden="true" />
                <span className="hidden truncate sm:inline">{restaurant.name} studio</span>
                <span className="sm:hidden">Studio</span>
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
            </>
          ) : (
            <button
              type="button"
              onClick={() => {
                buzz();
                openAuth();
              }}
              className="tap flex h-11 items-center gap-2 whitespace-nowrap rounded-full border border-amber-500/40 px-4 text-[13px] font-semibold text-amber-200 hover:border-amber-400 hover:bg-amber-500/10"
            >
              <LogIn size={16} className="shrink-0" aria-hidden="true" />
              <span className="sm:hidden">Sign in</span>
              <span className="hidden sm:inline">Staff Portal</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
}
