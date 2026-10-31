# Product requirements

Status: Phase 0 proposal. Current means source exists, not automatically runtime accepted. [Verification](REPOSITORY_VERIFICATION.md) supplies test scope; [index](README.md).

## Existing versus requested

| ID | Current source-backed behavior | Required change | Acceptance owner |
|---|---|---|---|
| R01 | Photo menu, search/categories, per-dish models/AR in [GuestMenuPage](../frontend/src/pages/GuestMenuPage.jsx) and [DishSheet](../frontend/src/components/guest/DishSheet.jsx) | Exactly three entitlement-controlled restaurant modes | Menu modes spec |
| R02 | WhatsApp reservation/contact link in [SocialBar](../frontend/src/components/guest/SocialBar.jsx) | Guest cart, server-validated snapshot, branded image, honest native/manual sharing | WhatsApp spec |
| R03 | No dedicated waiter workflow found | Configurable waiter message with validated optional table context and resend friction | WhatsApp spec |
| R04 | Venue QR SVG/PNG/browser print in [qrCard](../frontend/src/utils/qrCard.js) | Table CRUD, revocable opaque identity, context resolution, table exports | QR/table spec |
| R05 | Direct owner models with canUpload3d, concierge/history; admin publication can already accept no active request | Explicit restaurant/dish admin selection, issued keys, actor/origin/version history, retention-safe replacement | Model spec |
| R06 | Accent/layout/contact/logo/banner/currency settings | Curated full themes, typography/layout/motion controls, draft/preview/publish | Brand spec |
| R07 | Two QR themes, fixed stand formats | Validated template/configuration catalog and reliable print/export | Brand/QR spec |
| R08 | Provisioning/status/notes/model permission/support sessions in [AdminController](../backend/src/main/java/com/atelier/media/web/AdminController.java) | Plans, revisions, typed entitlements, quotas, overrides, impact preview and history | Admin/entitlements specs |
| R09 | Counters, raw events, daily aggregates and basic dashboards | Correct concurrent aggregation, metric contracts, trends/date filters, source and sharing funnels | Analytics spec |
| R10 | Maps/contact links; no dedicated Google review setting/action found | Validated external review link, feature toggle and truthful click analytics | Reviews spec |
| R11 | Existing landing, showcase, mailto contact | Premium owner-focused narrative, labeled demos, configured visible plans | Landing spec |
| R12 | SecureRandom/BCrypt OTP, disabled default, logging sender | Transaction-safe failure/consume controls, approved real delivery, reset throttles/revocation | Security plan |
| R13 | Browser downsampling to 1400px/quality .82 in [media.js](../frontend/src/utils/media.js) | Preserve accepted originals; optimized delivery variants, orientation and content validation | Database/API and security plans |
| R14 | React responsive styling, shared overlays, lazy model-viewer | Measured mobile performance, keyboard/screen-reader acceptance, reduced motion, physical-device AR evidence | Design/guest specs |

## Cross-cutting requirements

No payments, payment gateways, checkout, billing collection or financial settlement. No guest account requirement. No large binary proxy through Spring Boot. No arbitrary owner/admin HTML, CSS, JavaScript, SQL or secret editing. Existing auth, public/legacy endpoints, printed QR URLs, pending media safety and public DTO privacy must survive.

Required modes, controls and quotas are backend-enforced. Admin business overrides are audited and restricted to code-supported keys. Changes are additive with explicit legacy-data defaults. Downgrades preserve records, block net growth where over limit, and explain remediation.

## Dependencies and decisions

[Roadmap](IMPLEMENTATION_ROADMAP.md) defines the dependency sequence. [Entitlements](PLANS_AND_ENTITLEMENTS_SPEC.md) owns policy semantics; [architecture](SYSTEM_ARCHITECTURE_PROPOSAL.md) owns domain boundaries. Retention, initial limits, public plan exposure, number verification and provider choices remain proposals in the decision register.

## Acceptance and test scenarios

Every R-ID must pass its owning spec’s scenarios, backend negative/ownership tests and relevant browser checks before being marked implemented. Include suspended/revoked accounts, foreign IDs, disabled features, simultaneous quota requests, stale preferences, unavailable dishes, failed uploads/shares, long content and zero-data dashboards. The release gate includes existing security regressions, full backend suite, frontend build and appropriate isolated PostgreSQL/browser/device evidence. No frontend test/lint script currently exists.

Security implication: treat public events, table links and client quantities/notes as untrusted. Never use frontend totals, slugs or table labels as authoritative ownership or price evidence.
