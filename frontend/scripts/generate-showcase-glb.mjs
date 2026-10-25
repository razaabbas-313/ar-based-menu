/**
 * Generates public/showcase/atelier-signature.glb: the interactive 3D piece on the landing page
 * (a plated chocolate-dome dessert on a marble-and-gold pedestal). Pure Node, no dependencies.
 *
 *   node scripts/generate-showcase-glb.mjs
 *
 * Geometry is built from lathed profiles (true-to-scale metres: the plate is 26 cm wide) with
 * smooth normals inside each profile piece and hard edges between pieces. PBR metallic-roughness
 * materials only, so it renders identically in <model-viewer>, Quick Look and Scene Viewer.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../public/showcase/atelier-signature.glb');
const SEG = 72;

// ------------------------------------------------------------------ geometry helpers

function lathe(pieces, segments = SEG) {
  const positions = [];
  const normals = [];
  const indices = [];
  for (const piece of pieces) {
    const n = piece.length;
    const prof = piece.map(([r, y], i) => {
      const [r0, y0] = piece[Math.max(0, i - 1)];
      const [r1, y1] = piece[Math.min(n - 1, i + 1)];
      let dr = r1 - r0;
      let dy = y1 - y0;
      const len = Math.hypot(dr, dy) || 1;
      dr /= len;
      dy /= len;
      return { r, y, nr: dy, ny: -dr };
    });
    const base = positions.length / 3;
    for (let i = 0; i < n; i += 1) {
      for (let j = 0; j <= segments; j += 1) {
        const t = (j / segments) * Math.PI * 2;
        const c = Math.cos(t);
        const s = Math.sin(t);
        const p = prof[i];
        positions.push(p.r * c, p.y, p.r * s);
        normals.push(p.nr * c, p.ny, p.nr * s);
      }
    }
    const row = segments + 1;
    for (let i = 0; i < n - 1; i += 1) {
      for (let j = 0; j < segments; j += 1) {
        const a = base + i * row + j;
        const b = base + (i + 1) * row + j;
        const c = base + (i + 1) * row + j + 1;
        const d = base + i * row + j + 1;
        pushTri(positions, normals, indices, a, b, c);
        pushTri(positions, normals, indices, a, c, d);
      }
    }
  }
  return { positions, normals, indices };
}

/** Adds a triangle wound counter-clockwise as seen from the side its normals point to. */
function pushTri(P, N, I, a, b, c) {
  const v = (k) => [P[k * 3], P[k * 3 + 1], P[k * 3 + 2]];
  const [ax, ay, az] = v(a);
  const [bx, by, bz] = v(b);
  const [cx, cy, cz] = v(c);
  const ux = bx - ax, uy = by - ay, uz = bz - az;
  const wx = cx - ax, wy = cy - ay, wz = cz - az;
  const fx = uy * wz - uz * wy, fy = uz * wx - ux * wz, fz = ux * wy - uy * wx;
  if (Math.hypot(fx, fy, fz) < 1e-12) return; // degenerate (pole)
  const nx = N[a * 3] + N[b * 3] + N[c * 3];
  const ny = N[a * 3 + 1] + N[b * 3 + 1] + N[c * 3 + 1];
  const nz = N[a * 3 + 2] + N[b * 3 + 2] + N[c * 3 + 2];
  if (fx * nx + fy * ny + fz * nz >= 0) I.push(a, b, c);
  else I.push(a, c, b);
}

const arc = (cx, cy, rad, a0, a1, steps) =>
  Array.from({ length: steps + 1 }, (_, i) => {
    const a = a0 + ((a1 - a0) * i) / steps;
    return [Math.max(0, cx + rad * Math.cos(a)), cy + rad * Math.sin(a)];
  });

function sphere(radius, squashY = 1, steps = 24) {
  return lathe([arc(0, 0, radius, -Math.PI / 2, Math.PI / 2, steps).map(([r, y]) => [r, y * squashY])], 36);
}

function translate(mesh, x, y, z) {
  const p = mesh.positions.slice();
  for (let i = 0; i < p.length; i += 3) {
    p[i] += x;
    p[i + 1] += y;
    p[i + 2] += z;
  }
  return { ...mesh, positions: p };
}

function merge(...meshes) {
  const out = { positions: [], normals: [], indices: [] };
  for (const m of meshes) {
    const base = out.positions.length / 3;
    out.positions.push(...m.positions);
    out.normals.push(...m.normals);
    out.indices.push(...m.indices.map((i) => i + base));
  }
  return out;
}

// ------------------------------------------------------------------ the scene (metres)

const PED_TOP = 0.12;
const pedestal = lathe([
  [[0, 0], [0.17, 0]],
  [[0.17, 0], [0.17, 0.016]],
  [[0.17, 0.016], [0.15, 0.024]],
  [[0.15, 0.024], [0.11, 0.032], [0.095, 0.05], [0.09, 0.085], [0.1, 0.103]],
  [[0.1, 0.103], [0.155, 0.108]],
  [[0.155, 0.108], [0.16, PED_TOP]],
  [[0.16, PED_TOP], [0, PED_TOP]],
]);
const goldRings = merge(
  lathe([[[0.1705, 0.004], [0.1712, 0.008], [0.1705, 0.012]]]),
  lathe([[[0.161, PED_TOP - 0.006], [0.1618, PED_TOP - 0.003], [0.161, PED_TOP]]]),
);

const PLATE = PED_TOP;
const plate = lathe([
  [[0, PLATE + 0.002], [0.07, PLATE + 0.002], [0.075, PLATE]],
  [[0.075, PLATE], [0.1, PLATE + 0.004], [0.122, PLATE + 0.012], [0.13, PLATE + 0.016]],
  [[0.13, PLATE + 0.016], [0.1305, PLATE + 0.018], [0.129, PLATE + 0.019]],
  [[0.129, PLATE + 0.019], [0.11, PLATE + 0.012], [0.085, PLATE + 0.008], [0.075, PLATE + 0.008]],
  [[0.075, PLATE + 0.008], [0.06, PLATE + 0.0075], [0, PLATE + 0.0075]],
]);
const goldRim = lathe([[[0.1288, PLATE + 0.0186], [0.1302, PLATE + 0.0181], [0.1306, PLATE + 0.0172]]]);

const FLOOR = PLATE + 0.0075;
const biscuit = lathe([
  [[0, FLOOR], [0.05, FLOOR]],
  [[0.05, FLOOR], [0.0505, FLOOR + 0.006], [0.05, FLOOR + 0.012]],
  [[0.05, FLOOR + 0.012], [0, FLOOR + 0.012]],
]);
const DOME_BASE = FLOOR + 0.012;
const dome = lathe([
  [[0, DOME_BASE], [0.046, DOME_BASE]],
  arc(0, DOME_BASE, 0.046, 0, Math.PI / 2, 28).map(([r, y]) => [r, DOME_BASE + (y - DOME_BASE) * 1.02]),
]);
const DOME_TOP = DOME_BASE + 0.046 * 1.02;
const goldLeaf = merge(
  translate(sphere(0.011, 0.12, 10), 0.004, DOME_TOP + 0.0008, -0.003),
  translate(sphere(0.006, 0.12, 10), -0.009, DOME_TOP - 0.0012, 0.006),
);
const berries = merge(
  translate(sphere(0.0085), 0.07, FLOOR + 0.0085, 0.028),
  translate(sphere(0.008), 0.082, FLOOR + 0.008, 0.008),
  translate(sphere(0.0075), 0.064, FLOOR + 0.0075, 0.047),
  translate(sphere(0.006), 0.0, DOME_TOP + 0.0055, 0.0),
);
const sauce = merge(
  ...[0, 1, 2, 3, 4, 5].map((i) => {
    const a = -1.9 + i * 0.32;
    const rad = 0.0055 - i * 0.0006;
    return translate(sphere(rad, 0.35, 12), Math.cos(a) * 0.088, FLOOR + rad * 0.3, Math.sin(a) * 0.088);
  }),
);
const cream = translate(lathe([arc(0, 0, 0.012, -Math.PI / 2, Math.PI / 2, 18).map(([r, y]) => [r * (1 - 0.25 * Math.max(0, y / 0.012)), y * 0.9])], 48), -0.06, FLOOR + 0.0105, -0.045);

const MATERIALS = [
  { name: 'Black marble pedestal', pbrMetallicRoughness: { baseColorFactor: [0.035, 0.034, 0.038, 1], metallicFactor: 0.1, roughnessFactor: 0.28 } },
  { name: 'Brushed gold', pbrMetallicRoughness: { baseColorFactor: [0.93, 0.74, 0.38, 1], metallicFactor: 1, roughnessFactor: 0.26 } },
  { name: 'Porcelain', pbrMetallicRoughness: { baseColorFactor: [0.96, 0.95, 0.92, 1], metallicFactor: 0, roughnessFactor: 0.2 } },
  { name: 'Hazelnut sable', pbrMetallicRoughness: { baseColorFactor: [0.55, 0.36, 0.18, 1], metallicFactor: 0, roughnessFactor: 0.85 } },
  { name: 'Mirror glaze chocolate', pbrMetallicRoughness: { baseColorFactor: [0.11, 0.045, 0.025, 1], metallicFactor: 0, roughnessFactor: 0.07 } },
  { name: 'Gold leaf', pbrMetallicRoughness: { baseColorFactor: [1, 0.8, 0.4, 1], metallicFactor: 1, roughnessFactor: 0.18 } },
  { name: 'Raspberry', pbrMetallicRoughness: { baseColorFactor: [0.62, 0.05, 0.11, 1], metallicFactor: 0, roughnessFactor: 0.45 } },
  { name: 'Raspberry coulis', pbrMetallicRoughness: { baseColorFactor: [0.45, 0.02, 0.07, 1], metallicFactor: 0, roughnessFactor: 0.12 } },
  { name: 'Chantilly', pbrMetallicRoughness: { baseColorFactor: [0.98, 0.95, 0.86, 1], metallicFactor: 0, roughnessFactor: 0.55 } },
];
const PARTS = [
  ['Pedestal', pedestal, 0],
  ['Pedestal trim', goldRings, 1],
  ['Plate', plate, 2],
  ['Plate rim', goldRim, 1],
  ['Sable base', biscuit, 3],
  ['Chocolate dome', dome, 4],
  ['Gold leaf', goldLeaf, 5],
  ['Raspberries', berries, 6],
  ['Coulis', sauce, 7],
  ['Chantilly', cream, 8],
];

// ------------------------------------------------------------------ glTF 2.0 binary writer

const chunks = [];
let byteLength = 0;
const bufferViews = [];
const accessors = [];

function addView(typed, target) {
  const pad = (4 - (byteLength % 4)) % 4;
  if (pad) {
    chunks.push(Buffer.alloc(pad));
    byteLength += pad;
  }
  const buf = Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength);
  chunks.push(buf);
  bufferViews.push({ buffer: 0, byteOffset: byteLength, byteLength: buf.length, target });
  byteLength += buf.length;
  return bufferViews.length - 1;
}

function addAccessor(view, componentType, count, type, extra = {}) {
  accessors.push({ bufferView: view, componentType, count, type, ...extra });
  return accessors.length - 1;
}

const meshes = [];
const nodes = [];
for (const [name, mesh, material] of PARTS) {
  const pos = new Float32Array(mesh.positions);
  const nor = new Float32Array(mesh.normals.map((v, i, a) => {
    const k = i - (i % 3);
    const len = Math.hypot(a[k], a[k + 1], a[k + 2]) || 1;
    return v / len;
  }));
  const vertexCount = pos.length / 3;
  const idx = vertexCount > 65535 ? new Uint32Array(mesh.indices) : new Uint16Array(mesh.indices);
  const min = [Infinity, Infinity, Infinity];
  const max = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < pos.length; i += 3) {
    for (let k = 0; k < 3; k += 1) {
      min[k] = Math.min(min[k], pos[i + k]);
      max[k] = Math.max(max[k], pos[i + k]);
    }
  }
  const pAcc = addAccessor(addView(pos, 34962), 5126, vertexCount, 'VEC3', { min, max });
  const nAcc = addAccessor(addView(nor, 34962), 5126, vertexCount, 'VEC3');
  const iAcc = addAccessor(addView(idx, 34963), idx instanceof Uint32Array ? 5125 : 5123, idx.length, 'SCALAR');
  meshes.push({ name, primitives: [{ attributes: { POSITION: pAcc, NORMAL: nAcc }, indices: iAcc, material, mode: 4 }] });
  nodes.push({ name, mesh: meshes.length - 1 });
}

const gltf = {
  asset: { version: '2.0', generator: 'Atelier showcase generator 1.0' },
  scene: 0,
  scenes: [{ name: 'Atelier signature dessert', nodes: nodes.map((_, i) => i) }],
  nodes,
  meshes,
  materials: MATERIALS,
  accessors,
  bufferViews,
  buffers: [{ byteLength: 0 }],
};

const binPad = (4 - (byteLength % 4)) % 4;
const bin = Buffer.concat([...chunks, Buffer.alloc(binPad)]);
gltf.buffers[0].byteLength = bin.length;
let json = Buffer.from(JSON.stringify(gltf), 'utf8');
json = Buffer.concat([json, Buffer.alloc((4 - (json.length % 4)) % 4, 0x20)]);

const header = Buffer.alloc(12);
header.writeUInt32LE(0x46546c67, 0); // "glTF"
header.writeUInt32LE(2, 4);
header.writeUInt32LE(12 + 8 + json.length + 8 + bin.length, 8);
const jsonHeader = Buffer.alloc(8);
jsonHeader.writeUInt32LE(json.length, 0);
jsonHeader.writeUInt32LE(0x4e4f534a, 4); // "JSON"
const binHeader = Buffer.alloc(8);
binHeader.writeUInt32LE(bin.length, 0);
binHeader.writeUInt32LE(0x004e4942, 4); // "BIN\0"

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, Buffer.concat([header, jsonHeader, json, binHeader, bin]));
const tris = meshes.reduce((sum, m) => sum + accessors[m.primitives[0].indices].count / 3, 0);
console.log(`Wrote ${OUT} (${(header.readUInt32LE(8) / 1024).toFixed(1)} KB, ${Math.round(tris).toLocaleString()} triangles)`);
