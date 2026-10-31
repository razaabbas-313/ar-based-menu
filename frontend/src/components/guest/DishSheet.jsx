import { useCallback, useEffect, useRef, useState } from 'react';
import { Box, Crown, Hand, Image as ImageIcon, LoaderCircle, Smartphone, Sun, SunDim, X } from 'lucide-react';
import ModelStage from '../ModelStage.jsx';
import { IconButton, Overlay } from '../ui.jsx';
import { isChefSpecial } from './GuestDishCard.jsx';
import { CATEGORY_LABEL, buzz, cx, fmtPrice } from '../../utils/format.js';
import { preloadModelViewer } from '../../utils/modelViewer.js';
import { api } from '../../api/client.js';

/**
 * Dish inspector. Opens on the cover photo, title, description and price, and IMMEDIATELY starts
 * loading the dish's .glb in the background: the <model-viewer> is mounted right away but kept
 * invisible underneath the photo. "View in 3D" only reveals the already-parsed model, so the 3D
 * view appears instantly, without a download spinner.
 */
export default function DishSheet({ item, slug, currency, currencySymbol, onClose, onArFallback, arAllowed = true, onAddToCart }) {
  const [view, setView] = useState('photo'); // photo | 3d
  const [lighting, setLighting] = useState('studio');
  const [progress, setProgress] = useState(0);
  const [viewerReady, setViewerReady] = useState(false);
  const [arIntent, setArIntent] = useState(false);
  const [hint, setHint] = useState(true);
  const viewer = useRef(null);
  const arIntentRef = useRef(false);
  const has3d = !!(item.is3d && item.modelUrl);

  useEffect(() => {
    if(!has3d)setView('photo');
    if(!arAllowed){arIntentRef.current=false;setArIntent(false);}
  },[has3d,arAllowed]);

  useEffect(() => {
    if (has3d) preloadModelViewer();
  }, [has3d]);

  // Gesture hint: shown when the 3D view opens, hidden after the first drag or a few seconds.
  useEffect(() => {
    if (view !== '3d') return undefined;
    setHint(true);
    const t = setTimeout(() => setHint(false), 4500);
    return () => clearTimeout(t);
  }, [view]);

  const tryAr = useCallback(
    (el) => {
      if(!arAllowed)return false;
      const mv = el || viewer.current;
      if (mv && mv.canActivateAR) {
        mv.activateAR();
        return true;
      }
      onArFallback(item);
      return false;
    },
    [item, onArFallback, arAllowed],
  );

  const onReady = useCallback(
    (el) => {
      setViewerReady(true);
      if (arIntentRef.current) {
        arIntentRef.current = false;
        setArIntent(false);
        tryAr(el);
      }
    },
    [tryAr],
  );
  const onProgress = useCallback((p) => setProgress(p), []);
  const onInteract = useCallback(() => setHint(false), []);

  const show3d = () => {
    buzz();
    setView('3d');
    if (slug) {
      api.trackEvent({ slug, dishId: item.id, eventType: 'MODEL_3D_VIEW' }).catch(() => {});
    }
  };

  const requestAr = () => {
    if(!arAllowed)return;
    buzz(14);
    setView('3d');
    if (slug) {
      api.trackEvent({ slug, dishId: item.id, eventType: 'AR_LAUNCH' }).catch(() => {});
    }
    if (viewerReady) {
      tryAr();
    } else {
      arIntentRef.current = true;
      setArIntent(true);
    }
  };

  const price = fmtPrice(item.priceTag, currency, currencySymbol);
  const special = isChefSpecial(item);
  const percent = Math.round(progress * 100);

  return (
    <Overlay onClose={onClose} align="bottom" label={item.dishTitle}>
      <div
        className="sheet pb-safe relative z-10 flex max-h-[95vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-[30px] border border-white/10 bg-[#0e0e10] sm:rounded-[30px]"
        data-testid="dish-inspector"
      >
        <div className="mx-auto mt-2 h-1 w-10 shrink-0 rounded-full bg-zinc-700 sm:hidden" />
        <div className="flex-1 overflow-y-auto">
          {/* Media: photo layer on top, preloaded 3D layer underneath (same box, cross-faded). */}
          <div className="relative mx-3 mt-3 aspect-[4/3] overflow-hidden rounded-[24px] bg-black sm:aspect-[16/11]">
            {has3d && (
              <div
                className={cx(
                  'absolute inset-0 transition-opacity duration-500',
                  lighting === 'warm' ? 'stage-warm' : 'stage-bg',
                  view === '3d' ? 'opacity-100' : 'pointer-events-none opacity-0',
                )}
                aria-hidden={view !== '3d'}
                data-testid="preloaded-3d"
                data-ready={viewerReady ? 'true' : 'false'}
              >
                <ModelStage
                  src={item.modelUrl}
                  alt={item.dishTitle}
                  arPlacement={item.arPlacement}
                  arEnabled={arAllowed}
                  variant="full"
                  lighting={lighting}
                  autoRotate={view === '3d'}
                  viewerRef={viewer}
                  onReady={onReady}
                  onProgress={onProgress}
                  onInteract={onInteract}
                />
              </div>
            )}
            <div
              className={cx('absolute inset-0 transition-opacity duration-500', view === 'photo' ? 'opacity-100' : 'pointer-events-none opacity-0')}
              aria-hidden={view !== 'photo'}
            >
              {item.imageUrl ? (
                <img src={item.imageUrl} alt={item.dishTitle} className="absolute inset-0 h-full w-full object-cover" />
              ) : (
                <div className="ambient absolute inset-0 grid place-items-center">
                  <span className="font-display text-7xl italic text-accent-500/40">{item.dishTitle.charAt(0)}</span>
                </div>
              )}
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent" />
            </div>

            {view === '3d' && hint && viewerReady && (
              <div className="pointer-events-none absolute inset-x-0 top-3 flex justify-center fade">
                <span className="flex items-center gap-2 rounded-full border border-white/15 bg-black/55 px-3.5 py-1.5 text-[11px] font-medium text-zinc-200 backdrop-blur-md">
                  <Hand size={13} aria-hidden="true" /> Drag to rotate · Pinch to zoom
                </span>
              </div>
            )}
            {view === '3d' && arIntent && (
              <div className="pointer-events-none absolute inset-x-0 bottom-3 flex justify-center">
                <span className="flex items-center gap-2 rounded-full border border-white/15 bg-black/60 px-4 py-2 text-xs text-zinc-200 backdrop-blur-md">
                  <LoaderCircle size={14} className="animate-spin" aria-hidden="true" /> Preparing AR…
                </span>
              </div>
            )}
            <div className="absolute right-3 top-3">
              <IconButton icon={X} label="Close" onClick={onClose} />
            </div>
          </div>

          {has3d && (
            <div className="mx-3 mt-3 flex items-center gap-2">
              <div className="grid flex-1 grid-cols-2 gap-1 rounded-2xl border border-white/10 bg-white/[0.03] p-1" role="tablist" aria-label="View">
                {[
                  ['photo', 'Photo', ImageIcon],
                  ['3d', 'View in 3D', Box],
                ].map(([key, label, Icon]) => (
                  <button
                    key={key}
                    type="button"
                    role="tab"
                    aria-selected={view === key}
                    onClick={() => {
                      buzz();
                      setView(key);
                    }}
                    className={cx(
                      'tap flex min-h-11 items-center justify-center gap-2 rounded-xl text-[13px] font-semibold transition-colors',
                      view === key ? 'bg-accent-500 text-zinc-950' : 'text-zinc-400 hover:text-zinc-200',
                    )}
                  >
                    <Icon size={16} aria-hidden="true" />
                    {label}
                  </button>
                ))}
              </div>
              {view === '3d' && (
                <div className="flex gap-1 rounded-2xl border border-white/10 bg-white/[0.03] p-1" role="group" aria-label="Lighting">
                  {[
                    ['studio', 'Studio', Sun],
                    ['warm', 'Warm dining', SunDim],
                  ].map(([key, label, Icon]) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => {
                        buzz();
                        setLighting(key);
                      }}
                      aria-pressed={lighting === key}
                      title={`${label} lighting`}
                      className={cx(
                        'tap flex min-h-11 items-center gap-1.5 rounded-xl px-3 text-[12px] font-semibold',
                        lighting === key ? 'bg-white/10 text-[#f6efdf]' : 'text-zinc-500 hover:text-zinc-200',
                      )}
                      data-testid={`lighting-${key}`}
                    >
                      <Icon size={15} aria-hidden="true" />
                      <span className="hidden sm:inline">{label}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="px-5 pb-5 pt-4">
            <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.3em] text-accent-400/90">
              {item.categoryName || CATEGORY_LABEL[item.dishCategory] || item.dishCategory}
              {special && (
                <span className="flex items-center gap-1 text-[#f6efdf]/80">
                  · <Crown size={11} aria-hidden="true" /> Chef&apos;s Special
                </span>
              )}
            </div>
            <div className="mt-1.5 flex items-start justify-between gap-4">
              <h2 className="font-display text-[34px] font-semibold leading-[1.02] text-[#f6efdf] sm:text-[40px]">{item.dishTitle}</h2>
              <span className="shrink-0 pt-2 font-sans text-xl font-semibold tabular-nums tracking-tight text-accent-300">{price}</span>
            </div>
            {item.description && (
              <div className="mt-4">
                <div className="mb-1 text-[10px] font-semibold uppercase tracking-[0.26em] text-zinc-500">Ingredients &amp; allergens</div>
                <p className="whitespace-pre-line text-[15px] leading-relaxed text-zinc-300">{item.description}</p>
              </div>
            )}
          </div>
        </div>

        {onAddToCart && item.priceTag != null && <div className="shrink-0 border-t border-white/10 p-4"><button type="button" className="min-h-12 w-full rounded-2xl bg-amber-300 px-5 font-semibold text-zinc-950" onClick={()=>onAddToCart(item)}>Add to request</button></div>}
        {has3d && (
          <div className="grid shrink-0 grid-cols-1 gap-2 border-t border-white/10 bg-black/40 px-4 py-4 sm:grid-cols-2">
            {view === 'photo' && (
              <button
                type="button"
                onClick={show3d}
                className="tap flex h-14 items-center justify-center gap-2 rounded-2xl border border-accent-500/40 text-[15px] font-semibold text-accent-300 hover:bg-accent-500/10"
                data-testid="launch-3d"
              >
                <Box size={19} aria-hidden="true" /> Launch 3D Model
                <span className="ml-1 rounded-full bg-white/[0.06] px-2 py-0.5 font-mono text-[10px] text-zinc-400" data-testid="preload-status">
                  {viewerReady ? 'Ready' : `${percent}%`}
                </span>
              </button>
            )}
            {arAllowed && <button
              type="button"
              onClick={requestAr}
              disabled={!arAllowed}
              data-testid="launch-ar"
              title={arAllowed ? 'View in AR' : 'AR is not available under this menu policy'}
              className={cx(
                'tap pulse-ring flex h-14 items-center justify-center gap-2 rounded-2xl bg-gradient-to-b from-accent-400 to-accent-600 text-[15px] font-bold text-zinc-950',
                view === '3d' && 'sm:col-span-2',
              )}
            >
              <Smartphone size={19} aria-hidden="true" /> View on Your Table (AR)
            </button>}
          </div>
        )}
      </div>
    </Overlay>
  );
}
