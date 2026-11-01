import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ChefHat, Home, RefreshCw, SearchX, TriangleAlert, UtensilsCrossed } from 'lucide-react';
import Navbar from '../components/Navbar.jsx';
import MenuHero from '../components/guest/MenuHero.jsx';
import CategoryNav from '../components/guest/CategoryNav.jsx';
import GuestDishCard from '../components/guest/GuestDishCard.jsx';
import GuestActions, { useGuestCart } from '../components/guest/GuestActions.jsx';
import { addDish } from '../utils/guestOrders.js';
import DishSheet from '../components/guest/DishSheet.jsx';
import FilterBar from '../components/guest/FilterBar.jsx';
import ArHandoffModal from '../components/ArHandoffModal.jsx';
import { Spinner } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api, API_BASE } from '../api/client.js';
import { CATEGORY_LABEL, CATEGORY_ORDER, buzz, cx } from '../utils/format.js';
import { observe } from '../utils/insights.js';
import { appearanceStyle, brandedRestaurant } from '../utils/appearance.js';
import { accentStyle } from '../utils/theme.js';
import { linkProps, navigate, useLocation } from '../utils/router.js';

const EMPTY_FILTERS = { tags: [], chefPicks: false, arOnly: false };

/** /r/:slug: the public, mobile-first menu. Photos only on the feed; 3D loads on demand. */
export default function GuestMenuPage({ slug,previewOnly=false,studioAppearance }) {
  const { restaurant: me } = useAuth();
  const { query: params } = useLocation();
  const tableToken=params.get('table');
  const [cart,setCart,notes,setNotes]=useGuestCart(slug,tableToken);
  const [context,setContext]=useState(null);
  const [cartNotice,setCartNotice]=useState('');
  const isOwner = me?.slug === slug;
  const isPreview = previewOnly || params.get('preview') === 'true';
  const [appearance,setAppearance]=useState(null),[reviewUrl,setReviewUrl]=useState(null);
  useEffect(()=>{let alive=true;setAppearance(null);setReviewUrl(null);if(studioAppearance)return;api.publicAppearance(slug).then(data=>{if(alive){setAppearance(data.appearance);setReviewUrl(data.review?.url||null);}}).catch(()=>{});return()=>{alive=false;};},[slug,studioAppearance]);
  const effectiveAppearance=studioAppearance||appearance;
  const [state, setState] = useState({ status: 'loading', restaurant: null, categories: [], items: [], error: '' });
  const [query, setQuery] = useState('');
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [open, setOpen] = useState(null);
  const [handoff, setHandoff] = useState(null);
  const [activeCat, setActiveCat] = useState(null);
  const deepLinkDone = useRef(false);
  const sectionRefs = useRef({});
  const visitRecorded=useRef('');
  const observation=(type,dishId=null,category=null)=>{if(!isOwner&&!isPreview)observe(slug,type,{dishId,tableToken,category});};
  useEffect(()=>{if(!isOwner&&!isPreview&&state.status==='ready'&&context&&!context.contextError){const key=slug+'|'+(tableToken||'venue');if(visitRecorded.current!==key){visitRecorded.current=key;observation('MENU_VISIT');}}},[slug,tableToken,state.status,context,isOwner,isPreview]);
  useEffect(()=>{if(open?.id)observation('DISH_DETAIL_VIEW',open.id);},[open?.id]);

  const load = useCallback(() => {
    const fetchMenu = (isPreview && isOwner)
      ? api.studioMenuPreview()
      : api.publicMenu(slug, { fresh: isOwner });

    fetchMenu
      .then((menu) => {
        setState({
          status: 'ready',
          restaurant: { ...menu.restaurant, isMenuLive: (menu.menuLive ?? menu.isMenuLive ?? true) !== false },
          categories: menu.categories || [],
          items: menu.items || [],
          menuMode: menu.menuMode,
          allowedModes: menu.allowedModes || ['SIMPLE','THREE_D','AR'],
          error: '',
        });
        setOpen(current => current ? (menu.items || []).find(item => item.id === current.id) || null : null);
        if (!isOwner && !isPreview) {
          api.trackEvent({ slug, eventType: 'MENU_VIEW' }).catch(() => {});
        }
      })
      .catch((e) =>
        setState({ status: e.status === 404 ? 'not-found' : 'error', restaurant: null, categories: [], items: [], error: e.message })
      );
  }, [slug, isOwner, isPreview]);

  useEffect(() => {
    setState({ status: 'loading', restaurant: null, categories: [], items: [], error: '' });
    deepLinkDone.current = false;
    load();
  }, [load]);

  useEffect(()=>{if(isPreview||state.status!=='ready')return;let live=true;setContext(null);api.guestContext(slug,tableToken).then(value=>{if(live)setContext(value);}).catch(()=>{if(live)setContext({contextError:'Table and request availability could not be checked. Refresh the menu to try again.',orderingAvailable:false,waiterAvailable:false});});return()=>{live=false;};},[slug,tableToken,state.status,state.items,isPreview]);
  useEffect(()=>{if(state.menuMode==='SIMPLE')setFilters(f=>({...f,arOnly:false}));},[state.menuMode]);

  // Deep link from the AR hand-off QR code: /r/:slug?dish=<id>
  useEffect(() => {
    if (deepLinkDone.current || state.status !== 'ready') return;
    deepLinkDone.current = true;
    const id = params.get('dish');
    const found = id && state.items.find((i) => String(i.id) === id);
    if (found) setOpen(found);
  }, [state, params]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    return state.items.filter((i) => {
      if (q && !`${i.dishTitle} ${i.description || ''}`.toLowerCase().includes(q)) return false;
      if (filters.chefPicks && !(i.dailySpecial || i.dishCategory === 'CHEF_SPECIALS')) return false;
      if (filters.arOnly && !i.is3d) return false;
      return true;
    });
  }, [state.items, query, filters]);

  const sections = useMemo(() => {
    const customCats = state.categories;
    const assignedDishIds = new Set();
    const result = [];

    if (customCats && customCats.length > 0) {
      // 1. Deduplicate custom categories by clean name (case-insensitive)
      const uniqueCats = [];
      const seenNames = new Set();
      for (const cat of customCats) {
        const norm = (cat.name || '').trim().toLowerCase();
        if (norm && !seenNames.has(norm)) {
          seenNames.add(norm);
          uniqueCats.push(cat);
        }
      }

      // 2. Assign each dish to its single primary category
      for (const cat of uniqueCats) {
        const normName = cat.name.trim().toLowerCase();
        const catDishes = visible.filter((i) => {
          if (assignedDishIds.has(i.id)) return false;
          // Priority 1: explicit categoryId match
          if (i.categoryId != null && String(i.categoryId) === String(cat.id)) return true;
          // Priority 2: categoryName matches
          if (i.categoryName && i.categoryName.trim().toLowerCase() === normName) return true;
          // Priority 3: legacy dishCategory enum matches category name
          if (i.dishCategory) {
            const dcNorm = i.dishCategory.trim().toLowerCase();
            const dcLabel = (CATEGORY_LABEL[i.dishCategory] || '').trim().toLowerCase();
            if ((dcNorm === normName || dcLabel === normName) && !i.categoryId) return true;
          }
          return false;
        });

        if (catDishes.length > 0) {
          catDishes.forEach((d) => assignedDishIds.add(d.id));
          result.push({
            key: String(cat.id),
            label: cat.name,
            items: catDishes,
          });
        }
      }

      // 3. Catch-all for any unassigned dishes so none are lost
      const unassigned = visible.filter((i) => !assignedDishIds.has(i.id));
      if (unassigned.length > 0) {
        const fallbackGroups = {};
        for (const item of unassigned) {
          const groupName = item.categoryName || (item.dishCategory && (CATEGORY_LABEL[item.dishCategory] || item.dishCategory)) || 'Other';
          if (!fallbackGroups[groupName]) fallbackGroups[groupName] = [];
          fallbackGroups[groupName].push(item);
        }
        for (const [groupName, items] of Object.entries(fallbackGroups)) {
          result.push({
            key: `extra-${groupName.toLowerCase().replace(/\s+/g, '-')}`,
            label: groupName,
            items,
          });
        }
      }

      return result;
    }

    // Default enum categories fallback when no custom categories defined
    for (const key of CATEGORY_ORDER) {
      const label = CATEGORY_LABEL[key] || key;
      const catDishes = visible.filter((i) => {
        if (assignedDishIds.has(i.id)) return false;
        const dCat = i.categoryName || i.dishCategory;
        return dCat === key || dCat === label;
      });
      if (catDishes.length > 0) {
        catDishes.forEach((d) => assignedDishIds.add(d.id));
        result.push({
          key,
          label,
          items: catDishes,
        });
      }
    }
    const unassigned = visible.filter((i) => !assignedDishIds.has(i.id));
    if (unassigned.length > 0) {
      result.push({
        key: 'other',
        label: 'Other',
        items: unassigned,
      });
    }
    return result;
  }, [visible, state.categories]);

  // Scroll spy: the section nearest the top (below the sticky nav) is active.
  useEffect(() => {
    if (!sections.length) return undefined;
    setActiveCat((c) => (sections.some((s) => s.key === c) ? c : sections[0].key));
    const observer = new IntersectionObserver(
      (entries) => {
        const visibleEntries = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visibleEntries[0]) setActiveCat(visibleEntries[0].target.dataset.cat);
      },
      { rootMargin: '-140px 0px -55% 0px', threshold: 0 },
    );
    sections.forEach((s) => sectionRefs.current[s.key] && observer.observe(sectionRefs.current[s.key]));
    return () => observer.disconnect();
  }, [sections]);

  const jumpTo = (key) => {
    observation('CATEGORY_SELECTED',null,/^\d+$/.test(key)||['STARTERS','MAINS','CHEF_SPECIALS','DESSERTS','DRINKS'].includes(key)?key:'OTHER');
    setActiveCat(key);
    sectionRefs.current[key]?.scrollIntoView({ behavior: effectiveAppearance?.motion==='reduced'||window.matchMedia?.('(prefers-reduced-motion: reduce)').matches?'auto':'smooth', block: 'start' });
  };

  const closeSheet = () => {
    setOpen(null);
    if (params.get('dish')){const query=new URLSearchParams(params);query.delete('dish');navigate(`/r/${slug}${query.size?'?'+query.toString():''}`,{replace:true,scroll:false});}
  };

  if (state.status === 'loading') {
    return (
      <div className="grid min-h-screen place-items-center">
        <Spinner />
      </div>
    );
  }

  // Luxury Holding Screen when Menu is set to Private
  if (state.restaurant && state.restaurant.isMenuLive === false && !isOwner && !isPreview) {
    const r = brandedRestaurant(state.restaurant,effectiveAppearance,API_BASE);
    return (
      <div className="min-h-screen bg-[#0a0a0c] text-zinc-100 flex flex-col justify-between" style={accentStyle(r.themeAccent)}>
        {!previewOnly&&<Navbar />}
        <div className="mx-auto flex max-w-xl flex-col items-center px-6 py-20 text-center">
          <div className="relative mb-6 h-24 w-24 overflow-hidden rounded-3xl border border-accent-500/30 bg-onyx shadow-2xl">
            {r.logoUrl ? (
              <img src={r.logoUrl} alt={r.name} className="h-full w-full object-cover" />
            ) : (
              <span className="grid h-full w-full place-items-center font-display text-4xl italic text-accent-300">
                {r.name.charAt(0)}
              </span>
            )}
          </div>
          <span className="rounded-full border border-amber-500/30 bg-amber-500/10 px-3.5 py-1 text-xs font-semibold text-amber-300">
            Seasonal Curation in Progress
          </span>
          <h1 className="mt-4 font-display text-4xl font-semibold tracking-tight sm:text-5xl">{r.name}</h1>
          {r.tagline && <p className="mt-2 font-display text-lg italic text-zinc-400">{r.tagline}</p>}
          <div className="mt-8 rounded-3xl border hairline bg-black/40 p-6 text-center shadow-xl">
            <p className="text-base leading-relaxed text-zinc-300">
              “Our culinary team is currently curating our new seasonal menu. Please ask your server or check back shortly.”
            </p>
            {r.openingHours && (
              <div className="mt-5 border-t hairline pt-4 text-xs text-zinc-400">
                <span className="font-semibold text-zinc-300">Service Hours: </span>
                {r.openingHours}
              </div>
            )}
            {r.phoneNumber && (
              <div className="mt-2 text-xs text-zinc-400">
                <span className="font-semibold text-zinc-300">Inquiries: </span>
                <a href={`tel:${r.phoneNumber}`} className="text-accent-300 hover:underline">
                  {r.phoneNumber}
                </a>
              </div>
            )}
          </div>
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            {r.websiteUrl && (
              <a
                href={r.websiteUrl}
                target="_blank"
                rel="noreferrer"
                className="tap flex h-11 items-center gap-2 rounded-full border hairline bg-white/5 px-5 text-xs font-semibold text-zinc-200 hover:bg-white/10"
              >
                Official Website
              </a>
            )}
            {r.instagramHandle && (
              <a
                href={`https://instagram.com/${r.instagramHandle.replace('@', '')}`}
                target="_blank"
                rel="noreferrer"
                className="tap flex h-11 items-center gap-2 rounded-full border hairline bg-white/5 px-5 text-xs font-semibold text-zinc-200 hover:bg-white/10"
              >
                Instagram
              </a>
            )}
          </div>
        </div>
        <footer className="border-t border-white/5 py-6 text-center font-mono text-[10px] uppercase tracking-widest text-zinc-600">
          Atelier 3D Culinary Experience
        </footer>
      </div>
    );
  }

  if (state.status === 'not-found' || state.status === 'error') {
    return (
      <div className="min-h-screen">
        <Navbar />
        <div className="mx-auto grid max-w-xl place-items-center px-6 py-24 text-center">
          {state.status === 'error' ? (
            <TriangleAlert size={30} className="text-red-300" aria-hidden="true" />
          ) : (
            <UtensilsCrossed size={30} className="text-zinc-600" aria-hidden="true" />
          )}
          <h1 className="mt-4 font-display text-4xl font-semibold">
            {state.status === 'error' ? 'The menu could not load' : `No menu at /r/${slug}`}
          </h1>
          <p className="mt-2 text-zinc-400">{state.status === 'error' ? state.error : 'This restaurant may be unavailable right now.'}</p>
          <div className="mt-6 flex gap-2">
            {state.status === 'error' && (
              <button type="button" onClick={load} className="tap flex h-12 items-center gap-2 rounded-full border hairline px-5 text-sm text-zinc-200">
                <RefreshCw size={16} aria-hidden="true" /> Try again
              </button>
            )}
            <a {...linkProps('/')} className="tap flex h-12 items-center gap-2 rounded-full border hairline px-5 text-sm text-zinc-200">
              <Home size={16} aria-hidden="true" /> Atelier home
            </a>
          </div>
        </div>
      </div>
    );
  }

  const r = brandedRestaurant(state.restaurant,effectiveAppearance,API_BASE);
  const compact = r.menuLayout === 'compact';

  return (
    <div className="atelier-menu min-h-screen bg-[#0a0a0c] pb-32" data-theme={effectiveAppearance?.preset} data-card={effectiveAppearance?.card} data-category={effectiveAppearance?.category} data-motion={effectiveAppearance?.motion} style={{...accentStyle(r.themeAccent),...(effectiveAppearance?appearanceStyle(effectiveAppearance):{})}}>
      <MenuHero reducedMotion={previewOnly||effectiveAppearance?.motion==='reduced'} menuMode={state.menuMode} restaurant={r} dishCount={state.items.length} arCount={state.items.filter((i) => i.is3d).length} />

      {!isPreview&&<GuestActions slug={slug} tableToken={tableToken} context={context} onObservation={isOwner||isPreview?undefined:observation} cart={cart} setCart={setCart} notes={notes} setNotes={setNotes} dishes={state.items} onRefresh={()=>{setContext(null);load();}} />}
      {cartNotice&&<p role="status" className="mx-auto max-w-3xl px-6 text-sm text-amber-200">{cartNotice}</p>}
      <div className="mt-8">
        <CategoryNav
          reducedMotion={effectiveAppearance?.motion==='reduced'}
          restaurantName={r.name}
          categories={sections.map((s) => ({ key: s.key, label: s.label, count: s.items.length }))}
          active={activeCat}
          onSelect={jumpTo}
        />
      </div>

      <main className="mx-auto max-w-3xl px-4 pt-6 sm:px-6">
        {isOwner && !isPreview && (
          <a
            {...linkProps('/studio')}
            className="tap mb-6 flex items-center justify-between gap-3 rounded-2xl border border-accent-500/30 bg-accent-500/10 px-4 py-3 text-sm text-accent-300"
          >
            <span className="flex items-center gap-2">
              <ChefHat size={17} aria-hidden="true" /> This is your guest menu.
            </span>
            <span className="font-semibold">Open studio →</span>
          </a>
        )}

        {state.items.length === 0 ? (
          <div className="relative overflow-hidden rounded-[32px] border hairline bg-onyx px-6 py-16 text-center">
            <div className="stage-bg pointer-events-none absolute inset-0 opacity-70" />
            <div className="relative">
              <UtensilsCrossed size={30} className="mx-auto text-accent-300" aria-hidden="true" />
              <h2 className="mt-4 font-display text-4xl font-semibold">
                The menu is <span className="italic text-accent-300">being plated.</span>
              </h2>
              <p className="mx-auto mt-3 max-w-md text-[15px] text-zinc-400">New dishes appear here the moment the chef publishes them.</p>
            </div>
          </div>
        ) : sections.length === 0 ? (
          <div className="grid place-items-center rounded-[28px] border border-dashed hairline px-6 py-16 text-center">
            <SearchX size={26} className="text-zinc-600" aria-hidden="true" />
            <div className="mt-3 font-display text-2xl text-zinc-300">No dishes match</div>
            <button
              type="button"
              onClick={() => {
                setQuery('');
                setFilters(EMPTY_FILTERS);
              }}
              className="tap mt-4 h-11 rounded-full border hairline px-5 text-sm text-zinc-200"
            >
              Clear search &amp; filters
            </button>
          </div>
        ) : (
          sections.map((section) => (
            <section
              key={section.key}
              ref={(el) => {
                sectionRefs.current[section.key] = el;
              }}
              data-cat={section.key}
              className="menu-section mb-10"
              aria-labelledby={`cat-${section.key}`}
            >
              <h2 id={`cat-${section.key}`} className="mb-5 flex items-baseline gap-3">
                <span className="font-display text-[34px] font-semibold tracking-[-0.01em] text-[#f6efdf]">{section.label}</span>
                <span className="font-mono text-[11px] text-zinc-600">{String(section.items.length).padStart(2, '0')}</span>
                <span className="h-px flex-1 bg-gradient-to-r from-accent-500/35 via-white/10 to-transparent" />
              </h2>
              <div className={cx(compact ? 'flex flex-col gap-3' : 'grid grid-cols-1 gap-4 sm:grid-cols-2')}>
                {section.items.map((item, i) => (
                  <GuestDishCard
                    key={item.id}
                    item={item}
                    index={i}
                    layout={compact ? 'compact' : 'cards'}
                    currency={r.currencyCode}
                    currencySymbol={r.currencySymbol}
                    onOpen={setOpen}
                  />
                ))}
              </div>
            </section>
          ))
        )}

        <footer className="mt-14 border-t border-white/5 pb-6 pt-6 text-center">
          {reviewUrl&&!isPreview&&<a href={reviewUrl} onClick={()=>observation('REVIEW_LINK_OPENED')} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" className="mb-6 inline-flex min-h-12 items-center rounded-full border border-accent-400/30 px-6 text-sm font-semibold text-accent-300">Review us on Google ↗</a>}
          <div className="font-display text-lg italic text-zinc-500">{r.name}</div>
          <a {...linkProps('/')} className="mt-1 inline-block font-mono text-[10px] uppercase tracking-[0.28em] text-zinc-600 hover:text-zinc-400">
            Menu by Atelier
          </a>
        </footer>
      </main>

      {state.items.length > 0 && (
        <FilterBar menuMode={state.menuMode} query={query} onQuery={setQuery} filters={filters} onFilters={setFilters} resultCount={visible.length} />
      )}

      {open && (
        <DishSheet
          item={open}
          appearance={effectiveAppearance}
          onObservation={isOwner||isPreview?undefined:observation}
          reducedMotion={effectiveAppearance?.motion==='reduced'}
          arAllowed={state.menuMode ? state.menuMode==='AR' : (state.allowedModes || ['SIMPLE','THREE_D','AR']).includes('AR')}
          onAddToCart={!isPreview&&context?.orderingAvailable ? item=>{try{setCart(addDish(cart,item));observation('CART_ADD',item.id);setCartNotice(`${item.dishTitle} added. Open Your request above the menu to review.`);closeSheet();}catch(e){setCartNotice(e.message);}} : undefined}
          slug={isPreview?undefined:slug}
          currency={r.currencyCode}
          currencySymbol={r.currencySymbol}
          onClose={closeSheet}
          onArFallback={setHandoff}
        />
      )}
      {handoff && (state.menuMode ? state.menuMode==='AR' : (state.allowedModes || ['SIMPLE','THREE_D','AR']).includes('AR')) && <ArHandoffModal item={handoff} slug={slug} tableToken={tableToken} onClose={() => setHandoff(null)} />}
    </div>
  );
}
