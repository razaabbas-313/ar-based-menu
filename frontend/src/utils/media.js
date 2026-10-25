/**
 * On-device media processing. Nothing here touches the network:
 *  - photos are downsampled on an off-screen canvas and re-encoded to WebP (JPEG where WebP
 *    encoding is unavailable, e.g. Safari) before upload;
 *  - glTF/GLB files are validated and inspected from their header/JSON chunk.
 */

export const MAX_IMAGE_DIM = 1400;
export const IMAGE_QUALITY = 0.82;
export const MAX_MODEL_BYTES = 50 * 1024 * 1024;
export const MAX_FILENAME_BASE = 100;

// ---------------------------------------------------------------- filenames

/**
 * Makes a camera-roll name safe for the presign API (max 255 chars server-side):
 * strips the extension and any path, removes accents and unsafe characters, collapses
 * separators and truncates the base to {@link MAX_FILENAME_BASE} characters.
 */
export function sanitizeFilename(name, ext, maxBase = MAX_FILENAME_BASE) {
  const leaf = String(name || '').split(/[\\/]/).pop();
  const base = leaf
    .replace(/\.[^.]*$/, '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Za-z0-9._-]+/g, '-')
    .replace(/[-_.]{2,}/g, '-')
    .replace(/^[-_.]+|[-_.]+$/g, '')
    .slice(0, maxBase)
    .replace(/[-_.]+$/, '');
  const safeExt = String(ext || '').replace(/[^a-z0-9]/gi, '').toLowerCase().slice(0, 8);
  return `${base || 'dish'}${safeExt ? `.${safeExt}` : ''}`;
}

/** "IMG_2041.HEIC" -> "", "hokkaido_scallop.jpg" -> "Hokkaido Scallop". Used to prefill the title. */
export function prettifyName(name) {
  const base = String(name || '')
    .replace(/\.[^.]+$/, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\b(img|dsc|dscn|pxl|photo|image|scan)\s*\d+/gi, '')
    .replace(/\s+/g, ' ')
    .trim();
  return base ? base.replace(/\b\w/g, (c) => c.toUpperCase()).slice(0, 80) : '';
}

export function savedPercent(originalSize, finalSize) {
  if (!originalSize) return 0;
  return Math.max(0, Math.round((1 - finalSize / originalSize) * 100));
}

// ---------------------------------------------------------------- 2D photos

async function decodeImage(file) {
  if ('createImageBitmap' in window) {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      /* fall back to <img> (older Safari, some HEIC paths) */
    }
  }
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    img.decoding = 'async';
    img.src = url;
    await img.decode();
    return img;
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}

function makeCanvas(w, h) {
  if (typeof OffscreenCanvas !== 'undefined') return new OffscreenCanvas(w, h);
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

function canvasToBlob(canvas, type, quality) {
  if (canvas.convertToBlob) return canvas.convertToBlob({ type, quality });
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

/**
 * GPU-accelerated downsample (default max 1400px, WebP @ 0.82, JPEG fallback where WebP encoding
 * is unavailable). Logos and banners use smaller/larger limits via {@code options}.
 */
export async function downsampleImage(file, { maxDim = MAX_IMAGE_DIM, quality = IMAGE_QUALITY } = {}) {
  const looksLikeImage = file.type.startsWith('image/') || /\.(jpe?g|png|webp|heic|heif|avif)$/i.test(file.name);
  if (!looksLikeImage) throw new Error('Please choose a photo (JPEG, PNG, WebP or HEIC).');

  const t0 = performance.now();
  let src;
  try {
    src = await decodeImage(file);
  } catch {
    throw new Error('This browser could not read that photo. Try a JPEG or PNG.');
  }
  const w0 = src.width || src.naturalWidth;
  const h0 = src.height || src.naturalHeight;
  const scale = Math.min(1, maxDim / Math.max(w0, h0));
  const tw = Math.max(1, Math.round(w0 * scale));
  const th = Math.max(1, Math.round(h0 * scale));

  // Step down by halves for crisp, alias-free results on 12–48 MP phone photos.
  let current = src;
  let cw = w0;
  let ch = h0;
  while (cw / 2 >= tw * 1.05 && ch / 2 >= th * 1.05) {
    const nw = Math.round(cw / 2);
    const nh = Math.round(ch / 2);
    const step = makeCanvas(nw, nh);
    const sctx = step.getContext('2d');
    sctx.imageSmoothingEnabled = true;
    sctx.imageSmoothingQuality = 'high';
    sctx.drawImage(current, 0, 0, nw, nh);
    current = step;
    cw = nw;
    ch = nh;
  }
  const out = makeCanvas(tw, th);
  const ctx = out.getContext('2d');
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(current, 0, 0, tw, th);
  if (src.close) src.close();

  let blob = await canvasToBlob(out, 'image/webp', quality).catch(() => null);
  if (!blob || blob.type !== 'image/webp') {
    const flat = makeCanvas(tw, th);
    const fctx = flat.getContext('2d');
    fctx.fillStyle = '#121214';
    fctx.fillRect(0, 0, tw, th);
    fctx.drawImage(out, 0, 0);
    blob = await canvasToBlob(flat, 'image/jpeg', quality);
  }
  if (!blob) throw new Error('This browser could not encode the photo.');

  return {
    blob,
    mime: blob.type,
    ext: blob.type === 'image/webp' ? 'webp' : 'jpg',
    url: URL.createObjectURL(blob),
    width: tw,
    height: th,
    originalWidth: w0,
    originalHeight: h0,
    originalSize: file.size,
    finalSize: blob.size,
    ms: Math.round(performance.now() - t0),
  };
}

// ---------------------------------------------------------------- 3D models

const GLB_MAGIC = 0x46546c67; // "glTF"
const CHUNK_JSON = 0x4e4f534a; // "JSON"

/** Validates a binary glTF 2.0 (.glb) or self-contained .gltf entirely in the browser. */
export async function inspectModel(file) {
  const ext = (file.name.split('.').pop() || '').toLowerCase();
  if (ext !== 'glb' && ext !== 'gltf') throw new Error('3D dish scans must be .glb or .gltf files.');
  const contentType = ext === 'glb' ? 'model/gltf-binary' : 'model/gltf+json';
  const allowedDeclared = ['', 'model/gltf-binary', 'model/gltf+json', 'application/octet-stream', 'application/json', 'model/gltf'];
  if (!allowedDeclared.includes(file.type)) throw new Error(`Unexpected file type "${file.type}" for a .${ext} model.`);
  if (file.size > MAX_MODEL_BYTES) {
    throw new Error(`This scan is ${(file.size / 1048576).toFixed(1)} MB; the limit is 50 MB. Try compressing it (Draco/Meshopt).`);
  }

  let json;
  if (ext === 'glb') {
    if (file.size < 20) throw new Error('This .glb file is truncated.');
    const head = new DataView(await file.slice(0, 20).arrayBuffer());
    if (head.getUint32(0, true) !== GLB_MAGIC) throw new Error('Not a valid GLB: the "glTF" signature is missing.');
    const version = head.getUint32(4, true);
    if (version !== 2) throw new Error(`GLB version ${version} is not supported (glTF 2.0 required).`);
    const declaredLength = head.getUint32(8, true);
    if (declaredLength > file.size) throw new Error('This .glb file is incomplete (header length exceeds file size).');
    const chunkLength = head.getUint32(12, true);
    if (head.getUint32(16, true) !== CHUNK_JSON) throw new Error('Malformed GLB: first chunk is not JSON.');
    if (20 + chunkLength > file.size) throw new Error('Malformed GLB: JSON chunk runs past the end of the file.');
    try {
      json = JSON.parse(new TextDecoder().decode(await file.slice(20, 20 + chunkLength).arrayBuffer()));
    } catch {
      throw new Error('Malformed GLB: the JSON chunk could not be parsed.');
    }
  } else {
    try {
      json = JSON.parse(await file.text());
    } catch {
      throw new Error('This .gltf file is not valid JSON.');
    }
    const external = [...(json.buffers || []), ...(json.images || [])]
      .map((x) => x.uri)
      .filter((u) => u && !u.startsWith('data:'));
    if (external.length) {
      throw new Error(
        `This .gltf references external files (${external.slice(0, 2).join(', ')}${external.length > 2 ? '…' : ''}). Export it as a single .glb instead.`,
      );
    }
  }
  if (!json.asset || !String(json.asset.version || '').startsWith('2')) throw new Error('Only glTF 2.0 assets are supported.');

  let triangles = 0;
  for (const mesh of json.meshes || []) {
    for (const p of mesh.primitives || []) {
      if (p.mode !== undefined && p.mode !== 4) continue;
      const acc = p.indices !== undefined ? json.accessors?.[p.indices] : json.accessors?.[p.attributes?.POSITION];
      triangles += acc ? Math.floor(acc.count / 3) : 0;
    }
  }
  return {
    contentType,
    ext,
    generator: json.asset.generator || 'Unknown exporter',
    meshes: (json.meshes || []).length,
    materials: (json.materials || []).length,
    textures: (json.textures || []).length,
    animations: (json.animations || []).length,
    triangles,
    compression: (json.extensionsUsed || []).filter((e) => /draco|meshopt|ktx|webp/i.test(e)),
    sizeBytes: file.size,
  };
}

export const LOGO_OPTIONS = { maxDim: 512, quality: 0.88 };
export const BANNER_OPTIONS = { maxDim: 1920, quality: 0.84 };
