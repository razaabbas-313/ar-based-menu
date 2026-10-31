# Decisions and open questions

Status: Phase 0 decision register, 2026-10-31. Recommendations below are **not approvals**. [Index](README.md).

Subsequent decision D20: the user explicitly approved Milestone 1A only. OTP transaction repair and local evidence are recorded in [Milestone 1A verification](../project-documentation/MILESTONE_1A_VERIFICATION.md); no later feature/provider/infrastructure work was authorized. The original recommendations below remain the Phase 0 record.

## Fixed by the user

| Decision | Binding consequence |
|---|---|
| Existing product, no rebuild | Preserve React18/Vite/custom routing/client/AuthContext, Java21/SpringBoot/JPA/PostgreSQL/Flyway, S3 direct uploads and model-viewer |
| No payments or automated billing | No gateways/cards/payment checkout/webhooks/subscription collection; dish totals are estimates for communication |
| Exactly three menu modes | SIMPLE, THREE_D, AR in shared menu infrastructure |
| Guest WhatsApp image ordering | No required guest account, truthful native/manual sharing and no false sent/accepted state |
| Admin business flexibility within safety | Supported typed capability/quota configuration only; tenant/auth/audit/integrity/secret rules cannot be overridden |
| Human model concierge remains | Extend existing upload/publication/history; no automatic reconstruction provider |
| Phase 0 only | Only product-evolution planning edits; no app/schema/infrastructure/credentials/AGENTS/skill installation/commit/push |

Current source and working-tree evidence are recorded in [verification](REPOSITORY_VERIFICATION.md), with [existing audit](../TECHNICAL_AUDIT.md) and [current security ledger](../project-documentation/SECURITY_DOCUMENTATION.md) reconciled. No policy proposal retroactively marks a current feature implemented.

## Recommended decisions for milestone review

| ID | Question | Recommendation | Needed before |
|---|---|---|---|
| D01 | Do plan edits affect existing restaurants? | Immutable revisions pinned per restaurant; explicit impact-preview migration | Phase 2 schema |
| D02 | Initial plan names/limits/public visibility? | Admin-configured catalog, hidden marketing until approved; no invented prices | Phase 2 configuration / Phase 9 marketing |
| D03 | Legacy entitlement and mode mapping? | Preserve current behavior with a legacy revision; map canUpload3d separately from viewing; no bulk forced downgrade | Phase 2 backfill |
| D04 | Cap accounting and unlimited semantics? | Typed limits, explicit unlimited, reserve pending creation/bytes; retain over-limit records, prohibit net growth | Phase 2 enforcement |
| D05 | Concierge period/refunds and storage scope? | UTC calendar month; count submissions to prevent cancel cycling; count retained managed bytes, audit exceptions | Phase 2 quotas |
| D06 | Persist order requests and for how long? | Minimal immutable snapshot/lines with 30-day notes retention; image local only; bounded read capability | Phase 4 schema/privacy |
| D07 | Cart lifetime, quantities/notes and unpriced dishes? | Tenant/table-scoped session storage; 2-hour inactivity; 50 lines/1–99 quantity; bounded notes; unpriced items excluded initially | Phase 4 UX/API |
| D08 | WhatsApp number verification claim? | Syntax validation + owner-confirmed test chat; no platform-verified badge without approved verification mechanism | Phase 4 settings |
| D09 | Automated WhatsApp delivery? | Defer; separate business/account/opt-in/template/cost/webhook proposal; guest flow first | Any provider activation |
| D10 | Slug edits versus printed QR permanence? | Freeze published slugs until aliases/redirect behavior is explicitly designed | Phase 3 routing |
| D11 | Table labels/archive/QR expiry? | Opaque rotatable tokens; rename stable; deactivate/revoke invalid; archive referenced tables; no automatic expiry of printed tokens | Phase 3 schema |
| D12 | Model history object retention and rollback? | Preserve superseded models initially within explicit storage budget; define retention/holds before cleanup; label legacy unknown | Phase 5 lifecycle |
| D13 | Original image validation and derivatives? | Supported JPEG/PNG/WebP masters, private/staged validation, responsive derivatives; approve worker/cost first; HEIC separately | Phase 6 upload changes |
| D14 | Branding presets/fonts, publish history and PDF? | Curated approved assets, one draft/published appearance row, current browser-print first; dedicated PDF later if needed | Phase 7 design/schema |
| D15 | Analytics identity, consent, time zone and retention? | No fingerprinting; session-level first, unique browser ID only after privacy approval; UTC reporting initially; raw90d/aggregate13mo proposal | Analytics schema/cutover |
| D16 | Backend topology/cache/rate guarantees? | Verify current deployment; assume one instance for current local caches; require coordination before multi-instance rollout | Scaling/release |
| D17 | Password reset delivery provider and account policy? | Foundation remains disabled; choose approved transport later; explicitly preserve suspension restrictions and coherent revocation | Recovery enablement |
| D18 | Contact/onboarding workflow? | Preserve admin provisioning and honest mailto demo flow until real lead/public signup scope approved | Landing launch |
| D19 | Project-local skills? | Approve five instruction-only scopes plus recommended sixth quota skill; create only in separate task | Skill creation |
| D20 | First implementation scope? | Approve only milestone 1A transaction-safe OTP foundation, not all of Phase 1 | Any code edit |

Product owner approves commercial behavior, retention/region/consent choices, marketing claims and rollout. Engineering prepares concrete contracts/schema/tests and infrastructure/provider proposals. New facts revise this register with date/reason/evidence; do not silently turn recommendations into user decisions.

## Architectural conflicts requiring explicit handling

- Current image processing downscales/re-encodes masters; removing it without delivery variants would harm mobile payload and server accepted-type rules. Stage a master/variant lifecycle instead.
- Current model replacement deletes old objects; request history metadata alone cannot satisfy retained version availability. Retention/accounting/cleanup must change together.
- Existing admin model approval already supports a no-request target; improve selection, provenance and issued-key validation rather than duplicate a competing pipeline.
- Current private menu API withholds dishes but UI/legacy envelope misrepresents live state (PD01). Repair before new guest controls; this is not evidence of leaked private dishes.
- Local caches and H2 tests cannot establish multi-node policy freshness or PostgreSQL race safety. Plan explicit verification rather than claim inferred guarantees.
- Existing public asset URLs remain reachable when known; changing menu mode/privacy does not retroactively make public media private.

## Acceptance, dependencies and security

Each milestone names the D-IDs it depends on, records accepted values and leaves unrelated questions open. No need to answer every long-term question before fixing A04. Approval must identify a concrete slice/files/tests and exclude unapproved services/infrastructure. Review scenarios include downgrade with existing usage, revoked printed table links, share cancellation/manual fallback, no-model menu, failed publication and legacy data unknowns. No decision permits bypassing account/tenant/audit/integrity controls. This register introduces no production changes.
