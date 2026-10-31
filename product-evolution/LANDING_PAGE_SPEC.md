# Landing page specification

Status: proposed extension of the existing landing page. [Index](README.md).

## Current source and reuse

[HomePage](../frontend/src/pages/HomePage.jsx), [ShowcaseViewer](../frontend/src/components/landing/ShowcaseViewer.jsx), [TableStandMockup](../frontend/src/components/landing/TableStandMockup.jsx), [ContactModal](../frontend/src/components/landing/ContactModal.jsx) and [Navbar](../frontend/src/components/Navbar.jsx) already implement marketing, model demonstration and email contact composition. Contact is mailto, not a stored lead, guaranteed delivered request or public account registration.

## Proposed composition

| Section | Content and action | Truthfulness boundary |
|---|---|---|
| Navigation | Logo, Features, How it works, Menu experiences, Customization, Analytics, Demo, optional Plans, Sign in, Request a demo | Preserve actual sign-in and contact destinations |
| Hero | “Your menu deserves to be experienced.” Large food-led composition with a guest-menu preview; Request a demo / Explore demo | Model loads on interaction; demo clearly identified |
| Journey demonstration | QR entry → photo menu → detail → 3D → supported AR → cart → order image → guest WhatsApp sharing | Label simulated/unreleased steps; never send real requests from demo |
| Three modes | Switch between Simple, 3D and AR presentations of the same sample dish | AR is device-dependent; no 3D controls in Simple |
| Customization | Curated theme/layout variations and owner preview | Show only live features as available; future previews labeled |
| WhatsApp workflow | Prepare image, select share target or download/open chat/attach manually | No automatic media attachment through wa.me |
| Analytics | Engagement and request funnel with metric explanations | Illustrative sample data visibly labeled; no invented sales uplift |
| Restaurant management | Real approved screens or labeled prototype | No false claims that proposed admin tools already ship |
| Plans | Server-configured, explicitly marketed plan revisions | Hidden until approved; no invented prices/payment links |
| FAQ | Onboarding, 3D production, modes, QR compatibility, WhatsApp sharing, devices | Explain human concierge and share limitations |
| Final CTA | Request a demo using approved contact flow | Explain mail-app requirement and provide a usable email fallback |

Use the [design system](DESIGN_SYSTEM_SPEC.md): deliberate typography, real food imagery, one memorable preview, generous section rhythm and subtle responsive interaction. Avoid mandatory 3D downloads or long entrance sequences. Mobile shows the value proposition and useful CTA before a compact preview. Keep important content readable without animation or WebGL.

## Dependencies, decisions and security

Depends on accepted guest workflows, plan visibility rules and approved content/assets. A static labeled prototype may precede production capability; marketing claims may not. Decide whether to keep email composition or separately approve a real lead endpoint/provider. No public signup is inferred from the SaaS ambition. Demo state must be isolated from real tenants, private assets and analytics.

## Acceptance and test scenarios

All navigation links and CTAs resolve; email-unavailable fallback is understandable; owner sign-in remains usable. Test 320px through desktop, keyboard/reduced motion, slow connection, no WebGL and missing demo assets. Confirm no live ordering, private uploads or production mutations from demos. Measure initial payload and LCP/INP/CLS on representative mobile hardware/network before setting final performance budgets; current build size is only a baseline. Review every claim against implemented capability and public-plan authorization before launch.
