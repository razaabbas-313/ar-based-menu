/**
 * Restaurant-wide QR "table stand" card: one vector layout used for the on-screen preview, the
 * SVG download, the 2000×2000 PNG and the print-ready 4×6" / A6 PDF (via the browser's print dialog).
 *
 * The QR itself is drawn module by module (error correction H, quiet zone kept) with rounded
 * finder "eyes"; dark modules always sit on a light panel so every phone camera can read it.
 */

export const QR_THEMES = {
  onyx: {
    label: 'Dark Onyx & Gold',
    bg: '#0b0b0d',
    glow: '#d4af37',
    frame: '#c9a24a',
    frameSoft: 'rgba(201,162,74,0.35)',
    name: '#f4ead2',
    text: '#d6c9a8',
    muted: '#8c826c',
    panel: '#f6f0e1',
    ink: '#0b0b0d',
    eye: '#0b0b0d',
  },
  ivory: {
    label: 'Crisp White & Black',
    bg: '#ffffff',
    glow: null,
    frame: '#111111',
    frameSoft: 'rgba(17,17,17,0.25)',
    name: '#0b0b0d',
    text: '#2b2b2b',
    muted: '#6b6b6b',
    panel: '#ffffff',
    ink: '#0b0b0d',
    eye: '#0b0b0d',
  },
};

/** Print formats: physical size for SVG/PDF; the layout is drawn on a 1000-unit wide canvas. */
export const QR_FORMATS = {
  '4x6': { label: '4 × 6 in table tent', width: '4in', height: '6in', page: '4in 6in', w: 1000, h: 1500 },
  a6: { label: 'A6 (105 × 148 mm)', width: '105mm', height: '148mm', page: '105mm 148mm', w: 1000, h: 1410 },
  square: { label: 'Square 2000 px', width: '2000px', height: '2000px', page: null, w: 1000, h: 1000 },
};

export const QR_TAGLINE = 'Scan to Explore Our Menu in 3D & Augmented Reality';

const SERIF = "'Cormorant Garamond', 'Playfair Display', Georgia, 'Times New Roman', serif";
const SANS = "Manrope, 'Helvetica Neue', Arial, sans-serif";

/** Public menu URL encoded in the QR (override the origin with VITE_PUBLIC_MENU_ORIGIN in production). */
export function menuUrl(slug) {
  const origin = (import.meta.env.VITE_PUBLIC_MENU_ORIGIN || window.location.origin).replace(/\/+$/, '');
  return `${origin}/r/${encodeURIComponent(slug)}`;
}

/** QR matrix with error correction H (up to 30% damage, survives a small logo or scratches on acrylic). */
export async function buildQrMatrix(text) {
  const mod = await import('qrcode');
  const lib = mod.default || mod;
  const qr = lib.create(text, { errorCorrectionLevel: 'H' });
  const { size } = qr.modules;
  return { size, get: (row, col) => !!qr.modules.get(row, col) };
}

export function escapeXml(value) {
  return String(value ?? '').replace(/[<>&"']/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;', "'": '&apos;' })[c]);
}

/** Greedy word wrap into at most `maxLines` lines of roughly `maxChars` characters. */
function wrap(text, maxChars, maxLines) {
  const words = String(text).trim().split(/\s+/);
  const lines = [];
  let line = '';
  for (const word of words) {
    const candidate = line ? `${line} ${word}` : word;
    if (candidate.length > maxChars && line) {
      lines.push(line);
      line = word;
    } else {
      line = candidate;
    }
  }
  if (line) lines.push(line);
  if (lines.length > maxLines) {
    const kept = lines.slice(0, maxLines);
    kept[maxLines - 1] = `${kept[maxLines - 1].replace(/[.,;:\s]+$/, '')}…`;
    return kept;
  }
  return lines;
}

function isFinder(size, r, c) {
  const inBox = (r0, c0) => r >= r0 && r < r0 + 7 && c >= c0 && c < c0 + 7;
  return inBox(0, 0) || inBox(0, size - 7) || inBox(size - 7, 0);
}

/** QR modules as SVG (path for data modules, rounded squares for the three finder eyes). */
function qrSvg(matrix, x, y, sizePx, color) {
  const n = matrix.size;
  const cell = sizePx / n;
  let d = '';
  for (let r = 0; r < n; r += 1) {
    for (let c = 0; c < n; c += 1) {
      if (matrix.get(r, c) && !isFinder(n, r, c)) {
        const px = (x + c * cell).toFixed(2);
        const py = (y + r * cell).toFixed(2);
        const s = (cell + 0.02).toFixed(2); // tiny overlap avoids hairline seams when rasterized
        d += `M${px} ${py}h${s}v${s}h-${s}z`;
      }
    }
  }
  const eye = (r0, c0) => {
    const ex = x + c0 * cell;
    const ey = y + r0 * cell;
    return [
      `<rect x="${ex.toFixed(2)}" y="${ey.toFixed(2)}" width="${(7 * cell).toFixed(2)}" height="${(7 * cell).toFixed(2)}" rx="${(1.8 * cell).toFixed(2)}" fill="${color.eye}"/>`,
      `<rect x="${(ex + cell).toFixed(2)}" y="${(ey + cell).toFixed(2)}" width="${(5 * cell).toFixed(2)}" height="${(5 * cell).toFixed(2)}" rx="${(1.2 * cell).toFixed(2)}" fill="${color.panel}"/>`,
      `<rect x="${(ex + 2 * cell).toFixed(2)}" y="${(ey + 2 * cell).toFixed(2)}" width="${(3 * cell).toFixed(2)}" height="${(3 * cell).toFixed(2)}" rx="${(0.8 * cell).toFixed(2)}" fill="${color.eye}"/>`,
    ].join('');
  };
  return `<path d="${d}" fill="${color.ink}"/>${eye(0, 0)}${eye(0, n - 7)}${eye(n - 7, 0)}`;
}

/**
 * Builds the complete card as an SVG string.
 * @param {object} o
 * @param {{size:number, get:Function}} o.matrix QR matrix from buildQrMatrix
 * @param {string} o.name   restaurant name (serif title)
 * @param {string} o.url    the encoded URL, printed small under the QR
 * @param {'onyx'|'ivory'} o.theme
 * @param {'4x6'|'a6'|'square'} o.format
 * @param {string} [o.fontCss] optional @font-face CSS (embedded web font for exports)
 * @param {boolean} [o.physicalSize] set width/height in inches/mm (SVG download & print)
 */
export function qrCardSvg({ matrix, name, url, theme = 'onyx', format = '4x6', fontCss = '', physicalSize = false }) {
  const t = QR_THEMES[theme] || QR_THEMES.onyx;
  const f = QR_FORMATS[format] || QR_FORMATS['4x6'];
  const W = f.w;
  const H = f.h;
  const square = format === 'square';
  const inset = 44;

  // Title: 1-2 lines; the font shrinks until the name fits the frame without truncation.
  const sizes = square ? [76, 64, 54] : [92, 78, 66, 56];
  const fitChars = (size) => Math.max(8, Math.floor((W - 2 * inset - 140) / (size * 0.56)));
  let titleSize = sizes[sizes.length - 1];
  for (const size of sizes) {
    if (wrap(name || 'Our Menu', fitChars(size), 99).length <= 2) {
      titleSize = size;
      break;
    }
  }
  const titleLines = wrap(name || 'Our Menu', fitChars(titleSize), 2);
  const titleBlock = titleLines.length * titleSize * 1.04;

  const top = square ? 150 : 178;
  const kickerY = top - 44;
  const titleY = top + titleSize * 0.78;
  const dividerY = top + titleBlock + (square ? 26 : 34);
  const panelPad = square ? 30 : 40;
  const panelY = dividerY + (square ? 34 : 56);
  const taglineLines = square ? [QR_TAGLINE] : ['Scan to Explore Our Menu', 'in 3D & Augmented Reality'];
  const taglineSize = square ? 32 : 38;
  // Lay the footer out from the bottom up, then give the QR whatever height is left.
  const brandY = H - inset - (square ? 40 : 60);
  const urlY = brandY - (square ? 52 : 62);
  const taglineLastY = urlY - (square ? 40 : 46);
  const taglineY = taglineLastY - (taglineLines.length - 1) * 42;
  const panelBottom = taglineY - taglineSize - (square ? 22 : 34);
  const preferred = square ? 400 : format === 'a6' ? 540 : 580;
  const qrSize = Math.max(240, Math.min(preferred, panelBottom - panelY - 2 * panelPad, W - 2 * inset - 2 * panelPad - 80));
  const panelH = qrSize + 2 * panelPad;
  const panelX = (W - qrSize - 2 * panelPad) / 2;
  const qrX = panelX + panelPad;
  const qrY = panelY + Math.max(0, (panelBottom - panelY - panelH) / 2) + panelPad;
  const panelTop = qrY - panelPad;

  const prettyUrl = url.replace(/^https?:\/\//, '');
  const mid = W / 2;
  const sizeAttrs = physicalSize ? `width="${f.width}" height="${f.height}"` : `width="100%" height="100%"`;

  return `<svg xmlns="http://www.w3.org/2000/svg" ${sizeAttrs} viewBox="0 0 ${W} ${H}" role="img" aria-label="${escapeXml(`${name} menu QR code`)}">
<defs>
  ${fontCss ? `<style>${fontCss}</style>` : ''}
  ${t.glow ? `<radialGradient id="glow" cx="50%" cy="0%" r="85%"><stop offset="0%" stop-color="${t.glow}" stop-opacity="0.22"/><stop offset="60%" stop-color="${t.glow}" stop-opacity="0.04"/><stop offset="100%" stop-color="${t.glow}" stop-opacity="0"/></radialGradient>` : ''}
  ${t.glow ? `<linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#f1d98f"/><stop offset="50%" stop-color="#c9a24a"/><stop offset="100%" stop-color="#8f6f2a"/></linearGradient>` : ''}
</defs>
<rect width="${W}" height="${H}" fill="${t.bg}"/>
${t.glow ? `<rect width="${W}" height="${H}" fill="url(#glow)"/>` : ''}
<rect x="${inset}" y="${inset}" width="${W - 2 * inset}" height="${H - 2 * inset}" rx="34" fill="none" stroke="${t.glow ? 'url(#gold)' : t.frame}" stroke-width="3"/>
<rect x="${inset + 16}" y="${inset + 16}" width="${W - 2 * inset - 32}" height="${H - 2 * inset - 32}" rx="24" fill="none" stroke="${t.frameSoft}" stroke-width="1.2"/>
<text x="${mid}" y="${kickerY}" text-anchor="middle" font-family="${SANS}" font-size="20" font-weight="600" letter-spacing="9" fill="${t.muted}">MENU · CARTE</text>
${titleLines
  .map(
    (line, i) =>
      `<text x="${mid}" y="${(titleY + i * titleSize * 1.04).toFixed(1)}" text-anchor="middle" font-family="${SERIF}" font-size="${titleSize}" font-weight="600" fill="${t.name}">${escapeXml(line)}</text>`,
  )
  .join('\n')}
<line x1="${mid - 150}" y1="${dividerY}" x2="${mid - 22}" y2="${dividerY}" stroke="${t.frame}" stroke-width="1.6"/>
<rect x="${mid - 8}" y="${dividerY - 8}" width="16" height="16" transform="rotate(45 ${mid} ${dividerY})" fill="${t.frame}"/>
<line x1="${mid + 22}" y1="${dividerY}" x2="${mid + 150}" y2="${dividerY}" stroke="${t.frame}" stroke-width="1.6"/>
<rect x="${panelX}" y="${panelTop}" width="${qrSize + 2 * panelPad}" height="${panelH}" rx="30" fill="${t.panel}" ${theme === 'ivory' ? `stroke="${t.frame}" stroke-width="2"` : ''}/>
${qrSvg(matrix, qrX, qrY, qrSize, t)}
${taglineLines
  .map(
    (line, i) =>
      `<text x="${mid}" y="${taglineY + i * 42}" text-anchor="middle" font-family="${SERIF}" font-style="italic" font-size="${taglineSize}" font-weight="500" fill="${t.text}">${escapeXml(line)}</text>`,
  )
  .join('\n')}
<text x="${mid}" y="${urlY}" text-anchor="middle" font-family="${SANS}" font-size="19" letter-spacing="1.5" fill="${t.muted}">${escapeXml(prettyUrl)}</text>
<g transform="translate(${mid} ${brandY})">
  <circle cx="-74" cy="-7" r="17" fill="none" stroke="${t.frame}" stroke-width="1.6"/>
  <text x="-74" y="1" text-anchor="middle" font-family="${SERIF}" font-style="italic" font-size="25" font-weight="600" fill="${t.frame}">A</text>
  <text x="-44" y="0" font-family="${SANS}" font-size="17" font-weight="700" letter-spacing="7" fill="${t.muted}">ATELIER</text>
</g>
</svg>`;
}

/**
 * Embeds just the glyphs of the restaurant name / tagline from Google Fonts so the exported SVG,
 * PNG and PDF keep the serif typography offline. Falls back to system serif silently.
 */
export async function embeddedFontCss(text) {
  try {
    const glyphs = Array.from(new Set(`${text}${QR_TAGLINE}MENU·CARTE…`)).join('');
    const cssUrl = `https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,600;1,500&text=${encodeURIComponent(glyphs)}`;
    const css = await (await fetch(cssUrl)).text();
    const urls = [...css.matchAll(/url\((https:[^)]+)\)/g)].map((m) => m[1]);
    let out = css;
    for (const u of urls) {
      const buf = await (await fetch(u)).arrayBuffer();
      let bin = '';
      const bytes = new Uint8Array(buf);
      for (let i = 0; i < bytes.length; i += 1) bin += String.fromCharCode(bytes[i]);
      out = out.replace(u, `data:font/woff2;base64,${btoa(bin)}`);
    }
    return out;
  } catch {
    return '';
  }
}

export function downloadBlob(blob, filename) {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 10000);
}

/** Rasterizes an SVG string to a PNG blob of `px`×`px` (or the SVG's aspect at `px` width). */
export function svgToPng(svg, widthPx, heightPx) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml;charset=utf-8' }));
    img.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = widthPx;
        canvas.height = heightPx;
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = false; // crisp QR modules
        ctx.drawImage(img, 0, 0, widthPx, heightPx);
        canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('PNG export failed'))), 'image/png');
      } catch (e) {
        reject(e);
      } finally {
        URL.revokeObjectURL(url);
      }
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('This browser could not render the QR card.'));
    };
    img.src = url;
  });
}

/** Opens a print-ready page (exact paper size, no margins); "Save as PDF" in the dialog gives the PDF. */
export function printCard(win, svg, format, title) {
  const f = QR_FORMATS[format] || QR_FORMATS['4x6'];
  win.document.open();
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${escapeXml(title)}</title>
<style>
  @page { size: ${f.page || '4in 6in'}; margin: 0; }
  html, body { margin: 0; padding: 0; background: #fff; }
  .sheet { width: ${f.width}; height: ${f.height}; display: block; }
  .sheet svg { width: 100%; height: 100%; display: block; }
  @media screen { body { display: grid; place-items: center; min-height: 100vh; background: #e4e4e7; } .sheet { box-shadow: 0 20px 60px rgba(0,0,0,.25); } }
  * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
</style></head><body><div class="sheet">${svg}</div>
<script>window.addEventListener('load', function () { setTimeout(function () { window.focus(); window.print(); }, 350); });<\/script>
</body></html>`);
  win.document.close();
}
