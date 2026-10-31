# Design system specification

Status: proposed visual direction, not implemented or approved pixels. [Index](README.md).

## Current system

[index.css](../frontend/src/index.css), [theme.js](../frontend/src/utils/theme.js), [shared UI](../frontend/src/components/ui.jsx), [BrandCustomizer](../frontend/src/components/studio/BrandCustomizer.jsx) and [QR renderer](../frontend/src/utils/qrCard.js) already provide reusable styling and controls. Four accents are amber, emerald, gold and zinc; these are not six complete theme systems. Preserve recognizable existing themes while adding semantic tokens incrementally.

## Proposed direction and tokens

The signature is a restaurant’s dish photograph beside a restrained menu-style caption, with a tangible plate-scale 3D reveal on demand. Guests see food first; owners see tasks and usage first. Marketing uses one deliberate product demonstration instead of repeated decorative card grids.

| Token group | Proposed starting point | Validation |
|---|---|---|
| Palette | Porcelain #F7F5F0, charcoal #20211F, linen #E6E1D7, herb #41624A, brass #9A7129; retain existing dark theme as alternate | Check text/surface pairs and selected/disabled states; these swatches alone do not certify contrast |
| Typography | Existing Manrope for controls/body, existing Cormorant/Georgia stack for editorial guest/marketing headings | Verify actual font availability, licenses and fallback; no font dependency installation |
| Scale | Body 16px; metadata 13–14px; headings 24/32/48px, fluid hero capped near 72px | Long names, 200% zoom, text spacing |
| Spacing | 4px base; control gaps 8/12px; panel padding 16/24px; section space 32/48/64px | Dense admin work versus relaxed food browsing |
| Geometry | Controls 8px radius, cards 16px, sheets 24px; subtle shadows on light, tonal borders on dark | Consistent hierarchy and nested radii |
| Interaction | Prefer 44px touch areas; visible focus; explicit selected/error/disabled status | No color-only meaning or hover-only actions |
| Motion | Press 100–150ms; disclosure 150–220ms; sheet 220–280ms, interruptible | Transform/opacity, no transition-all or mandatory waiting |

Motion preference is a ceiling: system reduced-motion wins over restaurant expressive settings. Keep essential loading/progress communication without large movement. Use existing CSS first; a new animation library needs justification and approval.

## Component contracts

Reuse buttons, fields, toasts, overlay focus/scroll behavior, dish cards/sheets, category navigation, media loading/error states, usage indicators and empty states. Audit existing overlay keyboard handling before reuse; existence is not proof of full accessibility. Add semantic tokens behind existing utilities rather than rewriting all pages. Text status accompanies every color. Price/counter numerals use tabular figures. A disabled business feature explains the entitlement; a pending network action exposes retry and does not erase entered data.

## Dependencies, decisions and security

Guest and administration design specs own layouts. Brand Studio maps validated token choices to a versioned schema; no raw stylesheet injection. Final palette/type/layout specimens need review in their future milestone. Theme contrast enforcement, font loading costs and available rights must be settled before publishing presets. No arbitrary external font/script URLs.

## Acceptance and test scenarios

Verify at 320/390/768/1280/1440px, portrait/landscape where relevant, 200% zoom and keyboard-only use. Check focus trap/return, Escape, accessible names, screen-reader order, contrast, safe areas and reduced motion. Test long restaurant/dish names, missing photos, empty/error/loading states and denied features. Capture actual browser evidence after approved implementation; this specification provides no visual runtime verification.
