import { useState } from 'react';
import { KeyRound, LoaderCircle, TriangleAlert, X } from 'lucide-react';
import { Field, IconButton, Overlay, inputClass, primaryButtonClass } from '../ui.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import { useToast } from '../Toast.jsx';
import { api } from '../../api/client.js';

export default function ChangePasswordModal({ onClose }) {
  const { updateAdminProfile } = useAuth();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const mismatch = repeat.length > 0 && next !== repeat;
  const valid = current && next.length >= 10 && next === repeat;

  async function submit(e) {
    e.preventDefault();
    if (!valid) return;
    setBusy(true);
    setError('');
    try {
      const profile = await api.adminChangePassword({ currentPassword: current, newPassword: next });
      updateAdminProfile(profile);
      toast('Password changed');
      onClose();
    } catch (err) {
      setError(err.fields?.newPassword ? `New password ${err.fields.newPassword}` : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Overlay onClose={busy ? undefined : onClose} align="bottom" label="Change password">
      <form onSubmit={submit} className="sheet pb-safe relative z-10 w-full max-w-md rounded-t-[30px] border hairline bg-onyx p-5 sm:rounded-[30px]" noValidate>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-display text-3xl font-semibold">Change password</h2>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>
        <div className="space-y-4">
          <Field label="Current password">
            <input className={inputClass} type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
          </Field>
          <Field label="New password" hint="At least 10 characters.">
            <input className={inputClass} type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
          </Field>
          <Field label="Repeat new password" error={mismatch ? 'Passwords do not match' : null}>
            <input className={inputClass} type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
          </Field>
        </div>
        {error && (
          <div className="mt-4 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
            <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
        <button type="submit" disabled={!valid || busy} className={`${primaryButtonClass} mt-5`}>
          {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <KeyRound size={18} aria-hidden="true" />}
          Update password
        </button>
      </form>
    </Overlay>
  );
}
