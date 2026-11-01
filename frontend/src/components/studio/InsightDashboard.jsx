import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
const LABELS = {
  MENU_VISIT: 'Menu visits',
  DISH_DETAIL_VIEW: 'Dish details opened',
  MODEL_VIEW_READY: 'Visible model ready',
  AR_ATTEMPT: 'AR launch attempts',
  CART_ADD: 'Cart addition actions',
  ORDER_PREPARED: 'Verified requests prepared',
  IMAGE_GENERATED: 'Image generation reported',
  SHARE_FLOW_OPENED: 'Share flow attempts',
  SHARE_REPORTED_COMPLETED: 'Native handoffs reported',
  WAITER_PREPARED: 'Waiter messages prepared',
  WAITER_CHAT_OPENED: 'Waiter chat clicks',
  REVIEW_LINK_OPENED: 'Google review link clicks',
  CATEGORY_SELECTED: 'Category selections'
};
const today = () => new Date().toISOString().slice(0, 10),
  daysAgo = n => new Date(Date.now() - n * 86400000).toISOString().slice(0, 10);
export default function InsightDashboard({
  platform = false
}) {
  const [from, setFrom] = useState(daysAgo(29)),
    [to, setTo] = useState(today),
    [data, setData] = useState(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState('');
  async function load() {
    setLoading(true);
    setError('');
    try {
      setData(await (platform ? api.platformInsights(from, to) : api.insights(from, to)));
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    load();
  }, [platform]);
  const totals = data?.totals || {},
    trend = (data?.trend || []).filter(r => r.event_type === 'MENU_VISIT'),
    max = Math.max(1, ...trend.map(r => Number(r.count)));
  return <section className="space-y-6"><header><p className="text-xs uppercase tracking-[.3em] text-amber-300">{platform ? 'Platform insight' : 'Dining intelligence'}</p><h2 className="mt-2 font-display text-4xl">Understand the experience.</h2><p className="mt-3 max-w-2xl text-sm leading-6 text-zinc-400">Accepted guest observations and server-validated preparations. Visits are URL displays, table context may be copied, and share handoffs do not establish delivery, acceptance or sales.</p></header>
 <form className="flex flex-wrap items-end gap-3" onSubmit={e => {
      e.preventDefault();
      load();
    }}><label className="text-xs text-zinc-400">From · UTC<input type="date" required min={daysAgo(89)} max={to} className="mt-1 block min-h-11 rounded-xl border hairline bg-zinc-900 px-3" value={from} onChange={e => setFrom(e.target.value)} /></label><label className="text-xs text-zinc-400">Through · UTC<input type="date" required min={from} max={today()} className="mt-1 block min-h-11 rounded-xl border hairline bg-zinc-900 px-3" value={to} onChange={e => setTo(e.target.value)} /></label><button disabled={loading} className="min-h-11 rounded-xl border hairline px-5 text-sm">{loading ? 'Loading…' : 'Apply dates / refresh'}</button></form>
 {error && <p role="alert" className="rounded-xl border border-red-400/30 p-4 text-red-200">{error}</p>}
 {!data ? <div role="status" className="rounded-3xl border hairline p-12 text-center text-sm text-zinc-500">{loading ? 'Loading actual engagement data…' : 'Data is unavailable. Retry the request above.'}</div> : <><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">{['MENU_VISIT', 'DISH_DETAIL_VIEW', 'MODEL_VIEW_READY', 'AR_ATTEMPT'].map(key => <article key={key} className="rounded-2xl border hairline bg-white/[.025] p-5"><h3 className="text-xs text-zinc-400">{LABELS[key]}</h3><p className="mt-3 font-display text-4xl text-amber-200">{Number(totals[key] || 0).toLocaleString()}</p><p className="mt-2 text-xs text-zinc-500">V2 event counts · selected UTC window</p></article>)}</div>
 {!Object.keys(totals).length && <p role="status" className="rounded-xl border border-dashed hairline p-6 text-sm text-zinc-500">No accepted V2 observations in this window. Legacy activity is separate; missing client telemetry cannot be reconstructed.</p>}
 <div className="grid gap-5 lg:grid-cols-2"><section className="rounded-3xl border hairline p-5"><h3 className="font-display text-2xl">Menu visit trend</h3><p className="mt-2 text-xs text-zinc-500">Received UTC day · accepted observations, not unique people</p>{!trend.length ? <p className="py-8 text-sm text-zinc-500">No recorded visits.</p> : <ul className="mt-5 space-y-3">{trend.map(row => <li key={row.day} className="grid grid-cols-[90px_1fr_45px] items-center gap-3 text-xs"><span>{row.day}</span><span className="h-3 overflow-hidden rounded-full bg-white/5" aria-hidden="true"><span className="block h-full rounded-full bg-amber-300/70" style={{
                  width: `${Number(row.count) / max * 100}%`
                }} /></span><strong className="text-right tabular-nums">{row.count}</strong></li>)}</ul>}</section>
 <section className="rounded-3xl border hairline p-5"><h3 className="font-display text-2xl">Request &amp; sharing activity</h3><p className="mt-2 text-xs leading-5 text-zinc-500">Independent step counts in the same time window, not a visitor conversion funnel. Client observations may be absent. No order acceptance or revenue is inferred.</p><dl className="mt-4 divide-y divide-white/10">{['CART_ADD', 'ORDER_PREPARED', 'IMAGE_GENERATED', 'SHARE_FLOW_OPENED', 'SHARE_REPORTED_COMPLETED', 'WAITER_PREPARED'].map(key => <div key={key} className="flex justify-between gap-4 py-3 text-sm"><dt className="text-zinc-400">{LABELS[key]}</dt><dd className="font-mono text-amber-200">{Number(totals[key] || 0).toLocaleString()}</dd></div>)}</dl></section></div>
 {!data.advanced ? <p className="rounded-2xl border border-amber-300/20 p-5 text-sm text-zinc-400">Advanced dish, mode and table-context breakdowns are unavailable under your current plan. Basic event counts remain available.</p> : <div className="grid gap-5 lg:grid-cols-3"><Breakdown title="Menu entry context" values={data.context} labels={{
          GENERAL: 'General URL context',
          VALID_TABLE: 'Validated table-link context'
        }} /><Breakdown title="Active menu modes" values={data.modes} /><section className="rounded-2xl border hairline p-5"><h3 className="font-display text-xl">Most-opened dishes</h3>{!data.topDishes.length ? <p className="mt-4 text-xs text-zinc-500">No dish detail observations yet.</p> : <ol className="mt-4 divide-y divide-white/10">{data.topDishes.map(d => <li key={d.dish_id} className="flex justify-between gap-3 py-3 text-sm"><span>{d.name}</span><strong>{d.count}</strong></li>)}</ol>}</section></div>}
 <details className="rounded-2xl border hairline p-5"><summary className="cursor-pointer text-sm font-semibold">Metric definitions &amp; limitations</summary><dl className="mt-4 space-y-3">{Object.entries(data.definitions).map(([key, value]) => <div key={key}><dt className="text-xs font-semibold capitalize text-amber-200">{key}</dt><dd className="mt-1 text-xs leading-5 text-zinc-400">{value}</dd></div>)}</dl></details></>}
 </section>;
}
function Breakdown({
  title,
  values = {},
  labels = {}
}) {
  return <section className="rounded-2xl border hairline p-5"><h3 className="font-display text-xl">{title}</h3>{!Object.keys(values).length ? <p className="mt-4 text-xs text-zinc-500">No observations in this window.</p> : <dl className="mt-4 divide-y divide-white/10">{Object.entries(values).map(([key, count]) => <div key={key} className="flex justify-between gap-3 py-3 text-sm"><dt>{labels[key] || key}</dt><dd>{count}</dd></div>)}</dl>}</section>;
}
