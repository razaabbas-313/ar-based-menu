import { useCallback, useEffect, useState } from 'react';
import { BarChart3, ChefHat, FolderPlus, FolderUp, LayoutList, LogIn, Menu, Palette, PlusCircle, ShieldAlert, TriangleAlert, X } from 'lucide-react';
import Navbar from '../components/Navbar.jsx';
import BrandStudio from '../components/studio/BrandStudio.jsx';
import ModelsManager from '../components/studio/ModelsManager.jsx';
import ExperienceManager from '../components/studio/ExperienceManager.jsx';
import OwnerPlanOverview from '../components/studio/OwnerPlanOverview.jsx';
import StudioTopBar from '../components/studio/StudioTopBar.jsx';
import DishManager from '../components/studio/DishManager.jsx';
import NewDishStudio from '../components/studio/NewDishStudio.jsx';
import BatchUploadStudio from '../components/studio/BatchUploadStudio.jsx';
import InsightDashboard from '../components/studio/InsightDashboard.jsx';
import StudioAnalytics from '../components/studio/StudioAnalytics.jsx';
import CategoryManagerModal from '../components/studio/CategoryManagerModal.jsx';
import BrandCustomizer from '../components/studio/BrandCustomizer.jsx';
import SetPasswordModal from '../components/studio/SetPasswordModal.jsx';
import { Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../api/client.js';
import { buzz, cx } from '../utils/format.js';
import { navigate, useLocation } from '../utils/router.js';

const TABS = [
  {key:'models',label:'3D Models',short:'Models',icon:FolderPlus},
  { key: 'experience', label: 'Tables & WhatsApp', short: 'Guest setup', icon: Menu },
  { key: 'plan', label: 'Your plan', short: 'Plan', icon: ShieldAlert },
  { key: 'dishes', label: 'Dish Management', short: 'Dishes', icon: LayoutList },
  { key: 'batch', label: 'Batch Upload', short: 'Batch', icon: FolderUp },
  { key: 'new', label: 'New Dish Studio', short: 'New dish', icon: PlusCircle },
  { key: 'brand', label: 'Brand & Menu', short: 'Brand', icon: Palette },
  { key: 'analytics', label: 'Guest Analytics', short: 'Analytics', icon: BarChart3 },
];

/** /studio: the restaurant admin dashboard (ROLE_RESTAURANT). */
export default function StudioPage() {
  const { isAuthenticated, mustChangePassword } = useAuth();
  if (!isAuthenticated) return <SignInPrompt />;
  if (mustChangePassword) return <LockedStudio />;
  return <Studio />;
}

/** Temporary password: nothing of the studio is loaded until the owner sets their own. */
function LockedStudio() {
  return (
    <div className="min-h-screen">
      <Navbar />
      <div className="pointer-events-none mx-auto max-w-7xl select-none px-4 py-6 opacity-40 blur-sm sm:px-6" aria-hidden="true">
        <div className="flex gap-2">
          {['Dish Management', 'New Dish Studio', 'Brand & Menu'].map((t) => (
            <div key={t} className="h-12 rounded-full border hairline px-6 py-3 text-sm text-zinc-500">{t}</div>
          ))}
        </div>
        <div className="mt-6 grid gap-3 sm:grid-cols-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-40 rounded-3xl border hairline bg-onyx" />
          ))}
        </div>
      </div>
      <SetPasswordModal />
    </div>
  );
}

function Studio() {
  const { restaurant, logout, updateRestaurant } = useAuth();
  const { query } = useLocation();
  const tab = TABS.some((t) => t.key === query.get('tab')) ? query.get('tab') : 'dishes';
  const [settings, setSettings] = useState(null);
  const [policy, setPolicy] = useState(null);
  const [settingsError, setSettingsError] = useState(null);
  const [dishes, setDishes] = useState([]);
  const [dishState, setDishState] = useState({ loading: true, error: '' });
  const [categories, setCategories] = useState([]);
  const [categoryModalOpen, setCategoryModalOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const loadSettings = useCallback(() => {
    setSettingsError(null);
    Promise.all([api.settings(), api.ownerEntitlements()])
      .then(([s, effective]) => {
        const allowed=Boolean(effective.operational['model.ownerUpload']);
        setPolicy(effective);setSettings({ ...s, canUpload3d: allowed });
        updateRestaurant?.({ canUpload3d: allowed });
      })
      .catch((e) => setSettingsError(e));
  }, [updateRestaurant]);

  useEffect(() => {
    if(!policy?.nextReevaluationAt)return;
    const timer=setTimeout(loadSettings,Math.min(2147483000,Math.max(100,new Date(policy.nextReevaluationAt).getTime()-Date.now()+100)));
    return ()=>clearTimeout(timer);
  },[policy?.nextReevaluationAt,loadSettings]);

  const loadDishes = useCallback(() => {
    setDishState({ loading: true, error: '' });
    api
      .studioDishes()
      .then((list) => {
        setDishes(list || []);
        setDishState({ loading: false, error: '' });
      })
      .catch((e) => setDishState({ loading: false, error: e.message }));
  }, []);

  const loadCategories = useCallback(() => {
    api
      .categories()
      .then((cats) => setCategories(cats || []))
      .catch(() => {});
  }, []);

  useEffect(() => {
    loadSettings();
    loadDishes();
    loadCategories();
  }, [loadSettings, loadDishes, loadCategories, restaurant.id]);

  // Sync settings when studio window gains focus so Super Admin permission changes reflect in real-time
  useEffect(() => {
    const onFocus = () => loadSettings();
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [loadSettings]);

  // Keep the top-bar counters (dishes, 3D models, storage) in step with the server after edits.
  const [revision, setRevision] = useState(0);
  const updateDishes = useCallback((updater) => {
    setDishes(updater);
    setRevision((r) => r + 1);
  }, []);
  useEffect(() => {
    if (revision === 0) return undefined;
    const t = setTimeout(() => api.settings().then(setSettings).catch(() => {}), 600);
    return () => clearTimeout(t);
  }, [revision]);

  const go = (key) => {
    buzz();
    navigate(key === 'dishes' ? '/studio' : `/studio?tab=${key}`, { scroll: false });
  };

  if (settingsError?.status === 403) {
    return (
      <Blocked
        title="This studio is suspended"
        text={settingsError.message || 'Please contact the Atelier platform team to reactivate your account.'}
        onSignOut={logout}
      />
    );
  }

  const branding = settings?.branding;
  const currency = branding?.currencyCode || 'USD';
  const currencySymbol = branding?.currencySymbol || '$';

  return (
    <div className="min-h-screen pb-24">
      <StudioTopBar settings={settings} qrAllowed={Boolean(policy?.operational['qr.venue'])} />
      <div className="mx-auto max-w-7xl px-4 sm:px-6">
        <div className="py-4">
          {/* Desktop Tab Navigation */}
          <nav className="hidden sm:flex sm:items-center sm:gap-2" role="tablist" aria-label="Studio sections">
            {TABS.map(({ key, label, icon: Icon }) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={tab === key}
                onClick={() => go(key)}
                className={cx(
                  'tap flex h-12 shrink-0 items-center gap-2 rounded-full border px-5 text-sm font-semibold transition-all',
                  tab === key ? 'border-amber-500 bg-amber-500 text-zinc-950 shadow-fab' : 'hairline text-zinc-400 hover:text-zinc-100',
                )}
              >
                <Icon size={17} aria-hidden="true" />
                <span>{label}</span>
              </button>
            ))}
          </nav>

          {/* Mobile Hamburger Navigation */}
          <div className="flex sm:hidden w-full flex-col gap-2">
            <div className="flex items-center justify-between rounded-2xl border hairline bg-onyx/80 p-2 backdrop-blur-md">
              <div className="flex items-center gap-2.5 px-3 py-1">
                {(() => {
                  const currentTab = TABS.find((t) => t.key === tab) || TABS[0];
                  const Icon = currentTab.icon;
                  return (
                    <>
                      <div className="grid h-8 w-8 place-items-center rounded-xl bg-amber-500/20 text-amber-300">
                        <Icon size={16} />
                      </div>
                      <div className="text-sm font-semibold text-zinc-100">{currentTab.label}</div>
                    </>
                  );
                })()}
              </div>
              <button
                type="button"
                onClick={() => setMobileMenuOpen((o) => !o)}
                className="tap flex h-10 items-center gap-1.5 rounded-xl border hairline bg-zinc-900/60 px-3.5 text-xs font-semibold text-zinc-200"
                aria-label="Toggle navigation menu"
              >
                {mobileMenuOpen ? <X size={16} /> : <Menu size={16} />}
                <span>{mobileMenuOpen ? 'Close' : 'Menu'}</span>
              </button>
            </div>

            {mobileMenuOpen && (
              <div className="animate-in fade-in slide-in-from-top-2 duration-150 rounded-2xl border hairline bg-zinc-950/95 p-2 shadow-2xl backdrop-blur-xl">
                <div className="flex flex-col gap-1">
                  {TABS.map(({ key, label, icon: Icon }) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => {
                        go(key);
                        setMobileMenuOpen(false);
                      }}
                      className={cx(
                        'tap flex h-12 w-full items-center gap-3 rounded-xl px-4 text-sm font-semibold transition-all',
                        tab === key
                          ? 'border border-amber-500/60 bg-amber-500 text-zinc-950 shadow-fab'
                          : 'text-zinc-300 hover:bg-zinc-900 hover:text-zinc-100',
                      )}
                    >
                      <Icon size={18} />
                      <span>{label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {settingsError && settingsError.status !== 403 && (
          <div className="mb-4 flex items-center justify-between gap-3 rounded-2xl border border-red-500/25 bg-red-500/5 px-4 py-3 text-sm text-red-200">
            <span className="flex items-center gap-2">
              <TriangleAlert size={16} aria-hidden="true" /> {settingsError.message}
            </span>
            <button type="button" onClick={loadSettings} className="shrink-0 font-semibold underline">
              Retry
            </button>
          </div>
        )}

        <main>
          {tab === 'dishes' && (
            <DishManager
              dishes={dishes}
              loading={dishState.loading}
              error={dishState.error}
              currency={currency}
              currencySymbol={currencySymbol}
              currencyLabel={currencySymbol}
              categories={categories}
              onDishes={updateDishes}
              onNewDish={() => go('new')}
              onReload={loadDishes}
              conciergeAllowed={Boolean(policy?.operational['model.concierge'])}
              canUpload3d={Boolean(policy?.operational['model.ownerUpload'])}
            />
          )}
          {tab === 'batch' && (
            <BatchUploadStudio
              currencyLabel={currencySymbol}
              categories={categories}
              onDishesAdded={(newDishes) => updateDishes((list) => [...newDishes, ...list])}
              onDone={() => go('dishes')}
            />
          )}
          {tab === 'new' && (
            <NewDishStudio
              currencyLabel={currencySymbol}
              categories={categories}
              onPublished={(dish) => updateDishes((list) => [dish, ...list.filter((d) => d.id !== dish.id)])}
            />
          )}
          {tab === 'brand' && !policy?.operational['branding.enabled'] && <p className="rounded-2xl border border-amber-400/20 p-6 text-sm text-zinc-400">Brand customization is unavailable under your current policy. View Your plan for details.</p>}
          {tab === 'brand' && policy?.operational['branding.enabled'] &&
            (settings ? (
              <BrandStudio restaurant={restaurant}><BrandCustomizer
                key={settings.branding.id}
                settings={settings}
                onSettings={setSettings}
                onOpenCategoryManager={() => setCategoryModalOpen(true)}
              /></BrandStudio>
            ) : (
              <div className="grid place-items-center py-24">
                <Spinner />
              </div>
            ))}
          {tab === 'models' && <ModelsManager dishes={dishes} policy={policy} onSaved={()=>{loadDishes();loadSettings();}}/>}
          {tab === 'experience' && <ExperienceManager restaurant={restaurant} />}
          {tab === 'plan' && <OwnerPlanOverview />}
          {tab === 'analytics' && <><InsightDashboard/><details className="mt-8 rounded-2xl border hairline p-5"><summary className="cursor-pointer text-sm">Historical legacy counters · separate definitions</summary><div className="mt-5"><StudioAnalytics/></div></details></>}
        </main>
      </div>

      {categoryModalOpen && (
        <CategoryManagerModal
          categories={categories}
          onClose={() => setCategoryModalOpen(false)}
          onCategoriesUpdated={(updated) => setCategories(updated)}
        />
      )}
    </div>
  );
}

function SignInPrompt() {
  const { openAuth } = useAuth();
  return (
    <div className="min-h-screen">
      <Navbar />
      <div className="mx-auto flex max-w-xl flex-col items-center px-6 py-20 text-center">
        <div className="grid h-16 w-16 place-items-center rounded-full border border-amber-500/30 bg-onyx">
          <ChefHat size={28} className="text-amber-300" aria-hidden="true" />
        </div>
        <h1 className="mt-5 font-display text-4xl font-semibold">Restaurant Studio</h1>
        <p className="mt-2 text-sm leading-relaxed text-zinc-400">Sign in to manage your dishes, 3D models and the look of your guest menu.</p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <button type="button" onClick={() => openAuth()} className="tap shadow-fab flex h-14 items-center gap-2 rounded-full bg-gradient-to-b from-amber-400 to-amber-600 px-6 text-[15px] font-bold text-zinc-950">
            <LogIn size={18} aria-hidden="true" /> Sign in
          </button>
        </div>
      </div>
    </div>
  );
}

function Blocked({ title, text, onSignOut }) {
  return (
    <div className="min-h-screen">
      <Navbar />
      <div className="mx-auto flex max-w-xl flex-col items-center px-6 py-20 text-center">
        <ShieldAlert size={30} className="text-red-300" aria-hidden="true" />
        <h1 className="mt-4 font-display text-4xl font-semibold">{title}</h1>
        <p className="mt-2 text-sm text-zinc-400">{text}</p>
        <button type="button" onClick={onSignOut} className="tap mt-6 h-12 rounded-full border hairline px-6 text-sm text-zinc-200">
          Sign out
        </button>
      </div>
    </div>
  );
}
