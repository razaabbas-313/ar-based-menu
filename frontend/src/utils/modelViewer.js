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
