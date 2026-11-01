import { useCallback, useEffect, useState } from 'react';
import {
  Archive,
  Box,
  Calculator,
  ExternalLink,
  FileText,
  History,
  Image as ImageIcon,
  LoaderCircle,
  LogIn,
  KeyRound,
  Pause,
  Pencil,
  Play,
  QrCode,
  RotateCcw,
  Sparkles,
  TriangleAlert,
  X,
} from 'lucide-react';
import { IconButton, Overlay, Spinner } from '../ui.jsx';
import { useToast } from '../Toast.jsx';
import { api } from '../../api/client.js';
import { CATEGORY_LABEL, STATUS_STYLE, cx, fmtBytes, fmtDate, fmtPrice } from '../../utils/format.js';
import { impersonateRestaurant } from './impersonate.js';
import RestaurantQrModal from '../RestaurantQrModal.jsx';
import TemporaryPasswordModal from './TemporaryPasswordModal.jsx';
import ModelsManager from '../studio/ModelsManager.jsx';
import RestaurantEntitlements from './RestaurantEntitlements.jsx';
import LuxurySelect from '../ui/LuxurySelect.jsx';

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'ACTIVE', color: 'text-emerald-300' },
  { value: 'PENDING_ONBOARDING', label: 'PENDING ONBOARDING', color: 'text-amber-300' },
  { value: 'SUSPENDED', label: 'SUSPENDED', color: 'text-red-300' },
];

/** Per-restaurant deep dive: stats, storage split, dishes, audit trail and operator actions. */
export default function RestaurantDrawer({ restaurantId, version = 0, onClose, onChanged, onEdit, onDelete, onStatus }) {
  const toast = useToast();
  const [detail, setDetail] = useState(null);
  const [effectivePolicy, setEffectivePolicy] = useState(null);
  const [modelRequests, setModelRequests] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [qrOpen, setQrOpen] = useState(false);
  const [tempPassword, setTempPassword] = useState(null);
  const [notes, setNotes] = useState('');
  const [savingNotes, setSavingNotes] = useState(false);

  async function resetPassword() {
    if (!window.confirm(`Issue a new temporary password for ${detail.restaurant.name}? The owner's current password stops working immediately.`)) return;
    setBusy('reset');
    try {
      const res = await api.adminResetPassword(restaurantId);
      setTempPassword(res);
      onChanged(res.restaurant);
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setBusy('');
    }
  }

  const load = useCallback(() => {
    setError('');
    api
      .adminRestaurant(restaurantId)
      .then((d) => {
        setDetail(d);
        setNotes(d?.restaurant?.adminNotes || '');
      })
      .catch((e) => setError(e.message));

    api
      .adminRestaurantModelRequests(restaurantId)
      .then((reqs) => setModelRequests(reqs || []))
      .catch(() => {});
  }, [restaurantId]);

  useEffect(load, [load, version]);

  async function saveNotes() {
    setSavingNotes(true);
    try {
      await api.adminUpdateNotes(restaurantId, notes.trim());
      toast('Private CRM notes saved');
      onChanged?.();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setSavingNotes(false);
    }
  }

  async function run(key, fn, message) {
    setBusy(key);
    try {
      const res = await fn();
      if (message) toast(message);
      if (res && res.id) {
        setDetail((d) => (d ? { ...d, restaurant: res } : d));
        onChanged?.(res);
      } else {
        load();
      }
      return res;
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setBusy('');
    }
  }

  const r = detail?.restaurant;
  const deleted = !!r?.deletedAt;
  const totalBytes = detail ? detail.photoBytes + detail.modelBytes + detail.brandingBytes : 0;
  const share = (n) => (totalBytes ? Math.max(2, Math.round((n / totalBytes) * 100)) : 0);

  return (
    <Overlay onClose={onClose} label="Restaurant details">
      <aside className="drawer pb-safe absolute inset-y-0 right-0 z-10 flex w-full max-w-xl flex-col border-l hairline bg-onyx" data-testid="restaurant-drawer">
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div className="flex min-w-0 items-center gap-3">
            <div className="grid h-12 w-12 shrink-0 place-items-center overflow-hidden rounded-2xl border border-amber-500/40 bg-black">
              {r?.logoUrl ? (
                <img src={r.logoUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <span className="font-display text-2xl italic text-amber-300">{(r?.name || '?').charAt(0)}</span>
              )}
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h2 className="truncate font-display text-2xl font-semibold">{r?.name || 'Loading…'}</h2>
                {r && (
                  <span className={cx('shrink-0 rounded-full border px-2 py-0.5 text-[9px] font-bold uppercase tracking-[0.16em]', STATUS_STYLE[deleted ? 'DELETED' : r.status])}>
                    {deleted ? 'DELETED' : r.status}
                  </span>
                )}
              </div>
              {r && <div className="truncate font-mono text-[11px] text-zinc-500">/r/{r.slug} · {r.email}</div>}
            </div>
          </div>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-5">
          {error && (
            <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">
              <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" /> {error}
            </div>
          )}
          {!detail && !error && (
            <div className="grid place-items-center py-24">
              <Spinner />
            </div>
          )}
          {detail && (
            <div className="space-y-7">
              <RestaurantEntitlements restaurantId={restaurantId} onPolicyChange={setEffectivePolicy} key={`${restaurantId}-${version}`} />
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <Stat label="Dishes" value={r.dishCount} />
                <Stat label="3D models" value={r.model3dCount} />
                <Stat label="Storage" value={fmtBytes(r.storageUsedBytes)} />
                <Stat label="Tier" value={r.bandwidthTier} />
              </div>

              {/* Horizontally scrollable action strip */}
              <div className="relative">
                <div className="no-scrollbar -mx-5 flex items-center gap-2.5 overflow-x-auto px-5 py-2.5 touch-pan-x">
                  {!deleted && (
                    <>
                      <div className="flex shrink-0 items-center gap-2 rounded-full border hairline bg-black/50 px-3.5 py-1 text-xs">
                        <span className="text-[10px] font-semibold uppercase tracking-wider text-zinc-400 whitespace-nowrap">Status:</span>
                        <LuxurySelect
                          value={r.status}
                          onChange={(val) => onStatus(r, val, load)}
                          options={STATUS_OPTIONS}
                          buttonClassName="h-7 border-0 bg-transparent px-1 py-0 text-xs font-bold whitespace-nowrap shrink-0"
                          dropdownClassName="min-w-[12rem]"
                        />
                      </div>
                      <Action icon={LogIn} label="Open Studio" primary busy={busy === 'imp'} onClick={() => run('imp', () => impersonateRestaurant(r.id).then(() => null), 'Studio opened in a new tab')} />
                      {effectivePolicy?.operational['qr.venue'] && <Action icon={QrCode} label="Restaurant QR" onClick={() => setQrOpen(true)} />}
                      <Action icon={ExternalLink} label="View Live Menu" href={`/r/${r.slug}`} />
                      <Action icon={Pencil} label="Edit Details" onClick={() => onEdit(r)} />
                    </>
                  )}
                  {deleted && (
                    <Action icon={RotateCcw} label="Restore Restaurant" primary busy={busy === 'restore'} onClick={() => run('restore', () => api.adminRestore(r.id), `${r.name} restored`)} />
                  )}
                  {!deleted && <Action icon={KeyRound} label="Reset Password" busy={busy === 'reset'} onClick={resetPassword} />}
                  <Action icon={Calculator} label="Recalculate Usage" busy={busy === 'calc'} onClick={() => run('calc', () => api.adminRecalculate(r.id), 'Usage counters recalculated')} />
                  <Action icon={Archive} label="Delete Restaurant" danger onClick={() => onDelete(r)} />
                </div>
              </div>



              {/* 3D Model & Concierge Request History */}
              <section className="rounded-2xl border hairline bg-black/30 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Box size={15} className="text-amber-400" />
                    <span className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
                      3D Model &amp; Concierge History ({modelRequests.length})
                    </span>
                  </div>
                  <span className="text-[10px] text-zinc-500 font-mono">Digital Twin Records</span>
                </div>
                {modelRequests.length === 0 ? (
                  <div className="py-4 text-center text-xs text-zinc-500">No 3D model requests recorded for this restaurant yet.</div>
                ) : (
                  <div className="divide-y hairline">
                    {modelRequests.map((req) => (
                      <div key={req.id} className="py-2.5 flex items-center justify-between gap-3 text-xs">
                        <div className="min-w-0 flex items-center gap-2.5">
                          {req.dishImageUrl && (
                            <img src={req.dishImageUrl} alt="" className="h-9 w-9 shrink-0 rounded-xl object-cover border hairline" />
                          )}
                          <div className="min-w-0">
                            <div className="font-semibold text-zinc-200 truncate flex items-center gap-1.5">
                              <span className="truncate">{req.dishTitle}</span>
                              {req.dishCode && (
                                <span className="font-mono text-[9px] text-amber-300/80 px-1 py-0.5 rounded bg-black/40 border hairline shrink-0">
                                  {req.dishCode}
                                </span>
                              )}
                            </div>
                            <div className="text-[10px] text-zinc-500 flex items-center gap-1.5">
                              <span>{req.requestType === 'REPLACEMENT' ? '🔄 3D Replacement' : '✨ New 3D Request'}</span>
                              <span>·</span>
                              <span>{fmtDate(req.requestedAt)}</span>
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <span className={cx(
                            'px-2 py-0.5 rounded-full text-[10px] font-semibold border',
                            req.status === 'COMPLETED' ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300' :
                            req.status === 'SUPERSEDED' ? 'border-zinc-700 bg-zinc-800/50 text-zinc-400' :
                            'border-amber-500/40 bg-amber-500/10 text-amber-300'
                          )}>
                            {req.status}
                          </span>
                          {req.completedModelUrl && (
                            <a
                              href={req.completedModelUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="tap p-1 rounded-lg border hairline text-zinc-400 hover:text-amber-300 hover:border-amber-500/40"
                              title="Download / View .glb"
                            >
                              <ExternalLink size={13} />
                            </a>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              {/* Private CRM Notes */}
              <section className="rounded-2xl border hairline bg-black/30 p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <FileText size={15} className="text-amber-400" />
                    <span className="text-xs font-semibold uppercase tracking-wider text-zinc-300">
                      Private CRM Notes
                    </span>
                  </div>
                  <span className="text-[10px] text-amber-500/80 font-mono">Platform Admin Only</span>
                </div>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Record internal details (VIP contacts, billing arrangements, acrylic QR stand orders)… Never visible to restaurant staff."
                  rows={3}
                  className="w-full rounded-xl border hairline bg-black/50 p-3 text-xs text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
                />
                <div className="flex justify-end">
                  <button
                    type="button"
                    disabled={savingNotes}
                    onClick={saveNotes}
                    className="tap flex h-8 items-center gap-1.5 rounded-lg bg-amber-500 px-3 text-xs font-bold text-zinc-950 hover:bg-amber-400 disabled:opacity-50"
                  >
                    {savingNotes && <LoaderCircle size={13} className="animate-spin" />}
                    <span>{savingNotes ? 'Saving…' : 'Save Notes'}</span>
                  </button>
                </div>
              </section>

              <section>
                <H>Storage breakdown</H>
                <div className="flex h-3 overflow-hidden rounded-full bg-zinc-800">
                  <span className="bg-amber-400" style={{ width: `${share(detail.photoBytes)}%` }} />
                  <span className="bg-emerald-400" style={{ width: `${share(detail.modelBytes)}%` }} />
                  <span className="bg-sky-400" style={{ width: `${share(detail.brandingBytes)}%` }} />
                </div>
                <div className="mt-2 grid grid-cols-3 gap-2 text-xs text-zinc-400">
                  <Legend color="bg-amber-400" label="Photos" value={fmtBytes(detail.photoBytes)} />
                  <Legend color="bg-emerald-400" label="3D models" value={fmtBytes(detail.modelBytes)} />
                  <Legend color="bg-sky-400" label="Branding" value={fmtBytes(detail.brandingBytes)} />
                </div>
                {detail.pendingUploads > 0 && <p className="mt-2 text-xs text-sky-300">{detail.pendingUploads} upload(s) in progress or abandoned.</p>}
              </section>

              <section>
                <ModelsManager adminRestaurantId={restaurantId} dishes={detail.dishes} policy={effectivePolicy} onSaved={()=>{load();onChanged?.();}}/>
                <H>Dishes ({detail.dishes.length})</H>
                {detail.dishes.length === 0 ? (
                  <p className="rounded-2xl border border-dashed hairline px-4 py-6 text-center text-sm text-zinc-500">No dishes yet.</p>
                ) : (
                  <ul className="divide-y divide-zinc-800 overflow-hidden rounded-2xl border hairline">
                    {detail.dishes.map((d) => (
                      <li key={d.id} className={cx('flex items-center gap-3 px-3 py-2.5', !d.available && 'opacity-60')}>
                        <div className="h-10 w-10 shrink-0 overflow-hidden rounded-xl bg-black">
                          {d.imageUrl ? (
                            <img src={d.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
                          ) : (
                            <span className="grid h-full place-items-center text-zinc-600">
                              <ImageIcon size={15} aria-hidden="true" />
                            </span>
                          )}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="truncate text-sm font-semibold text-zinc-100">{d.dishTitle}</div>
                          <div className="text-[11px] text-zinc-500">
                            {CATEGORY_LABEL[d.dishCategory]} · {d.available ? 'In stock' : 'Out of stock'}
                          </div>
                        </div>
                        {d.is3d && <Box size={15} className="shrink-0 text-amber-300" aria-label="3D" />}
                        <span className="shrink-0 font-mono text-xs text-zinc-300">
                          {fmtPrice(d.priceTag, detail.branding.currencyCode, detail.branding.currencySymbol)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>

              <section>
                <H>
                  <History size={14} aria-hidden="true" /> Audit trail
                </H>
                {detail.recentAudit.length === 0 ? (
                  <p className="text-sm text-zinc-500">No admin actions recorded.</p>
                ) : (
                  <ol className="space-y-2">
                    {detail.recentAudit.map((a, i) => (
                      <li key={i} className="rounded-2xl border hairline px-3 py-2">
                        <div className="flex items-center justify-between gap-2 text-xs">
                          <span className="font-mono font-semibold text-amber-200">{a.action}</span>
                          <span className="text-zinc-500">{fmtDate(a.createdAt)}</span>
                        </div>
                        {a.detail && <div className="mt-0.5 text-xs text-zinc-400">{a.detail}</div>}
                        <div className="text-[11px] text-zinc-600">{a.adminEmail}</div>
                      </li>
                    ))}
                  </ol>
                )}
              </section>

              <section className="text-xs text-zinc-500">
                {r.mustChangePassword && <span className="mb-1 block text-amber-300">Owner has not replaced the temporary password yet.</span>}
                Owner {r.ownerName}{r.phoneNumber ? ` · ${r.phoneNumber}` : ''} · joined {fmtDate(r.createdAt)}
                {r.updatedAt ? ` · updated ${fmtDate(r.updatedAt)}` : ''}
                {deleted ? ` · deleted ${fmtDate(r.deletedAt)}` : ''}
              </section>
            </div>
          )}
        </div>
      </aside>
      {qrOpen && r && effectivePolicy?.operational['qr.venue'] && <RestaurantQrModal restaurant={{ name: r.name, slug: r.slug }} onClose={() => setQrOpen(false)} />}
      {tempPassword && (
        <TemporaryPasswordModal
          title="New temporary password"
          restaurant={tempPassword.restaurant}
          password={tempPassword.temporaryPassword}
          onDone={() => {
            setTempPassword(null);
            load();
          }}
        />
      )}
    </Overlay>
  );
}

function H({ children }) {
  return <h3 className="mb-2 flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">{children}</h3>;
}

function Stat({ label, value }) {
  return (
    <div className="rounded-2xl border hairline bg-black/30 px-3 py-3">
      <div className="text-[10px] uppercase tracking-[0.2em] text-zinc-500">{label}</div>
      <div className="mt-1 font-display text-2xl font-semibold text-zinc-50">{value}</div>
    </div>
  );
}

function Legend({ color, label, value }) {
  return (
    <span className="flex items-center gap-1.5">
      <span className={cx('h-2 w-2 shrink-0 rounded-full', color)} />
      <span className="truncate">
        {label} <span className="text-zinc-300">{value}</span>
      </span>
    </span>
  );
}

function Action({ icon: Icon, label, onClick, href, primary, danger, busy }) {
  const cls = cx(
    'tap inline-flex shrink-0 whitespace-nowrap h-10 items-center gap-2 rounded-full border px-4 text-xs font-semibold transition-all shadow-sm select-none',
    primary
      ? 'border-amber-500 bg-amber-500 text-zinc-950 shadow-fab hover:brightness-105'
      : danger
        ? 'border-red-500/30 bg-red-500/5 text-red-300 hover:border-red-500/60 hover:bg-red-500/15'
        : 'hairline bg-black/40 text-zinc-200 hover:border-amber-500/40 hover:text-amber-200',
  );
  const content = (
    <>
      {busy ? <LoaderCircle size={14} className="shrink-0 animate-spin" aria-hidden="true" /> : <Icon size={14} className="shrink-0" aria-hidden="true" />}
      <span className="whitespace-nowrap">{label}</span>
    </>
  );
  if (href) {
    return (
      <a href={href} target="_blank" rel="noreferrer" className={cls}>
        {content}
      </a>
    );
  }
  return (
    <button type="button" onClick={onClick} disabled={busy} className={cls}>
      {content}
    </button>
  );
}
