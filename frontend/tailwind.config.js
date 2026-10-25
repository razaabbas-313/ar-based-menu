/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        obsidian: '#09090b',
        onyx: '#121214',
        seam: '#27272a',
        // Per-restaurant accent (guest menu theme). Values are "R G B" CSS variables set by utils/theme.js.
        accent: {
          300: 'rgb(var(--accent-300) / <alpha-value>)',
          400: 'rgb(var(--accent-400) / <alpha-value>)',
          500: 'rgb(var(--accent-500) / <alpha-value>)',
          600: 'rgb(var(--accent-600) / <alpha-value>)',
        },
      },
      fontFamily: {
        display: ['"Cormorant Garamond"', 'Georgia', 'serif'],
        sans: ['Manrope', 'system-ui', '-apple-system', '"Segoe UI"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(245,158,11,0.10), 0 24px 60px -24px rgba(245,158,11,0.28)',
        fab: '0 10px 40px -6px rgba(245,158,11,0.55), 0 0 0 1px rgba(251,191,36,0.4) inset',
      },
    },
  },
  plugins: [],
};
