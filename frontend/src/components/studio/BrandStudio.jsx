import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import { PRESETS, appearanceStyle } from '../../utils/appearance.js';
import { downsampleImage, LOGO_OPTIONS, BANNER_OPTIONS } from '../../utils/media.js';
import { uploadBrandingImage } from '../../utils/upload.js';
import BrandPreview from './BrandPreview.jsx';
const field = 'min-h-11 w-full rounded-xl border border-white/15 bg-zinc-900 px-3 py-2 text-sm';
const button = 'min-h-11 rounded-xl border border-white/20 px-4 py-2 text-sm font-semibold disabled:opacity-40';
export default function BrandStudio({
  restaurant,
  children
}) {
  const [state, setState] = useState(null),
    [draft, setDraft] = useState(null),
    [size, setSize] = useState('mobile'),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [notice, setNotice] = useState('');
  async function load() {
    setError('');
    try {
      const data = await api.appearance();
      setState(data);
      setDraft(data.draft);
    } catch (e) {
      setError(e.message);
    }
  }
  useEffect(() => {
    load();
  }, [restaurant.slug]);
  async function write(action, message = 'Draft saved. Guests still see the published appearance.') {
    setError('');
    setNotice('');
    setBusy(true);
    try {
      const response = await action();
      setState(response);
      setDraft(response.draft);
      setNotice(message);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  if (!state || !draft) return <div className="rounded-3xl border hairline p-8" aria-live="polite">{error ? <><p role="alert">{error}</p><button className={button} onClick={load}>Retry Brand Studio</button></> : <p>Preparing your design studio…</p>}</div>;
  async function image(kind, file) {
    if (!file) return;
    setBusy(true);
    setError('');
    try {
      const optimized = await downsampleImage(file, kind === 'logo' ? LOGO_OPTIONS : BANNER_OPTIONS);
      const asset = await uploadBrandingImage(kind, optimized, () => {}, {
        draft: true
      });
      setState(s => ({
        ...s,
        assets: [asset, ...s.assets]
      }));
      setDraft(d => ({
        ...d,
        [kind + 'Asset']: asset.id
      }));
      setNotice('Image ready in this draft only. Save the draft, then Publish to show it to guests.');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const preview = {
    ...draft
  };
  for (const kind of ['logo', 'banner']) {
    const asset = state.assets?.find(a => a.id === draft[kind + 'Asset']);
    if (asset) preview[kind + 'Url'] = asset.url;
  }
  const premium = state.entitlements?.operational?.['branding.premiumOptions'];
  const changed = JSON.stringify(draft) !== JSON.stringify(state.draft);
  return <section className="space-y-6"><header><p className="text-xs uppercase tracking-[.3em] text-amber-300">Brand Studio</p><h2 className="mt-2 font-display text-4xl">Set the mood. Make it yours.</h2><p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">A curated canvas for your restaurant. Preview the same menu guests see, save a private draft, then publish when it feels right.</p><p className="mt-3 text-xs text-zinc-500">Revision {state.version} · {state.publishedAt ? 'Published ' + new Date(state.publishedAt).toLocaleString() : 'Existing restaurant theme'} · {changed ? 'Unsaved local changes' : JSON.stringify(state.draft) !== JSON.stringify(state.published) ? 'Saved draft differs from published' : 'Draft matches published'}</p></header>
 {error && <p role="alert" className="rounded-xl border border-red-400/30 p-4 text-sm text-red-200">{error} <button className="underline" onClick={load}>Reload current revision</button></p>}{notice && <p role="status" className="text-sm text-emerald-200">{notice}</p>}
 <div className="grid items-start gap-6 xl:grid-cols-[320px_minmax(0,1fr)]"><div className="space-y-5 rounded-3xl border hairline p-5"><div><h3 className="font-display text-2xl">Design collection</h3><div className="mt-3 grid grid-cols-2 gap-2">{Object.entries(PRESETS).map(([key, p]) => <button key={key} disabled={busy || !premium && key !== 'LEGACY'} className={button + ' text-left'} aria-pressed={draft.preset === key} onClick={() => setDraft(key === 'LEGACY' ? {
              ...state.legacy
            } : {
              ...p
            })}><span className="mb-2 block h-8 rounded-lg border border-white/10" style={{
                background: p.background,
                color: p.text
              }}><span className="px-2 font-serif">Aa <span style={{
                    color: p.accent
                  }}>●</span></span></span>{key[0] + key.slice(1).toLowerCase()}</button>)}</div></div>
 {!premium && <p className="text-xs leading-5 text-amber-200">Premium design controls are unavailable under your current policy. Your existing restaurant theme remains safe.</p>}
 <div className="space-y-3"><h4 className="text-sm font-semibold">Logo &amp; hero image</h4>{['logo', 'banner'].map(kind => <label key={kind} className="block text-xs capitalize text-zinc-400">{kind}<input type="file" accept="image/jpeg,image/png,image/webp,image/avif" disabled={busy} className="mt-2 block w-full text-xs" onChange={e => {
              image(kind, e.target.files?.[0]);
              e.target.value = '';
            }} />{draft[kind + 'Asset'] && <button type="button" className="mt-2 min-h-11 text-xs underline" onClick={() => setDraft(d => {
              const next = {
                ...d
              };
              delete next[kind + 'Asset'];
              return next;
            })}>Use existing restaurant {kind}</button>}</label>)}<p className="text-xs text-zinc-500">Uploads go directly to storage. Verified draft images are retained safely and shown on the menu only after publication. Up to 100 retained studio assets.</p></div>
 <fieldset disabled={busy || !premium} className="space-y-4 disabled:opacity-50"><legend className="sr-only">Appearance options</legend><div className="grid grid-cols-2 gap-3">{['accent', 'background', 'surface', 'text'].map(key => <label key={key} className="text-xs capitalize text-zinc-400">{key}<input type="color" className="mt-2 h-11 w-full rounded-xl border hairline bg-transparent" value={draft[key]} onChange={e => setDraft(d => ({
                ...d,
                [key]: e.target.value
              }))} /></label>)}</div>{Object.entries({
            heading: ['SERIF', 'SANS'],
            layout: ['cards', 'compact'],
            card: ['soft', 'outlined'],
            category: ['pills', 'underline'],
            button: ['rounded', 'square'],
            motion: ['reduced', 'subtle', 'standard']
          }).map(([key, values]) => <label key={key} className="block text-xs capitalize text-zinc-400">{({heading:'Typography',layout:'Menu layout',card:'Dish cards',category:'Category navigation',button:'Buttons',motion:'Motion'})[key]}<select className={field + ' mt-1'} value={draft[key]} onChange={e => setDraft(d => ({
              ...d,
              [key]: e.target.value
            }))}>{values.map(v => <option key={v}>{({SERIF:'Editorial serif',SANS:'Modern sans serif',cards:'Photo cards',compact:'Compact list',soft:'Soft surface',outlined:'Fine outline',pills:'Soft pills',underline:'Underlined tabs',rounded:'Rounded',square:'Square edge',reduced:'Reduced',subtle:'Subtle',standard:'Standard'})[v]}</option>)}</select></label>)}<p className="text-xs text-zinc-500">Only approved font families and validated colors are used. Text and accents need 4.5:1 contrast; the system reduced-motion preference always wins.</p></fieldset>
 <div className="flex flex-wrap gap-2"><button className={button} disabled={busy} onClick={() => write(() => api.saveAppearance(state.version, draft))}>Save draft</button><button className="min-h-11 rounded-xl bg-amber-300 px-4 text-sm font-semibold text-zinc-950 disabled:opacity-40" disabled={busy || changed} onClick={() => write(() => api.publishAppearance(state.version), 'Appearance published. Guests now see this saved design.')}>Publish saved draft</button><button className={button} disabled={busy} onClick={() => write(() => api.discardAppearance(state.version), 'Draft discarded. Published appearance preserved.')}>Discard draft</button></div><p className="text-xs text-zinc-500">Publish never changes menu availability or menu mode. Save local edits before publishing.</p></div>
 <div className="min-w-0 rounded-3xl border hairline bg-black/20 p-3 sm:p-5"><div className="mb-4 flex items-center justify-between gap-3"><h3 className="text-sm font-semibold">Live draft preview</h3><div className="flex gap-2">{['mobile', 'desktop'].map(view => <button key={view} aria-pressed={size === view} className={button} onClick={() => setSize(view)}>{view}</button>)}</div></div><BrandPreview slug={restaurant.slug} appearance={preview} size={size}/><p className="mt-3 text-xs text-zinc-500">Preview traffic is excluded from analytics. Guest cart and request actions are disabled here.</p></div></div>
 {children && <details className="rounded-2xl border hairline p-5"><summary className="cursor-pointer text-sm font-semibold">Restaurant profile &amp; legacy image fallbacks</summary><p className="my-3 text-xs text-zinc-500">Profile details update immediately. These images are fallbacks when no studio image is selected. Use the draft image controls above for versioned publication.</p>{children}</details>}
 </section>;
}
