import { useState } from 'react';
import { Camera, Check, CircleAlert, Cloud, HelpCircle, LoaderCircle, Sparkles, Upload, X } from 'lucide-react';
import { api, putToS3 } from '../../api/client.js';
import { buzz, cx, fmtBytes } from '../../utils/format.js';
import { downsampleImage } from '../../utils/media.js';

const SLOTS = [
  { id: 'front', label: 'Front Angle (45°)', hint: 'Hero angle, showing depth and height' },
  { id: 'top', label: 'Top-Down (90°)', hint: 'Direct overhead view of the full plate' },
  { id: 'back', label: 'Back Angle (45°)', hint: 'Opposite side of hero angle' },
  { id: 'left', label: 'Left Side (Eye Level)', hint: 'Side profile showing layers and garnish' },
  { id: 'right', label: 'Right Side (Eye Level)', hint: 'Right profile showing textures' },
];

export default function Request3dModal({ dish, onClose, onSuccess }) {
  const [slots, setSlots] = useState({
    front: null,
    top: null,
    back: null,
    left: null,
    right: null,
  });
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState('');
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState('');

  const completedCount = Object.values(slots).filter(Boolean).length;
  const canSubmit = completedCount === 5 && !busy;

  const handleFile = async (slotId, file) => {
    if (!file) return;
    try {
      buzz(10);
      const processed = await downsampleImage(file, { maxDim: 3840, quality: 0.95 });
      setSlots((prev) => ({
        ...prev,
        [slotId]: {
          file,
          blob: processed.blob,
          url: processed.url,
          width: processed.width,
          height: processed.height,
          size: processed.blob.size,
          name: file.name,
        },
      }));
    } catch (err) {
      setError(`Failed to process photo: ${err.message}`);
    }
  };

  const removePhoto = (slotId, e) => {
    e.stopPropagation();
    buzz(10);
    setSlots((prev) => {
      if (prev[slotId]?.url) URL.revokeObjectURL(prev[slotId].url);
      return { ...prev, [slotId]: null };
    });
  };

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setBusy(true);
    setError('');
    buzz(20);
    try {
      setStage('Preparing upload authorizations…');
      setProgress(0.1);

      const items = SLOTS.map((s) => {
        const item = slots[s.id];
        return {
          slot: s.id,
          filename: `ref-${s.id.toLowerCase()}.webp`,
          contentType: 'image/webp',
          uploadSizeBytes: item.size,
        };
      });

      const presignResult = await api.presignModelRequest(dish.id, items);
      const list = Array.isArray(presignResult) ? presignResult : (presignResult?.uploads || []);
      const urlMap = Object.fromEntries(list.map((u) => [u.slot, u]));

      setStage('Saving reference photos to cloud studio…');
      const confirmedPhotos = [];
      let uploaded = 0;

      for (const s of SLOTS) {
        const item = slots[s.id];
        const presignInfo = urlMap[s.id];
        if (!presignInfo) throw new Error(`Missing upload slot ${s.id}`);

        const { etag } = await putToS3(presignInfo.uploadUrl, item.blob, presignInfo.requiredHeaders);
        uploaded += 1;
        setProgress(0.1 + (uploaded / 5) * 0.7);

        confirmedPhotos.push({
          slot: s.id,
          s3Key: presignInfo.objectKey,
          s3Url: presignInfo.uploadUrl ? presignInfo.uploadUrl.split('?')[0] : '',
          etag,
        });
      }

      setStage('Submitting request to 3D culinary desk…');
      setProgress(0.95);

      const updatedDish = await api.confirmModelRequest(dish.id, {
        photos: confirmedPhotos,
        notes: notes.trim() || null,
      });

      setProgress(1.0);
      buzz(30);
      onSuccess?.(updatedDish);
      onClose();
    } catch (err) {
      setError(err.message || 'Failed to submit 3D model request');
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/80 backdrop-blur-md" onClick={!busy ? onClose : undefined} />

      <div className="relative flex max-h-[90vh] w-full max-w-3xl flex-col overflow-hidden rounded-3xl border hairline bg-zinc-950 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b hairline px-6 py-5">
          <div className="flex items-center gap-3">
            <div className="grid h-10 w-10 place-items-center rounded-2xl bg-amber-500/15 text-amber-300">
              <Sparkles size={20} />
            </div>
            <div>
              <h3 className="font-display text-xl font-semibold text-zinc-100">
                {dish.is3d ? 'Request 3D Model Replacement' : 'Request 3D Digital Twin'}
              </h3>
              <p className="text-xs text-zinc-400">
                {dish.is3d ? '3D Model Update for ' : 'White-Glove Concierge for '}
                <span className="font-medium text-amber-200">{dish.dishTitle}</span>
              </p>
            </div>
          </div>
          {!busy && (
            <button
              type="button"
              onClick={onClose}
              className="tap grid h-9 w-9 place-items-center rounded-full text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100"
            >
              <X size={18} />
            </button>
          )}
        </div>

        {/* Scrollable Content */}
        <div className="no-scrollbar flex-1 overflow-y-auto p-6 space-y-6">
          {/* Concierge Info Banner */}
          <div className="rounded-2xl border border-amber-500/20 bg-amber-500/5 p-4 text-xs leading-relaxed text-zinc-300">
            <div className="flex items-start gap-2.5">
              <Camera size={16} className="mt-0.5 shrink-0 text-amber-400" />
              <div>
                <p className="font-semibold text-amber-200">
                  {dish.is3d ? 'Commissioning a New Replacement 3D Model' : 'Michelin-Standard 3D Culinary Reconstruction'}
                </p>
                <p className="mt-1 text-zinc-400">
                  {dish.is3d
                    ? 'Upload 5 updated photos of the dish. Our 3D culinary team will craft a new digital twin. Your existing 3D model stays active on your guest menu until the new replacement is approved.'
                    : 'Please upload 5 clear photos of the dish taken in bright lighting. Our 3D culinary team will craft a realistic digital twin with true-to-life WebAR scaling for your guests within 24 hours.'}
                </p>
              </div>
            </div>
          </div>

          {/* Guided 5-Photo Slots */}
          <div>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
                Required Angles ({completedCount}/5 Uploaded)
              </span>
              <span className="text-xs text-amber-300">Natural light recommended</span>
            </div>

            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {SLOTS.map((s, idx) => {
                const filled = slots[s.id];
                return (
                  <div
                    key={s.id}
                    className={cx(
                      'relative flex flex-col justify-between overflow-hidden rounded-2xl border p-3 transition-colors',
                      filled ? 'border-emerald-500/40 bg-emerald-950/15' : 'hairline bg-zinc-900/40 hover:border-zinc-700',
                    )}
                  >
                    <div>
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-zinc-200">
                          {idx + 1}. {s.label}
                        </span>
                        {filled && (
                          <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-500 text-zinc-950">
                            <Check size={12} strokeWidth={3} />
                          </span>
                        )}
                      </div>
                      <p className="mt-1 text-[11px] text-zinc-500">{s.hint}</p>
                    </div>

                    <div className="mt-3">
                      {filled ? (
                        <div className="relative aspect-video overflow-hidden rounded-xl bg-black">
                          <img src={filled.url} alt={s.label} className="h-full w-full object-cover" />
                          {!busy && (
                            <button
                              type="button"
                              onClick={(e) => removePhoto(s.id, e)}
                              className="tap absolute right-1.5 top-1.5 grid h-6 w-6 place-items-center rounded-full bg-black/70 text-zinc-300 hover:text-white"
                            >
                              <X size={13} />
                            </button>
                          )}
                          <div className="absolute bottom-1 left-1.5 text-[10px] text-zinc-300 font-mono">
                            {fmtBytes(filled.size)}
                          </div>
                        </div>
                      ) : (
                        <label className="tap flex aspect-video cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed hairline bg-zinc-950/60 transition-colors hover:border-amber-400/50 hover:bg-amber-500/5">
                          <Upload size={18} className="text-zinc-500" />
                          <span className="mt-1 text-[11px] font-medium text-zinc-400">Choose photo</span>
                          <input
                            type="file"
                            accept="image/*"
                            disabled={busy}
                            className="hidden"
                            onChange={(e) => {
                              const f = e.target.files?.[0];
                              if (f) handleFile(s.id, f);
                            }}
                          />
                        </label>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Notes textarea */}
          <div>
            <label className="block">
              <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-zinc-400">
                Plating Notes &amp; Video Walkaround (Optional)
              </span>
              <textarea
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                disabled={busy}
                rows={2}
                maxLength={1000}
                placeholder="e.g. Glossy glaze finish, crispy microgreens on top, or paste a link to a 10s video walkaround."
                className="w-full rounded-2xl border hairline bg-black/40 px-4 py-2.5 text-sm text-zinc-100 placeholder:text-zinc-600 focus:border-amber-500/60 focus:outline-none focus:ring-2 focus:ring-amber-500/20"
              />
            </label>
          </div>

          {/* Progress / Status display */}
          {busy && (
            <div className="rounded-2xl border hairline bg-zinc-900/60 p-4">
              <div className="flex items-center justify-between text-xs text-zinc-300">
                <span className="flex items-center gap-2">
                  <LoaderCircle size={15} className="animate-spin text-amber-400" />
                  {stage}
                </span>
                <span className="font-mono text-amber-300">{Math.round(progress * 100)}%</span>
              </div>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-zinc-800">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-amber-500 to-amber-300 transition-[width] duration-300"
                  style={{ width: `${progress * 100}%` }}
                />
              </div>
            </div>
          )}

          {error && (
            <div className="flex items-center gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">
              <CircleAlert size={16} className="shrink-0 text-red-400" />
              <span>{error}</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t hairline bg-zinc-950/80 px-6 py-4">
          <div className="text-xs text-zinc-500">
            {completedCount < 5 ? `Please select all 5 angles to submit (${5 - completedCount} remaining)` : 'Ready to submit to 3D team'}
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              disabled={busy}
              onClick={onClose}
              className="tap rounded-full border hairline px-5 py-2.5 text-sm font-semibold text-zinc-300 hover:bg-zinc-900"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={!canSubmit}
              onClick={handleSubmit}
              className={cx(
                'tap flex items-center gap-2 rounded-full px-6 py-2.5 text-sm font-bold text-zinc-950 transition-all',
                canSubmit
                  ? 'bg-gradient-to-b from-amber-400 to-amber-600 shadow-fab'
                  : 'cursor-not-allowed bg-zinc-800 text-zinc-500',
              )}
            >
              <Sparkles size={16} />
              Submit 3D Request
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
