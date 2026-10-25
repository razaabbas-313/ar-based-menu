import { useEffect, useMemo, useState } from 'react';
import { Info, X } from 'lucide-react';
import { IconButton, Overlay } from './ui.jsx';

/**
 * Desktop / non-AR fallback: a QR code that opens this exact dish on a phone, where
 * "View in Your Space" launches Quick Look (iOS) or WebXR / Scene Viewer (Android).
 */
export default function ArHandoffModal({ item, slug, onClose }) {
  const [qr, setQr] = useState(null);
  const url = useMemo(() => {
    const u = new URL(`/r/${encodeURIComponent(slug)}`, window.location.origin);
    u.searchParams.set('dish', String(item.id));
    return u.toString();
  }, [item, slug]);

  const isLocal = ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname);
  const insecure = window.location.protocol !== 'https:' && !isLocal;

  useEffect(() => {
    let alive = true;
    import('qrcode')
      .then((mod) => (mod.default || mod).toDataURL(url, { margin: 1, width: 480, color: { dark: '#09090b', light: '#fafaf9' } }))
      .then((data) => alive && setQr(data))
      .catch(() => alive && setQr(''));
    return () => {
      alive = false;
    };
  }, [url]);

  return (
    <Overlay onClose={onClose} align="bottom" z="z-[60]" label="View in augmented reality">
      <div className="sheet pb-safe relative z-10 w-full max-w-md rounded-t-[28px] border hairline bg-onyx p-6 sm:rounded-[28px]">
        <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-zinc-700 sm:hidden" />
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">Augmented reality</div>
            <h3 className="mt-1 font-display text-3xl font-semibold leading-tight">Place it on your table</h3>
          </div>
          <IconButton icon={X} label="Close" onClick={onClose} />
        </div>
        <p className="mt-2 text-sm leading-relaxed text-zinc-400">
          AR opens natively on phones: Quick Look on iPhone and iPad, WebXR or Scene Viewer on ARCore Android. Scan with
          your camera to open <span className="text-zinc-200">{item.dishTitle}</span>, then tap “View in Your Space”.
        </p>
        <div className="mt-5 grid place-items-center rounded-3xl border hairline bg-zinc-50 p-4">
          {qr ? (
            <img src={qr} alt={`QR code to open ${item.dishTitle} on a phone`} className="h-56 w-56" />
          ) : qr === '' ? (
            <div className="grid h-56 w-56 place-items-center text-center text-xs text-zinc-600">
              QR code unavailable.
              <br />
              Open this page on your phone.
            </div>
          ) : (
            <div className="h-56 w-56 animate-pulse rounded-xl bg-zinc-200" />
          )}
        </div>
        <div className="mt-3 break-all text-center font-mono text-[10px] text-zinc-500">{url}</div>
        {(isLocal || insecure) && (
          <div className="mt-4 flex gap-2 rounded-2xl border border-amber-500/25 bg-amber-500/10 p-3 text-xs leading-relaxed text-amber-200">
            <Info size={16} className="mt-0.5 shrink-0" aria-hidden="true" />
            <span>
              {isLocal
                ? 'This page is on localhost, which a phone cannot open. Use your HTTPS deployment (or an HTTPS tunnel) so the QR code works.'
                : 'AR needs HTTPS. Serve this site over HTTPS so phones can launch AR.'}
            </span>
          </div>
        )}
      </div>
    </Overlay>
  );
}
