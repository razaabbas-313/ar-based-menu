import { useMemo, useSyncExternalStore } from 'react';

/**
 * A tiny History-API router (four routes don't justify a dependency):
 *   /           public B2B landing page (no restaurant directory: menus are private)
 *   /r/:slug    a restaurant's guest menu (public)
 *   /studio     restaurant admin dashboard (ROLE_RESTAURANT)
 *   /admin      platform super-admin console (ROLE_SUPER_ADMIN)
 */
const NAV_EVENT = 'atelier:navigate';

function subscribe(callback) {
  window.addEventListener('popstate', callback);
  window.addEventListener(NAV_EVENT, callback);
  return () => {
    window.removeEventListener('popstate', callback);
    window.removeEventListener(NAV_EVENT, callback);
  };
}

const getHref = () => window.location.pathname + window.location.search;

export function navigate(to, { replace = false, scroll = true } = {}) {
  if (to === getHref()) return;
  if (replace) window.history.replaceState(null, '', to);
  else window.history.pushState(null, '', to);
  window.dispatchEvent(new Event(NAV_EVENT));
  if (scroll) window.scrollTo({ top: 0 });
}

export function useLocation() {
  const href = useSyncExternalStore(subscribe, getHref, getHref);
  return useMemo(() => {
    const url = new URL(href, window.location.origin);
    return { pathname: url.pathname, query: url.searchParams, href };
  }, [href]);
}

export function matchRoute(pathname) {
  const menu = pathname.match(/^\/r\/([^/]+)\/?$/);
  if (menu) return { name: 'menu', slug: decodeURIComponent(menu[1]).toLowerCase() };
  if (/^\/studio\/?$/.test(pathname)) return { name: 'studio' };
  if (/^\/admin\/?$/.test(pathname)) return { name: 'admin' };
  if (pathname === '/' || pathname === '') return { name: 'home' };
  return { name: 'not-found' };
}

/** <a> that navigates client-side but still works with modifier-clicks / new tabs. */
export function linkProps(href) {
  return {
    href,
    onClick: (e) => {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      e.preventDefault();
      navigate(href);
    },
  };
}
