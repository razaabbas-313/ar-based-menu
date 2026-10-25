import { useState } from 'react';
import { LoaderCircle, LockKeyhole, ShieldCheck, TriangleAlert } from 'lucide-react';
import { Field, inputClass, primaryButtonClass } from '../ui.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { buzz } from '../../utils/format.js';

/** Super-admin sign-in (separate session from any restaurant studio session). */
export default function AdminLogin() {
  const { adminLogin } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await adminLogin(email.trim(), password);
      buzz(20);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-screen place-items-center px-4 py-12">
      <form onSubmit={submit} className="w-full max-w-md rounded-[32px] border hairline bg-onyx/80 p-6 shadow-glow backdrop-blur sm:p-8" noValidate>
        <div className="grid h-14 w-14 place-items-center rounded-2xl border border-amber-500/40 bg-black/40">
          <ShieldCheck size={26} className="text-amber-300" aria-hidden="true" />
        </div>
        <div className="mt-5 font-mono text-[10px] uppercase tracking-[0.3em] text-amber-500/80">Atelier platform</div>
        <h1 className="mt-1 font-display text-4xl font-semibold">Super admin console</h1>
        <p className="mt-2 text-sm text-zinc-400">Restricted to platform operators. Every action is written to the audit log.</p>
        <div className="mt-6 space-y-4">
          <Field label="Email">
            <input className={inputClass} type="email" autoComplete="username" inputMode="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="admin@atelier.local" />
          </Field>
          <Field label="Password">
            <input className={inputClass} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••••" />
          </Field>
        </div>
        {error && (
          <div className="mt-4 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
            <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
        <button type="submit" disabled={busy || !email || !password} className={`${primaryButtonClass} mt-6`}>
          {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <LockKeyhole size={18} aria-hidden="true" />}
          {busy ? 'Verifying…' : 'Enter console'}
        </button>
        <p className="mt-4 text-center text-xs text-zinc-500">
          First run? The initial password is printed once in the API log, or set ATELIER_SUPERADMIN_PASSWORD.
        </p>
      </form>
    </div>
  );
}
