import { useRef, useState } from 'react';
import { Box, CircleCheck, Cloud, Cpu, Database, ImagePlus, KeyRound, LoaderCircle, RefreshCw, TriangleAlert, Upload } from 'lucide-react';
import PipelineView, { makeStages, overallProgress } from './PipelineView.jsx';
import { buzz, cx, fmtBytes } from '../../utils/format.js';
import { downsampleImage, inspectModel, savedPercent } from '../../utils/media.js';
import { replaceDishAsset } from '../../utils/upload.js';

const STAGES = [
  { key: 'prepare', label: 'Optimizing visual presentation…', icon: Cpu },
  { key: 'sign', label: 'Preparing dish for menu…', icon: KeyRound },
  { key: 'stream', label: 'Saving to cloud studio…', icon: Cloud, streams: true },
  { key: 'sync', label: 'Publishing to guest menu…', icon: Database },
];

const isModelFile = (file) => /\.(glb|gltf)$/i.test(file.name);

/**
 * "Update Visual Asset": drop a new photo (compressed to WebP on the device) or a new .glb
 * (magic-header validated) to replace the dish's current one in place. The dish keeps its id,
 * price, tags and position on the menu; the old S3 object is deleted after the swap.
 */
export default function VisualAssetReplacer({ dish, canDirectUpload = false, onReplaced, onBusyChange }) {
  const input = useRef(null);
  const [dragOver, setDragOver] = useState(false);
  const [stages, setStages] = useState(null);
  const [fraction, setFraction] = useState(0);
  const [phase, setPhase] = useState('idle'); // idle | running | done | error
  const [error, setError] = useState('');
  const [last, setLast] = useState(null);

  const legacy = dish.mediaType === 'MODEL_3D' && !dish.imageUrl;
  const patch = (key, update) => setStages((all) => all.map((s) => (s.key === key ? { ...s, ...update } : s)));

  async function run(file) {
    if (!file || phase === 'running') return;
    buzz(14);
    const model = isModelFile(file);
    if (model && !canDirectUpload) {
      setError("Direct 3D model uploads are not enabled for this restaurant. Please request a 3D model through the 3D Concierge.");
      setPhase('error');
      return;
    }
    setStages(makeStages(STAGES));
    setFraction(0);
    setPhase('running');
    setError('');
    onBusyChange?.(true);
    let current = 'prepare';
    const onStage = (key, update) => {
      current = key;
      if (update.status === 'active') setFraction(0);
      patch(key, update);
    };
    try {
      if (model && legacy) {
        throw new Error('Add a photo first: this dish was created as 3D-only in an earlier version.');
      }
      onStage('prepare', { status: 'active' });
      let asset;
      if (model) {
        const meta = await inspectModel(file);
        asset = { kind: 'model', model: { file, meta, name: file.name } };
        onStage('prepare', {
          status: 'done',
          detail: `glTF 2.0 ✓ magic 0x46546C67 · ${meta.triangles.toLocaleString()} tris · ${fmtBytes(meta.sizeBytes)}`,
        });
      } else {
        const photo = await downsampleImage(file);
        URL.revokeObjectURL(photo.url);
        asset = { kind: 'photo', photo: { ...photo, name: file.name } };
        onStage('prepare', {
          status: 'done',
          detail: `${photo.width}×${photo.height} ${photo.mime === 'image/webp' ? 'WebP' : 'JPEG'} · ${fmtBytes(photo.finalSize)} (−${savedPercent(photo.originalSize, photo.finalSize)}%) in ${photo.ms} ms`,
        });
      }
      const updated = await replaceDishAsset({ dishId: dish.id, asset, placement: dish.arPlacement, onStage, onProgress: setFraction });
      setLast(model ? '3D model' : 'Photo');
      setPhase('done');
      buzz(30);
      onReplaced(updated, model ? '3D model replaced' : 'Photo replaced');
    } catch (e) {
      patch(current, { status: 'error', detail: e.message });
      setError(e.message);
      setPhase('error');
    } finally {
      onBusyChange?.(false);
    }
  }

  return (
    <section className="rounded-3xl border hairline bg-black/20 p-4" data-testid="asset-replacer">
      <div className="mb-3 flex items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-zinc-100">
          <RefreshCw size={15} className="text-amber-300" aria-hidden="true" /> Update Visual Asset
        </h3>
        <span className="text-[11px] text-zinc-500">
          {canDirectUpload ? 'Photo or .glb · replaces in place' : 'Photo · replaces in place'}
        </span>
      </div>

      <div className="grid grid-cols-[96px_minmax(0,1fr)] gap-3">
        <div className="relative aspect-square overflow-hidden rounded-2xl bg-black">
          {dish.imageUrl ? (
            <img src={dish.imageUrl} alt="Current photo" className="h-full w-full object-cover" />
          ) : (
            <div className="grid h-full place-items-center text-zinc-600">
              <Box size={22} aria-hidden="true" />
            </div>
          )}
          {dish.is3d && (
            <span className="absolute bottom-1.5 left-1.5 rounded-full bg-black/70 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-amber-300">3D</span>
          )}
        </div>

        <button
          type="button"
          disabled={phase === 'running'}
          onClick={() => input.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            run(e.dataTransfer.files?.[0]);
          }}
          className={cx(
            'tap flex min-h-24 flex-col items-center justify-center gap-1.5 rounded-2xl border border-dashed px-3 text-center',
            dragOver ? 'border-amber-400 bg-amber-500/10' : 'border-zinc-700 hover:border-amber-500/50',
          )}
        >
          {phase === 'running' ? (
            <LoaderCircle size={20} className="animate-spin text-amber-300" aria-hidden="true" />
          ) : phase === 'done' ? (
            <CircleCheck size={20} className="text-emerald-300" aria-hidden="true" />
          ) : (
            <span className="flex gap-1.5 text-amber-300">
              <ImagePlus size={19} aria-hidden="true" />
              <Upload size={19} aria-hidden="true" />
            </span>
          )}
          <span className="text-[13px] font-semibold text-zinc-200">
            {phase === 'running'
              ? 'Replacing…'
              : phase === 'done'
              ? `${last} updated. Drop another to replace again`
              : canDirectUpload
              ? 'Drop a new photo or .glb, or tap to browse'
              : 'Drop a new photo, or tap to browse'}
          </span>
          <span className="text-[11px] text-zinc-500">Guests keep seeing the current version until the new one is verified.</span>
        </button>
      </div>
      <input
        ref={input}
        type="file"
        accept={canDirectUpload ? 'image/*,.glb,.gltf,model/gltf-binary,model/gltf+json' : 'image/*'}
        className="hidden"
        data-testid="replace-input"
        onChange={(e) => {
          run(e.target.files?.[0]);
          e.target.value = '';
        }}
      />

      {stages && (phase === 'running' || phase === 'error' || phase === 'done') && (
        <div className="mt-3">
          <PipelineView stages={stages} overall={overallProgress(stages, fraction)} title="Prepare → Sign → Stream → Sync" />
        </div>
      )}
      {error && (
        <div className="mt-3 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
          <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}
    </section>
  );
}
