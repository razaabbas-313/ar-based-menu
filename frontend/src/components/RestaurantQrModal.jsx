import { useEffect, useMemo, useState } from 'react';
import { Check, Copy, Download, FileImage, LoaderCircle, Printer, QrCode, TriangleAlert, X } from 'lucide-react';
import { IconButton, Overlay } from './ui.jsx';
import { useToast } from './Toast.jsx';
import { buzz, cx, slugify } from '../utils/format.js';
import {
  QR_FORMATS,
  QR_THEMES,
  buildQrMatrix,
  downloadBlob,
  embeddedFontCss,
  menuUrl,
  printCard,
  qrCardSvg,
  svgToPng,
} from '../utils/qrCard.js';

/**
 * One branded QR for the whole venue (not per table): live preview, Dark Onyx & Gold or Crisp
 * White themes, and exports (PNG 2000×2000, vector SVG, print-ready 4×6" / A6 PDF).
 */
export default function RestaurantQrModal({ restaurant, onClose }) {
  const toast = useToast();
  const url = useMemo(() => menuUrl(restaurant.slug), [restaurant.slug]);
  const [matrix, setMatrix] = useState(null);
  const [error, setError] = useState('');
  const [theme, setTheme] = useState('onyx');
  const [format, setFormat] = useState('4x6');
  const [busy, setBusy] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let alive = true;
    buildQrMatrix(url)
      .then((m) => alive && setMatrix(m))
      .catch(() => alive && setError('The QR generator could not load. Check your connection and try again.'));
    return () => {
      alive = false;
    };
  }, [url]);

  const preview = useMemo(
    () => (matrix ? qrCardSvg({ matrix, name: restaurant.name, url, theme, format }) : ''),
    [matrix, restaurant.name, url, theme, format],
  );
  const fileBase = `${slugify(restaurant.name) || restaurant.slug}-menu-qr-${theme}`;

  async function exportSvg(physical, targetFormat = format) {
    const fontCss = await embeddedFontCss(restaurant.name);
    return qrCardSvg({ matrix, name: restaurant.name, url, theme, format: targetFormat, fontCss, physicalSize: physical });
  }

  async function downloadPng() {
    setBusy('png');
    try {
      const svg = await exportSvg(false, 'square');
      downloadBlob(await svgToPng(svg, 2000, 2000), `${fileBase}-2000.png`);
      toast('PNG downloaded (2000 × 2000 px)');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setBusy('');
    }
  }

  async function downloadSvg() {
    setBusy('svg');
    try {
      const svg = await exportSvg(true);
      downloadBlob(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }), `${fileBase}-${format}.svg`);
      toast('Vector SVG downloaded');
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setBusy('');
    }
  }

  async function print() {
    // Opened inside the click so pop-up blockers allow it; filled once the fonts are embedded.
    const win = window.open('', '_blank');
    if (!win) {
      toast('Allow pop-ups for this site to print the card.', 'error');
      return;
    }
    win.document.write('<p style="font:14px system-ui;color:#555;padding:24px">Preparing print-ready card…</p>');
    setBusy('print');
    try {
      const svg = await exportSvg(false);
      printCard(win, svg, format, `${restaurant.name} · Menu QR`);
    } catch (e) {
      win.close();
      toast(e.message, 'error');
    } finally {
      setBusy('');
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      buzz();
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  const aspect = QR_FORMATS[format];
  return (
    <Overlay onClose={busy ? undefined : onClose} align="bottom" z="z-[60]" label="Restaurant QR code">
      <div className="sheet pb-safe relative z-10 flex max-h-[96vh] w-full max-w-4xl flex-col overflow-hidden rounded-t-[30px] border hairline bg-onyx sm:rounded-[30px]" data-testid="qr-modal">
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2 text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">
              <QrCode size={13} aria-hidden="true" /> Restaurant QR code
            </div>
            <h2 className="truncate font-display text-2xl font-semibold sm:text-3xl">{restaurant.name}</h2>
          </div>
          <IconButton icon={X} label="Close" onClick={() => !busy && onClose()} />
        </div>

        <div className="grid flex-1 gap-6 overflow-y-auto p-5 md:grid-cols-[minmax(0,1fr)_300px]">
          <div className="flex min-h-[320px] flex-col items-center justify-center rounded-3xl border hairline bg-[radial-gradient(circle_at_50%_0%,rgba(245,158,11,0.10),transparent_60%),#060607] p-4 sm:p-8">
            {error ? (
              <div className="flex max-w-xs flex-col items-center gap-2 text-center text-sm text-red-200">
                <TriangleAlert size={22} aria-hidden="true" /> {error}
              </div>
            ) : preview ? (
              <div
                className="w-full max-w-[340px] overflow-hidden rounded-[18px] shadow-[0_30px_80px_-20px_rgba(0,0,0,0.9)]"
                style={{ aspectRatio: `${aspect.w} / ${aspect.h}` }}
                data-testid="qr-preview"
                // The SVG is generated locally from escaped values (see qrCardSvg).
                dangerouslySetInnerHTML={{ __html: preview }}
              />
            ) : (
              <LoaderCircle size={26} className="animate-spin text-amber-400" aria-hidden="true" />
            )}
          </div>

          <div className="space-y-5">
            <div>
              <span className="mb-2 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Theme</span>
              <div className="grid grid-cols-2 gap-2">
                {Object.entries(QR_THEMES).map(([key, t]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setTheme(key)}
                    aria-pressed={theme === key}
                    className={cx(
                      'tap flex flex-col items-start gap-2 rounded-2xl border p-3 text-left',
                      theme === key ? 'border-amber-500/70 bg-amber-500/10' : 'hairline hover:border-zinc-600',
                    )}
                  >
                    <span className="flex gap-1">
                      <span className="h-6 w-6 rounded-full border border-white/10" style={{ background: t.bg }} />
                      <span className="h-6 w-6 rounded-full" style={{ background: t.frame }} />
                    </span>
                    <span className="text-xs font-semibold text-zinc-200">{t.label}</span>
                  </button>
                ))}
              </div>
            </div>
            <div>
              <span className="mb-2 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Print format</span>
              <div className="grid grid-cols-3 gap-1 rounded-2xl border hairline bg-black/30 p-1">
                {[
                  ['4x6', '4×6"'],
                  ['a6', 'A6'],
                  ['square', 'Square'],
                ].map(([key, label]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => setFormat(key)}
                    className={cx('tap h-10 rounded-xl text-xs font-bold', format === key ? 'bg-zinc-800 text-amber-200' : 'text-zinc-500 hover:text-zinc-300')}
                  >
                    {label}
                  </button>
                ))}
              </div>
              <p className="mt-1.5 text-[11px] text-zinc-500">{QR_FORMATS[format].label}</p>
            </div>

            <div className="space-y-2">
              <ExportButton icon={Printer} label="Print / Save as PDF" sub={`${QR_FORMATS[format === 'square' ? '4x6' : format].label}, no margins`} busy={busy === 'print'} disabled={!matrix || !!busy} onClick={print} primary />
              <ExportButton icon={FileImage} label="Download PNG" sub="2000 × 2000 px, for designers & printers" busy={busy === 'png'} disabled={!matrix || !!busy} onClick={downloadPng} />
              <ExportButton icon={Download} label="Download SVG" sub="Vector, scales to any acrylic stand" busy={busy === 'svg'} disabled={!matrix || !!busy} onClick={downloadSvg} />
            </div>

            <div className="rounded-2xl border hairline bg-black/30 p-3">
              <div className="text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">Encoded link</div>
              <div className="mt-1 flex items-center gap-2">
                <span className="min-w-0 flex-1 truncate font-mono text-xs text-zinc-300" data-testid="qr-url">{url}</span>
                <button type="button" onClick={copy} className="tap grid h-9 w-9 shrink-0 place-items-center rounded-full border hairline text-zinc-300" aria-label="Copy menu link">
                  {copied ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
                </button>
              </div>
              <p className="mt-2 text-[11px] leading-relaxed text-zinc-500">One code for the whole venue. Menu edits appear instantly; you never need to reprint.</p>
            </div>
          </div>
        </div>
      </div>
    </Overlay>
  );
}

function ExportButton({ icon: Icon, label, sub, onClick, busy, disabled, primary }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cx(
        'tap flex w-full items-center gap-3 rounded-2xl border px-4 py-3 text-left disabled:opacity-50',
        primary ? 'border-amber-500 bg-gradient-to-b from-amber-400 to-amber-600 text-zinc-950' : 'hairline text-zinc-200 hover:border-zinc-600',
      )}
    >
      {busy ? <LoaderCircle size={18} className="shrink-0 animate-spin" aria-hidden="true" /> : <Icon size={18} className="shrink-0" aria-hidden="true" />}
      <span className="min-w-0">
        <span className="block text-sm font-bold">{label}</span>
        <span className={cx('block truncate text-[11px]', primary ? 'text-zinc-900/70' : 'text-zinc-500')}>{sub}</span>
      </span>
    </button>
  );
}
