import { useRef, useState } from 'react';
import { Check, FolderPlus, ImagePlus, KeyRound, LayoutGrid, LoaderCircle, Rows3, Save, Trash2, TriangleAlert } from 'lucide-react';
import MenuHero from '../guest/MenuHero.jsx';
import { Field, inputClass } from '../ui.jsx';
import LuxurySelect from '../ui/LuxurySelect.jsx';
import StudioChangePasswordModal from './StudioChangePasswordModal.jsx';
import { useToast } from '../Toast.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { api } from '../../api/client.js';
import { ACCENTS, CURRENCIES, accentStyle } from '../../utils/theme.js';
import { buzz, cx, fmtPrice } from '../../utils/format.js';
import { BANNER_OPTIONS, LOGO_OPTIONS, downsampleImage } from '../../utils/media.js';
import { uploadBrandingImage } from '../../utils/upload.js';

function brandingToForm(b) {
  return {
    name: b.name || '',
    tagline: b.tagline || '',
    bio: b.bio || '',
    openingHours: b.openingHours || '',
    phoneNumber: b.phoneNumber || '',
    whatsappNumber: b.whatsappNumber || '',
    instagramHandle: b.instagramHandle || '',
    facebookUrl: b.facebookUrl || '',
    websiteUrl: b.websiteUrl || '',
    tiktokHandle: b.tiktokHandle || '',
    googleMapsUrl: b.googleMapsUrl || '',
    themeAccent: b.themeAccent || 'amber',
    menuLayout: b.menuLayout || 'cards',
    currencyCode: b.currencyCode || 'USD',
    currencySymbol: b.currencySymbol || '$',
  };
}

/** Tab 3: logo/banner (direct to S3), socials, theme, layout, currency, with a live preview. */
export default function BrandCustomizer({ settings, onSettings, onOpenCategoryManager }) {
  const toast = useToast();
  const { updateRestaurant } = useAuth();
  const [form, setForm] = useState(() => brandingToForm(settings.branding));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const dirty = JSON.stringify(form) !== JSON.stringify(brandingToForm(settings.branding));
  const preview = { ...settings.branding, ...form };

  async function save(extra = {}) {
    setSaving(true);
    setError('');
    setFieldErrors({});
    try {
      const next = await api.updateSettings({ ...form, name: form.name.trim(), removeLogo: false, removeBanner: false, ...extra });
      onSettings(next);
      setForm(brandingToForm(next.branding));
      updateRestaurant({ name: next.branding.name });
      buzz(20);
      toast('Brand settings saved. The guest menu is updated.');
    } catch (e) {
      setError(e.message);
      if (e.fields) setFieldErrors(e.fields);
    } finally {
      setSaving(false);
    }
  }

  async function removeAsset(kind) {
    if (!window.confirm(`Remove the ${kind}?`)) return;
    await save(kind === 'logo' ? { removeLogo: true } : { removeBanner: true });
  }

  return (
    <div className="grid grid-cols-1 gap-8 xl:grid-cols-[minmax(0,1fr)_420px]">
      <div className="min-w-0 space-y-8">
        <Section title="Imagery" hint="Compressed on your device, then uploaded straight to S3.">
          <div className="grid grid-cols-[112px_minmax(0,1fr)] gap-4 sm:grid-cols-[180px_minmax(0,1fr)]">
            <AssetUpload
              kind="logo"
              label="Logo"
              url={settings.branding.logoUrl}
              options={LOGO_OPTIONS}
              aspect="aspect-square"
              onUploaded={(next) => {
                onSettings(next);
                toast('Logo updated');
              }}
              onRemove={() => removeAsset('logo')}
            />
            <AssetUpload
              kind="banner"
              label="Cover banner"
              url={settings.branding.bannerUrl}
              options={BANNER_OPTIONS}
              aspect="aspect-[16/7]"
              onUploaded={(next) => {
                onSettings(next);
                toast('Banner updated');
              }}
              onRemove={() => removeAsset('banner')}
            />
          </div>
        </Section>

        <Section title="Identity">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Restaurant name" error={fieldErrors.name}>
              <input className={inputClass} maxLength={120} value={form.name} onChange={(e) => set('name', e.target.value)} />
            </Field>
            <Field label="Tagline" error={fieldErrors.tagline} hint="One line under your name.">
              <input className={inputClass} maxLength={160} value={form.tagline} onChange={(e) => set('tagline', e.target.value)} placeholder="Modern French, wood-fired" />
            </Field>
          </div>
          <div className="mt-4 grid gap-4">
            <Field label="Bio" error={fieldErrors.bio}>
              <textarea
                className={cx(inputClass, 'h-28 resize-none py-3')}
                maxLength={2000}
                value={form.bio}
                onChange={(e) => set('bio', e.target.value)}
                placeholder="Tell guests about your kitchen, your sourcing and your story."
              />
            </Field>
            <Field label="Opening hours" error={fieldErrors.openingHours}>
              <input className={inputClass} maxLength={500} value={form.openingHours} onChange={(e) => set('openingHours', e.target.value)} placeholder="Tue–Sun · 12:00–15:00, 19:00–23:00" />
            </Field>
          </div>
        </Section>

        <Section title="Restaurant Profile & Socials" hint="Shown as a swipeable contact bar under your cover. Only filled-in links appear.">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Instagram" error={fieldErrors.instagramHandle}>
              <input className={inputClass} autoCapitalize="none" value={form.instagramHandle} onChange={(e) => set('instagramHandle', e.target.value)} placeholder="@bistro" />
            </Field>
            <Field label="TikTok" error={fieldErrors.tiktokHandle}>
              <input className={inputClass} autoCapitalize="none" value={form.tiktokHandle} onChange={(e) => set('tiktokHandle', e.target.value)} placeholder="@bistro" data-testid="tiktok-input" />
            </Field>
            <Field label="WhatsApp concierge" error={fieldErrors.whatsappNumber} hint="With country code. Opens wa.me for reservations.">
              <input className={inputClass} inputMode="tel" value={form.whatsappNumber} onChange={(e) => set('whatsappNumber', e.target.value)} placeholder="+92 300 1234567" />
            </Field>
            <Field label="Phone" error={fieldErrors.phoneNumber}>
              <input className={inputClass} inputMode="tel" value={form.phoneNumber} onChange={(e) => set('phoneNumber', e.target.value)} placeholder="+92 42 1234567" />
            </Field>
            <Field label="Directions (Google / Apple Maps link)" error={fieldErrors.googleMapsUrl} hint="In Maps, tap Share → Copy link.">
              <input className={inputClass} inputMode="url" autoCapitalize="none" value={form.googleMapsUrl} onChange={(e) => set('googleMapsUrl', e.target.value)} placeholder="https://maps.app.goo.gl/…" data-testid="maps-input" />
            </Field>
            <Field label="Facebook" error={fieldErrors.facebookUrl}>
              <input className={inputClass} inputMode="url" autoCapitalize="none" value={form.facebookUrl} onChange={(e) => set('facebookUrl', e.target.value)} placeholder="https://facebook.com/bistro" />
            </Field>
            <Field label="Website" error={fieldErrors.websiteUrl}>
              <input className={inputClass} inputMode="url" autoCapitalize="none" value={form.websiteUrl} onChange={(e) => set('websiteUrl', e.target.value)} placeholder="https://bistro.com" />
            </Field>
          </div>
        </Section>

        <Section title="Theme accent">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {Object.entries(ACCENTS).map(([key, a]) => (
              <button
                key={key}
                type="button"
                onClick={() => set('themeAccent', key)}
                aria-pressed={form.themeAccent === key}
                className={cx(
                  'tap relative flex flex-col items-start gap-3 rounded-2xl border p-3 text-left',
                  form.themeAccent === key ? 'border-zinc-300 bg-white/[0.04]' : 'hairline hover:border-zinc-600',
                )}
              >
                <span className="flex gap-1">
                  {[300, 500, 600].map((s) => (
                    <span key={s} className="h-7 w-7 rounded-full" style={{ background: `rgb(${a[s]})` }} />
                  ))}
                </span>
                <span className="text-[13px] font-semibold text-zinc-200">{a.label}</span>
                {form.themeAccent === key && <Check size={16} className="absolute right-3 top-3 text-zinc-100" aria-hidden="true" />}
              </button>
            ))}
          </div>
        </Section>

        <Section title="Menu layout & currency">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid grid-cols-2 gap-2">
              {[
                ['cards', 'Cards', 'Large photos', LayoutGrid],
                ['compact', 'Compact', 'List with thumbnails', Rows3],
              ].map(([key, label, hint, Icon]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => set('menuLayout', key)}
                  aria-pressed={form.menuLayout === key}
                  className={cx(
                    'tap flex min-h-20 flex-col items-center justify-center gap-1 rounded-2xl border px-2',
                    form.menuLayout === key ? 'border-amber-500/60 bg-amber-500/10 text-amber-200' : 'hairline text-zinc-400',
                  )}
                >
                  <Icon size={20} aria-hidden="true" />
                  <span className="text-sm font-semibold">{label}</span>
                  <span className="text-[10px] text-zinc-500">{hint}</span>
                </button>
              ))}
            </div>
            <Field label="Currency" error={fieldErrors.currencyCode} hint={`Preview: ${fmtPrice(24.5, form.currencyCode, form.currencySymbol)}`}>
              <LuxurySelect
                value={form.currencyCode}
                onChange={(code) => {
                  const c = CURRENCIES.find((x) => x.code === code);
                  if (c) setForm((f) => ({ ...f, currencyCode: c.code, currencySymbol: c.symbol }));
                }}
                options={CURRENCIES.map((c) => ({
                  value: c.code,
                  label: `${c.code} · ${c.label} (${c.symbol})`,
                }))}
                buttonClassName="h-12 rounded-2xl bg-black/40 border hairline"
                dropdownClassName="min-w-[16rem]"
              />
            </Field>
          </div>
        </Section>

        {/* Categories Manager inside Brand & Menu */}
        <Section title="Menu Categories" hint="Organize dishes into custom sections (e.g. Starters, Signature Cocktails, Desserts)">
          <div className="flex flex-col gap-3 rounded-2xl border hairline bg-black/40 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h4 className="text-sm font-semibold text-zinc-200">Custom Category Manager</h4>
              <p className="mt-0.5 text-xs text-zinc-500">Add, rename, or reorder the culinary sections of your live guest menu.</p>
            </div>
            <button
              type="button"
              onClick={onOpenCategoryManager}
              className="tap flex h-10 shrink-0 items-center gap-2 rounded-full border hairline bg-amber-500/10 px-4 text-xs font-semibold text-amber-300 hover:border-amber-500/50 hover:bg-amber-500/20"
            >
              <FolderPlus size={15} />
              <span>Manage Categories</span>
            </button>
          </div>
        </Section>

        {/* Security & Password */}
        <Section title="Account Security" hint="Update your restaurant studio credentials anytime">
          <div className="flex flex-col gap-3 rounded-2xl border hairline bg-black/40 p-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h4 className="text-sm font-semibold text-zinc-200">Studio Login Password</h4>
              <p className="mt-0.5 text-xs text-zinc-500">Change your owner account password securely whenever needed.</p>
            </div>
            <button
              type="button"
              onClick={() => setPasswordModalOpen(true)}
              className="tap flex h-10 shrink-0 items-center gap-2 rounded-full border hairline px-4 text-xs font-semibold text-zinc-300 hover:border-zinc-500 hover:text-zinc-100"
            >
              <KeyRound size={15} />
              <span>Change Password</span>
            </button>
          </div>
        </Section>

        {passwordModalOpen && (
          <StudioChangePasswordModal onClose={() => setPasswordModalOpen(false)} />
        )}

        {error && (
          <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
            <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
        <div className="pb-safe sticky bottom-0 z-20 -mx-4 border-t hairline bg-obsidian/90 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
          <button
            type="button"
            disabled={!dirty || saving || !form.name.trim()}
            onClick={() => save()}
            className={cx(
              'tap flex h-14 w-full items-center justify-center gap-2 rounded-2xl text-base font-bold sm:w-auto sm:px-8',
              dirty && form.name.trim() ? 'bg-gradient-to-b from-amber-400 to-amber-600 text-zinc-950' : 'bg-zinc-800 text-zinc-500',
            )}
          >
            {saving ? <LoaderCircle size={19} className="animate-spin" aria-hidden="true" /> : <Save size={19} aria-hidden="true" />}
            {saving ? 'Saving…' : dirty ? 'Save brand settings' : 'All changes saved'}
          </button>
        </div>
      </div>

      <aside className="min-w-0 xl:sticky xl:top-28 xl:self-start">
        <div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.28em] text-zinc-500">Live preview</div>
        <div className="overflow-hidden rounded-[32px] border hairline bg-obsidian" style={accentStyle(form.themeAccent)} data-testid="brand-preview">
          <MenuHero restaurant={preview} />
          <div className="px-5 pb-6">
            <div className="mb-3 flex gap-2">
              {['Starters', 'Mains', 'Drinks'].map((c, i) => (
                <span key={c} className={cx('rounded-full px-3 py-1.5 text-xs font-semibold', i === 0 ? 'bg-accent-500 text-zinc-950' : 'border hairline text-zinc-400')}>
                  {c}
                </span>
              ))}
            </div>
            <div className={cx(form.menuLayout === 'compact' ? 'space-y-2' : 'grid grid-cols-2 gap-2')}>
              {[0, 1].map((i) =>
                form.menuLayout === 'compact' ? (
                  <div key={i} className="flex items-center gap-3 rounded-2xl border hairline p-2">
                    <div className="h-12 w-12 rounded-xl bg-zinc-800" />
                    <div className="flex-1">
                      <div className="h-2.5 w-24 rounded bg-zinc-700" />
                      <div className="mt-2 h-2 w-16 rounded bg-zinc-800" />
                    </div>
                    <span className="font-mono text-xs text-accent-300">{fmtPrice(18 + i * 6, form.currencyCode, form.currencySymbol)}</span>
                  </div>
                ) : (
                  <div key={i} className="overflow-hidden rounded-2xl border hairline">
                    <div className="relative aspect-[4/3] bg-zinc-800">
                      <span className="absolute bottom-2 right-2 rounded-full bg-black/70 px-2 py-0.5 font-mono text-[11px] text-accent-300">
                        {fmtPrice(18 + i * 6, form.currencyCode, form.currencySymbol)}
                      </span>
                    </div>
                    <div className="p-2">
                      <div className="h-2.5 w-20 rounded bg-zinc-700" />
                    </div>
                  </div>
                ),
              )}
            </div>
          </div>
        </div>
      </aside>
    </div>
  );
}

function Section({ title, hint, children }) {
  return (
    <section>
      <div className="mb-3">
        <h3 className="font-display text-2xl font-semibold text-zinc-100">{title}</h3>
        {hint && <p className="text-xs text-zinc-500">{hint}</p>}
      </div>
      {children}
    </section>
  );
}

function AssetUpload({ kind, label, url, options, aspect, onUploaded, onRemove }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState('');

  async function pick(file) {
    if (!file) return;
    setBusy(true);
    setError('');
    setProgress(0);
    try {
      const image = await downsampleImage(file, options);
      URL.revokeObjectURL(image.url);
      const next = await uploadBrandingImage(kind, { ...image, name: file.name }, setProgress);
      onUploaded(next);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <input
        ref={input}
        type="file"
        accept="image/*"
        className="hidden"
        data-testid={`${kind}-input`}
        onChange={(e) => {
          pick(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
      <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">{label}</span>
      <button
        type="button"
        disabled={busy}
        onClick={() => input.current?.click()}
        className={cx('tap group relative w-full overflow-hidden rounded-2xl border border-dashed border-zinc-700 bg-black/30 hover:border-amber-500/50', aspect)}
      >
        {url && <img src={url} alt="" className="absolute inset-0 h-full w-full object-cover" />}
        <span
          className={cx(
            'absolute inset-0 flex flex-col items-center justify-center gap-1 text-xs font-semibold',
            url ? 'bg-black/55 text-zinc-100 opacity-0 transition-opacity group-hover:opacity-100' : 'text-zinc-400',
            busy && 'opacity-100',
          )}
        >
          {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <ImagePlus size={20} aria-hidden="true" />}
          {busy ? `Uploading ${Math.round(progress * 100)}%` : url ? 'Replace' : `Upload ${label.toLowerCase()}`}
        </span>
      </button>
      {url && !busy && (
        <button type="button" onClick={onRemove} className="mt-1.5 flex items-center gap-1 text-xs text-zinc-500 hover:text-red-300">
          <Trash2 size={12} aria-hidden="true" /> Remove
        </button>
      )}
      {error && <p className="mt-1.5 text-xs text-red-300">{error}</p>}
    </div>
  );
}
