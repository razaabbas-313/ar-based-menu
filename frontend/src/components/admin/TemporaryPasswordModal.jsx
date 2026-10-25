import { useState } from 'react';
import { Check, Copy, KeyRound, Mail } from 'lucide-react';
import { Overlay, primaryButtonClass } from '../ui.jsx';
import { menuUrl } from '../../utils/qrCard.js';

/**
 * Shows a freshly generated temporary password exactly once, with a ready-to-send welcome note.
 * The owner must replace it on first sign-in.
 */
export default function TemporaryPasswordModal({ title, restaurant, password, onDone }) {
  const [copied, setCopied] = useState('');
  const studio = `${window.location.origin}/studio`;
  const message = `Welcome to Atelier, ${restaurant.ownerName}!

Your restaurant studio for ${restaurant.name} is ready.

Sign in: ${studio}
Email: ${restaurant.email}
Temporary password: ${password}

You will be asked to choose your own password right after signing in.
Your guest menu: ${menuUrl(restaurant.slug)}`;

  async function copy(kind, text) {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(kind);
      setTimeout(() => setCopied(''), 2000);
    } catch {
      setCopied('');
    }
  }

  const mailto = `mailto:${encodeURIComponent(restaurant.email)}?subject=${encodeURIComponent(`Your Atelier studio for ${restaurant.name}`)}&body=${encodeURIComponent(message)}`;

  return (
    <Overlay align="bottom" z="z-[70]" label={title}>
      <div className="sheet pb-safe relative z-10 w-full max-w-md rounded-t-[30px] border hairline bg-onyx p-6 sm:rounded-[30px]" data-testid="temp-password-modal">
        <div className="grid h-12 w-12 place-items-center rounded-full bg-emerald-500/15 text-emerald-300">
          <KeyRound size={22} aria-hidden="true" />
        </div>
        <h2 className="mt-4 font-display text-3xl font-semibold">{title}</h2>
        <p className="mt-2 text-sm text-zinc-400">
          Send these details to {restaurant.ownerName}. The password is shown <span className="text-zinc-200">only once</span> and must be changed at first sign-in.
        </p>
        <div className="mt-4 space-y-2 rounded-2xl border hairline bg-black/40 p-4 font-mono text-sm">
          <div className="flex justify-between gap-3">
            <span className="text-zinc-500">Email</span>
            <span className="truncate text-zinc-200">{restaurant.email}</span>
          </div>
          <div className="flex items-center justify-between gap-3">
            <span className="text-zinc-500">Password</span>
            <span className="flex items-center gap-2">
              <span className="text-amber-200" data-testid="temp-password">{password}</span>
              <button type="button" onClick={() => copy('pw', password)} className="text-zinc-400 hover:text-zinc-100" aria-label="Copy password">
                {copied === 'pw' ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
              </button>
            </span>
          </div>
          <div className="flex justify-between gap-3">
            <span className="text-zinc-500">Menu</span>
            <span className="truncate text-zinc-300">/r/{restaurant.slug}</span>
          </div>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-2">
          <button type="button" onClick={() => copy('msg', message)} className="tap flex h-11 items-center justify-center gap-2 rounded-full border hairline text-sm text-zinc-200">
            {copied === 'msg' ? <Check size={15} aria-hidden="true" /> : <Copy size={15} aria-hidden="true" />} {copied === 'msg' ? 'Copied' : 'Copy welcome note'}
          </button>
          <a href={mailto} className="tap flex h-11 items-center justify-center gap-2 rounded-full border hairline text-sm text-zinc-200">
            <Mail size={15} aria-hidden="true" /> Email owner
          </a>
        </div>
        <button type="button" onClick={onDone} className={`${primaryButtonClass} mt-5`}>
          I have sent the details
        </button>
      </div>
    </Overlay>
  );
}
