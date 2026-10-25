import { useCallback, useRef, useState } from 'react';
import { Hand, Smartphone, Sun, SunDim } from 'lucide-react';
import ModelStage from '../ModelStage.jsx';
import { buzz, cx } from '../../utils/format.js';

const SHOWCASE_SRC = '/showcase/atelier-signature.glb';

/**
 * Interactive hero piece: a plated dessert on a virtual pedestal that visitors can rotate, zoom
 * and (on phones) place on their own table in AR.
 */
export default function ShowcaseViewer() {
  const viewer = useRef(null);
  const [ready, setReady] = useState(false);
  const [progress, setProgress] = useState(0);
  const [hint, setHint] = useState(true);
  const [lighting, setLighting] = useState('studio');
  const [arAvailable, setArAvailable] = useState(false);

  const onReady = useCallback((el) => {
    setReady(true);
    setArAvailable(!!el.canActivateAR);
  }, []);
  const onProgress = useCallback((p) => setProgress(p), []);
  const onInteract = useCallback(() => setHint(false), []);

  return (
    <div className="relative" data-testid="showcase">
      {/* Pedestal glow + floor reflection */}
      <div className="pointer-events-none absolute inset-x-[8%] bottom-[6%] h-[22%] rounded-[50%] bg-[radial-gradient(closest-side,rgba(245,158,11,0.35),transparent)] blur-2xl" />
      <div className={cx('relative aspect-square w-full overflow-hidden rounded-[36px] border border-white/10', lighting === 'warm' ? 'stage-warm' : 'stage-bg')}>
        <div className="absolute inset-0 bg-[radial-gradient(60%_45%_at_50%_30%,rgba(255,255,255,0.06),transparent_70%)]" />
        <ModelStage
          src={SHOWCASE_SRC}
          alt="Chocolate dome with raspberries and gold leaf on a marble pedestal"
          variant="full"
          arPlacement="table"
          lighting={lighting}
          viewerRef={viewer}
          onReady={onReady}
          onProgress={onProgress}
          onInteract={onInteract}
        />
        {!ready && (
          <div className="pointer-events-none absolute bottom-4 left-1/2 -translate-x-1/2 font-mono text-[10px] uppercase tracking-[0.3em] text-zinc-500">
            Plating {Math.round(progress * 100)}%
          </div>
        )}
        {ready && hint && (
          <div className="pointer-events-none absolute inset-x-0 top-4 flex justify-center fade">
            <span className="flex items-center gap-2 rounded-full border border-white/15 bg-black/55 px-3.5 py-1.5 text-[11px] font-medium text-zinc-200 backdrop-blur-md">
              <Hand size={13} aria-hidden="true" /> Drag to rotate · Pinch to zoom
            </span>
          </div>
        )}
        <div className="absolute bottom-3 left-3 right-3 flex items-center justify-between gap-2">
          <div className="flex gap-1 rounded-full border border-white/10 bg-black/50 p-1 backdrop-blur-md">
            {[
              ['studio', Sun, 'Studio light'],
              ['warm', SunDim, 'Warm dining light'],
            ].map(([key, Icon, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => setLighting(key)}
                aria-pressed={lighting === key}
                aria-label={label}
                title={label}
                className={cx('tap grid h-9 w-9 place-items-center rounded-full', lighting === key ? 'bg-white/15 text-amber-200' : 'text-zinc-500 hover:text-zinc-200')}
              >
                <Icon size={15} aria-hidden="true" />
              </button>
            ))}
          </div>
          {arAvailable && (
            <button
              type="button"
              onClick={() => {
                buzz(14);
                viewer.current?.activateAR();
              }}
              className="tap flex h-11 items-center gap-2 rounded-full bg-amber-500 px-4 text-[13px] font-bold text-zinc-950"
            >
              <Smartphone size={16} aria-hidden="true" /> Try it on your table
            </button>
          )}
        </div>
      </div>
      <div className="mt-3 flex items-center justify-between px-1 font-mono text-[10px] uppercase tracking-[0.24em] text-zinc-500">
        <span>Live 3D · true to scale</span>
        <span className="hidden sm:inline">26 cm plate · glTF 2.0</span>
      </div>
    </div>
  );
}
