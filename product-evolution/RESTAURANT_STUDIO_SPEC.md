# Restaurant studio

Status: proposed evolution of the existing owner UI. [Index](README.md).

## Current implementation

[StudioPage](../frontend/src/pages/StudioPage.jsx), [DishManager](../frontend/src/components/studio/DishManager.jsx), [BrandCustomizer](../frontend/src/components/studio/BrandCustomizer.jsx), [CategoryManagerModal](../frontend/src/components/studio/CategoryManagerModal.jsx), [StudioAnalytics](../frontend/src/components/studio/StudioAnalytics.jsx) and [AuthContext](../frontend/src/context/AuthContext.jsx) implement substantial existing workflows. Preserve photo creation/batch uploads, edit/replacement, categories, model requests/history, settings, privacy, QR and password setup. Existing settings mostly save directly; they are not a full draft/publish studio.

## Proposed navigation and tasks

| Area | Primary task | Visibility |
|---|---|---|
| Overview | See menu publication state, actionable setup/problems and measured engagement | Authenticated owner |
| Menu & dishes / Categories | Create/edit/order/availability and category management | Current access, dish limits |
| Media & 3D models / Model requests | Upload, inspect versions, request human production | Resolved model permissions |
| Appearance | Choose curated theme, edit draft, preview, publish | Allowed customization level |
| QR Studio / Tables | Export venue/table stands; manage table identities | QR/table entitlements |
| WhatsApp ordering | Set recipient, enable channels, preview image and honest request records | Ordering/waiter permissions |
| Analytics | Date-filtered metrics with definitions and data freshness | Analytics access level |
| Reviews & contact / Settings | Review destination, business/contact/currency and account | Appropriate feature/access gates |

Hide irrelevant sections. For an otherwise relevant locked capability, show a concise explanation and contact-admin action; never expose a broken form. Display effective limits and defined usage, including pending reservations/over-limit states where applicable. Do not imply existing global display limits are enforced per-plan quotas.

Use a calm, compact sidebar/work area inspired by task clarity in Shopify, without copying its branding. High-frequency work uses minimal motion and predictable tables. Search/filter/menu actions remain reachable on mobile. Forms have field errors, loading/dirty/save states, preserved edits on failure and explicit publication boundaries. Destructive actions identify the affected item and consequences. Dangerous table token rotation warns about printed cards.

## Contracts and scope

Continue using [client.js](../frontend/src/api/client.js), existing authentication and [StudioController](../backend/src/main/java/com/atelier/media/web/StudioController.java). New studio handlers require equivalent current-account checks and services must enforce tenant/capability ownership. UI derives from server-resolved effective capabilities; saving rechecks them. Support sessions retain their established restrictions and audit attribution; do not silently turn impersonation into unrestricted owner identity.

Separate menu live/private, appearance published revision and dish availability. Publishing a theme must not make a private menu public. Price/currency changes warn about existing cart snapshots and apply only to future regenerated requests. Background uploads show persistent progress without navigating away from unfinished confirmation.

## Dependencies, decisions and security

Depends on Phase 1 contract/cache repairs, entitlements, stable module APIs and feature-specific specs. Review section rollout, request-dashboard need and usage definitions before redesign. No arbitrary website builder, staff-role system or new router/state library is included. No credential/private asset data enters owner preview URLs or exported cards.

## Acceptance and test scenarios

Complete existing owner journeys before/after incremental navigation rollout. Test forced-password/suspended/revoked accounts on every new handler, foreign resource IDs, stale feature grants and failed saves/uploads. Check usage explanations and over-limit remediation, draft/publish concurrency, mobile tables/forms, keyboard dialogs and 200% zoom. Confirm toggling menu privacy/appearance updates the public route after commit without exposing staff data. Build plus browser/isolated API evidence is required; no currently nonexistent frontend test command is claimed.
