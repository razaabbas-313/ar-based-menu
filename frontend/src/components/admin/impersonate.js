import { api } from '../../api/client.js';
import { stashImpersonationHandoff } from '../../context/AuthContext.jsx';

/**
 * Opens the restaurant's studio in a new tab as its owner. The tab is opened synchronously (inside
 * the click) so popup blockers allow it; the short-lived token is handed over via localStorage and
 * then lives only in that tab's sessionStorage.
 */
export async function impersonateRestaurant(id) {
  const win = window.open('', '_blank');
  try {
    if (win) {
      win.document.title = 'Opening studio…';
      win.document.body.style.cssText = 'background:#09090b;color:#a1a1aa;font:14px system-ui;display:grid;place-items:center;height:100vh;margin:0';
      win.document.body.textContent = 'Opening restaurant studio…';
    }
    const auth = await api.adminImpersonate(id);
    const handoff = stashImpersonationHandoff(auth);
    const url = `/studio?handoff=${encodeURIComponent(handoff)}`;
    if (win) win.location.href = url;
    else window.location.assign(url);
    return auth;
  } catch (e) {
    if (win) win.close();
    throw e;
  }
}
