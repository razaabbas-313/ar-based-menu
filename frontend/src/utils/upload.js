import { api, putToS3 } from '../api/client.js';
import { fmtBytes, fmtCountdown } from './format.js';
import { sanitizeFilename } from './media.js';

/**
 * The zero-server-RAM pipelines. Each helper reports progress through `onStage(key, patch)`
 * (patch: { status: 'active' | 'done' | 'error', detail }) and `onProgress(fraction)` while
 * bytes stream browser -> S3. File bytes never pass through the API server.
 */

/**
 * Seconds a presigned URL stays valid, measured from when we received it. Uses the server's
 * relative `expiresInSeconds` so a skewed phone clock can't show "valid 0:00"; falls back to the
 * absolute timestamp only when it is plausible, else to the 5-minute default.
 */
export function presignTtlSeconds(presign, now = Date.now()) {
  const relative = Number(presign?.expiresInSeconds);
  if (Number.isFinite(relative) && relative > 0) return Math.min(relative, 7 * 24 * 3600);
  const absolute = Math.round((Date.parse(presign?.expiresAt) - now) / 1000);
  if (Number.isFinite(absolute) && absolute > 0 && absolute <= 3600) return absolute;
  return 300;
}

const noop = () => {};

async function stream(presign, blob, onStage, onProgress, stageKey) {
  onStage(stageKey, { status: 'active' });
  const t0 = performance.now();
  const { etag } = await putToS3(presign.uploadUrl, blob, presign.requiredHeaders, (p) => {
    onProgress(p);
    onStage(stageKey, { detail: `${fmtBytes(blob.size * p)} / ${fmtBytes(blob.size)} · Instant lightning upload` });
  });
  const secs = Math.max(0.05, (performance.now() - t0) / 1000);
  onStage(stageKey, {
    status: 'done',
    detail: `${fmtBytes(blob.size)} in ${secs.toFixed(1)}s · Instant lightning upload`,
  });
  return etag;
}

/** New dish: photo presign -> PUT -> confirm. Returns the published dish. */
export async function uploadDishPhoto({ photo, meta, onStage = noop, onProgress = noop }) {
  onStage('sign', { status: 'active' });
  const legacyCategory = (() => {
    if (!meta.category) return 'MAINS';
    const norm = String(meta.category).toUpperCase().replace(/[-\s]/g, '_').replace(/'/g, '');
    if (['STARTERS', 'MAINS', 'CHEF_SPECIALS', 'DESSERTS', 'DRINKS'].includes(norm)) return norm;
    if (norm.includes('CHEF')) return 'CHEF_SPECIALS';
    if (norm.includes('START') || norm.includes('APPETIZ')) return 'STARTERS';
    if (norm.includes('DESSERT') || norm.includes('SWEET')) return 'DESSERTS';
    if (norm.includes('DRINK') || norm.includes('BEVERAG')) return 'DRINKS';
    return 'MAINS';
  })();

  const presign = await api.presign({
    filename: sanitizeFilename(photo.name, photo.ext),
    contentType: photo.mime,
    dishTitle: meta.title.trim().slice(0, 160),
    priceTag: Number(Number(meta.price).toFixed(2)),
    dishCategory: legacyCategory,
    categoryId: meta.categoryId || null,
    mediaType: meta.special ? 'DAILY_SPECIAL' : 'IMAGE_2D',
    originalSizeBytes: photo.originalSize,
    uploadSizeBytes: photo.blob.size,
    arPlacement: meta.placement || 'table',
    description: meta.description?.trim() || null,
    dietaryTags: meta.dietaryTags || [],
    available: meta.available !== false,
  });
  onStage('sign', { status: 'done', detail: 'Secure upload active' });

  const etag = await stream(presign, photo.blob, onStage, onProgress, 'stream');

  onStage('sync', { status: 'active' });
  const dish = await api.confirm({
    s3Key: presign.objectKey,
    etag,
    finalSizeBytes: photo.blob.size,
    widthPx: photo.width,
    heightPx: photo.height,
    hasArSupport: false,
  });
  onStage('sync', { status: 'done', detail: 'Dish published · menu updated' });
  return dish;
}

/** Attach or replace a 3D model on an existing dish: attach-3d -> PUT -> confirm-3d. */
export async function attachModel({ dishId, model, placement, onStage = noop, onProgress = noop }) {
  onStage('model-sign', { status: 'active' });
  const presign = await api.attach3d(dishId, {
    filename: sanitizeFilename(model.name, model.meta.ext),
    contentType: model.meta.contentType,
    uploadSizeBytes: model.file.size,
    arPlacement: placement || 'table',
  });
  onStage('model-sign', { status: 'done', detail: 'Secure upload active' });

  const etag = await stream(presign, model.file, onStage, onProgress, 'model-stream');

  onStage('model-sync', { status: 'active' });
  const dish = await api.confirm3d(dishId, { s3Key: presign.objectKey, etag });
  onStage('model-sync', { status: 'done', detail: '3D model linked · AR enabled' });
  return dish;
}

/** Logo / banner: presign -> PUT -> confirm. Returns the updated studio settings. */
export async function uploadBrandingImage(kind, image, onProgress = noop) {
  const presign = await api.brandingPresign(kind, {
    filename: sanitizeFilename(image.name || kind, image.ext),
    contentType: image.mime,
    uploadSizeBytes: image.blob.size,
  });
  await putToS3(presign.uploadUrl, image.blob, presign.requiredHeaders, onProgress);
  return api.brandingConfirm(kind, { s3Key: presign.objectKey });
}

/**
 * In-place replacement of a dish's cover photo or 3D model:
 * replace-presign -> PUT straight to S3 -> replace-confirm (S3 HEAD + swap + old object removed).
 * Stage keys: 'sign' | 'stream' | 'sync'. Returns the updated dish.
 */
export async function replaceDishAsset({ dishId, asset, placement, onStage = noop, onProgress = noop }) {
  const isModel = asset.kind === 'model';
  const blob = isModel ? asset.model.file : asset.photo.blob;
  onStage('sign', { status: 'active' });
  const presign = await api.replacePresign(dishId, {
    assetType: isModel ? 'MODEL' : 'PHOTO',
    filename: isModel ? sanitizeFilename(asset.model.name, asset.model.meta.ext) : sanitizeFilename(asset.photo.name, asset.photo.ext),
    contentType: isModel ? asset.model.meta.contentType : asset.photo.mime,
    uploadSizeBytes: blob.size,
    originalSizeBytes: isModel ? null : asset.photo.originalSize,
    arPlacement: isModel ? placement || 'table' : null,
  });
  onStage('sign', { status: 'done', detail: 'Secure upload active' });

  const etag = await stream(presign, blob, onStage, onProgress, 'stream');

  onStage('sync', { status: 'active' });
  const dish = await api.replaceConfirm(dishId, {
    assetType: isModel ? 'MODEL' : 'PHOTO',
    s3Key: presign.objectKey,
    etag,
    widthPx: isModel ? null : asset.photo.width,
    heightPx: isModel ? null : asset.photo.height,
  });
  onStage('sync', { status: 'done', detail: 'Dish updated · menu updated' });
  return dish;
}
