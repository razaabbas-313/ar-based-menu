import { useState } from 'react';
import { Box, CircleCheck, Cloud, Database, KeyRound, LoaderCircle, Sparkles, TriangleAlert } from 'lucide-react';
import PhotoPicker from './PhotoPicker.jsx';
import DishFields, { dishToForm, formIsValid } from './DishFields.jsx';
import PipelineView, { makeStages, overallProgress } from './PipelineView.jsx';
import { uploadDishPhoto } from '../../utils/upload.js';
import { buzz, cx, fmtBytes } from '../../utils/format.js';
import { prettifyName, savedPercent } from '../../utils/media.js';

const PHOTO_STAGES = [
  { key: 'prepare', label: 'Optimizing dish presentation…', icon: Sparkles },
  { key: 'sign', label: 'Preparing dish for menu…', icon: KeyRound },
  { key: 'stream', label: 'Saving to cloud studio…', icon: Cloud, streams: true },
  { key: 'sync', label: 'Publishing to guest menu…', icon: Database },
];

/** Tab: photo (required) + culinary metadata -> live dish. 3D Digital Twins are requested via Concierge. */
export default function NewDishStudio({ currencyLabel, categories = [], onPublished }) {
  const [photo, setPhoto] = useState(null);
  const [form, setForm] = useState(() => dishToForm(null, categories));
  const [stages, setStages] = useState([]);
  const [fraction, setFraction] = useState(0);
  const [phase, setPhase] = useState('edit'); // edit | running | done | error
  const [error, setError] = useState('');
  const [result, setResult] = useState(null);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));
  const onPhoto = (p) => {
    setPhoto(p);
    if (p) setForm((f) => (f.title ? f : { ...f, title: prettifyName(p.name) }));
  };
  const patch = (key, update) => setStages((all) => all.map((s) => (s.key === key ? { ...s, ...update } : s)));
  const canPublish = !!photo && formIsValid(form) && phase !== 'running';

  async function publish() {
    if (!canPublish) return;
    buzz(16);
    setStages(makeStages(PHOTO_STAGES));
    setPhase('running');
    setError('');
    setFraction(0);
    const t0 = performance.now();
    let current = 'prepare';
    const onStage = (key, update) => {
      current = key;
      if (update.status === 'active') setFraction(0);
      patch(key, update);
    };
    try {
      onStage('prepare', {
        status: 'done',
        detail: `${photo.width}×${photo.height} ${photo.mime === 'image/webp' ? 'WebP' : 'JPEG'} · ${fmtBytes(photo.finalSize)} (−${savedPercent(photo.originalSize, photo.finalSize)}%)`,
      });
      const dish = await uploadDishPhoto({ photo, meta: form, onStage, onProgress: setFraction });
      setResult({ dish, seconds: (performance.now() - t0) / 1000 });
      setPhase('done');
      buzz(30);
      onPublished(dish);
    } catch (e) {
      patch(current, { status: 'error', detail: e.message });
      setError(e.message);
      setPhase('error');
      buzz(60);
    }
  }

  function reset() {
    setPhoto(null);
    setForm(dishToForm(null, categories));
    setStages([]);
    setPhase('edit');
    setResult(null);
  }

  const overall = overallProgress(stages, fraction);

  if (phase === 'running' || phase === 'done') {
    return (
      <div className="mx-auto max-w-2xl">
        <PipelineView stages={stages} overall={overall} title="Publishing to menu" />
        {phase === 'done' && result && (
          <div className="fade mt-5 rounded-3xl border border-amber-500/30 bg-gradient-to-b from-amber-500/10 to-transparent p-6 text-center">
            <div className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-amber-500 text-zinc-950">
              <CircleCheck size={28} aria-hidden="true" />
            </div>
            <div className="mt-3 font-display text-3xl font-semibold">{result.dish.dishTitle} is live</div>
            <div className="mt-1 text-sm text-zinc-400">
              Published in {result.seconds.toFixed(1)}s to your live menu.
            </div>
            <button type="button" onClick={reset} className="tap mx-auto mt-5 flex h-12 items-center gap-2 rounded-full bg-amber-500 px-6 text-sm font-bold text-zinc-950">
              <Sparkles size={17} aria-hidden="true" /> Add another dish
            </button>
          </div>
        )}
        {phase === 'running' && (
          <div className="mt-4 flex items-center justify-center gap-2 text-sm text-zinc-400">
            <LoaderCircle size={16} className="animate-spin" aria-hidden="true" /> Keep this page open while we save your dish.
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
      <div className="min-w-0 space-y-6">
        <section>
          <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-zinc-200">
            <span className="grid h-6 w-6 place-items-center rounded-full bg-amber-500 text-xs font-bold text-zinc-950">1</span>
            Dish photo <span className="font-normal text-zinc-500">(required)</span>
          </h3>
          <PhotoPicker photo={photo} onPhoto={onPhoto} />
        </section>

        <section className="rounded-3xl border border-amber-500/20 bg-gradient-to-br from-amber-500/5 via-amber-950/10 to-transparent p-5">
          <div className="flex items-start gap-3">
            <div className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-amber-500/10 text-amber-300">
              <Box size={20} aria-hidden="true" />
            </div>
            <div>
              <h4 className="text-sm font-semibold text-amber-200">White-Glove 3D Digital Twin Concierge</h4>
              <p className="mt-1 text-xs leading-relaxed text-zinc-400">
                To guarantee photorealistic Michelin-standard 3D dishes and WebAR, our culinary 3D team reconstructs every model for you.
                Once you publish your dish photo, click <strong className="text-amber-300">“Request 3D Model”</strong> on its card to upload 5 simple angles.
              </p>
            </div>
          </div>
        </section>
      </div>

      <div className="min-w-0">
        <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-zinc-200">
          <span className="grid h-6 w-6 place-items-center rounded-full border border-amber-500/50 text-xs font-bold text-amber-300">2</span>
          Menu details
        </h3>
        <DishFields form={form} set={set} currencyLabel={currencyLabel} categories={categories} />
        {phase === 'error' && (
          <>
            <div className="mt-5">
              <PipelineView stages={stages} overall={overall} title="Publishing to menu" />
            </div>
            <div className="mt-3 flex gap-2 rounded-2xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200" role="alert">
              <TriangleAlert size={17} className="mt-0.5 shrink-0" aria-hidden="true" />
              <span>{error}</span>
            </div>
          </>
        )}
        <button
          type="button"
          disabled={!canPublish}
          onClick={publish}
          className={cx(
            'tap mt-6 flex h-14 w-full items-center justify-center gap-2 rounded-2xl text-base font-bold',
            canPublish ? 'bg-gradient-to-b from-amber-400 to-amber-600 text-zinc-950 shadow-fab' : 'bg-zinc-800 text-zinc-500',
          )}
        >
          <Sparkles size={19} aria-hidden="true" /> {phase === 'error' ? 'Retry publish' : 'Publish to live menu'}
        </button>
        {!photo && <p className="mt-2 text-center text-xs text-zinc-500">Choose a photo above to publish your dish.</p>}
      </div>
    </div>
  );
}
