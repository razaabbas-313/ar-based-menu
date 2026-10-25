import { useRef, useState } from 'react';
import JSZip from 'jszip';
import {
  Box,
  Check,
  ChevronDown,
  Download,
  FileCheck2,
  Image as ImageIcon,
  LoaderCircle,
  Sparkles,
  Upload,
  X,
  ExternalLink,
} from 'lucide-react';
import { api, putToS3 } from '../../api/client.js';
import { useToast } from '../Toast.jsx';
import { buzz, cx, fmtBytes } from '../../utils/format.js';

const SLOT_LABELS = {
  front: 'Front Angle (45°)',
  top: 'Top-Down (90°)',
  back: 'Back Angle (45°)',
  left: 'Left Side (Eye Level)',
  right: 'Right Side (Eye Level)',
  front_45: 'Front Angle (45°)',
  top_90: 'Top-Down (90°)',
  back_45: 'Back Angle (45°)',
  left_side: 'Left Side (Eye Level)',
  right_side: 'Right Side (Eye Level)',
};

export default function Admin3dDeskModal({ request, onClose, onApproved }) {
  const toast = useToast();
  const fileInputRef = useRef(null);

  const [selectedPhoto, setSelectedPhoto] = useState(request.photos?.[0] || null);
  const [modelFile, setModelFile] = useState(null);
  const [zipping, setZipping] = useState(false);
  const [busy, setBusy] = useState(false);
  const [stage, setStage] = useState('');
  const [progress, setProgress] = useState(0);
  const [uploadBytes, setUploadBytes] = useState({ loaded: 0, total: 0 });

  const handleDownloadZip = async () => {
    if (!request.photos || request.photos.length === 0) {
      toast('No photos available to download', 'error');
      return;
    }
    setZipping(true);
    buzz(10);
    try {
      const zip = new JSZip();
      const folderName = `dish-${request.dishId}-${request.restaurantSlug || 'ref'}`;
      const folder = zip.folder(folderName);

      for (let i = 0; i < request.photos.length; i++) {
        const p = request.photos[i];
        try {
          const resp = await fetch(p.s3Url, { mode: 'cors' });
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const blob = await resp.blob();
          const ext = p.s3Key?.split('.').pop() || 'webp';
          folder.file(`${p.slot.toLowerCase()}.${ext}`, blob);
        } catch (fetchErr) {
          console.warn(`Could not fetch photo ${p.slot}`, fetchErr);
        }
      }

      const content = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(content);
      const a = document.createElement('a');
      a.href = url;
      a.download = `photo-pack-${request.dishId}-${request.restaurantSlug || 'ref'}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(() => URL.revokeObjectURL(url), 10000);
      toast('Photo pack downloaded!');
    } catch (err) {
      toast(`Failed to package photos: ${err.message}`, 'error');
    } finally {
      setZipping(false);
    }
  };

  const handleSelectModel = (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.glb')) {
      toast('Please upload a 3D model in binary glTF (.glb) format', 'error');
      return;
    }
    setModelFile(file);
    buzz(15);
  };

  const handleApproveAndPublish = async () => {
    if (!modelFile) {
      toast('Please drop or select a finished .glb 3D model first', 'error');
      return;
    }

    setBusy(true);
    setProgress(0.1);
    setStage('Generating secure cloud destination…');
    buzz(20);

    try {
      // 1. Presign model upload
      const presign = await api.adminPresignModel(request.dishId, {
        filename: modelFile.name,
        contentType: 'model/gltf-binary',
        uploadSizeBytes: modelFile.size,
      });

      // 2. Direct upload to AWS S3
      setStage('Uploading finished 3D model to cloud…');
      const { etag } = await putToS3(
        presign.uploadUrl,
        modelFile,
        presign.requiredHeaders,
        (fraction, loaded, total) => {
          setProgress(0.1 + fraction * 0.7);
          setUploadBytes({ loaded, total: total || modelFile.size });
        },
      );

      // 3. Approve and publish to live guest menu
      setStage('Publishing 3D & AR digital twin to restaurant menu…');
      setProgress(0.9);

      const approvedMedia = await api.adminApproveModel(request.dishId, {
        s3Key: presign.objectKey,
        etag,
        arPlacement: 'floor',
      });

      setProgress(1.0);
      buzz(30);
      toast(`Published 3D model for “${request.dishTitle}”! Now live on menu.`);
      onApproved?.(approvedMedia);
      onClose();
    } catch (err) {
      toast(err.message || 'Failed to approve and publish 3D model', 'error');
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="fixed inset-0 bg-black/80 backdrop-blur-md" onClick={!busy ? onClose : undefined} />

      <div className="relative flex max-h-[92vh] w-full max-w-4xl flex-col overflow-hidden rounded-3xl border hairline bg-zinc-950 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b hairline px-6 py-4">
          <div className="flex items-center gap-3 min-w-0">
            <div className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-amber-500/15 text-amber-300">
              <Box size={20} />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h3 className="truncate font-display text-xl font-semibold text-zinc-100">
                  3D Production Desk
                </h3>
                <span className="shrink-0 rounded-full border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 text-[10px] font-bold text-amber-300">
                  {request.status}
                </span>
              </div>
              <div className="truncate text-xs text-zinc-400">
                <span className="font-semibold text-zinc-200">{request.dishTitle}</span> · {request.restaurantName} (/r/{request.restaurantSlug})
              </div>
            </div>
          </div>
          <button
            type="button"
            disabled={busy}
            onClick={onClose}
            className="tap grid h-9 w-9 shrink-0 place-items-center rounded-full text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100 disabled:opacity-40"
          >
            <X size={18} />
          </button>
        </div>

        {/* Modal Body */}
        <div className="no-scrollbar flex-1 overflow-y-auto p-6 space-y-6">
          {/* Notes from Restaurant Chef / Owner */}
          {request.notes && (
            <div className="rounded-2xl border border-amber-500/20 bg-amber-500/5 p-4 text-xs text-amber-100">
              <span className="font-bold uppercase tracking-wider text-amber-400">Chef's Notes: </span>
              {request.notes}
            </div>
          )}

          {/* Reference Photos Pack */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
                  Reference Angle Photos ({request.photos?.length || 0} slots)
                </h4>
                <p className="text-[11px] text-zinc-500">
                  High-resolution culinary captures provided by restaurant staff
                </p>
              </div>
              <button
                type="button"
                disabled={zipping}
                onClick={handleDownloadZip}
                className="tap flex h-9 items-center gap-2 rounded-full border hairline px-4 text-xs font-semibold text-zinc-200 hover:border-amber-500/50 hover:text-amber-300 disabled:opacity-50"
              >
                {zipping ? (
                  <LoaderCircle size={14} className="animate-spin" />
                ) : (
                  <Download size={14} />
                )}
                <span>{zipping ? 'Zipping…' : 'Download Photo Pack (.zip)'}</span>
              </button>
            </div>

            {/* Photo Preview Strip & Main View */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {/* Main Photo Preview */}
              <div className="relative aspect-square overflow-hidden rounded-2xl border hairline bg-black md:col-span-2">
                {selectedPhoto ? (
                  <img
                    src={selectedPhoto.s3Url}
                    alt={selectedPhoto.slot}
                    className="h-full w-full object-contain"
                  />
                ) : (
                  <div className="grid h-full place-items-center text-zinc-600">
                    <ImageIcon size={32} />
                  </div>
                )}
                {selectedPhoto && (
                  <span className="absolute bottom-3 left-3 rounded-full bg-black/80 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-zinc-200">
                    {SLOT_LABELS[selectedPhoto.slot?.toLowerCase()] || selectedPhoto.slot?.replace('_', ' ')}
                  </span>
                )}
              </div>

              {/* Thumbnails List */}
              <div className="flex flex-row gap-2 overflow-x-auto md:flex-col md:overflow-y-auto">
                {(request.photos || []).map((p) => (
                  <button
                    key={p.slot}
                    type="button"
                    onClick={() => setSelectedPhoto(p)}
                    className={cx(
                      'tap flex shrink-0 items-center gap-3 rounded-xl border p-2 text-left transition-all',
                      selectedPhoto?.slot === p.slot
                        ? 'border-amber-500 bg-amber-500/10'
                        : 'hairline bg-onyx hover:bg-zinc-900',
                    )}
                  >
                    <div className="h-14 w-14 shrink-0 overflow-hidden rounded-lg bg-black">
                      <img src={p.s3Url} alt="" className="h-full w-full object-cover" />
                    </div>
                    <div className="hidden min-w-0 flex-1 md:block">
                      <div className="truncate text-xs font-semibold text-zinc-200">
                        {SLOT_LABELS[p.slot?.toLowerCase()] || p.slot?.replace('_', ' ')}
                      </div>
                      <div className="truncate text-[10px] text-zinc-500 font-mono">
                        {p.s3Key.split('/').pop()}
                      </div>
                    </div>
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Finished 3D Model Upload Area */}
          <div className="space-y-3 pt-2 border-t hairline">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
              Upload Finished 3D Digital Twin (.glb)
            </h4>

            {modelFile ? (
              <div className="flex items-center justify-between rounded-2xl border border-emerald-500/30 bg-emerald-950/15 p-4">
                <div className="flex items-center gap-3">
                  <div className="grid h-12 w-12 place-items-center rounded-xl bg-emerald-500/20 text-emerald-300">
                    <FileCheck2 size={24} />
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-zinc-100">{modelFile.name}</div>
                    <div className="text-xs text-zinc-400 font-mono">{fmtBytes(modelFile.size)} · GLB Binary Model</div>
                  </div>
                </div>
                {!busy && (
                  <button
                    type="button"
                    onClick={() => setModelFile(null)}
                    className="tap text-xs text-zinc-400 hover:text-red-400"
                  >
                    Change file
                  </button>
                )}
              </div>
            ) : (
              <div
                onClick={() => fileInputRef.current?.click()}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  const file = e.dataTransfer.files?.[0];
                  if (file && file.name.toLowerCase().endsWith('.glb')) {
                    setModelFile(file);
                  } else {
                    toast('Please drop a .glb file', 'error');
                  }
                }}
                className="tap flex flex-col items-center justify-center rounded-2xl border-2 border-dashed hairline bg-black/40 p-8 text-center transition-colors hover:border-amber-500/50 hover:bg-amber-500/5 cursor-pointer"
              >
                <div className="grid h-12 w-12 place-items-center rounded-xl bg-amber-500/10 text-amber-300">
                  <Upload size={22} />
                </div>
                <h5 className="mt-3 font-semibold text-sm text-zinc-200">
                  Click or drag optimized .glb model here
                </h5>
                <p className="mt-1 text-xs text-zinc-500">
                  Maximum recommended size: 15 MB · Draco/Meshopt compressed
                </p>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".glb"
                  className="hidden"
                  onChange={handleSelectModel}
                />
              </div>
            )}
          </div>

          {/* Progress / Status during publishing */}
          {busy && (
            <div className="space-y-3 rounded-2xl border border-amber-500/40 bg-black/70 p-4.5 shadow-lg">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-amber-300 flex items-center gap-2">
                  <LoaderCircle size={14} className="animate-spin text-amber-400" />
                  {stage}
                </span>
                <span className="font-mono font-bold text-amber-300">
                  {Math.round(progress * 100)}%
                </span>
              </div>
              <div className="h-2.5 w-full overflow-hidden rounded-full bg-zinc-800 p-0.5">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-amber-500 via-amber-400 to-emerald-400 transition-all duration-200"
                  style={{ width: `${Math.round(progress * 100)}%` }}
                />
              </div>
              {uploadBytes.total > 0 && (
                <div className="flex items-center justify-between text-[11px] font-mono text-zinc-400">
                  <span>Direct AWS S3 upload stream</span>
                  <span>{fmtBytes(uploadBytes.loaded)} / {fmtBytes(uploadBytes.total)}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t hairline bg-zinc-950/80 px-6 py-4">
          <div className="text-xs text-zinc-500">
            Approved models activate the pulsing <span className="text-emerald-300 font-semibold">3D &amp; AR Live</span> badge instantly.
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              disabled={busy}
              onClick={onClose}
              className="tap rounded-full border hairline px-5 py-2.5 text-xs font-semibold text-zinc-400 hover:text-zinc-200 disabled:opacity-40"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={busy || !modelFile}
              onClick={handleApproveAndPublish}
              className={cx(
                'tap flex items-center gap-2 rounded-full px-6 py-2.5 text-xs font-bold text-zinc-950 transition-all',
                modelFile && !busy
                  ? 'bg-gradient-to-b from-amber-400 to-amber-600 shadow-fab hover:brightness-105'
                  : 'cursor-not-allowed bg-zinc-800 text-zinc-500',
              )}
            >
              {busy ? <LoaderCircle size={15} className="animate-spin" /> : <Sparkles size={15} />}
              {busy ? 'Publishing…' : 'Approve & Publish to Menu'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
