import { useEffect, useState } from 'react';
import { ArrowLeft, Check, KeyRound, LoaderCircle, LogIn, Mail, ShieldCheck, TriangleAlert, X } from 'lucide-react';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from './Toast.jsx';
import { Field, IconButton, Overlay, inputClass, primaryButtonClass } from './ui.jsx';
import { api } from '../api/client.js';
import { buzz, cx } from '../utils/format.js';
import { navigate } from '../utils/router.js';

/**
 * Staff Portal sign-in & Self-Service OTP Password Recovery.
 */
export default function AuthModal({ onClose }) {
  const { login } = useAuth();
  const toast = useToast();

  const [mode, setMode] = useState('login'); // 'login' | 'forgot_request' | 'forgot_verify'
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [passwordResetEnabled, setPasswordResetEnabled] = useState(
    import.meta.env.VITE_PASSWORD_RESET_ENABLED === 'true'
  );

  useEffect(() => {
    api.authConfig().then((cfg) => {
      if (cfg && typeof cfg.passwordResetEnabled === 'boolean') {
        setPasswordResetEnabled(cfg.passwordResetEnabled);
      }
    }).catch(() => {});
  }, []);

  // 1. Normal Sign In
  async function submitLogin(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const session = await login(email.trim(), password);
      buzz(20);
      if (!session.mustChangePassword) toast(`Welcome back to ${session.restaurant.name}`);
      onClose();
      navigate('/studio');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  // 2. Request OTP
  async function submitForgotRequest(e) {
    e.preventDefault();
    const cleanEmail = email.trim();
    if (!cleanEmail) {
      setError('Please enter your email address');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api.forgotPassword({ email: cleanEmail });
      buzz(15);
      toast('Verification code sent! Please check your email.');
      setMode('forgot_verify');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  // 3. Verify OTP & Reset Password
  async function submitResetOtp(e) {
    e.preventDefault();
    const cleanEmail = email.trim();
    const cleanOtp = otp.trim();
    if (!cleanOtp || cleanOtp.length < 6) {
      setError('Please enter the full 6-digit code');
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      setError('New password must be at least 8 characters');
      return;
    }
    setBusy(true);
    setError('');
    try {
      await api.resetPasswordOtp({
        email: cleanEmail,
        otp: cleanOtp,
        newPassword,
      });
      buzz(25);
      toast('Password reset successfully! You can now sign in with your new password.');
      setPassword('');
      setOtp('');
      setNewPassword('');
      setMode('login');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Overlay onClose={onClose} align="bottom" label="Staff portal sign in">
      <div className="sheet pb-safe relative z-10 flex max-h-[94vh] w-full max-w-md flex-col overflow-hidden rounded-t-[30px] border hairline bg-onyx sm:rounded-[30px]">
        {/* Header */}
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">
              Staff Portal
            </div>
            <h2 className="font-display text-[26px] font-semibold leading-tight">
              {mode === 'login' && 'Welcome back'}
              {mode === 'forgot_request' && 'Reset Password'}
              {mode === 'forgot_verify' && 'Enter Verification Code'}
            </h2>
          </div>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>

        {/* Mode: Normal Login */}
        {mode === 'login' && (
          <form onSubmit={submitLogin} className="space-y-4 overflow-y-auto px-5 pb-6 pt-5" noValidate>
            <Field label="Email">
              <input
                className={inputClass}
                type="email"
                autoComplete="email"
                inputMode="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="chef@your-restaurant.com"
              />
            </Field>

            <div>
              <div className="mb-1 flex items-center justify-between">
                <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400">Password</span>
                {passwordResetEnabled && (
                  <button
                    type="button"
                    onClick={() => {
                      setError('');
                      setMode('forgot_request');
                    }}
                    className="tap text-xs font-semibold text-amber-400 hover:text-amber-300"
                  >
                    Forgot password?
                  </button>
                )}
              </div>
              <input
                className={inputClass}
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </div>

            {error && (
              <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
                <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" disabled={busy || !email || !password} className={primaryButtonClass}>
              {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <LogIn size={19} aria-hidden="true" />}
              {busy ? 'Signing in…' : 'Sign in to studio'}
            </button>

            <p className="flex gap-2 rounded-2xl border hairline bg-black/30 p-3 text-xs leading-relaxed text-zinc-400">
              <KeyRound size={15} className="mt-0.5 shrink-0 text-amber-300" aria-hidden="true" />
              Accounts are provisioned by the Atelier team. First time here? Use the temporary password you received; you will choose your own right after signing in.
            </p>
          </form>
        )}

        {/* Mode: Forgot Password - Request OTP */}
        {mode === 'forgot_request' && (
          <form onSubmit={submitForgotRequest} className="space-y-4 overflow-y-auto px-5 pb-6 pt-5" noValidate>
            <p className="text-xs leading-relaxed text-zinc-400">
              Enter your registered restaurant email address. We will send you a 6-digit verification code to reset your password.
            </p>

            <Field label="Registered Email">
              <input
                className={inputClass}
                type="email"
                autoComplete="email"
                inputMode="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="chef@your-restaurant.com"
                autoFocus
              />
            </Field>

            {error && (
              <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
                <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </div>
            )}

            <button type="submit" disabled={busy || !email} className={primaryButtonClass}>
              {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <Mail size={19} aria-hidden="true" />}
              {busy ? 'Sending verification code…' : 'Send Verification Code'}
            </button>

            <div className="flex justify-center pt-2">
              <button
                type="button"
                onClick={() => {
                  setError('');
                  setMode('login');
                }}
                className="tap flex items-center gap-1.5 text-xs font-semibold text-zinc-400 hover:text-zinc-200"
              >
                <ArrowLeft size={14} /> Back to sign in
              </button>
            </div>
          </form>
        )}

        {/* Mode: Forgot Password - Verify OTP & Set New Password */}
        {mode === 'forgot_verify' && (
          <form onSubmit={submitResetOtp} className="space-y-4 overflow-y-auto px-5 pb-6 pt-5" noValidate>
            <p className="text-xs leading-relaxed text-zinc-400">
              We sent a 6-digit verification code to <span className="font-semibold text-zinc-200">{email}</span>. Valid for 10 minutes.
            </p>

            <Field label="6-Digit Verification Code">
              <input
                className={cx(inputClass, 'font-mono text-center tracking-[0.3em] text-lg font-bold text-amber-300')}
                type="text"
                inputMode="numeric"
                maxLength={6}
                required
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/[^0-9]/g, ''))}
                placeholder="123456"
                autoFocus
              />
            </Field>

            <Field label="New Password (min 8 characters)">
              <input
                className={inputClass}
                type="password"
                required
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Choose a strong password"
              />
            </Field>

            {error && (
              <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
                <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
                <span>{error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={busy || otp.length < 6 || newPassword.length < 8}
              className={primaryButtonClass}
            >
              {busy ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : <ShieldCheck size={19} aria-hidden="true" />}
              {busy ? 'Updating password…' : 'Reset Password'}
            </button>

            <div className="flex items-center justify-between pt-2 text-xs">
              <button
                type="button"
                onClick={() => {
                  setError('');
                  setMode('login');
                }}
                className="tap flex items-center gap-1 text-zinc-400 hover:text-zinc-200"
              >
                <ArrowLeft size={13} /> Back to sign in
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={submitForgotRequest}
                className="tap font-semibold text-amber-400 hover:text-amber-300"
              >
                Resend code
              </button>
            </div>
          </form>
        )}
      </div>
    </Overlay>
  );
}
