import { useRef, useState } from 'react';
import { CircleCheck, LoaderCircle, TriangleAlert, Upload } from 'lucide-react';
import ModelStage from '../ModelStage.jsx';
import { Badge } from '../ui.jsx';
import { cx, fmtBytes } from '../../utils/format.js';
import { MAX_MODEL_BYTES, inspectModel } from '../../utils/media.js';

/** .glb / .gltf dropzone with in-browser validation (magic header + JSON chunk) and live preview. */
export default function ModelPicker({ model, onModel, placement, optional = false }) {
  const input = useRef(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [dragOver, setDragOver] = useState(false);

  async function pick(file) {
    if (!file) return;
    setError('');
    setBusy(true);
    try {
      const meta = await inspectModel(file);
      if (model?.url) URL.revokeObjectURL(model.url);
      onModel({ file, meta, url: URL.createObjectURL(file), name: file.name });
    } catch (e) {
      setError(e.message || 'That 3D file could not be read.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <input
        ref={input}
        type="file"
        accept=".glb,.gltf,model/gltf-binary,model/gltf+json"
        className="hidden"
        data-testid="model-input"
        onChange={(e) => {
          pick(e.target.files?.[0]);
          e.target.value = '';
        }}
      />
      {model ? (
        <div className="overflow-hidden rounded-3xl border hairline">
          <div className="stage-bg relative aspect-[16/11]">
            <ModelStage src={model.url} alt="Preview" arPlacement={placement} variant="full" />
            <div className="absolute left-3 top-3">
              <Badge icon={CircleCheck}>Validated glTF 2.0</Badge>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-px bg-zinc-800 sm:grid-cols-4">
            {[
              ['Size', fmtBytes(model.meta.sizeBytes)],
              ['Triangles', model.meta.triangles.toLocaleString()],
              ['Meshes / Mat.', `${model.meta.meshes} / ${model.meta.materials}`],
              ['Textures', String(model.meta.textures)],
            ].map(([k, v]) => (
              <div key={k} className="bg-onyx p-3">
                <div className="font-mono text-[10px] uppercase tracking-widest text-zinc-500">{k}</div>
                <div className="mt-1 truncate font-mono text-sm text-zinc-200">{v}</div>
              </div>
            ))}
          </div>
          <div className="flex items-center justify-between gap-2 border-t hairline px-3 py-2">
            <span className="truncate font-mono text-[10px] text-zinc-500">{model.name}</span>
            <button type="button" onClick={() => onModel(null)} className="shrink-0 text-xs font-semibold text-zinc-400 hover:text-amber-300">
              Remove
            </button>
          </div>
        </div>
      ) : (
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragOver(true);
          }}
          onDragLeave={() => setDragOver(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragOver(false);
            pick(e.dataTransfer.files?.[0]);
          }}
          onClick={() => input.current?.click()}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current?.click()}
          role="button"
          tabIndex={0}
          className={cx(
            'tap flex cursor-pointer flex-col items-center justify-center gap-3 rounded-3xl border border-dashed px-6 py-8 text-center',
            dragOver ? 'border-amber-400 bg-amber-500/10' : 'border-amber-500/40 bg-amber-500/[0.05] hover:bg-amber-500/[0.08]',
          )}
        >
          <span className="grid h-12 w-12 place-items-center rounded-full bg-amber-500 text-zinc-950">
            {busy ? <LoaderCircle size={22} className="animate-spin" aria-hidden="true" /> : <Upload size={22} aria-hidden="true" />}
          </span>
          <div className="text-sm font-semibold text-amber-100">
            {busy ? 'Validating scan…' : `Drop a .glb / .gltf dish scan${optional ? ' (optional)' : ''}, or tap to browse`}
          </div>
          <div className="text-xs text-zinc-500">Checked in your browser before upload · up to {fmtBytes(MAX_MODEL_BYTES)}</div>
        </div>
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
