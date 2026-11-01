import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client.js';
import { safeHttpUrl } from '../../utils/format.js';
import ModelModal from './ModelModal.jsx';
import Request3dModal from './Request3dModal.jsx';
const button = 'min-h-11 rounded-xl border border-white/15 px-4 py-2 text-sm hover:bg-white/10 disabled:opacity-40';
export default function ModelsManager({
  dishes = [],
  policy,
  onSaved,
  adminRestaurantId
}) {
  const requestVersion=useRef(0);
  const [query, setQuery] = useState(''),
    [filter, setFilter] = useState('all'),
    [upload, setUpload] = useState(null),
    [request, setRequest] = useState(null),
    [selected, setSelected] = useState(null),
    [history, setHistory] = useState([]),
    [requests, setRequests] = useState([]),
    [error, setError] = useState(''),
    [loading, setLoading] = useState(false),
    [more, setMore] = useState(false);
  useEffect(() => {
    requestVersion.current++;setSelected(null);setHistory([]);setRequests([]);let alive=true;
    (adminRestaurantId ? api.adminRestaurantModelRequests(adminRestaurantId) : api.studioModelRequests()).then(rows=>{if(alive)setRequests(rows);}).catch(e=>{if(alive)setError(e.message);});
    return()=>{alive=false;requestVersion.current++;};
  }, [adminRestaurantId, dishes]);
  async function inspect(dish, after = 0) {
    const current=++requestVersion.current;
    setLoading(true);
    setError('');
    setSelected(dish);
    try {
      const rows = await (adminRestaurantId ? api.adminModelHistory(adminRestaurantId, dish.id, after) : api.modelHistory(dish.id, after));
      if(current!==requestVersion.current)return;
      setHistory(previous => after ? [...previous, ...rows] : rows);
      setMore(rows.length === 100);
    } catch (e) {
      if(current===requestVersion.current)setError(e.message);
    } finally {
      if(current===requestVersion.current)setLoading(false);
    }
  }
  const flags = policy?.operational || {},
    canUpload = !!adminRestaurantId || !!flags['model.ownerUpload'];
  const usage = policy?.usage?.models,
    limit = policy?.values?.['quota.models'];
  const shown = dishes.filter(d => d.dishTitle?.toLowerCase().includes(query.toLowerCase()) && (filter === 'all' || (filter === 'with' ? d.is3d : !d.is3d)));
  return <section className="space-y-5"><header><p className="text-xs uppercase tracking-[.3em] text-amber-300">Model atelier</p><h2 className="mt-2 font-display text-3xl">A new dimension for every dish.</h2><p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">Manage live models, retained versions and human concierge requests. Replacing a model keeps its earlier version on hold; history does not consume another active model slot.</p>{policy && <p className="mt-3 text-sm text-amber-200">{usage} active / {limit === 'UNLIMITED' ? 'Unlimited' : limit} model slots · {limit === 'UNLIMITED' ? 'No plan capacity limit' : Math.max(0, Number(limit) - Number(usage)) + ' remaining'}</p>}</header>{Number(policy?.usage?.retainedModelVersions)>0&&<p className="text-xs text-zinc-500">{policy.usage.retainedModelVersions} retained superseded versions · {Number(policy.usage.retainedModelBytes).toLocaleString()} known metadata bytes, separate from active slots. Physical S3 storage is unverified.</p>}
  <div className="flex flex-wrap gap-3"><label className="flex-1 text-xs text-zinc-400">Find a dish<input className="mt-1 min-h-11 w-full rounded-xl border hairline bg-black/30 px-4 text-sm" value={query} onChange={e => setQuery(e.target.value)} placeholder="Search the collection" /></label><label className="text-xs text-zinc-400">Model status<select className="mt-1 block min-h-11 rounded-xl border hairline bg-zinc-900 px-4 text-sm" value={filter} onChange={e => setFilter(e.target.value)}><option value="all">All dishes</option><option value="with">With model</option><option value="without">Without model</option></select></label></div>
  {error && <p role="alert" className="rounded-xl border border-red-400/30 p-4 text-sm text-red-200">{error}</p>}
  {!shown.length ? <p className="rounded-2xl border border-dashed hairline p-10 text-center text-zinc-500">No dishes match. Add a dish before assigning a model.</p> : <ul className="divide-y divide-white/10 rounded-2xl border hairline px-4">{shown.map(d => <li key={d.id} className="flex flex-wrap items-center justify-between gap-3 py-4"><div><h3 className="font-semibold">{d.dishTitle}</h3><p className="mt-1 text-xs text-zinc-500">{d.is3d ? 'Live model' : 'Photo menu'} · {d.modelPending ? 'Upload pending' : d.modelRequestStatus || 'No active request'}</p></div><div className="flex flex-wrap gap-2"><button className={button} disabled={!canUpload} onClick={() => setUpload(d)}>{d.is3d ? 'Replace model' : 'Upload model'}</button>{!adminRestaurantId && <button className={button} disabled={!flags['model.concierge'] || d.modelPending || ['PENDING', 'IN_PRODUCTION'].includes(d.modelRequestStatus)} onClick={() => setRequest(d)}>Request creation</button>}<button className={button} onClick={() => inspect(d)}>History &amp; provenance</button></div></li>)}</ul>}
  {selected && <div className="rounded-2xl border border-amber-300/20 p-5"><div className="flex justify-between gap-3"><h3 className="font-display text-2xl">{selected.dishTitle} · history</h3><button className={button} onClick={() => {requestVersion.current++;setSelected(null);}}>Close</button></div>{loading ? <p className="py-5 text-sm text-zinc-500">Loading model lineage…</p> : !history.length ? <p className="py-5 text-sm text-zinc-500">No recorded model versions. Missing historical files cannot be reconstructed.</p> : <ul className="mt-4 divide-y divide-white/10">{history.map(v => <li key={v.id} className="flex flex-wrap items-center justify-between gap-3 py-3"><div><p className="text-sm">Version {v.version_number} · {v.origin.replaceAll('_', ' ')}</p><p className="mt-1 text-xs text-zinc-500">{v.state} · {v.retention} · {v.availability} · Publisher: {v.actor_kind || 'Unknown'} {v.actor_id ?? ''}</p><p className="mt-1 text-xs text-zinc-500">Upload issuer: {v.issued_actor_kind||'Not recorded'} {v.issued_actor_id??''}</p><p className="mt-1 text-xs text-zinc-500">{new Date(v.published_at || v.created_at).toLocaleString()}{!v.published_at ? ' · legacy observation date' : ''}{v.request_id ? ' · Request ' + v.request_id : ''}</p></div>{safeHttpUrl(v.url) && <a className={button} href={safeHttpUrl(v.url)} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer">Open model asset</a>}</li>)}</ul>}{more && <button className={button} onClick={() => inspect(selected, history.at(-1).id)}>Load more versions</button>}
   <h4 className="mt-6 text-sm font-semibold">Concierge request history</h4><ul className="mt-2 space-y-2 text-xs text-zinc-400">{requests.filter(r => r.dishId === selected.id).map(r => <li key={r.id}>{r.status} · {r.requestedAt ? new Date(r.requestedAt).toLocaleString() : ''}{r.notes && <p className="mt-1 whitespace-pre-wrap">{r.notes}</p>}</li>)}</ul>{!requests.some(r => r.dishId === selected.id) && <p className="mt-2 text-xs text-zinc-500">No recorded concierge requests for this dish.</p>}
  </div>}
  {upload && <ModelModal dish={upload} adminRestaurantId={adminRestaurantId} onClose={() => setUpload(null)} onSaved={(...args) => {
      setUpload(null);
      onSaved?.(...args);
      if (selected) inspect(selected);
    }} />}
  {request && <Request3dModal dish={request} onClose={() => setRequest(null)} onSuccess={dish => {
      setRequest(null);
      onSaved?.(dish);
    }} />}
 </section>;
}
