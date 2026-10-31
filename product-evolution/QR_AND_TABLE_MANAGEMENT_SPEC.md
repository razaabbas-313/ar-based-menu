# QR and table management

Status: proposed table foundation. [Index](README.md).

## Current implementation

[RestaurantQrModal](../frontend/src/components/RestaurantQrModal.jsx) and [qrCard](../frontend/src/utils/qrCard.js) export venue QR targeting `/r/{slug}` using optional `VITE_PUBLIC_MENU_ORIGIN`. SVG, PNG and browser printing exist; no table entity or QR identity repository exists. [router](../frontend/src/utils/router.js) supplies the custom routing boundary. Do not change existing destinations or silently change restaurant slugs.

## Proposed identity model

`restaurant_tables`: ID, restaurant FK, display name/number, optional bounded zone, active/archive state, QR-token hash/version, created/updated timestamps and optimistic version. Recommend 128+ bits of cryptographic randomness; token is an opaque public context capability, not a password or proof of presence. Unique token hash, unique tenant/table normalized active label if approved, and tenant/activity listing index. Never derive token from a table ID/number.

Keep `/r/{slug}` as venue entry. Proposed table links: `/r/{slug}?table={opaqueToken}`. Resolve through a narrowly public backend endpoint or menu context request. Match token hash, restaurant ownership, current active table, current restaurant publication/status and table-QR capability. Return only safe table label and allowed channel context; no internal notes or token hash. Store/use validated context separately from arbitrary query parameters. Venue QR attribution may use an additive `source=venue-qr` parameter in new exports; old links still work and cannot be retroactively classified as scans.

Renaming a table retains its token and updates future context display. Deactivation invalidates table actions; regeneration atomically replaces the token, warns that printed table cards need replacement, and invalidates caches. Archive rather than hard-delete referenced tables; request snapshots retain historical labels. A revoked/unknown token displays a useful message and optional venue-menu link, with no inherited table-only ordering/waiter permission. Rotating/revoking may make old cards unusable intentionally; venue URLs remain stable.

Slug edits already exist in admin code and may break printed links. Recommend freezing published slugs until an explicit alias/redirect policy is approved. A future alias registry must resolve restaurant identity before table validation and preserve revoked-token behavior. Do not silently rewrite existing QR destinations in this phase.

## Management and quotas

Owners create, rename, zone, deactivate/reactivate, rotate identity and export permitted table templates. Super admins configure table availability and maximum active tables. Recommend counting active tables plus pending creation reservations, with archived records retained separately. Over-limit downgrades preserve existing rows; block new activation/creation until usage permits. Rotation/rename/deactivation remains available for remediation. Backend locks a tenant quota row before count/reservation and mutation, in the same transaction; no frontend count enforcement.

## Dependencies, decisions and security

Depends on entitlements, stable public URL policy and QR renderer separation. Decide label uniqueness, zone needs, archived-table retention and initial quotas. Table token holders can copy links and make remote requests; UI must not claim authenticated seating or presence. Scrub token query values from logs/referrers/analytics; use an appropriate referrer policy when linking externally. Bound resolution rate and avoid enumerable public table listings. New admin access never bypasses ownership checks.

## Acceptance and test scenarios

Existing venue SVG/PNG destinations still resolve. Test A’s token under B’s slug, malformed/random/revoked tokens, renamed/deactivated/archived tables, quota exhaustion/races and plan/override expiry. Requests with arbitrary table text never acquire trusted context. Confirm historical snapshots survive renames/archive and public DTOs expose only safe data. Physically scan representative venue/table prints at intended sizes, light levels and phones after export; URL generation alone proves no optical reliability. QR entry metrics report context, never physical scans.
