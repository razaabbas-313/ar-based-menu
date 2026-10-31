/**
 * Loads Google's <model-viewer> (and three.js) on first use only. The guest menu never pays for
 * it on page load; it is fetched when a guest opens a dish's 3D view or taps AR.
 */
let loading = null;

export function loadModelViewer() {
  if (!loading) {
    loading = import('@google/model-viewer').catch((e) => {
      loading = null;
      throw e;
    });
  }
  return loading;
}

export function preloadModelViewer() {
  loadModelViewer().catch(() => {});
}

/** Keep React 18 custom-element flags synchronized, including removal after a policy change. */
export function setViewerFlags(element, { arEnabled = true, autoRotate = true, isCard = false }) {
  const flags={ar: arEnabled, 'camera-controls':true, 'xr-environment':arEnabled, 'auto-rotate':autoRotate, 'disable-zoom':isCard, 'disable-pan':isCard};
  Object.entries(flags).forEach(([name,on])=>element.toggleAttribute(name,!!on));
}
