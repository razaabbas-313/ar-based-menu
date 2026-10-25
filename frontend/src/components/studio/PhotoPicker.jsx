import { useRef, useState } from 'react';
import { Camera, Cpu, ImagePlus, LoaderCircle, TriangleAlert } from 'lucide-react';
import { Badge } from '../ui.jsx';
import { fmtBytes } from '../../utils/format.js';
import { IMAGE_QUALITY, MAX_IMAGE_DIM, downsampleImage, savedPercent } from '../../utils/media.js';

/** Camera / library picker with on-device WebP compression and a before/after counter. */
export default function PhotoPicker({ photo, onPhoto }) {
  const cameraInput = useRef(null);
  const libraryInput = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function pick(file) {
    if (!file) return;
    setError('');
    setBusy(true);
    try {
      const out = await downsampleImage(file);
      if (photo?.url) URL.revokeObjectURL(photo.url);
      onPhoto({ ...out, name: file.name });
    } catch (e) {
      setError(e.message || 'Could not process that photo.');
    } finally {
      setBusy(false);
    }
  }
  const onChange = (e) => {
    pick(e.target.files?.[0]);
    e.target.value = '';
  };

  return (
    <div>
      <input ref={cameraInput} type="file" accept="image/*" capture="environment" className="hidden" onChange={onChange} data-testid="camera-input" />
      <input ref={libraryInput} type="file" accept="image/*" className="hidden" onChange={onChange} data-testid="photo-input" />
      {photo ? (
        <div className="overflow-hidden rounded-3xl border hairline">
          <div className="relative aspect-[16/10] bg-black">
            <img src={photo.url} alt="Compressed preview" className="absolute inset-0 h-full w-full object-cover" />
            <div className="absolute left-3 top-3">
              <Badge tone="green" icon={Cpu}>
                {photo.mime === 'image/webp' ? 'WebP' : 'JPEG'} Saved {savedPercent(photo.originalSize, photo.finalSize)}%
              </Badge>
            </div>
          </div>
          <div className="grid grid-cols-3 divide-x divide-zinc-800 bg-black/30 text-center">
            <Stat label="Before" value={fmtBytes(photo.originalSize)} sub={`${photo.originalWidth}×${photo.originalHeight}`} />
            <Stat label="After" value={fmtBytes(photo.finalSize)} sub={`${photo.width}×${photo.height}`} accent />
            <Stat label="On-device" value={`${photo.ms} ms`} sub={`q ${IMAGE_QUALITY}`} />
          </div>
          <div className="flex justify-end border-t hairline px-3 py-2">
            <button type="button" onClick={() => libraryInput.current?.click()} className="text-xs font-semibold text-zinc-400 hover:text-amber-300">
              Choose another photo
            </button>
          </div>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3">
            <button
              type="button"
              disabled={busy}
              onClick={() => cameraInput.current?.click()}
              className="tap flex aspect-[4/3] flex-col items-center justify-center gap-3 rounded-3xl border border-dashed border-amber-500/40 bg-amber-500/[0.06] text-amber-200 hover:bg-amber-500/10"
            >
              <span className="grid h-14 w-14 place-items-center rounded-full bg-amber-500 text-zinc-950">
                {busy ? <LoaderCircle size={24} className="animate-spin" aria-hidden="true" /> : <Camera size={24} aria-hidden="true" />}
              </span>
              <span className="text-sm font-semibold">{busy ? 'Downsampling…' : 'Take photo'}</span>
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => libraryInput.current?.click()}
              className="tap flex aspect-[4/3] flex-col items-center justify-center gap-3 rounded-3xl border border-dashed hairline text-zinc-300 hover:border-zinc-600"
            >
              <span className="grid h-14 w-14 place-items-center rounded-full border hairline">
                <ImagePlus size={24} aria-hidden="true" />
              </span>
              <span className="text-sm font-semibold">Choose from library</span>
            </button>
          </div>
          <p className="mt-3 text-xs leading-relaxed text-zinc-500">
            Resized to {MAX_IMAGE_DIM}px and re-encoded to WebP ({IMAGE_QUALITY}) on this device. A 12 MB shot typically leaves as ~400 KB.
          </p>
        </>
      )}
      {error && (
        <div className="mt-3 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
          <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
}

function Stat({ label, value, sub, accent }) {
  return (
    <div className="p-3">
      <div className="font-mono text-[10px] uppercase tracking-widest text-zinc-500">{label}</div>
      <div className={accent ? 'mt-1 font-mono text-sm font-semibold text-amber-300' : 'mt-1 font-mono text-sm text-zinc-300'}>{value}</div>
      <div className="font-mono text-[10px] text-zinc-600">{sub}</div>
    </div>
  );
}
