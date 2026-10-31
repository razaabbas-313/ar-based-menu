# Guest experience specification

Status: Phase 0 proposal. [Index](README.md).

## Current guest behavior

[GuestMenuPage](../frontend/src/pages/GuestMenuPage.jsx) resolves `/r/{slug}`, loads public or authenticated owner preview, renders categories/photos/search and opens [DishSheet](../frontend/src/components/guest/DishSheet.jsx). [ModelStage](../frontend/src/components/ModelStage.jsx) lazily loads model-viewer; the sheet currently preloads model infrastructure and a hidden viewer for modeled dishes. [SocialBar](../frontend/src/components/guest/SocialBar.jsx) includes a WhatsApp reservation/contact link. Cart, waiter request and dedicated review action are proposed additions. PD01 affects private-menu presentation; the server already withholds private dishes.

## Proposed journey

Load branding and an immediately useful photo menu. Show validated table context unobtrusively when present. Sticky categories and search aid browsing; retain existing chef-pick filtering and define dietary filter semantics before adding controls. Cards show photo, name, price and availability. Dish details show description, dietary information and permitted model/AR controls. Missing models always retain photographs and an accessible explanation.

Ordering adds a clear “Add” action only where enabled and priced/available. A compact cart summary appears after the first addition; it avoids overlapping category controls and safe areas. Quantity changes and optional notes lead to review, server revalidation, image preview and explicit sharing actions. See [WhatsApp spec](WHATSAPP_ORDERING_SPEC.md) for authoritative semantics.

Waiter assistance and Google Reviews live in a secondary venue/action area so browsing remains primary. Feature-disabled actions are omitted; owner-facing explanations belong in the studio. Invalid/revoked table links show a truthful state and may offer the venue menu without claiming a table or permitting table-only actions.

## Responsive and interaction contract

One-column browsing on narrow screens, measured two-column cards where readable, wider grid/list according to approved theme. Bottom sheets fit the visual viewport and safe areas; close remains reachable with keyboard or browser chrome. Support keyboard navigation, focus containment/return, Escape, accessible dialog names, announce cart changes without stealing focus and avoid gesture-only dismissal. Preserve scroll/search/category state when closing details.

Photos reserve dimensions and use delivery variants with responsive sizing. SIMPLE never initializes a viewer. THREE_D/AR should request model bytes when needed rather than making every photo detail expensive; compare against existing preload behavior with measurements. At most one active viewer in the detail surface. Model errors expose photo fallback and retry without blocking ordering.

## Dependencies, decisions and security

Depends on privacy-contract repair, effective capabilities, table resolution, stable prices and validated theme configuration. Cart persistence scope and maximum item/note counts are proposed in the ordering spec. Browser storage is tenant/context scoped and bounded. No notes, private reference media or auth context leak through public DTOs. Table context is not proof of presence. No client-controlled entitlement or table label grants access.

## Acceptance and test scenarios

Exercise public/private/owner/admin-preview paths, valid/invalid QR, all three modes, model-free dishes, unsupported AR, slow/error/retry, search/no results, long content and unavailable or repriced cart items. Check 320/390/768/1280px, safe areas, keyboard, screen reader, 200% zoom and reduced motion. Desktop checks cover layout/fallbacks; physical iOS Safari/Quick Look and Android Scene Viewer/WebXR checks are required for AR claims. Test existing printed venue QR and deep links after every routing change.
