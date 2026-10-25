import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, setAdminToken, setAuthToken, setPasswordRequiredHandler, setUnauthorizedHandler } from '../api/client.js';

/**
 * Two independent sessions:
 *  - restaurant (studio) session: localStorage, or sessionStorage for a super-admin impersonation
 *    session so it lives only in the tab the admin opened;
 *  - super-admin session: localStorage under its own key.
 * Deadlines are computed locally from expiresInSeconds (immune to server/device clock skew).
 */
const RESTAURANT_KEY = 'atelier.session.v3';
const ADMIN_KEY = 'atelier.admin.v3';
const HANDOFF_PREFIX = 'atelier.handoff.';

function storages() {
  const out = [];
  try {
    out.push(window.sessionStorage);
  } catch {
    /* blocked */
  }
  try {
    out.push(window.localStorage);
  } catch {
    /* blocked */
  }
  return out;
}

function readJson(storage, key) {
  try {
    const raw = storage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function valid(s, field) {
  return s && s.token && s[field] ? s : null;
}

function readRestaurantSession() {
  for (const st of storages()) {
    const s = valid(readJson(st, RESTAURANT_KEY), 'restaurant');
    if (s) return s;
  }
  return null;
}

function writeRestaurantSession(session) {
  const [sessionStore, localStore] = storages();
  try {
    sessionStore?.removeItem(RESTAURANT_KEY);
    localStore?.removeItem(RESTAURANT_KEY);
    if (session) (session.impersonatedBy ? sessionStore : localStore)?.setItem(RESTAURANT_KEY, JSON.stringify(session));
  } catch {
    /* private mode: session lasts for this page only */
  }
}

function readAdminSession() {
  try {
    return valid(readJson(window.localStorage, ADMIN_KEY), 'admin');
  } catch {
    return null;
  }
}

function writeAdminSession(session) {
  try {
    if (session) window.localStorage.setItem(ADMIN_KEY, JSON.stringify(session));
    else window.localStorage.removeItem(ADMIN_KEY);
  } catch {
    /* ignore */
  }
}

function toSession(auth) {
  return {
    token: auth.token,
    restaurant: auth.restaurant,
    impersonatedBy: auth.impersonatedBy || null,
    mustChangePassword: !auth.impersonatedBy && !!(auth.mustChangePassword || auth.restaurant?.mustChangePassword),
    localExpiresAt: null,
  };
}

/** Consumes a one-time impersonation handoff written by the admin console (?handoff=<id>). */
function consumeHandoff() {
  try {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('handoff');
    if (!id) return null;
    const raw = window.localStorage.getItem(HANDOFF_PREFIX + id);
    window.localStorage.removeItem(HANDOFF_PREFIX + id);
    params.delete('handoff');
    const rest = params.toString();
    window.history.replaceState(null, '', window.location.pathname + (rest ? `?${rest}` : ''));
    if (!raw) return null;
    const session = toSession(JSON.parse(raw));
    writeRestaurantSession(session);
    return session;
  } catch {
    return null;
  }
}

/** Admin console side of the handoff: park the session for the new tab to pick up. */
export function stashImpersonationHandoff(auth) {
  const id = Math.random().toString(36).slice(2) + Date.now().toString(36);
  window.localStorage.setItem(HANDOFF_PREFIX + id, JSON.stringify(auth));
  setTimeout(() => {
    try {
      window.localStorage.removeItem(HANDOFF_PREFIX + id);
    } catch {
      /* ignore */
    }
  }, 60000);
  return id;
}

// Restore before the first render so the first API calls already carry the tokens.
const initialSession = consumeHandoff() || readRestaurantSession();
const initialAdmin = readAdminSession();
setAuthToken(initialSession?.token);
setAdminToken(initialAdmin?.token);

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(initialSession);
  const [adminSession, setAdminSession] = useState(initialAdmin);
  const [authDialog, setAuthDialog] = useState(null); // null | 'signin'

  const persist = useCallback((auth) => {
    const next = toSession(auth);
    setAuthToken(next.token);
    writeRestaurantSession(next);
    setSession(next);
    return next;
  }, []);

  const logout = useCallback(() => {
    setAuthToken(null);
    writeRestaurantSession(null);
    setSession(null);
  }, []);

  const adminLogout = useCallback(() => {
    setAdminToken(null);
    writeAdminSession(null);
    setAdminSession(null);
  }, []);

  // The super admin reset the password while this session was open: lock the studio again.
  const requirePasswordChange = useCallback(() => {
    setSession((s) => {
      if (!s || s.mustChangePassword) return s;
      const next = { ...s, mustChangePassword: true };
      writeRestaurantSession(next);
      return next;
    });
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => logout(), 'restaurant');
    setUnauthorizedHandler(() => adminLogout(), 'admin');
    setPasswordRequiredHandler(requirePasswordChange);
    return () => {
      setUnauthorizedHandler(null, 'restaurant');
      setUnauthorizedHandler(null, 'admin');
      setPasswordRequiredHandler(null);
    };
  }, [logout, adminLogout, requirePasswordChange]);

  // Re-validate restored sessions once (suspended account, rotated secret, ...).
  useEffect(() => {
    let alive = true;
    if (initialSession) {
      api
        .me()
        .then((restaurant) => {
          if (!alive) return;
          setSession((s) => {
            if (!s) return s;
            const next = { ...s, restaurant, mustChangePassword: !s.impersonatedBy && !!restaurant.mustChangePassword };
            writeRestaurantSession(next);
            return next;
          });
        })
        .catch((e) => {
          if (alive && e.status === 403) logout();
        });
    }
    if (initialAdmin) {
      api
        .adminMe()
        .then((admin) => {
          if (!alive) return;
          setAdminSession((s) => {
            if (!s) return s;
            const next = { ...s, admin };
            writeAdminSession(next);
            return next;
          });
        })
        .catch(() => {});
    }
    return () => {
      alive = false;
    };
  }, [logout]);

  const login = useCallback(async (email, password) => persist(await api.login({ email, password })), [persist]);
  /** Replaces the temporary password; the API answers with a new full-access token. */
  const changePassword = useCallback(
    async (currentPassword, newPassword) => persist(await api.changePassword({ currentPassword, newPassword })),
    [persist],
  );

  const adminLogin = useCallback(async (email, password) => {
    const auth = await api.adminLogin({ email, password });
    const next = {
      token: auth.token,
      admin: auth.admin,
      localExpiresAt: null,
    };
    setAdminToken(next.token);
    writeAdminSession(next);
    setAdminSession(next);
    return next;
  }, []);

  const updateAdminProfile = useCallback((admin) => {
    setAdminSession((s) => {
      if (!s) return s;
      const next = { ...s, admin };
      writeAdminSession(next);
      return next;
    });
  }, []);

  const updateRestaurant = useCallback((patch) => {
    setSession((s) => {
      if (!s) return s;
      const next = { ...s, restaurant: { ...s.restaurant, ...patch } };
      writeRestaurantSession(next);
      return next;
    });
  }, []);

  const value = useMemo(
    () => ({
      session,
      restaurant: session?.restaurant || null,
      isAuthenticated: !!session,
      impersonatedBy: session?.impersonatedBy || null,
      mustChangePassword: !!session?.mustChangePassword,
      login,
      changePassword,
      logout,
      updateRestaurant,
      adminSession,
      admin: adminSession?.admin || null,
      isAdmin: !!adminSession,
      adminLogin,
      adminLogout,
      updateAdminProfile,
      authDialog,
      openAuth: () => setAuthDialog('signin'),
      closeAuth: () => setAuthDialog(null),
    }),
    [session, adminSession, login, changePassword, logout, updateRestaurant, adminLogin, adminLogout, updateAdminProfile, authDialog],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
