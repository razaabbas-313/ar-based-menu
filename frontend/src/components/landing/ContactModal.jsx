import { useState } from 'react';
import { CalendarCheck, Check, Copy, Mail, X } from 'lucide-react';
import { Field, IconButton, Overlay, inputClass, primaryButtonClass } from '../ui.jsx';

/** Sales inbox for demo requests (set VITE_SALES_EMAIL); without it the mail app opens with the note ready. */
export const SALES_EMAIL = import.meta.env.VITE_SALES_EMAIL || '';

/** "Book a Demo / Onboard Your Restaurant": composes a ready-to-send email (no backend needed). */
export default function ContactModal({ onClose }) {
  const [form, setForm] = useState({ name: '', restaurant: '', city: '', email: '', phone: '', covers: '', message: '' });
  const [copied, setCopied] = useState(false);
  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const valid = form.name.trim() && form.restaurant.trim() && /.+@.+\..+/.test(form.email.trim());

  const body = `Hello Atelier team,

I'd like a demo and to onboard our restaurant.

Name: ${form.name}
Restaurant / hotel: ${form.restaurant}
City: ${form.city}
Email: ${form.email}
Phone: ${form.phone}
Covers per night: ${form.covers}

${form.message}`.trim();
  const subject = `Demo request: ${form.restaurant || 'restaurant'}`;
  const mailto = `mailto:${SALES_EMAIL}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(`${subject}\n\n${body}`);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Overlay onClose={onClose} align="bottom" label="Book a demo">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (valid) window.location.href = mailto;
        }}
        className="sheet pb-safe relative z-10 flex max-h-[94vh] w-full max-w-lg flex-col overflow-hidden rounded-t-[30px] border hairline bg-onyx sm:rounded-[30px]"
        noValidate
        data-testid="contact-modal"
      >
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">Book a demo</div>
            <h2 className="font-display text-3xl font-semibold leading-tight">Onboard your restaurant</h2>
          </div>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>
        <div className="flex-1 space-y-4 overflow-y-auto px-5 py-5">
          <p className="text-sm leading-relaxed text-zinc-400">
            Tell us about your venue. We set up your private studio, scan or import your signature dishes and ship your QR stands.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Your name">
              <input className={inputClass} value={form.name} onChange={(e) => set('name', e.target.value)} autoComplete="name" />
            </Field>
            <Field label="Restaurant / hotel">
              <input className={inputClass} value={form.restaurant} onChange={(e) => set('restaurant', e.target.value)} autoComplete="organization" />
            </Field>
            <Field label="Email">
              <input className={inputClass} type="email" inputMode="email" value={form.email} onChange={(e) => set('email', e.target.value)} autoComplete="email" />
            </Field>
            <Field label="Phone (optional)">
              <input className={inputClass} inputMode="tel" value={form.phone} onChange={(e) => set('phone', e.target.value)} autoComplete="tel" />
            </Field>
            <Field label="City">
              <input className={inputClass} value={form.city} onChange={(e) => set('city', e.target.value)} autoComplete="address-level2" />
            </Field>
            <Field label="Covers per night">
              <input className={inputClass} inputMode="numeric" value={form.covers} onChange={(e) => set('covers', e.target.value)} placeholder="e.g. 120" />
            </Field>
          </div>
          <Field label="Anything we should know?">
            <textarea className={`${inputClass} h-24 resize-none py-3`} value={form.message} onChange={(e) => set('message', e.target.value)} placeholder="Tasting menu, cocktail program, number of outlets…" />
          </Field>
        </div>
        <div className="space-y-2 border-t hairline bg-black/30 px-5 py-4">
          <button type="submit" disabled={!valid} className={primaryButtonClass}>
            <CalendarCheck size={19} aria-hidden="true" /> Request my demo
          </button>
          <div className="grid grid-cols-2 gap-2">
            <button type="button" onClick={copy} disabled={!valid} className="tap flex h-11 items-center justify-center gap-2 rounded-full border hairline text-sm text-zinc-300 disabled:opacity-40">
              {copied ? <Check size={15} aria-hidden="true" /> : <Copy size={15} aria-hidden="true" />} {copied ? 'Copied' : 'Copy details'}
            </button>
            <a
              href={SALES_EMAIL ? `mailto:${SALES_EMAIL}` : 'mailto:'}
              className="tap flex h-11 items-center justify-center gap-2 rounded-full border hairline text-sm text-zinc-300"
            >
              <Mail size={15} aria-hidden="true" /> {SALES_EMAIL || 'Open mail app'}
            </a>
          </div>
        </div>
      </form>
    </Overlay>
  );
}
