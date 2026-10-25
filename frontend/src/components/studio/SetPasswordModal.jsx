import { useMemo, useState } from 'react';
import { Check, Eye, EyeOff, LoaderCircle, LockKeyhole, LogOut, ShieldCheck, TriangleAlert } from 'lucide-react';
import { Overlay, inputClass, primaryButtonClass } from '../ui.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { useToast } from '../Toast.jsx';
import { buzz, cx } from '../../utils/format.js';

const RULES = [
  { key: 'length', label: 'At least 8 characters', test: (p) => p.length >= 8 },
  { key: 'mixed', label: 'Upper- and lower-case letters', test: (p) => /[a-z]/.test(p) && /[A-Z]/.test(p) },
  { key: 'digit', label: 'A number or symbol', test: (p) => /[\d\W_]/.test(p) },
];

/**
 * Un-dismissible "Set your new password" step after signing in with the temporary password the
 * Atelier team issued. The studio stays locked (server-side too) until it succeeds.
 */
export default function SetPasswordModal() {
  const { restaurant, changePassword, logout } = useAuth();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  const passed = useMemo(() => RULES.filter((r) => r.test(next)).length, [next]);
  const mismatch = repeat.length > 0 && repeat !== next;
  const valid = current.length > 0 && next.length >= 8 && next === repeat && next !== current;

  async function submit(e) {
    e.preventDefault();
    if (!valid) return;
    setBusy(true);
    setError('');
    setFieldErrors({});
    try {
      await changePassword(current, next);
      buzz(30);
      toast('Password set. Welcome to your studio.');
    } catch (err) {
      setError(err.message);
      if (err.fields) setFieldErrors(err.fields);
    } finally {
      setBusy(false);
    }
  }

  const type = show ? 'text' : 'password';
  return (
    <Overlay label="Set your new password" z="z-[70]">
      <form
        onSubmit={submit}
        className="sheet pb-safe relative z-10 m-4 w-full max-w-md overflow-hidden rounded-[30px] border border-amber-500/25 bg-onyx shadow-glow"
        noValidate
        data-testid="set-password-modal"
      >
        <div className="border-b hairline bg-gradient-to-b from-amber-500/10 to-transparent px-6 pb-5 pt-6">
          <div className="grid h-12 w-12 place-items-center rounded-2xl border border-amber-500/40 bg-black/40">
            <LockKeyhole size={22} className="text-amber-300" aria-hidden="true" />
          </div>
          <h2 className="mt-4 font-display text-3xl font-semibold leading-tight">Set your new password</h2>
          <p className="mt-1 text-sm leading-relaxed text-zinc-400">
            {restaurant?.name ? `${restaurant.name} was set up with a temporary password.` : 'Your account uses a temporary password.'} Choose your own to open the studio.
          </p>
        </div>
        <div className="space-y-4 px-6 py-5">
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Temporary password</span>
            <input className={inputClass} type={type} autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} placeholder="Temp-…" />
            {fieldErrors.currentPassword && <span className="mt-1.5 block text-xs text-red-300">{fieldErrors.currentPassword}</span>}
          </label>
          <label className="block">
            <span className="mb-1.5 flex items-center justify-between text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">
              New password
              <button type="button" onClick={() => setShow((v) => !v)} className="flex items-center gap-1 normal-case tracking-normal text-zinc-400 hover:text-zinc-200">
                {show ? <EyeOff size={13} aria-hidden="true" /> : <Eye size={13} aria-hidden="true" />} {show ? 'Hide' : 'Show'}
              </button>
            </span>
            <input className={inputClass} type={type} autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
            {fieldErrors.newPassword && <span className="mt-1.5 block text-xs text-red-300">{fieldErrors.newPassword}</span>}
          </label>
          <div className="flex gap-1" aria-hidden="true">
            {RULES.map((r, i) => (
              <span key={r.key} className={cx('h-1 flex-1 rounded-full', i < passed ? (passed === 3 ? 'bg-emerald-400' : 'bg-amber-400') : 'bg-zinc-800')} />
            ))}
          </div>
          <ul className="space-y-1 text-xs">
            {RULES.map((r) => (
              <li key={r.key} className={cx('flex items-center gap-2', r.test(next) ? 'text-emerald-300' : 'text-zinc-500')}>
                <Check size={13} aria-hidden="true" /> {r.label}
              </li>
            ))}
          </ul>
          <label className="block">
            <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Confirm new password</span>
            <input className={inputClass} type={type} autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
            {mismatch && <span className="mt-1.5 block text-xs text-red-300">Passwords do not match.</span>}
            {!mismatch && next && next === current && <span className="mt-1.5 block text-xs text-red-300">Choose something different from the temporary password.</span>}
          </label>
          {error && !fieldErrors.currentPassword && !fieldErrors.newPassword && (
            <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
              <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}
          <button type="submit" disabled={!valid || busy} className={primaryButtonClass}>
            {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <ShieldCheck size={19} aria-hidden="true" />}
            {busy ? 'Securing your account…' : 'Save password & open studio'}
          </button>
          <button type="button" onClick={logout} className="mx-auto flex items-center gap-1.5 text-xs text-zinc-500 hover:text-zinc-300">
            <LogOut size={13} aria-hidden="true" /> Sign out
          </button>
        </div>
      </form>
    </Overlay>
  );
}
