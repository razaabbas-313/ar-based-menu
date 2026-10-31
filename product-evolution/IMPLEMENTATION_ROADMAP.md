# Dependency-driven implementation roadmap

Status: Phase 0 complete; user separately authorized **Milestone 1A only** on 2026-10-31. Its OTP repair is implemented with H2 verification; PostgreSQL concurrency remains blocked. Later milestones remain recommendations, not authorization. [Index](README.md) · [Milestone 1A actual evidence](../project-documentation/MILESTONE_1A_VERIFICATION.md).

## Current baseline and sequencing

The inherited application and uncommitted A01–A03 fixes are preserved. Fresh backend suite: 374 passed, no failures/errors/skips; frontend production build passed. H2/mocks do not prove PostgreSQL, S3, browser or physical AR readiness. [Verification ledger](REPOSITORY_VERIFICATION.md) and [security plan](SECURITY_AND_RELIABILITY_PLAN.md) identify remaining defects. Existing modules to extend include [services](../backend/src/main/java/com/atelier/media/service), [controllers](../backend/src/main/java/com/atelier/media/web) and [frontend components](../frontend/src/components).

| Phase | Independently reviewable slices | Dependency and exit gate |
|---|---|---|
| 0 Product blueprint | Current-state evidence, 21 specifications, decisions, skill specifications | This documentation set; no implementation |
| 1 Security/stability | 1A OTP commit/consume foundation; 1B nonproduction private-S3 proposal; 1C PD01/A06 contracts; 1D category invalidation/seeding; 1E atomic analytics/metric alignment; 1F concierge transactions/cleanup/integrity/session lifecycle | Each slice separately approved; existing security regressions preserved; isolated DB/runtime evidence closes relevant finding |
| 2 Administrative capability foundation | Registry + plan revisions/resolver; assignments/overrides/audit; quotas/reservations; owner effective-settings read; admin controls | Agree definitions/defaults first; real concurrent PostgreSQL final-slot tests; versioned audit and downgrade tests |
| 3 Menu/QR foundation | Mode preference/public projection; viewer gating; table CRUD/context/rotation; quota-controlled QR exports | Phase 2 + public contract/cache repair; printed venue compatibility and valid/foreign/revoked table tests |
| 4 Guest request/assistance | Cart; validated snapshot/idempotency; accessible image preview/pagination; native/manual share branches; contact/waiter settings; bounded event observations | Phases 2/3, recipient/retention decisions; no payment/delivery claims; browser share matrix and server repricing/isolation tests |
| 5 Model operations | Shared issued upload/finalization; proactive admin target selection; model versions/provenance/retention; concierge transitions/history | S3/transaction/cleanup fixes + quotas; preserve old live media until confirm, validate all origins, history and races |
| 6 Design and guest quality | Incremental shared tokens/primitives; guest cards/details/navigation; original/delivery-image lifecycle; measured viewer/motion improvements | Security gates continue; image worker/retention approved separately; responsive/a11y/performance and physical AR evidence |
| 7 Restaurant/Brand/QR Studio | Task-based owner navigation; appearance draft/preview/publish; allowed presets; richer QR templates/print; admin refinements | Stable domain APIs, feature levels, table QR and design foundation; concurrency/publish/print scan acceptance |
| 8 Advanced analytics/reviews | Date/zone/cohort reports, mode/source/request funnel, accessible dashboard, review link setting/action | Core analytics correctness moves to Phase 1; new events ship with originating features, not postponed here; no fabricated history |
| 9 Marketing/launch | Refined landing, real/labeled demos, approved visible plans, real contact/onboarding decisions, release/restore/runbook gates | Product acceptance evidence; infrastructure/provider changes separately reviewed; no launch based on build alone |

Design directions/prototypes may be reviewed early as labeled proposals, without public feature claims or replacing working UI. Do not postpone a discovered critical issue to a later numbered phase. After each slice, reconsider dependencies and record remaining limitations; approval for one slice does not authorize the next.

## Milestone 1A: approved implementation and original acceptance scope

**Transaction-safe password reset foundation, while password reset remains disabled and delivery remains unconfigured.**

Problem: [OtpService](../backend/src/main/java/com/atelier/media/service/OtpService.java) saves attempts/use state inside a transaction and throws a runtime validation exception, so a real DB rollback can discard lockout state. Repository selection lacks atomic consumption. Current mock tests pass without proving either guarantee.

Proposed scope:

1. Add real transactional regression tests reproducing invalid-attempt rollback, fifth-attempt invalidation, expiry/single-use and concurrent valid consumption. Use fresh H2 for commit/rollback, then the approved disposable PostgreSQL target for lock/race evidence; do not provision infrastructure without approval.
2. Refactor verification into explicit committed outcomes, with consistent account/OTP lock order and atomic successful password/version change. Throw public failure outside the transaction that must commit attempt state. Serialize resend/replacement versus reset and bound failed/reset request attempts using existing tooling.
3. Preserve SecureRandom, BCrypt, generic account semantics and existing client/API shapes unless a concrete narrow contract adjustment is reviewed. Verify token revocation and suspended/deleted account policy. Keep production reset disabled, no real email/provider/credential changes.
4. Run the full existing suite, fresh transaction/race cases and frontend build; document the exact evidence, review diff and update actual-state docs. Browser password-reset UX changes are excluded unless needed for an explicitly approved contract adjustment.

Expected files: OtpService, PasswordResetOtpRepository, OtpServiceTest and new focused transaction/concurrency tests; RestaurantRepository or AuthController only if account serialization/reset rate input requires it. No expected migration, feature entitlement, new dependency, paid service or frontend redesign. Schema need discovered during implementation returns as a concrete proposal; do not silently expand scope.

Acceptance: wrong attempts survive independent transaction reload; fifth failure prevents later use; two concurrent correct codes yield exactly one password change; expired/replaced/used codes fail; a reset/resend race cannot revive a code; successful consume/password hash/version change is atomic; injected persistence failure rolls back the successful path; old tokens fail current-account checks; no OTP/secret logs; reset remains disabled by default; all existing tests pass.

This milestone does not enable secure real-world recovery until separately approved delivery, nonproduction mail evidence and full reset UX acceptance exist. The user approved this slice only; its implementation/evidence is linked above. PostgreSQL acceptance remains blocked until a confirmed disposable instance is available. The first full run exposed a new fixture using a state unsupported by H2 migrations; the fixture was corrected without changing migrations, and PostgreSQL's additional state is covered by service unit tests.

Recommended next milestone: **1B, a read-only concrete private-S3 policy/lifecycle proposal** with least-privilege changes and a disposable-prefix test plan. Production policy edits, credentials, cloud actions and real mail delivery remain separately approval-dependent. A disposable PostgreSQL run of the 1A concurrency cases is still required before recovery activation; do not replace that evidence with H2 results.

## Approval and release discipline

For every slice, specify changed files, API/schema/data migration, tenant/entitlement rules, failure behavior, tests, rollout/rollback and measurable exit criteria before coding. Test failure first where a defect is being repaired. Use existing dependencies; new services/dependencies/infrastructure need explicit reviewed scope. Do not auto-commit/push.

The security and integration gates apply throughout: valid/revoked/foreign/disabled inputs, concurrency/idempotency, safe media replacement, accurate metric semantics, responsiveness/accessibility and failure/retry states. PostgreSQL target availability, physical devices, cloud test prefixes and provider accounts are explicit prerequisites, not reasons to claim a blocked check passed. Approval can begin with 1A while the S3 proposal is prepared read-only; neither authorizes production mutation.
