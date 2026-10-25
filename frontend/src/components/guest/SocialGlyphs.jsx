/**
 * Minimal monochrome brand glyphs for the guest contact bar (stroke-based, inherit currentColor,
 * same 24×24 grid as the rest of the icon set).
 */
const base = (size) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
});

export function InstagramGlyph({ size = 16 }) {
  return (
    <svg {...base(size)}>
      <rect x="3" y="3" width="18" height="18" rx="5" />
      <circle cx="12" cy="12" r="4.2" />
      <circle cx="17.3" cy="6.7" r="1" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function TikTokGlyph({ size = 16 }) {
  return (
    <svg {...base(size)}>
      <path d="M14 3v11.5a3.5 3.5 0 1 1-3.5-3.5" />
      <path d="M14 3c.4 2.6 2.2 4.4 5 4.7" />
    </svg>
  );
}

export function WhatsAppGlyph({ size = 16 }) {
  return (
    <svg {...base(size)}>
      <path d="M4.5 19.5l1.1-3.6A8.5 8.5 0 1 1 8.4 18.6z" />
      <path d="M9.2 8.6c.2-.5.6-.6.9-.5l.6.1.8 1.8c.1.3 0 .5-.2.7l-.5.6c.6 1.2 1.6 2.2 2.8 2.8l.6-.5c.2-.2.4-.3.7-.2l1.8.8.1.6c.1.3 0 .7-.5.9-.8.4-1.8.4-2.7 0a9 9 0 0 1-4.6-4.6c-.4-.9-.4-1.9.2-2.5z" fill="currentColor" stroke="none" />
    </svg>
  );
}

export function FacebookGlyph({ size = 16 }) {
  return (
    <svg {...base(size)}>
      <path d="M14.5 21v-7.5h2.6l.4-3h-3V8.6c0-.9.3-1.5 1.6-1.5h1.5V4.4a20 20 0 0 0-2.3-.1c-2.3 0-3.8 1.4-3.8 3.9v2.3H9v3h2.5V21" />
    </svg>
  );
}
