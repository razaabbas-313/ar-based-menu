/** Curated, local-font design tokens shared by the studio preview and live guest renderer. */
const base = {
  preset: 'LEGACY',
  accent: '#e6c36a',
  background: '#0a0a0c',
  surface: '#18181b',
  text: '#f6efdf',
  heading: 'SERIF',
  layout: 'cards',
  category: 'pills',
  button: 'rounded',
  motion: 'subtle',
  card: 'soft'
};
export const PRESETS = {
  LEGACY: {
    ...base
  },
  LUXURY: {
    ...base,
    preset: 'LUXURY'
  },
  MINIMAL: {
    ...base,
    preset: 'MINIMAL',
    accent: '#444444',
    background: '#faf9f6',
    surface: '#ffffff',
    text: '#202022',
    heading: 'SANS',
    card: 'outlined'
  },
  DARK: {
    ...base,
    preset: 'DARK',
    accent: '#b5d5d0',
    background: '#101716',
    surface: '#1c2524',
    text: '#f3faf8',
    heading: 'SANS'
  },
  ORGANIC: {
    ...base,
    preset: 'ORGANIC',
    accent: '#c2d6a1',
    background: '#172018',
    surface: '#263328',
    text: '#f4f6e9'
  },
  CONTEMPORARY: {
    ...base,
    preset: 'CONTEMPORARY',
    accent: '#e4b9d4',
    background: '#18151f',
    surface: '#292232',
    text: '#faf5fb',
    heading: 'SANS'
  },
  CLASSIC: {
    ...base,
    preset: 'CLASSIC',
    accent: '#65422b',
    background: '#f6f0e4',
    surface: '#fffaf1',
    text: '#292016',
    button: 'square',
    category: 'underline'
  }
};
function light(hex) {
  return [1, 3, 5].reduce((sum, at, i) => {
    let n = parseInt(hex.slice(at, at + 2), 16) / 255;
    return sum + [.2126, .7152, .0722][i] * (n <= .04045 ? n / 12.92 : ((n + .055) / 1.055) ** 2.4);
  }, 0);
}
export function appearanceStyle(value) {
  const p = {
    ...base,
    ...value
  };
  const color = (v, fallback) => /^#[a-f0-9]{6}$/i.test(v || '') ? v : fallback;
  const accent = color(p.accent, base.accent);
  const rgb = [1, 3, 5].map(i => parseInt(accent.slice(i, i + 2), 16)).join(' ');
  return {
    '--brand-bg': color(p.background, base.background),
    '--brand-surface': color(p.surface, base.surface),
    '--brand-text': color(p.text, base.text),
    '--brand-accent': accent,
    '--brand-onaccent': (light(accent) + .05) / .05 >= 1.05 / (light(accent) + .05) ? '#000000' : '#ffffff',
    '--accent-300': rgb,
    '--accent-400': rgb,
    '--accent-500': rgb,
    '--accent-600': rgb,
    '--brand-heading': p.heading === 'SANS' ? '"Manrope", Arial, sans-serif' : '"Cormorant Garamond", Georgia, serif',
    '--brand-radius': p.button === 'square' ? '8px' : '20px',
    '--brand-motion': p.motion === 'reduced' ? '0ms' : p.motion === 'standard' ? '300ms' : '150ms'
  };
}
export function brandedRestaurant(original,appearance,apiBase=''){
  if(!appearance)return original;
  const result={...original,menuLayout:['cards','compact'].includes(appearance.layout)?appearance.layout:original.menuLayout};
  for(const key of ['logoUrl','bannerUrl']){const value=appearance[key];if(typeof value!=='string')continue;if(/^\/api\/v1\/public\/brand-assets\/[a-f0-9-]{36}$/i.test(value))result[key]=apiBase+value;else if(/^https:\/\//.test(value))result[key]=value;}
  return result;
}
