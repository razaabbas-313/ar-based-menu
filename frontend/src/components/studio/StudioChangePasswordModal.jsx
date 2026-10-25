import { useState } from 'react';
import { KeyRound, LoaderCircle, Lock, ShieldCheck, TriangleAlert, X } from 'lucide-react';
import { IconButton, Overlay } from '../ui.jsx';
import { api } from '../../api/client.js';
import { buzz } from '../../utils/format.js';
import { useToast } from '../Toast.jsx';

export default function StudioChangePasswordModal({ onClose }) {
  const toast = useToast();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!currentPassword) {
      setError('Please enter your current password');
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      setError('New password must be at least 8 characters');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match');
      return;
    }

    setBusy(true);
    setError('');
    buzz(15);
    try {
      await api.changePassword({
        currentPassword,
        newPassword,
      });
      buzz(25);
      toast('Your password has been changed successfully!');
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to update password');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Overlay onClose={busy ? undefined : onClose} align="center" label="Change studio password">
      <div className="relative w-full max-w-md overflow-hidden rounded-3xl border hairline bg-onyx p-6 shadow-2xl">
        <div className="flex items-center justify-between pb-4 border-b hairline">
          <div className="flex items-center gap-2.5">
            <div className="grid h-9 w-9 place-items-center rounded-xl bg-amber-500/10 text-amber-300">
              <KeyRound size={18} />
            </div>
            <div>
              <h3 className="text-base font-semibold text-zinc-100">Change Password</h3>
              <p className="text-xs text-zinc-500">Update your restaurant studio credentials</p>
            </div>
          </div>
          <IconButton icon={X} label="Close" onClick={() => !busy && onClose()} />
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          {error && (
            <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-200" role="alert">
              <TriangleAlert size={15} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
              Current Password
            </label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              placeholder="••••••••••••"
              disabled={busy}
              className="h-11 w-full rounded-xl border hairline bg-black/40 px-3.5 text-sm text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
              New Password (min. 8 characters)
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="••••••••••••"
              disabled={busy}
              className="h-11 w-full rounded-xl border hairline bg-black/40 px-3.5 text-sm text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
            />
          </div>

          <div>
            <label className="mb-1.5 block text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
              Confirm New Password
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="••••••••••••"
              disabled={busy}
              className="h-11 w-full rounded-xl border hairline bg-black/40 px-3.5 text-sm text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none"
            />
          </div>

          <div className="pt-2 flex items-center justify-end gap-2.5">
            <button
              type="button"
              disabled={busy}
              onClick={onClose}
              className="tap rounded-full border hairline px-4 py-2 text-xs font-semibold text-zinc-400 hover:text-zinc-200"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={busy}
              className="tap flex items-center gap-2 rounded-full bg-amber-500 px-5 py-2 text-xs font-bold text-zinc-950 hover:bg-amber-400 disabled:opacity-50"
            >
              {busy ? <LoaderCircle size={14} className="animate-spin" /> : <ShieldCheck size={14} />}
              <span>{busy ? 'Updating…' : 'Save Password'}</span>
            </button>
          </div>
        </form>
      </div>
    </Overlay>
  );
}
