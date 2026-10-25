export const DEFAULT_CURRENCY = import.meta.env.VITE_CURRENCY || 'USD';

/** Guest menu section order. */
export const CATEGORY_ORDER = ['STARTERS', 'MAINS', 'CHEF_SPECIALS', 'DESSERTS', 'DRINKS'];

export const CATEGORY_LABEL = {
  STARTERS: 'Starters',
  MAINS: 'Mains',
  CHEF_SPECIALS: "Chef's Specials",
  DESSERTS: 'Desserts',
  DRINKS: 'Drinks',
};

export const DIETARY = [
  { key: 'vegan', label: 'Vegan', emoji: '🌱' },
  { key: 'vegetarian', label: 'Vegetarian', emoji: '🌿' },
  { key: 'gluten-free', label: 'Gluten-Free', emoji: '🌾' },
  { key: 'halal', label: 'Halal', emoji: '🥩' },
  { key: 'dairy-free', label: 'Dairy-Free', emoji: '🥛' },
  { key: 'nut-free', label: 'Nut-Free', emoji: '🥜' },
  { key: 'spicy', label: 'Spicy', emoji: '🌶️' },
  { key: 'chef-pick', label: "Chef's Pick", emoji: '👑' },
];
export const DIETARY_LABEL = Object.fromEntries(DIETARY.map((d) => [d.key, `${d.emoji} ${d.label}`]));

export const STATUS_STYLE = {
  ACTIVE: 'border-emerald-500/30 bg-emerald-500/10 text-emerald-300',
  PENDING_ONBOARDING: 'border-amber-500/30 bg-amber-500/10 text-amber-300',
  TRIAL: 'border-sky-500/30 bg-sky-500/10 text-sky-300',
  PENDING: 'border-zinc-600 bg-zinc-800/60 text-zinc-300',
  SUSPENDED: 'border-red-500/30 bg-red-500/10 text-red-300',
  DELETED: 'border-red-500/40 bg-red-950/60 text-red-300',
};

export const cx = (...parts) => parts.filter(Boolean).join(' ');

/** Short haptic tick on devices that support it. */
export function buzz(ms = 8) {
  try {
    if (navigator.vibrate) navigator.vibrate(ms);
  } catch {
    /* unsupported */
  }
}

export function fmtBytes(bytes) {
  if (bytes == null || Number.isNaN(Number(bytes))) return '—';
  const b = Number(bytes);
  if (b < 1024) return `${Math.round(b)} B`;
  if (b < 1024 * 1024) return `${(b / 1024).toFixed(b < 10240 ? 1 : 0)} KB`;
  if (b < 1024 * 1024 * 1024) return `${(b / 1024 / 1024).toFixed(1)} MB`;
  return `${(b / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

/** Price in the restaurant's currency; falls back to symbol + amount for unknown codes. */
export function fmtPrice(value, currency = DEFAULT_CURRENCY, symbol) {
  const n = Number(value);
  if (!Number.isFinite(n)) return '';
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: n % 1 === 0 ? 0 : 2,
    }).format(n);
  } catch {
    return `${symbol || currency} ${n % 1 === 0 ? n : n.toFixed(2)}`;
  }
}

export function fmtCountdown(totalSeconds) {
  const s = Math.max(0, Math.round(totalSeconds));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function fmtDate(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
  } catch {
    return iso;
  }
}

/** Same normalization as the server's SlugPolicy, for live previews. */
export function slugify(input) {
  return (input || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
    .replace(/-+$/, '');
}

/** WhatsApp click-to-chat link from a phone number in any common format. */
export function whatsappLink(number, text) {
  const digits = String(number || '').replace(/[^0-9]/g, '');
  if (!digits) return null;
  return `https://wa.me/${digits}${text ? `?text=${encodeURIComponent(text)}` : ''}`;
}

/** Only ever render http(s) links from restaurant-provided data. */
export function safeHttpUrl(url) {
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : null;
}
