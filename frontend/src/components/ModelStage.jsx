import { useEffect, useRef, useState } from 'react';
import { WifiOff } from 'lucide-react';
import { loadModelViewer } from '../utils/modelViewer.js';

export const AR_MODES = 'webxr scene-viewer quick-look';

/** Lighting presets for the guest inspector: neutral studio vs. warm candle-lit dining room. */
export const LIGHTING = {
  studio: { exposure: 1, shadow: 1.1, softness: 0.85, environment: 'neutral', filter: 'none' },
  warm: { exposure: 0.92, shadow: 1.35, softness: 1, environment: 'legacy', filter: 'sepia(0.22) saturate(1.18) brightness(0.96) hue-rotate(-6deg)' },
};

/**
 * React wrapper for Google's <model-viewer>. The library (three.js included) is imported the
 * first time a stage mounts, so pages without an open 3D view never download it.
 *
 * - Custom events (load/progress/error) are wired with addEventListener (React 18 does not
 *   forward them to custom elements).
 * - Boolean attributes (ar, camera-controls, auto-rotate, ...) are toggled imperatively.
 * - `viewerRef` exposes the element for activateAR() / canActivateAR; `onReady` fires after load.
 */
export default function ModelStage({
  src,
  alt,
  variant = 'card',
  arPlacement = 'table',
  autoRotate = true,
  exposure = 1,
  shadow = 1.1,
  softness = 0.85,
  environment = 'neutral',
  lighting,
  viewerRef,
  onReady,
  onProgress,
  onInteract,
}) {
  const light = lighting ? LIGHTING[lighting] || LIGHTING.studio : null;
  const exp = light ? light.exposure : exposure;
  const shadowIntensity = light ? light.shadow : shadow;
  const shadowSoftness = light ? light.softness : softness;
  const env = light ? light.environment : environment;
  const ref = useRef(null);
  const [libraryReady, setLibraryReady] = useState(false);
  const [progress, setProgress] = useState(0);
  const [state, setState] = useState('loading');
  const isCard = variant === 'card';

  useEffect(() => {
    let alive = true;
    loadModelViewer()
      .then(() => alive && setLibraryReady(true))
      .catch(() => alive && setState('error'));
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    setState((s) => (s === 'error' && !libraryReady ? s : 'loading'));
    setProgress(0);
  }, [src, libraryReady]);

  useEffect(() => {
    const el = ref.current;
    if (!el || !libraryReady) return undefined;
    if (viewerRef) viewerRef.current = el;

    if (el.loaded) {
      setState('ready');
      setProgress(1);
      onProgress?.(1);
      onReady?.(el);
    }

    const handleProgress = (e) => {
      const p = e.detail?.totalProgress ?? 0;
      setProgress(p);
      onProgress?.(p);
      if (p >= 1 && el.loaded) {
        setState('ready');
        onReady?.(el);
      }
    };
    const onLoad = () => {
      setState('ready');
      setProgress(1);
      onProgress?.(1);
      onReady?.(el);
    };
    const onError = (err) => {
      console.error('[ModelStage] Failed to load 3D model from:', src, err);
      setState('error');
    };
    const onCamera = (e) => {
      if (e.detail?.source === 'user-interaction') onInteract?.();
    };
    el.addEventListener('progress', handleProgress);
    el.addEventListener('load', onLoad);
    el.addEventListener('error', onError);
    el.addEventListener('camera-change', onCamera);
    return () => {
      el.removeEventListener('progress', handleProgress);
      el.removeEventListener('load', onLoad);
      el.removeEventListener('error', onError);
      el.removeEventListener('camera-change', onCamera);
      if (viewerRef && viewerRef.current === el) viewerRef.current = null;
    };
  }, [viewerRef, libraryReady, onReady, onProgress, onInteract, src]);

  useEffect(() => {
    const el = ref.current;
    if (!el || !libraryReady) return;
    const flags = {
      ar: true,
      'camera-controls': true,
      'xr-environment': true,
      'auto-rotate': autoRotate,
      'disable-zoom': isCard,
      'disable-pan': isCard,
    };
    Object.entries(flags).forEach(([name, on]) => el.toggleAttribute(name, !!on));
  }, [autoRotate, isCard, libraryReady]);

  return (
    <div className="relative h-full w-full">
      {libraryReady && (
        <model-viewer
          ref={ref}
          src={src}
          alt={alt || '3D dish'}
          ar-modes={AR_MODES}
          ar-placement="floor"
          ar-scale={arPlacement === 'floor' ? 'auto' : 'fixed'}
          shadow-intensity={String(shadowIntensity)}
          shadow-softness={String(shadowSoftness)}
          exposure={String(exp)}
          environment-image={env}
          tone-mapping={lighting === 'warm' ? 'aces' : 'commerce'}
          style={light ? { filter: light.filter, transition: 'filter 600ms ease' } : undefined}
          camera-orbit={isCard ? '32deg 64deg 92%' : '32deg 62deg 100%'}
          min-camera-orbit="auto 10deg auto"
          max-camera-orbit="auto 88deg auto"
          interpolation-decay="120"
          interaction-prompt={isCard ? 'none' : 'auto'}
          loading="eager"
          reveal="auto"
          touch-action={isCard ? 'pan-y' : 'none'}
          auto-rotate-delay="0"
          rotation-per-second={isCard ? '14deg' : '20deg'}
        />
      )}
      {state === 'loading' && (
        <div className="pointer-events-none absolute inset-0 grid place-items-center fade">
          <div className="flex flex-col items-center gap-3">
            <div className="h-10 w-10 animate-spin rounded-full border border-accent-500/30 border-t-accent-400" />
            <div className="font-mono text-[10px] uppercase tracking-[0.25em] text-zinc-500">
              {libraryReady ? `Plating ${Math.round(progress * 100)}%` : 'Warming up 3D'}
            </div>
          </div>
        </div>
      )}
      {state === 'error' && (
        <div className="absolute inset-0 grid place-items-center p-6 text-center">
          <div className="flex flex-col items-center gap-2 text-zinc-500">
            <WifiOff size={22} aria-hidden="true" />
            <div className="text-xs">3D model unavailable. Check the connection or the S3 bucket read policy.</div>
          </div>
        </div>
      )}
    </div>
  );
}
