import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  ExternalLink,
  History,
  KeyRound,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Store,
  TriangleAlert,
} from 'lucide-react';
import AdminLogin from '../components/admin/AdminLogin.jsx';
import ChangePasswordModal from '../components/admin/ChangePasswordModal.jsx';
import KpiCards from '../components/admin/KpiCards.jsx';
import InsightDashboard from '../components/studio/InsightDashboard.jsx';
import PlansManager from '../components/admin/PlansManager.jsx';
import RestaurantTable from '../components/admin/RestaurantTable.jsx';
import RestaurantFormModal from '../components/admin/RestaurantFormModal.jsx';
import RestaurantDrawer from '../components/admin/RestaurantDrawer.jsx';
import DeleteRestaurantModal from '../components/admin/DeleteRestaurantModal.jsx';
import Admin3dDeskModal from '../components/admin/Admin3dDeskModal.jsx';
import LuxurySelect from '../components/ui/LuxurySelect.jsx';
import { Spinner } from '../components/ui.jsx';
import { useToast } from '../components/Toast.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../api/client.js';
import { STATUS_STYLE, buzz, cx, fmtBytes, fmtDate } from '../utils/format.js';
import { linkProps } from '../utils/router.js';

const PAGE_SIZE = 20;
const STATUS_FILTERS = ['', 'ACTIVE', 'TRIAL', 'PENDING', 'SUSPENDED'];

/** /admin: platform super-admin console (ROLE_SUPER_ADMIN). */
export default function AdminPage() {
  const { isAdmin } = useAuth();
  return isAdmin ? <Console /> : <AdminLogin />;
}

function Console() {
  const { admin, adminLogout } = useAuth();
  const toast = useToast();
  const [mainTab, setMainTab] = useState('restaurants'); // 'restaurants' | '3d'
  const [analytics, setAnalytics] = useState(null);
  const [analyticsError, setAnalyticsError] = useState('');
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [status, setStatus] = useState('');
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [sort, setSort] = useState('createdAt');
  const [direction, setDirection] = useState('desc');
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [listError, setListError] = useState('');
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState(null); // null | {restaurant?}
  const [drawerId, setDrawerId] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [version, setVersion] = useState(0);

  // 3D Studio Pipeline State
  const [pipelineSubTab, setPipelineSubTab] = useState('active'); // 'active' | 'history'
  const [requests3d, setRequests3d] = useState([]);
  const [loading3d, setLoading3d] = useState(false);
  const [error3d, setError3d] = useState('');
  const [selected3dRequest, setSelected3dRequest] = useState(null);

  // 3D Concierge History State
  const [history3d, setHistory3d] = useState([]);
  const [loadingHistory3d, setLoadingHistory3d] = useState(false);
  const [errorHistory3d, setErrorHistory3d] = useState('');
  const [historyRestaurantFilter, setHistoryRestaurantFilter] = useState('');
  const [historyStatusFilter, setHistoryStatusFilter] = useState('');
  const [historyQuery, setHistoryQuery] = useState('');
  const [historyPage, setHistoryPage] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(1);
  const [historyTotalElements, setHistoryTotalElements] = useState(0);
  const [allRestaurantsList, setAllRestaurantsList] = useState([]);

  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [query]);

  const loadAnalytics = useCallback(() => {
    setAnalyticsError('');
    api
      .analytics()
      .then(setAnalytics)
      .catch((e) => setAnalyticsError(e.message));
  }, []);

  const loadList = useCallback(() => {
    setLoading(true);
    setListError('');
    api
      .adminRestaurants({ q: debounced, status, includeDeleted, page, size: PAGE_SIZE, sort, direction })
      .then(setData)
      .catch((e) => setListError(e.message))
      .finally(() => setLoading(false));
  }, [debounced, status, includeDeleted, page, sort, direction]);

  const load3dRequests = useCallback(() => {
    setLoading3d(true);
    setError3d('');
    api
      .admin3dRequests()
      .then((list) => {
        setRequests3d(list || []);
        setLoading3d(false);
      })
      .catch((e) => {
        setError3d(e.message);
        setLoading3d(false);
      });
  }, []);

  const load3dHistory = useCallback((restId, statusVal, pageNum = 0) => {
    setLoadingHistory3d(true);
    setErrorHistory3d('');
    api
      .admin3dRequestsHistory({
        restaurantId: restId ? Number(restId) : undefined,
        status: statusVal || undefined,
        page: pageNum,
        size: 20,
      })
      .then((res) => {
        const items = res?.items || res?.content || (Array.isArray(res) ? res : []);
        setHistory3d(items);
        setHistoryPage(res?.page ?? pageNum);
        setHistoryTotalPages(res?.totalPages || 1);
        setHistoryTotalElements(res?.totalElements ?? items.length);
        setLoadingHistory3d(false);
      })
      .catch((e) => {
        setErrorHistory3d(e.message);
        setLoadingHistory3d(false);
      });
  }, []);

  const loadAllRestaurants = useCallback(() => {
    api
      .adminRestaurants({ size: 100 })
      .then((res) => {
        if (res && res.items) setAllRestaurantsList(res.items);
      })
      .catch(() => {});
  }, []);

  useEffect(loadAnalytics, [loadAnalytics]);
  useEffect(loadList, [loadList]);
  useEffect(() => {
    if (mainTab === '3d') {
      load3dRequests();
      load3dHistory(historyRestaurantFilter, historyStatusFilter, historyPage);
      loadAllRestaurants();
    }
  }, [mainTab, historyRestaurantFilter, historyStatusFilter, historyPage, load3dRequests, load3dHistory, loadAllRestaurants]);

  const refresh = () => {
    loadAnalytics();
    loadList();
    if (mainTab === '3d') {
      load3dRequests();
      load3dHistory(historyRestaurantFilter, historyStatusFilter, historyPage);
    }
    setVersion((v) => v + 1);
  };

  const onSort = (key) => {
    if (sort === key) setDirection((d) => (d === 'asc' ? 'desc' : 'asc'));
    else {
      setSort(key);
      setDirection(key === 'name' ? 'asc' : 'desc');
    }
    setPage(0);
  };

  async function changeStatus(r, next, after) {
    let reason = '';
    if (next === 'SUSPENDED') {
      const answer = window.prompt(`Suspend ${r.name}? Its guest menu goes offline and the owner cannot sign in.\n\nReason (optional, saved to the audit log):`, '');
      if (answer === null) return;
      reason = answer.trim();
    }
    try {
      await api.adminSetStatus(r.id, next, reason || null);
      toast(next === 'SUSPENDED' ? `${r.name} suspended` : `${r.name} is ${next.toLowerCase()}`);
      refresh();
      after?.();
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  async function restore(r) {
    try {
      await api.adminRestore(r.id);
      toast(`${r.name} restored`);
      refresh();
    } catch (e) {
      toast(e.message, 'error');
    }
  }

  const actions = {
    open: (r) => setDrawerId(r.id),
    edit: (r) => setForm({ restaurant: r }),
    status: (r, next) => changeStatus(r, next),
    remove: (r) => setDeleting(r),
    restore,
  };

  const rows = data?.items || [];
  const filtered = debounced || status || includeDeleted;

  return (
    <div className="min-h-screen pb-28 sm:pb-20">
      <header className="glass pt-safe sticky top-0 z-40 border-b hairline">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <a {...linkProps('/')} className="flex min-w-0 items-center gap-3">
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl border border-amber-500/40 bg-onyx">
              <ShieldCheck size={20} className="text-amber-300" aria-hidden="true" />
            </span>
            <span className="min-w-0">
              <span className="block truncate text-[13px] font-bold uppercase tracking-[0.22em] text-zinc-100">Atelier Platform</span>
              <span className="block truncate font-mono text-[10px] text-zinc-500">Super admin · {admin?.email}</span>
            </span>
          </a>
          <div className="flex shrink-0 items-center gap-2">
            <button type="button" onClick={() => setPasswordOpen(true)} className="tap grid h-11 w-11 place-items-center rounded-full border hairline text-zinc-300" aria-label="Change password" title="Change password">
              <KeyRound size={16} aria-hidden="true" />
            </button>
            <button type="button" onClick={adminLogout} className="tap grid h-11 w-11 place-items-center rounded-full border hairline text-zinc-300" aria-label="Sign out" title="Sign out">
              <LogOut size={16} aria-hidden="true" />
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-4 sm:px-6">
        {admin?.mustChangePassword && (
          <div className="mt-5 flex flex-col gap-3 rounded-2xl border border-amber-500/40 bg-amber-500/10 p-4 text-sm text-amber-100 sm:flex-row sm:items-center sm:justify-between">
            <span className="flex items-center gap-2">
              <ShieldAlert size={18} aria-hidden="true" /> You are using the generated first-run password. Set your own now.
            </span>
            <button type="button" onClick={() => setPasswordOpen(true)} className="tap h-10 shrink-0 rounded-full bg-amber-500 px-4 text-sm font-bold text-zinc-950">
              Change password
            </button>
          </div>
        )}

        <section className="py-6">
          <div className="mb-4 flex items-end justify-between gap-3">
            <div>
              <div className="font-mono text-[10px] uppercase tracking-[0.3em] text-amber-500/80">Platform overview</div>
              <h1 className="font-display text-4xl font-semibold">Command center</h1>
            </div>
            <button type="button" onClick={refresh} className="tap flex h-11 items-center gap-2 rounded-full border hairline px-4 text-sm text-zinc-300" aria-label="Refresh">
              <RefreshCw size={15} aria-hidden="true" /> <span className="hidden sm:inline">Refresh</span>
            </button>
          </div>
          {analyticsError && <ErrorLine text={analyticsError} onRetry={loadAnalytics} />}
          <KpiCards analytics={analytics} />
          {analytics && analytics.topByStorage.length > 0 && (
            <div className="mt-4 grid gap-3 md:grid-cols-2">
              <TopList title="Top storage" items={analytics.topByStorage} value={(r) => fmtBytes(r.storageUsedBytes)} onOpen={(r) => setDrawerId(r.id)} />
              <TopList title="Largest menus" items={analytics.topByDishes} value={(r) => `${r.dishCount} dishes`} onOpen={(r) => setDrawerId(r.id)} />
            </div>
          )}
        </section>

        <div className="mb-4 sm:hidden"><label className="sr-only" htmlFor="admin-section">Administration section</label><select id="admin-section" value={mainTab} onChange={e => setMainTab(e.target.value)} className="w-full rounded-xl border border-white/10 bg-zinc-900 p-3 text-sm"><option value="restaurants">Restaurants & menus</option><option value="plans">Plans & capabilities</option><option value="3d">3D studio pipeline</option></select></div>
        {/* Section Navigation Tabs (Desktop) */}
        <div className="mb-6 hidden sm:flex gap-2 border-b hairline pb-4">
          <button
            type="button"
            onClick={() => setMainTab('restaurants')}
            className={cx(
              'tap flex h-11 items-center gap-2 rounded-full border px-5 text-sm font-semibold transition-all',
              mainTab === 'restaurants'
                ? 'border-amber-500 bg-amber-500 text-zinc-950 shadow-fab'
                : 'hairline text-zinc-400 hover:text-zinc-100',
            )}
          >
            <Store size={16} />
            <span>Restaurants &amp; Menus</span>
          </button>
          <button
            type="button"
            onClick={() => setMainTab('3d')}
            className={cx(
              'tap flex h-11 items-center gap-2 rounded-full border px-5 text-sm font-semibold transition-all',
              mainTab === '3d'
                ? 'border-amber-500 bg-amber-500 text-zinc-950 shadow-fab'
                : 'hairline text-zinc-400 hover:text-zinc-100',
            )}
          >
            <Box size={16} />
            <span>3D Studio Pipeline</span>
            {requests3d.length > 0 && (
              <span className="rounded-full bg-amber-400/20 px-2 py-0.5 text-xs font-bold text-amber-300">
                {requests3d.length}
              </span>
            )}
          </button>
          <button type="button" onClick={() => setMainTab('plans')} className={cx('tap flex h-11 items-center gap-2 rounded-full border px-5 text-sm font-semibold', mainTab === 'plans' ? 'border-amber-500 bg-amber-500 text-zinc-950' : 'hairline text-zinc-400 hover:text-zinc-100')}><ShieldCheck size={16} />Plans & capabilities</button>
        </div>

        <button type="button" onClick={()=>setMainTab('insights')} className="mb-5 min-h-11 rounded-full border hairline px-5 text-sm">Platform insights</button>
        {mainTab === 'insights' && <InsightDashboard platform/>}
        {mainTab === 'plans' && <PlansManager />}
        {mainTab === 'restaurants' && (
          <section>
            <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <h2 className="font-display text-3xl font-semibold">Restaurants</h2>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <div className="relative sm:w-72">
                  <Search size={16} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-zinc-500" aria-hidden="true" />
                  <input
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Search name, slug, email"
                    aria-label="Search restaurants"
                    className="h-11 w-full rounded-full border hairline bg-black/40 pl-11 pr-4 text-sm text-zinc-50 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
                  />
                </div>
                <button type="button" onClick={() => setForm({})} className="tap flex h-11 items-center justify-center gap-2 rounded-full bg-amber-500 px-5 text-sm font-bold text-zinc-950">
                  <Plus size={16} aria-hidden="true" /> New Restaurant
                </button>
              </div>
            </div>
            <div className="no-scrollbar -mx-4 mb-4 flex items-center gap-2 overflow-x-auto px-4 sm:mx-0 sm:px-0">
              {STATUS_FILTERS.map((s) => (
                <button
                  key={s || 'all'}
                  type="button"
                  onClick={() => {
                    setStatus(s);
                    setPage(0);
                  }}
                  className={cx(
                    'tap h-9 shrink-0 rounded-full border px-3 text-[11px] font-bold uppercase tracking-[0.14em]',
                    status === s ? (s ? STATUS_STYLE[s] : 'border-amber-500 bg-amber-500 text-zinc-950') : 'hairline text-zinc-500',
                  )}
                >
                  {s || 'All'}
                </button>
              ))}
              <label className="ml-1 flex shrink-0 items-center gap-2 text-xs text-zinc-400">
                <input
                  type="checkbox"
                  checked={includeDeleted}
                  onChange={(e) => {
                    setIncludeDeleted(e.target.checked);
                    setPage(0);
                  }}
                  className="h-4 w-4 accent-amber-500"
                />
                Include deleted
              </label>
            </div>

            {listError && <ErrorLine text={listError} onRetry={loadList} />}
            {loading && !data ? (
              <div className="grid place-items-center rounded-3xl border hairline py-20">
                <Spinner />
              </div>
            ) : rows.length === 0 ? (
              <div className="rounded-3xl border border-dashed hairline px-6 py-16 text-center">
                <div className="font-display text-2xl font-semibold">{filtered ? 'No restaurants match' : 'No restaurants yet'}</div>
                <p className="mt-1 text-sm text-zinc-500">{filtered ? 'Try another search or filter.' : 'Provision the first restaurant with “New restaurant”. Owners receive a temporary password.'}</p>
              </div>
            ) : (
              <div className={cx(loading && 'opacity-60 transition-opacity')}>
                <RestaurantTable rows={rows} sort={sort} direction={direction} onSort={onSort} actions={actions} />
              </div>
            )}

            {data && data.totalPages > 1 && (
              <div className="mt-4 flex items-center justify-between text-sm text-zinc-400">
                <span>
                  Page {data.page + 1} of {data.totalPages} · {data.totalElements} restaurants
                </span>
                <div className="flex gap-2">
                  <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)} className="tap grid h-10 w-10 place-items-center rounded-full border hairline disabled:opacity-30" aria-label="Previous page">
                    <ChevronLeft size={16} aria-hidden="true" />
                  </button>
                  <button type="button" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)} className="tap grid h-10 w-10 place-items-center rounded-full border hairline disabled:opacity-30" aria-label="Next page">
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                </div>
              </div>
            )}
          </section>
        )}

        {mainTab === '3d' && (
          <section>
            <div className="mb-6 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="font-display text-3xl font-semibold">3D Studio Pipeline</h2>
                <p className="text-xs text-zinc-400">
                  White-glove 3D digital twin requests from restaurant chefs ready for production and AR publishing.
                </p>
              </div>
              <div className="flex items-center gap-2 self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => {
                    load3dRequests();
                    load3dHistory(historyRestaurantFilter);
                  }}
                  className="tap flex h-10 items-center gap-2 rounded-full border hairline px-4 text-xs font-semibold text-zinc-300 hover:text-zinc-100"
                >
                  <RefreshCw size={13} className={cx((loading3d || loadingHistory3d) && 'animate-spin')} />
                  <span>Refresh Pipeline</span>
                </button>
              </div>
            </div>

            {/* Sub Tabs: Active Production Desk vs 3D Concierge History */}
            <div className="mb-6 flex items-center gap-2 border-b hairline pb-3">
              <button
                type="button"
                onClick={() => {
                  buzz(10);
                  setPipelineSubTab('active');
                }}
                className={cx(
                  'tap flex items-center gap-2 rounded-full px-4 py-2 text-xs font-bold transition-all',
                  pipelineSubTab === 'active'
                    ? 'bg-amber-500 text-zinc-950 shadow-fab'
                    : 'border hairline bg-black/40 text-zinc-400 hover:text-zinc-200'
                )}
              >
                <Box size={14} />
                <span>Active Production Desk</span>
                {requests3d.length > 0 && (
                  <span className={cx('rounded-full px-1.5 py-0.2 text-[10px] font-mono font-bold', pipelineSubTab === 'active' ? 'bg-zinc-950/20 text-zinc-950' : 'bg-amber-500/20 text-amber-300')}>
                    {requests3d.length}
                  </span>
                )}
              </button>

              <button
                type="button"
                onClick={() => {
                  buzz(10);
                  setPipelineSubTab('history');
                  if (history3d.length === 0) load3dHistory(historyRestaurantFilter, historyStatusFilter, historyPage);
                }}
                className={cx(
                  'tap flex items-center gap-2 rounded-full px-4 py-2 text-xs font-bold transition-all',
                  pipelineSubTab === 'history'
                    ? 'bg-amber-500 text-zinc-950 shadow-fab'
                    : 'border hairline bg-black/40 text-zinc-400 hover:text-zinc-200'
                )}
              >
                <History size={14} />
                <span>3D Concierge History</span>
                {history3d.length > 0 && (
                  <span className={cx('rounded-full px-1.5 py-0.2 text-[10px] font-mono font-bold', pipelineSubTab === 'history' ? 'bg-zinc-950/20 text-zinc-950' : 'bg-zinc-800 text-zinc-300')}>
                    {history3d.length}
                  </span>
                )}
              </button>
            </div>

            {/* ACTIVE PRODUCTION DESK TAB */}
            {pipelineSubTab === 'active' && (
              <>
                {error3d && <ErrorLine text={error3d} onRetry={load3dRequests} />}

                {loading3d && requests3d.length === 0 ? (
                  <div className="grid place-items-center rounded-3xl border hairline py-20">
                    <Spinner />
                  </div>
                ) : requests3d.length === 0 ? (
                  <div className="rounded-3xl border border-dashed hairline px-6 py-16 text-center">
                    <div className="grid h-14 w-14 place-items-center rounded-2xl bg-amber-500/10 text-amber-300 mx-auto">
                      <Box size={28} />
                    </div>
                    <h3 className="mt-4 font-display text-2xl font-semibold">No Pending 3D Requests</h3>
                    <p className="mt-1 text-sm text-zinc-500 max-w-md mx-auto">
                      When restaurant chefs request a white-glove 3D reconstruction with 5 angle photos, requests will appear here for 3D modeling and approval.
                    </p>
                  </div>
                ) : (
                  <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {requests3d.map((req) => (
                      <div
                        key={req.dishId}
                        className="overflow-hidden rounded-3xl border hairline bg-onyx transition-all hover:border-amber-500/40 p-5 flex flex-col justify-between gap-4"
                      >
                        <div>
                          <div className="flex gap-3">
                            <div className="h-16 w-16 shrink-0 overflow-hidden rounded-2xl bg-black">
                              {req.photoUrl ? (
                                <img src={req.photoUrl} alt="" className="h-full w-full object-cover" />
                              ) : (
                                <div className="grid h-full place-items-center text-zinc-700">
                                  <Box size={20} />
                                </div>
                              )}
                            </div>
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <span className="rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-0.5 text-[9px] font-bold text-amber-300">
                                  {req.status}
                                </span>
                                <span className="text-[10px] text-zinc-500 font-mono">
                                  {req.photos?.length || 0} angle photos
                                </span>
                              </div>
                              <h4 className="truncate font-semibold text-zinc-100 text-sm mt-1 flex items-center gap-1.5">
                                <span className="truncate">{req.dishTitle}</span>
                                {req.dishCode && (
                                  <span className="font-mono text-[9px] text-amber-300/90 px-1.5 py-0.5 rounded bg-black/40 border border-amber-500/30 shrink-0">
                                    {req.dishCode}
                                  </span>
                                )}
                              </h4>
                              <div className="truncate text-xs text-zinc-400">
                                {req.restaurantName} <span className="font-mono text-zinc-500">(/r/{req.restaurantSlug})</span>
                              </div>
                            </div>
                          </div>

                          {req.notes && (
                            <div className="mt-3 line-clamp-2 rounded-xl bg-black/40 p-2.5 text-[11px] text-amber-100/80 border hairline">
                              “{req.notes}”
                            </div>
                          )}
                        </div>

                        <div className="flex items-center justify-between border-t hairline pt-3 text-xs">
                          <span className="text-zinc-500 text-[11px]">{fmtDate(req.requestedAt)}</span>
                          <button
                            type="button"
                            onClick={() => setSelected3dRequest(req)}
                            className="tap flex items-center gap-1.5 rounded-full bg-amber-500 px-4 py-1.5 text-xs font-bold text-zinc-950 hover:bg-amber-400 shadow-fab"
                          >
                            <Sparkles size={13} />
                            <span>Open 3D Desk</span>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}

            {/* 3D CONCIERGE HISTORY TAB */}
            {pipelineSubTab === 'history' && (
              <div className="space-y-4">
                {/* History Filter Bar */}
                <div className="flex flex-col gap-3 rounded-2xl border hairline bg-black/30 p-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="relative flex-1">
                    <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
                    <input
                      value={historyQuery}
                      onChange={(e) => setHistoryQuery(e.target.value)}
                      placeholder="Search dish title, reference code, restaurant…"
                      className="h-10 w-full rounded-xl border hairline bg-black/50 pl-9 pr-3 text-xs text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
                    />
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    {/* Restaurant Filter Dropdown */}
                    <div className="flex items-center gap-1.5 text-xs">
                      <span className="text-[10px] uppercase font-semibold text-zinc-500">Restaurant:</span>
                      <LuxurySelect
                        value={historyRestaurantFilter}
                        onChange={(val) => {
                          setHistoryRestaurantFilter(val);
                          setHistoryPage(0);
                          load3dHistory(val, historyStatusFilter, 0);
                        }}
                        options={[
                          { value: '', label: 'All Restaurants' },
                          ...Array.from(
                            new Map(
                              [
                                ...allRestaurantsList.filter(Boolean).map((r) => [String(r.id), { value: String(r.id), label: `${r.name || 'Restaurant'}` }]),
                                ...history3d.filter(Boolean).map((h) => [String(h.restaurantId), { value: String(h.restaurantId), label: `${h.restaurantName || 'Restaurant'}` }]),
                              ]
                            ).values()
                          ).sort((a, b) => (a.label || '').localeCompare(b.label || '')),
                        ]}
                        buttonClassName="h-9 min-w-[9rem] text-xs font-medium"
                      />
                    </div>

                    {/* Status Filter Dropdown */}
                    <div className="flex items-center gap-1.5 text-xs">
                      <span className="text-[10px] uppercase font-semibold text-zinc-500">Status:</span>
                      <LuxurySelect
                        value={historyStatusFilter}
                        onChange={(val) => {
                          setHistoryStatusFilter(val);
                          setHistoryPage(0);
                          load3dHistory(historyRestaurantFilter, val, 0);
                        }}
                        options={[
                          { value: '', label: 'All Statuses' },
                          { value: 'COMPLETED', label: 'Completed (Live in 3D)', color: 'text-emerald-300' },
                          { value: 'SUPERSEDED', label: 'Superseded', color: 'text-zinc-400' },
                          { value: 'IN_PRODUCTION', label: 'In Production', color: 'text-amber-300' },
                          { value: 'PENDING', label: 'Pending Production', color: 'text-amber-300' },
                        ]}
                        buttonClassName="h-9 min-w-[8rem] text-xs font-medium"
                      />
                    </div>
                  </div>
                </div>

                {errorHistory3d && <ErrorLine text={errorHistory3d} onRetry={() => load3dHistory(historyRestaurantFilter, historyStatusFilter, historyPage)} />}

                {loadingHistory3d && history3d.length === 0 ? (
                  <div className="grid place-items-center rounded-3xl border hairline py-20">
                    <Spinner />
                  </div>
                ) : (
                  (() => {
                    const filtered = (Array.isArray(history3d) ? history3d : []).filter((item) => {
                      if (!item) return false;
                      if (historyStatusFilter && item.status !== historyStatusFilter) return false;
                      if (historyRestaurantFilter && String(item.restaurantId) !== String(historyRestaurantFilter)) return false;
                      if (historyQuery.trim()) {
                        const q = historyQuery.trim().toLowerCase();
                        const matchTitle = (item.dishTitle || '').toLowerCase().includes(q);
                        const matchCode = (item.dishCode || '').toLowerCase().includes(q);
                        const matchRest = (item.restaurantName || '').toLowerCase().includes(q);
                        const matchSlug = (item.restaurantSlug || '').toLowerCase().includes(q);
                        if (!matchTitle && !matchCode && !matchRest && !matchSlug) return false;
                      }
                      return true;
                    });

                    if (filtered.length === 0) {
                      return (
                        <div className="rounded-3xl border border-dashed hairline px-6 py-16 text-center">
                          <div className="grid h-14 w-14 place-items-center rounded-2xl bg-zinc-800 text-zinc-400 mx-auto">
                            <History size={26} />
                          </div>
                          <h3 className="mt-4 font-display text-2xl font-semibold">No 3D Concierge History</h3>
                          <p className="mt-1 text-sm text-zinc-500 max-w-md mx-auto">
                            No 3D model requests match your current search or restaurant filter.
                          </p>
                        </div>
                      );
                    }

                    return (
                      <>
                        <div className="space-y-3">
                        {filtered.map((item) => (
                          <div
                            key={item.id}
                            className="rounded-2xl border hairline bg-onyx p-4 transition-colors hover:border-amber-500/30 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
                          >
                            <div className="flex items-start gap-3 min-w-0 flex-1">
                              <div className="h-14 w-14 shrink-0 overflow-hidden rounded-xl bg-black border hairline">
                                {item.dishImageUrl ? (
                                  <img src={item.dishImageUrl} alt="" className="h-full w-full object-cover" />
                                ) : (
                                  <div className="grid h-full place-items-center text-zinc-600">
                                    <Box size={20} />
                                  </div>
                                )}
                              </div>
                              <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2 flex-wrap">
                                  <h4 className="font-semibold text-zinc-100 text-sm truncate">{item.dishTitle}</h4>
                                  {item.dishCode && (
                                    <span className="font-mono text-[10px] font-bold text-amber-300/90 border border-amber-500/30 bg-amber-500/10 px-1.5 py-0.5 rounded shrink-0">
                                      {item.dishCode}
                                    </span>
                                  )}
                                  <span className={cx(
                                    'px-2 py-0.5 rounded-full text-[9px] font-bold border uppercase tracking-wider',
                                    item.status === 'COMPLETED' ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300' :
                                    item.status === 'SUPERSEDED' ? 'border-zinc-700 bg-zinc-800/50 text-zinc-400' :
                                    'border-amber-500/40 bg-amber-500/10 text-amber-300'
                                  )}>
                                    {item.status === 'COMPLETED' ? 'Live in AR' : item.status}
                                  </span>
                                  <span className="text-[10px] text-zinc-400 font-medium">
                                    {item.requestType === 'REPLACEMENT' ? '🔄 3D Replacement' : '✨ New 3D Model'}
                                  </span>
                                </div>

                                <div className="mt-1 flex items-center gap-3 text-xs text-zinc-400 flex-wrap">
                                  <span className="text-zinc-300 font-medium">{item.restaurantName}</span>
                                  <span className="font-mono text-zinc-500">/r/{item.restaurantSlug}</span>
                                  <span className="text-zinc-600">·</span>
                                  <span className="flex items-center gap-1 text-[11px] text-zinc-500">
                                    <Clock size={11} /> Requested {fmtDate(item.requestedAt)}
                                  </span>
                                  {item.completedAt && (
                                    <span className="flex items-center gap-1 text-[11px] text-emerald-400/80">
                                      <CheckCircle2 size={11} /> Completed {fmtDate(item.completedAt)}
                                    </span>
                                  )}
                                </div>

                                {item.notes && (
                                  <div className="mt-2 text-xs text-amber-100/70 bg-black/40 px-2.5 py-1.5 rounded-lg border hairline line-clamp-2">
                                    “{item.notes}”
                                  </div>
                                )}
                              </div>
                            </div>

                            <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                              {item.completedModelUrl && (
                                <a
                                  href={item.completedModelUrl}
                                  target="_blank"
                                  rel="noopener noreferrer"
                                  download
                                  className="tap inline-flex items-center gap-1.5 rounded-full border hairline bg-black/40 px-3.5 py-1.5 text-xs font-semibold text-zinc-200 hover:border-amber-500/40 hover:text-amber-200"
                                  title="Download production .glb asset"
                                >
                                  <Download size={13} />
                                  <span>Download .glb</span>
                                </a>
                              )}
                              {(item.status === 'PENDING' || item.status === 'IN_PRODUCTION') && (
                                <button
                                  type="button"
                                  onClick={() => {
                                    const match = requests3d.find(r => r.dishId === item.dishId);
                                    if (match) setSelected3dRequest(match);
                                    else {
                                      let parsedPhotos = [];
                                      try {
                                        if (item.referencePhotos) parsedPhotos = JSON.parse(item.referencePhotos);
                                      } catch {}
                                      setSelected3dRequest({
                                        dishId: item.dishId,
                                        restaurantId: item.restaurantId,
                                        restaurantName: item.restaurantName,
                                        restaurantSlug: item.restaurantSlug,
                                        dishCode: item.dishCode,
                                        dishTitle: item.dishTitle,
                                        photoUrl: item.dishImageUrl,
                                        status: item.status,
                                        notes: item.notes,
                                        photos: parsedPhotos,
                                      });
                                    }
                                  }}
                                  className="tap inline-flex items-center gap-1.5 rounded-full bg-amber-500 px-3.5 py-1.5 text-xs font-bold text-zinc-950 hover:bg-amber-400 shadow-fab"
                                >
                                  <Sparkles size={13} />
                                  <span>Open 3D Desk</span>
                                </button>
                              )}
                            </div>
                          </div>
                        ))}
                      </div>

                      {/* Pagination Controls */}
                      {historyTotalPages > 1 && (
                        <div className="flex flex-col sm:flex-row items-center justify-between border-t hairline pt-4 px-2 gap-3">
                          <div className="text-xs text-zinc-500">
                            Showing <span className="font-semibold text-zinc-300">{history3d.length}</span> of{' '}
                            <span className="font-semibold text-zinc-300">{historyTotalElements}</span> records
                          </div>
                          <div className="flex items-center gap-2">
                            <button
                              type="button"
                              onClick={() => {
                                const newP = Math.max(0, historyPage - 1);
                                setHistoryPage(newP);
                                load3dHistory(historyRestaurantFilter, historyStatusFilter, newP);
                              }}
                              disabled={historyPage === 0 || loadingHistory3d}
                              className="rounded-xl border hairline bg-black/40 px-3 py-1.5 text-xs font-semibold text-zinc-300 hover:bg-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed transition"
                            >
                              Previous
                            </button>
                            <span className="text-xs font-mono text-zinc-400">
                              Page {historyPage + 1} of {historyTotalPages}
                            </span>
                            <button
                              type="button"
                              onClick={() => {
                                const newP = Math.min(historyTotalPages - 1, historyPage + 1);
                                setHistoryPage(newP);
                                load3dHistory(historyRestaurantFilter, historyStatusFilter, newP);
                              }}
                              disabled={historyPage >= historyTotalPages - 1 || loadingHistory3d}
                              className="rounded-xl border hairline bg-black/40 px-3 py-1.5 text-xs font-semibold text-zinc-300 hover:bg-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed transition"
                            >
                              Next
                            </button>
                          </div>
                        </div>
                      )}
                    </>
                    );
                  })()
                )}
              </div>
            )}
          </section>
        )}
      </main>

      {form && (
        <RestaurantFormModal
          restaurant={form.restaurant}
          onClose={() => setForm(null)}
          onSaved={(saved, message) => {
            setForm(null);
            toast(message);
            if (saved && saved.id) {
              setData((prev) => {
                if (!prev || !prev.items) return prev;
                return {
                  ...prev,
                  items: prev.items.map((it) => (it.id === saved.id ? { ...it, ...saved } : it)),
                };
              });
            }
            refresh();
          }}
        />
      )}
      {drawerId && (
        <RestaurantDrawer
          restaurantId={drawerId}
          version={version}
          onClose={() => setDrawerId(null)}
          onChanged={(updated) => {
            if (updated && updated.id) {
              setData((prev) => {
                if (!prev || !prev.items) return prev;
                return {
                  ...prev,
                  items: prev.items.map((it) => (it.id === updated.id ? { ...it, ...updated } : it)),
                };
              });
            }
            refresh();
          }}
          onEdit={(r) => setForm({ restaurant: r })}
          onDelete={(r) => setDeleting(r)}
          onStatus={(r, next, after) => changeStatus(r, next, after)}
        />
      )}
      {deleting && (
        <DeleteRestaurantModal
          restaurant={deleting}
          onClose={() => setDeleting(null)}
          onDone={(message, mode) => {
            setDeleting(null);
            if (mode === 'hard') setDrawerId(null);
            toast(message);
            refresh();
          }}
        />
      )}
      {passwordOpen && <ChangePasswordModal onClose={() => setPasswordOpen(false)} />}
      {selected3dRequest && (
        <Admin3dDeskModal
          request={selected3dRequest}
          onClose={() => setSelected3dRequest(null)}
          onApproved={() => {
            setSelected3dRequest(null);
            refresh();
            load3dRequests();
          }}
        />
      )}

      {/* Mobile App Bottom Navigation Bar */}
      <nav
        className="fixed bottom-0 left-0 right-0 z-40 flex items-center justify-around border-t hairline bg-zinc-950/95 py-2 px-4 backdrop-blur-xl sm:hidden"
        aria-label="Admin mobile navigation"
      >
        <button
          type="button"
          onClick={() => {
            buzz(10);
            setMainTab('restaurants');
          }}
          className={cx(
            'tap flex flex-1 flex-col items-center justify-center gap-1 py-1 text-xs font-semibold transition-all',
            mainTab === 'restaurants' ? 'text-amber-400' : 'text-zinc-400 hover:text-zinc-200',
          )}
        >
          <div className={cx('grid h-8 w-8 place-items-center rounded-full transition-colors', mainTab === 'restaurants' ? 'bg-amber-500/20' : '')}>
            <Store size={18} />
          </div>
          <span>Restaurants</span>
        </button>

        <button
          type="button"
          onClick={() => {
            buzz(10);
            setMainTab('3d');
          }}
          className={cx(
            'tap relative flex flex-1 flex-col items-center justify-center gap-1 py-1 text-xs font-semibold transition-all',
            mainTab === '3d' ? 'text-amber-400' : 'text-zinc-400 hover:text-zinc-200',
          )}
        >
          <div className={cx('relative grid h-8 w-8 place-items-center rounded-full transition-colors', mainTab === '3d' ? 'bg-amber-500/20' : '')}>
            <Box size={18} />
            {requests3d.length > 0 && (
              <span className="absolute -top-1 -right-1 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-amber-500 px-1 text-[9px] font-bold text-zinc-950">
                {requests3d.length}
              </span>
            )}
          </div>
          <span>3D Pipeline</span>
        </button>

        <button
          type="button"
          onClick={() => {
            buzz(10);
            refresh();
          }}
          className="tap flex flex-1 flex-col items-center justify-center gap-1 py-1 text-xs font-semibold text-zinc-400 hover:text-zinc-200 transition-all"
        >
          <div className="grid h-8 w-8 place-items-center rounded-full">
            <RefreshCw size={18} />
          </div>
          <span>Refresh</span>
        </button>
      </nav>
    </div>
  );
}

function ErrorLine({ text, onRetry }) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3 rounded-2xl border border-red-500/25 bg-red-500/5 px-4 py-3 text-sm text-red-200">
      <span className="flex items-center gap-2">
        <TriangleAlert size={16} aria-hidden="true" /> {text}
      </span>
      <button type="button" onClick={onRetry} className="shrink-0 font-semibold underline">
        Retry
      </button>
    </div>
  );
}

function TopList({ title, items, value, onOpen }) {
  return (
    <div className="rounded-3xl border hairline bg-onyx p-4">
      <div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">{title}</div>
      <ol className="space-y-1">
        {items.map((r, i) => (
          <li key={r.id}>
            <button type="button" onClick={() => onOpen(r)} className="flex w-full items-center gap-3 rounded-xl px-2 py-1.5 text-left hover:bg-white/[0.03]">
              <span className="w-4 font-mono text-xs text-zinc-600">{i + 1}</span>
              <span className="flex-1 truncate text-sm text-zinc-200">{r.name}</span>
              <span className="font-mono text-xs text-amber-200">{value(r)}</span>
            </button>
          </li>
        ))}
      </ol>
    </div>
  );
}
