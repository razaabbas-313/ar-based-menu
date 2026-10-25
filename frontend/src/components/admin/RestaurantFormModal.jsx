import { useEffect, useRef, useState } from 'react';
import { CircleCheck, LoaderCircle, Save, Store, TriangleAlert, X } from 'lucide-react';
import { Field, IconButton, Overlay, inputClass, primaryButtonClass } from '../ui.jsx';
import TemporaryPasswordModal from './TemporaryPasswordModal.jsx';
import { api } from '../../api/client.js';
import { cx, slugify } from '../../utils/format.js';

const STATUSES = ['ACTIVE', 'TRIAL', 'PENDING'];

/**
 * "New Restaurant" provisioning (the only way to create a restaurant) and profile edit.
 * The slug follows the name until edited by hand and is checked live; on create the server
 * generates a temporary password that is shown once.
 */
export default function RestaurantFormModal({ restaurant, onClose, onSaved }) {
  const editing = !!restaurant;
  const [form, setForm] = useState({
    name: restaurant?.name || '',
    slug: restaurant?.slug || '',
    ownerName: restaurant?.ownerName || '',
    email: restaurant?.email || '',
    phoneNumber: '',
    status: 'ACTIVE',
    canUpload3d: !!restaurant?.canUpload3d,
  });
  const [slugEdited, setSlugEdited] = useState(editing);
  const [availability, setAvailability] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [created, setCreated] = useState(null);
  const seq = useRef(0);

  const slug = slugEdited ? slugify(form.slug) : slugify(form.name);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const slugUnchanged = editing && slug === restaurant.slug;

  // Live "/r/slug is available" check (debounced, super-admin endpoint).
  useEffect(() => {
    if (slug.length < 3 || slugUnchanged) {
      setAvailability(null);
      return undefined;
    }
    const mine = ++seq.current;
    const t = setTimeout(() => {
      api
        .adminSlugAvailable(slug, editing ? restaurant.id : undefined)
        .then((res) => mine === seq.current && setAvailability(res))
        .catch(() => mine === seq.current && setAvailability(null));
    }, 300);
    return () => clearTimeout(t);
  }, [slug, slugUnchanged, editing, restaurant]);

  const slugProblem = availability && !availability.available ? availability.reason : null;
  const valid = form.name.trim() && form.ownerName.trim() && form.email.trim() && slug.length >= 3 && !slugProblem;

  async function submit(e) {
    e.preventDefault();
    if (!valid) return;
    setBusy(true);
    setError('');
    setFieldErrors({});
    try {
      const base = {
        name: form.name.trim(),
        slug,
        ownerName: form.ownerName.trim(),
        email: form.email.trim(),
        canUpload3d: form.canUpload3d,
      };
      if (editing) {
        const saved = await api.adminUpdateRestaurant(restaurant.id, base);
        onSaved(saved, `${saved.name} updated`);
      } else {
        const res = await api.adminCreateRestaurant({ ...base, phoneNumber: form.phoneNumber.trim() || null, status: form.status, canUpload3d: form.canUpload3d });
        setCreated(res);
      }
    } catch (err) {
      setError(err.message);
      if (err.fields) setFieldErrors(err.fields);
    } finally {
      setBusy(false);
    }
  }

  if (created) {
    return (
      <TemporaryPasswordModal
        title={`${created.restaurant.name} is ready`}
        restaurant={created.restaurant}
        password={created.temporaryPassword}
        onDone={() => onSaved(created.restaurant, `${created.restaurant.name} provisioned`)}
      />
    );
  }

  return (
    <Overlay onClose={busy ? undefined : onClose} align="bottom" label={editing ? 'Edit restaurant' : 'New restaurant'}>
      <form onSubmit={submit} className="sheet pb-safe relative z-10 flex max-h-[94vh] w-full max-w-lg flex-col overflow-hidden rounded-t-[30px] border hairline bg-onyx sm:rounded-[30px]" noValidate>
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">{editing ? 'Edit profile' : 'Provision'}</div>
            <h2 className="font-display text-3xl font-semibold">{editing ? restaurant.name : 'New restaurant'}</h2>
          </div>
          <IconButton icon={X} label="Close" onClick={() => !busy && onClose()} />
        </div>
        <div className="flex-1 space-y-4 overflow-y-auto px-5 py-5">
          <Field label="Restaurant name" error={fieldErrors.name}>
            <input className={inputClass} maxLength={120} value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Bistro Lumière" />
          </Field>
          <Field
            label="Desired slug"
            error={fieldErrors.slug || slugProblem}
            hint={editing && !slugUnchanged ? 'Changing the slug breaks printed QR codes that use the old link.' : `Private menu link: /r/${slug || '…'}`}
          >
            <div className="flex h-12 items-center rounded-2xl border hairline bg-black/40 pl-4 focus-within:border-amber-500/60">
              <span className="font-mono text-sm text-zinc-500">/r/</span>
              <input
                className="h-full min-w-0 flex-1 bg-transparent pr-3 font-mono text-sm text-zinc-50 focus:outline-none"
                value={slugEdited ? form.slug : slug}
                maxLength={60}
                autoCapitalize="none"
                spellCheck="false"
                onChange={(e) => {
                  setSlugEdited(true);
                  set('slug', e.target.value);
                }}
                data-testid="slug-input"
              />
              {availability?.available && (
                <span className="mr-3 flex shrink-0 items-center gap-1 text-xs text-emerald-300" data-testid="slug-available">
                  <CircleCheck size={14} aria-hidden="true" /> Available
                </span>
              )}
            </div>
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Owner name" error={fieldErrors.ownerName}>
              <input className={inputClass} maxLength={120} value={form.ownerName} onChange={(e) => set('ownerName', e.target.value)} placeholder="Camille Durand" />
            </Field>
            <Field label="Owner email" error={fieldErrors.email}>
              <input className={inputClass} type="email" maxLength={160} value={form.email} onChange={(e) => set('email', e.target.value)} placeholder="owner@restaurant.com" />
            </Field>
          </div>
          <div className="flex items-center justify-between rounded-2xl border hairline bg-black/30 p-3.5">
            <div className="pr-3">
              <span className="text-xs font-semibold uppercase tracking-wider text-zinc-200">
                Direct 3D Model Upload
              </span>
              <p className="text-[11px] text-zinc-400">
                Allow studio owner to upload .glb files directly without concierge approval.
              </p>
            </div>
            <button
              type="button"
              onClick={() => set('canUpload3d', !form.canUpload3d)}
              className={cx(
                'tap shrink-0 relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none',
                form.canUpload3d ? 'bg-emerald-500' : 'bg-zinc-700'
              )}
              role="switch"
              aria-checked={!!form.canUpload3d}
              title={form.canUpload3d ? 'Revoke direct 3D upload' : 'Grant direct 3D upload'}
            >
              <span
                className={cx(
                  'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                  form.canUpload3d ? 'translate-x-6' : 'translate-x-1'
                )}
              />
            </button>
          </div>
          {!editing && (
            <>
              <Field label="Contact phone (optional)" error={fieldErrors.phoneNumber} hint="Shown on the guest menu as a “Call” button.">
                <input className={inputClass} inputMode="tel" maxLength={30} value={form.phoneNumber} onChange={(e) => set('phoneNumber', e.target.value)} placeholder="+92 300 1234567" />
              </Field>
              <div>
                <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Initial status</span>
                <div className="grid grid-cols-3 gap-1 rounded-2xl border hairline bg-black/30 p-1">
                  {STATUSES.map((st) => (
                    <button
                      key={st}
                      type="button"
                      onClick={() => set('status', st)}
                      className={cx('tap h-11 rounded-xl text-xs font-bold tracking-wider', form.status === st ? 'bg-zinc-800 text-amber-200' : 'text-zinc-500')}
                    >
                      {st}
                    </button>
                  ))}
                </div>
              </div>
              <p className="rounded-2xl border hairline bg-black/30 p-3 text-xs leading-relaxed text-zinc-400">
                A secure temporary password (Temp-…) is generated and shown once. The owner must set their own password at first sign-in.
              </p>
            </>
          )}
          {error && (
            <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
              <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}
        </div>
        <div className="border-t hairline bg-black/30 px-5 py-4">
          <button type="submit" disabled={!valid || busy} className={primaryButtonClass}>
            {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : editing ? <Save size={18} aria-hidden="true" /> : <Store size={18} aria-hidden="true" />}
            {editing ? 'Save profile' : 'Create restaurant & temporary password'}
          </button>
        </div>
      </form>
    </Overlay>
  );
}
