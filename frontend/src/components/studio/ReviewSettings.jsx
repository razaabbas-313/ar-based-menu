import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
export default function ReviewSettings() {
  const [data, setData] = useState(null),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [saved, setSaved] = useState(false);
  const load = () => api.reviewSettings().then(setData).catch(e => setError(e.message));
  useEffect(() => {
    load();
  }, []);
  return <section className="rounded-3xl border hairline p-5 sm:p-8"><h3 className="font-display text-2xl">Invite honest feedback</h3><p className="mt-2 max-w-xl text-sm leading-6 text-zinc-400">Add the review link from your Google Business Profile. Every guest sees the same invitation. No ratings are collected here, and opening Google does not submit a review.</p>{error && <p role="alert" className="mt-3 text-sm text-red-200">{error} <button className="underline" onClick={load}>Reload</button></p>}{!data ? <p className="mt-4 text-sm text-zinc-500">Loading review settings…</p> : <form className="mt-5 space-y-4" onSubmit={async e => {
      e.preventDefault();
      setBusy(true);
      setError('');
      setSaved(false);
      try {
        setData(await api.saveReviewSettings({
          expectedVersion: data.version,
          url: data.url,
          enabled: data.enabled
        }));
        setSaved(true);
      } catch (e) {
        setError(e.message);
      } finally {
        setBusy(false);
      }
    }}><label className="block text-sm">Google review destination<input type="url" maxLength={600} className="mt-2 min-h-12 w-full rounded-xl border hairline bg-black/25 px-4" placeholder="https://g.page/r/…/review" value={data.url || ''} onChange={e => setData(d => ({
          ...d,
          url: e.target.value
        }))} /><span className="mt-2 block text-xs text-zinc-500">Supported: g.page/r/…/review or search.google.com/local/writereview?placeid=… . Venue ownership is not verified.</span></label><label className="flex gap-3 text-sm"><input type="checkbox" checked={data.enabled} disabled={!data.allowed && !data.enabled} onChange={e => setData(d => ({
          ...d,
          enabled: e.target.checked
        }))} /> Show “Review us on Google”</label>{!data.allowed && <p className="text-xs text-amber-200">Google Reviews is unavailable under your current plan. Existing configuration is preserved.</p>}<div className="flex flex-wrap gap-3"><button disabled={busy} className="min-h-11 rounded-xl bg-amber-300 px-5 font-semibold text-zinc-950">Save review settings</button>{/^https:\/\/(g\.page\/r\/[A-Za-z0-9_-]+\/review|search\.google\.com\/local\/writereview\?placeid=[A-Za-z0-9_-]+)$/.test(data.url || '') && <a href={data.url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" className="inline-flex min-h-11 items-center rounded-xl border hairline px-4 text-sm">Preview Google destination ↗</a>}</div>{saved && <p role="status" className="text-sm text-emerald-200">Review settings saved.</p>}</form>}</section>;
}
