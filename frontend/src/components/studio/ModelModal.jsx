import { useState } from 'react';
import { Box, CircleCheck, Database, KeyRound, Trash2, TriangleAlert, X } from 'lucide-react';
import ModelPicker from './ModelPicker.jsx';
import PipelineView, { makeStages, overallProgress } from './PipelineView.jsx';
import ModelStage from '../ModelStage.jsx';
import { IconButton, Overlay } from '../ui.jsx';
import { api } from '../../api/client.js';
import { attachModel } from '../../utils/upload.js';
import { buzz, cx, fmtBytes } from '../../utils/format.js';

const STAGES = [
  { key: 'model-sign', label: 'Signing 3D model upload…', icon: KeyRound },
  { key: 'model-stream', label: 'Streaming .glb directly to AWS S3…', icon: Box, streams: true },
  { key: 'model-sync', label: 'Linking model to the dish & enabling AR…', icon: Database },
];

/** Attach, replace or remove a dish's 3D model without touching its photo. */
export default function ModelModal({ dish, onClose, onSaved, adminRestaurantId }) {
  const [model, setModel] = useState(null);
  const [placement, setPlacement] = useState(dish.arPlacement || 'table');
  const [stages, setStages] = useState(makeStages(STAGES));
  const [fraction, setFraction] = useState(0);
  const [phase, setPhase] = useState('edit');
  const [error, setError] = useState('');

  const patch = (key, update) => setStages((all) => all.map((s) => (s.key === key ? { ...s, ...update } : s)));

  async function upload() {
    if (!model) return;
    buzz(16);
    setPhase('running');
    setError('');
    setStages(makeStages(STAGES));
    let current = 'model-sign';
    try {
      const saved = await attachModel({
        dishId: dish.id,
        adminRestaurantId,
        model,
        placement,
        onStage: (key, u) => {
          current = key;
          if (u.status === 'active') setFraction(0);
          patch(key, u);
        },
        onProgress: setFraction,
      });
      setPhase('done');
      buzz(30);
      onSaved(saved, dish.is3d ? '3D model replaced' : '3D model attached: AR is live');
    } catch (e) {
      patch(current, { status: 'error', detail: e.message });
      setError(e.message);
      setPhase('error');
    }
  }

  async function remove() {
    if (!window.confirm(`Remove the 3D model from “${dish.dishTitle}”? The photo stays on the menu.`)) return;
    setPhase('running');
    try {
      const saved = await api.removeModel(dish.id);
      onSaved(saved, '3D model removed');
    } catch (e) {
      setError(e.message);
      setPhase('error');
    }
  }

  return (
    <Overlay onClose={phase === 'running' ? undefined : onClose} align="bottom" label="3D model">
      <div className="sheet pb-safe relative z-10 flex max-h-[94vh] w-full max-w-2xl flex-col overflow-hidden rounded-t-[30px] border hairline bg-onyx sm:rounded-[30px]">
        <div className="flex items-center justify-between gap-3 border-b hairline px-5 py-4">
          <div className="min-w-0">
            <div className="text-[10px] font-semibold uppercase tracking-[0.28em] text-amber-500/80">{dish.is3d ? 'Change 3D model' : 'Attach 3D model'}</div>
            <h2 className="truncate font-display text-2xl font-semibold">{dish.dishTitle}</h2>
          </div>
          <IconButton icon={X} label="Close" onClick={() => phase !== 'running' && onClose()} />
        </div>
        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5">
          {!adminRestaurantId && dish.is3d && !model && phase !== 'running' && (
            <div className="overflow-hidden rounded-3xl border hairline">
              <div className="stage-bg relative aspect-[16/10]">
                <ModelStage src={dish.modelUrl} alt={dish.dishTitle} arPlacement={dish.arPlacement} variant="full" />
              </div>
              <div className="flex items-center justify-between gap-2 border-t hairline px-4 py-3 text-sm">
                <span className="flex items-center gap-2 text-zinc-300">
                  <CircleCheck size={16} className="text-emerald-400" aria-hidden="true" /> Current model · {fmtBytes(dish.modelFileSizeBytes)}
                </span>
                <button type="button" onClick={remove} className="tap flex h-10 items-center gap-2 rounded-full px-3 text-red-300 hover:bg-red-500/10">
                  <Trash2 size={15} aria-hidden="true" /> Remove
                </button>
              </div>
            </div>
          )}
          {phase === 'edit' || phase === 'error' ? (
            <>
              <ModelPicker model={model} onModel={setModel} placement={placement} />
              <div>
                <span className="mb-1.5 block text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">AR placement</span>
                <div className="grid grid-cols-2 gap-1 rounded-2xl border hairline bg-black/30 p-1">
                  {[
                    ['table', 'Tabletop', 'True-to-size'],
                    ['floor', 'Pedestal', 'Scalable showpiece'],
                  ].map(([key, label, hint]) => (
                    <button
                      key={key}
                      type="button"
                      onClick={() => setPlacement(key)}
                      className={cx(
                        'tap flex min-h-12 flex-col items-center justify-center rounded-xl px-2 py-1',
                        placement === key ? 'bg-zinc-800 text-amber-200' : 'text-zinc-400 hover:text-zinc-200',
                      )}
                    >
                      <span className="text-sm font-semibold">{label}</span>
                      <span className="text-[10px] text-zinc-500">{hint}</span>
                    </button>
                  ))}
                </div>
              </div>
            </>
          ) : null}
          {(phase === 'running' || phase === 'error') && model && <PipelineView stages={stages} overall={overallProgress(stages, fraction)} title="3D track" />}
          {error && (
            <div className="flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
              <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}
        </div>
        <div className="border-t hairline bg-black/30 px-5 py-4">
          <button
            type="button"
            disabled={!model || phase === 'running'}
            onClick={upload}
            className={cx(
              'tap flex h-14 w-full items-center justify-center gap-2 rounded-2xl text-base font-bold',
              model && phase !== 'running' ? 'bg-gradient-to-b from-amber-400 to-amber-600 text-zinc-950' : 'bg-zinc-800 text-zinc-500',
            )}
          >
            <Box size={19} aria-hidden="true" /> {dish.is3d ? 'Replace 3D model' : 'Attach 3D model'}
          </button>
        </div>
      </div>
    </Overlay>
  );
}
