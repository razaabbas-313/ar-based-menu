/** Guest-menu accent palettes ("R G B" triplets consumed by the Tailwind `accent-*` colours). */
export const ACCENTS = {
  amber: { label: 'Warm Amber', 300: '252 211 77', 400: '251 191 36', 500: '245 158 11', 600: '217 119 6' },
  emerald: { label: 'Emerald Atelier', 300: '110 231 183', 400: '52 211 153', 500: '16 185 129', 600: '5 150 105' },
  gold: { label: 'Royal Gold', 300: '243 223 162', 400: '230 195 106', 500: '212 175 55', 600: '184 150 46' },
  zinc: { label: 'Minimal Zinc', 300: '228 228 231', 400: '212 212 216', 500: '161 161 170', 600: '113 113 122' },
};

/** Inline style object that themes a subtree. */
export function accentStyle(accent) {
  const a = ACCENTS[accent] || ACCENTS.amber;
  return {
    '--accent-300': a[300],
    '--accent-400': a[400],
    '--accent-500': a[500],
    '--accent-600': a[600],
  };
}

export const CURRENCIES = [
  { code: 'USD', symbol: '$', label: 'US Dollar' },
  { code: 'EUR', symbol: '€', label: 'Euro' },
  { code: 'GBP', symbol: '£', label: 'British Pound' },
  { code: 'AED', symbol: 'AED', label: 'UAE Dirham' },
  { code: 'SAR', symbol: 'SAR', label: 'Saudi Riyal' },
  { code: 'PKR', symbol: 'Rs', label: 'Pakistani Rupee' },
  { code: 'INR', symbol: '₹', label: 'Indian Rupee' },
  { code: 'JPY', symbol: '¥', label: 'Japanese Yen' },
  { code: 'CHF', symbol: 'CHF', label: 'Swiss Franc' },
  { code: 'CAD', symbol: 'CA$', label: 'Canadian Dollar' },
  { code: 'AUD', symbol: 'A$', label: 'Australian Dollar' },
];
