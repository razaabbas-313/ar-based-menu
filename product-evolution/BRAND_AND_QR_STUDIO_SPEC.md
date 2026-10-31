# Brand and QR Studio

Status: proposed curated configuration. [Index](README.md).

## Current source

[BrandCustomizer](../frontend/src/components/studio/BrandCustomizer.jsx), [theme.js](../frontend/src/utils/theme.js), [BrandingService](../backend/src/main/java/com/atelier/media/service/BrandingService.java), [StudioSettingsUpdateDto](../backend/src/main/java/com/atelier/media/dto/StudioSettingsUpdateDto.java) and [Restaurant](../backend/src/main/java/com/atelier/media/model/Restaurant.java) store basic branding/contact/layout/currency. [qrCard](../frontend/src/utils/qrCard.js) supplies onyx/ivory and 4×6-inch, A6 and square formats with SVG/PNG/browser-print output. Browser Save as PDF is not a server-generated PDF service. Its fixed AR marketing tagline must become truthful for SIMPLE/THREE_D before mode rollout.

## Proposed brand controls

Curated presets: Modern Minimal, Elegant Luxury, Dark Premium, Warm Organic, Contemporary and Classic Restaurant are proposed design families, not implemented themes or fixed plan tiers. Map preset IDs into a versioned allowlisted schema. Controls cover primary/secondary/accent/background/text/surface/border/button colors; approved heading/body font pairs, weights/scale; grid/list/editorial/image-led layouts, card/detail/category styles; logo/banner/description, category/dish order/featured content and approved content blocks; motion ceiling reduced/subtle/standard/expressive. Entitlements restrict available controls/presets; OS reduced-motion always wins.

No arbitrary CSS, HTML, JavaScript, unvalidated URL or third-party script fields. Validate color formats, enum IDs, finite numeric ranges and minimum contrast pairs. Content is plain text with length limits. Curated fonts use existing/approved assets with fallback and licensing review. Prevent unlimited blocks/font/image loads.

## Small draft/publish model

Recommend one tenant appearance row with schemaVersion, draft JSON, published JSON, optimistic version, published timestamp and actor; no whole-site CMS. Draft saves affect owner preview only. Preview uses the same guest renderer and mobile/tablet/desktop viewport controls, with no production analytics. Publish validates full schema, entitlement and referenced tenant assets, then atomically copies draft to published and evicts menu cache after commit. Publishing must not change menu-live status or dish availability.

Allow restoring a prior approved preset or last published state; a full revision timeline is optional later, not required for the smallest initial schema. Existing branding fields project the published legacy-compatible configuration during rollout. Failed/concurrent publish preserves live appearance and returns a useful version conflict. Downgrade retains saved config, masks disallowed choices to a documented safe preset and lets owners preview remediation without destroying drafts. Draft asset cleanup must not delete objects still referenced by published state.

## QR Studio contract

Use one configuration/renderer for preview, SVG, PNG and browser print. Designs select supported frame/background template, logo placement, typography, theme colors, bounded CTA, venue/table context and physical format. QR destination comes only from the trusted route builder/resolved table identity, never arbitrary user URLs. Venue and table codes share branding but preserve separate identity semantics.

Keep a light quiet panel and dark modules regardless of decorative background. Preserve at least four modules of quiet zone; error-correction level and permitted logo area are controlled by a validated template. Prefer logos outside the code; embedded logo requires measured obstruction limits and physical scans. Proposed print floor: module pitch at least 0.4mm with actual payload/version considered; treat this as a design constraint to validate, not an optical guarantee. Flag excessive payload density/contrast violations, refuse exports that break hard scan rules and provide a safe reset preset.

Print formats carry explicit mm/in dimensions and safe text margins; raster export targets at least 300dpi at intended print size, with bounded canvas memory. SVG remains vector. A6/4×6 printed browser output needs page scaling/margin guidance. Do not call square pixel art “print ready” without a physical size. A dedicated PDF generation dependency is separate approval; preserve current browser-print option initially. Export filenames identify venue/table/design revision and never contain secret tokens as human labels.

## Dependencies, decisions and security

Depends on design tokens, capability registry, table/QR routing, draft asset accounting and existing private/public media safety. Decide approved presets/fonts, print formats, logo rules, history retention and dedicated PDF need. QR template assets are curated, not arbitrary SVG uploads with scripts. Bound/escape text in SVG/print rendering; no unchecked interpolation of HTML.

## Acceptance and test scenarios

Test all allowed/denied theme keys, contrast failures, long names/CTA, missing fonts/logo, save/reload, draft leakage, failed/concurrent publish and downgrade masking. Compare guest, preview and exports against the same published revision. Verify original venue destinations and table rotation behavior. Decode generated QR outputs and physically scan printed samples on iOS/Android at supported sizes/light levels; distinguish decode tests from camera evidence. Check keyboard preview controls, color-picker labeling, reduced motion, pagination and print scaling. No current full brand builder or automatic PDF backend is claimed.
