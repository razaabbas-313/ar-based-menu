import { useState } from 'react';
import {
  ArrowRight,
  Box,
  CalendarCheck,
  Camera,
  ChefHat,
  ChevronDown,
  Cloud,
  Gauge,
  Layers,
  LockKeyhole,
  LogIn,
  QrCode,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Wine,
} from 'lucide-react';
import ShowcaseViewer from '../components/landing/ShowcaseViewer.jsx';
import TableStandMockup from '../components/landing/TableStandMockup.jsx';
import ContactModal, { SALES_EMAIL } from '../components/landing/ContactModal.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { buzz, cx } from '../utils/format.js';
import { linkProps } from '../utils/router.js';

const STEPS = [
  {
    icon: Camera,
    title: 'Snap or Scan',
    text: 'Photograph a dish or drop in a .glb scan. Photos are compressed to WebP on the device and 3D files are validated in the browser before a single byte is uploaded.',
    meta: 'Client-side compression & validation',
  },
  {
    icon: QrCode,
    title: 'Print Custom QR Stands',
    text: 'One elegant, high-resolution QR for the whole venue, styled in your brand: onyx & gold or crisp white, ready for 4×6" table tents and acrylic stands.',
    meta: 'PNG 2000 px · SVG · print-ready PDF',
  },
  {
    icon: Smartphone,
    title: 'Guests Experience in AR',
    text: 'Guests scan, browse a refined photo menu and place any dish on their table, true to scale, straight from the mobile browser.',
    meta: 'Quick Look · Scene Viewer · WebXR',
  },
];

const FEATURES = [
  { icon: Wine, stat: '+25–30%', title: 'High-margin dessert & cocktail orders', text: 'Guests order what they can see. Visual and AR menus lift the dishes that matter most to your margins.' },
  { icon: Smartphone, stat: '0', title: 'App downloads', text: 'Native Safari Quick Look on iPhone and Chrome Scene Viewer on Android. Scan, tap, done.' },
  { icon: Cloud, stat: '0 bytes', title: 'Through our servers', text: 'Files stream from the browser straight to encrypted cloud storage with one-time signed URLs.' },
  { icon: LockKeyhole, stat: '100%', title: 'Multi-tenant isolation', text: 'Every restaurant is a private tenant. Menus are never listed publicly; only your QR and link open them.' },
];

const FAQ = [
  {
    q: 'Which phones and devices are supported?',
    a: 'Every modern smartphone opens the menu. AR uses Quick Look on iPhone and iPad (iOS 12+) and Scene Viewer or WebXR on ARCore Android phones. On laptops guests can still rotate dishes in 3D and get a QR to continue on their phone.',
  },
  {
    q: 'What if the restaurant Wi-Fi or mobile signal is slow?',
    a: 'The menu loads compressed WebP photos first, typically well under 100 KB each. A dish’s 3D model only downloads when a guest opens that dish, and it starts preloading the moment they tap it, so it is ready by the time they press “View in 3D”.',
  },
  {
    q: 'How do we update the menu, prices or sold-out dishes?',
    a: 'From the Staff Studio on any phone or laptop: edit prices and descriptions, toggle In Stock / Out of Stock, or replace a photo or 3D model in place. Changes are live within seconds and the printed QR never changes.',
  },
  {
    q: 'Can people browse other restaurants’ menus?',
    a: 'No. There is no public directory. Each menu is reachable only through its own QR code or private link, and every restaurant’s data and files are isolated from every other tenant.',
  },
  {
    q: 'Do we need to create 3D models ourselves?',
    a: 'No. Start with photos only; dishes show a “3D & AR” badge as soon as a model is attached. We can scan your signature dishes during onboarding, or you can upload .glb files from any photogrammetry app.',
  },
];

/** Public B2B landing page for luxury restaurants, hotel F&B directors and culinary ateliers. */
export default function HomePage() {
  const { isAuthenticated, restaurant, openAuth } = useAuth();
  const [contact, setContact] = useState(false);
  const [faqOpen, setFaqOpen] = useState(0);

  const bookDemo = () => {
    buzz(14);
    setContact(true);
  };
  const signIn = () => {
    buzz();
    openAuth();
  };

  return (
    <div className="min-h-screen overflow-x-hidden bg-[#0a0a0c]">
      {/* ------------------------------------------------------------ header */}
      <header className="pt-safe sticky top-0 z-40 border-b border-white/5 bg-[#0a0a0c]/70 backdrop-blur-xl">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-6 2xl:max-w-[1600px]">
          <a {...linkProps('/')} className="flex min-w-0 items-center gap-3" aria-label="Atelier home">
            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl border border-amber-500/40 bg-gradient-to-br from-amber-500/25 to-transparent">
              <span className="font-display text-2xl font-semibold italic text-amber-300">A</span>
            </span>
            <span className="hidden min-w-0 min-[380px]:block">
              <span className="block truncate text-[12px] font-bold uppercase tracking-[0.28em] text-zinc-100">Atelier</span>
              <span className="block truncate text-[9px] font-semibold tracking-[0.3em] text-amber-500/90">3D &amp; AR MENUS</span>
            </span>
          </a>
          <nav className="hidden items-center gap-7 text-[13px] text-zinc-400 lg:flex" aria-label="Sections">
            <a href="#how" className="hover:text-zinc-100">How it works</a>
            <a href="#impact" className="hover:text-zinc-100">Impact</a>
            <a href="#table" className="hover:text-zinc-100">Table experience</a>
            <a href="#faq" className="hover:text-zinc-100">FAQ</a>
          </nav>
          <div className="flex shrink-0 items-center gap-2">
            {isAuthenticated ? (
              <a {...linkProps('/studio')} className="tap flex h-10 items-center gap-2 rounded-full border border-amber-500/40 px-4 text-[13px] font-semibold text-amber-200">
                <ChefHat size={15} aria-hidden="true" /> <span className="max-w-[9rem] truncate">{restaurant.name}</span>
              </a>
            ) : (
              <button type="button" onClick={signIn} className="tap flex h-10 items-center gap-2 rounded-full border border-white/15 px-3.5 text-[13px] font-semibold text-zinc-200 hover:border-amber-500/50">
                <LogIn size={15} aria-hidden="true" /> <span className="hidden sm:inline">Staff Portal</span><span className="sm:hidden">Sign in</span>
              </button>
            )}
            <button type="button" onClick={bookDemo} className="tap hidden h-10 items-center gap-2 rounded-full bg-amber-500 px-4 text-[13px] font-bold text-zinc-950 hover:bg-amber-400 md:flex">
              Book a demo
            </button>
          </div>
        </div>
      </header>

      <main>
        {/* ------------------------------------------------------------ hero */}
        <section className="relative">
          <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(70%_60%_at_80%_0%,rgba(245,158,11,0.14),transparent_70%),radial-gradient(50%_50%_at_0%_60%,rgba(217,119,6,0.08),transparent_70%)]" />
          <div className="relative mx-auto grid max-w-7xl items-center gap-10 px-4 pb-16 pt-10 sm:px-6 sm:pt-16 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-14 lg:pb-24 2xl:max-w-[1600px]">
            <div className="min-w-0">
              <div className="inline-flex items-center gap-2 rounded-full border border-amber-500/30 bg-amber-500/10 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.2em] text-amber-300">
                <Sparkles size={13} aria-hidden="true" /> For fine dining, hotels &amp; culinary ateliers
              </div>
              <h1 className="mt-5 font-display text-[clamp(2.6rem,7vw,5.6rem)] font-semibold leading-[0.95] tracking-[-0.015em] text-[#f6efdf]">
                Turn Dining Menus into <span className="italic text-amber-300">Photorealistic 3D</span> &amp; Augmented Reality
              </h1>
              <p className="mt-5 max-w-xl text-[clamp(1rem,1.4vw,1.15rem)] leading-relaxed text-zinc-400">
                Zero-app WebAR: guests scan one elegant QR at the table, explore every plate in 360° and place it on the tablecloth, true to scale. Instant branded QR codes, a sensory menu that sells, and a studio your team runs from a phone.
              </p>
              <div className="mt-8 flex flex-col gap-3 min-[460px]:flex-row">
                <button
                  type="button"
                  onClick={bookDemo}
                  className="tap shadow-fab flex h-14 items-center justify-center gap-2 rounded-full bg-gradient-to-b from-amber-400 to-amber-600 px-6 text-[15px] font-bold text-zinc-950"
                  data-testid="cta-demo"
                >
                  <CalendarCheck size={19} aria-hidden="true" /> Book a Demo / Onboard Your Restaurant
                </button>
                <button
                  type="button"
                  onClick={signIn}
                  className="tap flex h-14 items-center justify-center gap-2 rounded-full border border-white/15 px-6 text-[15px] font-semibold text-zinc-100 hover:border-amber-500/50"
                  data-testid="cta-signin"
                >
                  <LogIn size={18} aria-hidden="true" /> Staff Portal Sign In
                </button>
              </div>
              <ul className="mt-8 flex max-w-xl flex-wrap gap-x-6 gap-y-2 text-[13px] text-zinc-400">
                {['No app download', 'Safari Quick Look', 'Chrome Scene Viewer', 'QR in minutes'].map((t) => (
                  <li key={t} className="flex items-center gap-2 whitespace-nowrap">
                    <span className="h-1 w-1 rounded-full bg-amber-400" aria-hidden="true" /> {t}
                  </li>
                ))}
              </ul>
            </div>
            <div className="mx-auto w-full max-w-[560px] min-w-0 lg:max-w-none">
              <ShowcaseViewer />
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------ how it works */}
        <section id="how" className="scroll-mt-20 border-t border-white/5">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 sm:py-24 2xl:max-w-[1600px]">
            <SectionTitle kicker="How it works" title="From kitchen pass to the guest’s table in three steps" />
            <ol className="mt-10 grid gap-4 md:grid-cols-3">
              {STEPS.map(({ icon: Icon, title, text, meta }, i) => (
                <li key={title} className="relative overflow-hidden rounded-[28px] border border-white/10 bg-white/[0.02] p-6">
                  <span className="absolute right-5 top-4 font-display text-6xl font-semibold text-white/[0.05]">0{i + 1}</span>
                  <span className="grid h-12 w-12 place-items-center rounded-2xl border border-amber-500/30 bg-amber-500/10 text-amber-300">
                    <Icon size={22} aria-hidden="true" />
                  </span>
                  <h3 className="mt-5 font-display text-3xl font-semibold text-[#f6efdf]">{title}</h3>
                  <p className="mt-2 text-[15px] leading-relaxed text-zinc-400">{text}</p>
                  <p className="mt-4 font-mono text-[11px] uppercase tracking-[0.18em] text-amber-400/80">{meta}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* ------------------------------------------------------------ impact */}
        <section id="impact" className="scroll-mt-20 border-t border-white/5 bg-[radial-gradient(60%_80%_at_50%_0%,rgba(245,158,11,0.07),transparent_70%)]">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 sm:py-24 2xl:max-w-[1600px]">
            <SectionTitle kicker="Feature highlights & ROI" title="A menu that sells the plates you are proudest of" />
            <div className="mt-10 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {FEATURES.map(({ icon: Icon, stat, title, text }) => (
                <div key={title} className="kpi-glow rounded-[28px] border border-white/10 bg-[#111113] p-6">
                  <Icon size={20} className="text-amber-300" aria-hidden="true" />
                  <div className="mt-4 font-display text-5xl font-semibold text-[#f6efdf]">{stat}</div>
                  <div className="mt-1 text-sm font-semibold text-zinc-200">{title}</div>
                  <p className="mt-2 text-[14px] leading-relaxed text-zinc-400">{text}</p>
                </div>
              ))}
            </div>
            <div className="mt-6 grid gap-3 text-[14px] text-zinc-400 sm:grid-cols-3">
              {[
                [Gauge, 'Photo-first menu loads fast even on hotel Wi-Fi; 3D preloads the moment a dish is tapped.'],
                [Layers, 'Brand studio: logo, cover, accent, layout, currency and your social links.'],
                [ShieldCheck, 'Owner accounts are provisioned by Atelier with a forced first-login password change.'],
              ].map(([Icon, text]) => (
                <p key={text} className="flex gap-3 rounded-2xl border border-white/5 p-4">
                  <Icon size={18} className="mt-0.5 shrink-0 text-amber-400" aria-hidden="true" /> {text}
                </p>
              ))}
            </div>
            <p className="mt-4 text-[11px] text-zinc-600">Uplift figures are typical of visual and AR menus in hospitality; results vary by venue, cuisine and menu design.</p>
          </div>
        </section>

        {/* ------------------------------------------------------------ table experience */}
        <section id="table" className="scroll-mt-20 border-t border-white/5">
          <div className="mx-auto grid max-w-7xl items-center gap-12 px-4 py-16 sm:px-6 sm:py-24 lg:grid-cols-2 2xl:max-w-[1600px]">
            <div className="min-w-0 lg:order-2">
              <SectionTitle kicker="The physical table experience" title="One acrylic stand. Every dish, in 3D." align="left" />
              <p className="mt-5 max-w-lg text-[15px] leading-relaxed text-zinc-400">
                Your restaurant name in refined serif, a discreet Atelier mark and a single high-resolution QR that opens your private menu. Print it on paper, card or acrylic; when the menu changes, the code stays the same.
              </p>
              <ul className="mt-6 space-y-3 text-[15px] text-zinc-300">
                {['Dark Onyx & Gold or Crisp White & Black', 'Print-ready 4×6" / A6 PDF, 2000 px PNG and vector SVG', 'Error-correcting QR that still scans through scratches and reflections'].map((t) => (
                  <li key={t} className="flex gap-3">
                    <Box size={17} className="mt-0.5 shrink-0 text-amber-400" aria-hidden="true" /> {t}
                  </li>
                ))}
              </ul>
              <button type="button" onClick={bookDemo} className="tap mt-8 flex h-12 items-center gap-2 rounded-full border border-amber-500/40 px-5 text-sm font-semibold text-amber-200 hover:bg-amber-500/10">
                Get your stands <ArrowRight size={16} aria-hidden="true" />
              </button>
            </div>
            <div className="min-w-0 lg:order-1">
              <TableStandMockup />
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------ FAQ */}
        <section id="faq" className="scroll-mt-20 border-t border-white/5">
          <div className="mx-auto max-w-3xl px-4 py-16 sm:px-6 sm:py-24">
            <SectionTitle kicker="FAQ" title="Questions from F&B directors" />
            <div className="mt-10 divide-y divide-white/10 rounded-[28px] border border-white/10">
              {FAQ.map(({ q, a }, i) => (
                <div key={q}>
                  <button
                    type="button"
                    onClick={() => setFaqOpen(faqOpen === i ? -1 : i)}
                    aria-expanded={faqOpen === i}
                    className="flex w-full items-center justify-between gap-4 px-5 py-5 text-left"
                  >
                    <span className="font-display text-xl font-semibold text-[#f6efdf]">{q}</span>
                    <ChevronDown size={18} className={cx('shrink-0 text-zinc-500 transition-transform', faqOpen === i && 'rotate-180 text-amber-300')} aria-hidden="true" />
                  </button>
                  {faqOpen === i && <p className="fade px-5 pb-5 text-[15px] leading-relaxed text-zinc-400">{a}</p>}
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* ------------------------------------------------------------ closing CTA */}
        <section className="border-t border-white/5">
          <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 2xl:max-w-[1600px]">
            <div className="relative overflow-hidden rounded-[36px] border border-amber-500/20 bg-gradient-to-br from-amber-500/15 via-[#111113] to-[#0a0a0c] px-6 py-12 text-center sm:px-12">
              <h2 className="mx-auto max-w-3xl font-display text-[clamp(2rem,4.5vw,3.6rem)] font-semibold leading-[1.02] text-[#f6efdf]">
                Let your guests taste with their eyes first.
              </h2>
              <div className="mt-8 flex flex-col justify-center gap-3 min-[460px]:flex-row">
                <button type="button" onClick={bookDemo} className="tap shadow-fab flex h-14 items-center justify-center gap-2 rounded-full bg-gradient-to-b from-amber-400 to-amber-600 px-6 text-[15px] font-bold text-zinc-950">
                  <CalendarCheck size={19} aria-hidden="true" /> Book a demo
                </button>
                <button type="button" onClick={signIn} className="tap flex h-14 items-center justify-center gap-2 rounded-full border border-white/15 px-6 text-[15px] font-semibold text-zinc-100">
                  <LogIn size={18} aria-hidden="true" /> Staff Portal Sign In
                </button>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* ------------------------------------------------------------ footer */}
      <footer className="pb-safe border-t border-white/5">
        <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-10 text-sm text-zinc-500 sm:px-6 md:flex-row md:items-center md:justify-between 2xl:max-w-[1600px]">
          <div>
            <div className="font-display text-2xl italic text-zinc-300">Atelier</div>
            <div className="mt-1 text-xs">Photorealistic 3D &amp; AR menus for hospitality.</div>
          </div>
          <nav className="flex flex-wrap gap-x-6 gap-y-3" aria-label="Footer">
            <button type="button" onClick={signIn} className="hover:text-zinc-200">Staff Sign In</button>
            <a {...linkProps('/admin')} className="hover:text-zinc-200">Platform Super Admin</a>
            <button type="button" onClick={bookDemo} className="hover:text-zinc-200">Book a demo</button>
            {SALES_EMAIL && <a href={`mailto:${SALES_EMAIL}`} className="hover:text-zinc-200">{SALES_EMAIL}</a>}
          </nav>
          <div className="font-mono text-[10px] uppercase tracking-[0.24em] text-zinc-600">© {new Date().getFullYear()} Atelier</div>
        </div>
      </footer>

      {contact && <ContactModal onClose={() => setContact(false)} />}
    </div>
  );
}

function SectionTitle({ kicker, title, align = 'center' }) {
  return (
    <div className={align === 'center' ? 'mx-auto max-w-3xl text-center' : 'max-w-xl'}>
      <div className="font-mono text-[11px] uppercase tracking-[0.3em] text-amber-500/80">{kicker}</div>
      <h2 className="mt-3 font-display text-[clamp(2rem,4vw,3.4rem)] font-semibold leading-[1.02] tracking-[-0.01em] text-[#f6efdf]">{title}</h2>
    </div>
  );
}
