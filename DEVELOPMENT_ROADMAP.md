# Atelier AR: development handover and roadmap

Audit date: 2026-10-31. This is a proposed sequence grounded in TECHNICAL_AUDIT.md, not authorization to implement changes. No rebuild, stack replacement, frontend redesign or speculative product scope is proposed.

## Existing completed implementation to preserve

The code already contains restaurant/admin authentication, provisioning, dish creation/editing/availability, photo and model upload/replacement, custom category CRUD, restaurant branding, guest menus, QR exports, model-viewer/AR handoff, a human-operated 3D concierge desk, analytics, migrations and backend tests. Most remain runtime-unverified in this environment; refer to IMPLEMENTATION_STATUS.md rather than treating this list as a production acceptance statement.

Actually verified: frontend production compilation, QR matrix generation and three isolated media helper assertions. No whole user journey was verified end to end. Password-reset delivery is a logging placeholder, category reordering has a contract defect, and supplied private S3 policy templates conflict with the download design.

Reuse these boundaries:

| Existing component | Reuse purpose / safe extension point |
|---|---|
| SecurityConfig, Tenant, AdminPrincipal, RestaurantAccess | Common request-level access checks; extend current account validation rather than replacing JWT or Spring Security |
| Controllers → validated DTOs → services → scoped repositories | Add related operations within the owning module; keep controllers thin and tenant scope explicit |
| MediaService, DishService, MediaReplacementService, S3Storage | Existing photo-first/direct-upload lifecycle and metadata plane; retain browser-to-S3 bytes and staged replacement semantics |
| ModelRequestService + history/pending-key tables | Existing human concierge workflow; improve authorization, lifecycle and transactions without inventing automatic reconstruction |
| TransactionTemplate, locking repositories, usage updates | Short atomic persistence and concurrency protection; exercise actual database transactions in regression tests |
| DishMapper guest DTO, PublicMenuService, MenuCache | Public data filtering and per-restaurant publication; extend only with explicit safe DTO fields and cache invalidation |
| api/client.js, AuthContext, upload.js | Existing session separation, error formatting and upload orchestration |
| Shared UI, studio/guest/admin components, ModelStage, QR helpers | Add controls into existing surfaces and preserve themes, routes, printed QR links and component conventions |
| Flyway vendor trees and backend test helpers | Add migrations only when approved; retain historical migrations and account for vendor differences |

## Immediate blockers and safest sequence

1. **Restore reproducible backend verification without touching business behavior.** Provide Java 21/Maven 3.9+ and an isolated database target; do not point application startup at production. Run the existing suite, record actual failures, and validate PostgreSQL V1-V13 on a disposable database. The current machine cannot compile/run Java. Reuse existing dependencies; do not install infrastructure or change schemas without the appropriate authorization.
2. **Fix account enforcement as the first approved implementation task (A01-A03).** Cover every admin/studio endpoint with current account/version/status validation; use existing role/audience rules and RestaurantAccess semantics. Validate category ownership on both dish create/update and response resolution. Include signed-token route tests for revoked/suspended/deleted accounts and foreign category IDs. Keep legitimate public menus/analytics accessible and setup-password flow intact.
3. **Repair OTP transaction semantics before enabling reset (A04).** Add actual commit/rollback and concurrent-use tests; preserve generic account responses, hashing, expiry and resend limits. Do not enable the logging sender as though email were delivered.
4. **Repair/review nonproduction S3 templates and lifecycle (A05, A10, A12-A13).** Preserve anonymous denial for private assets; grant the application only required prefix operations. Test private uploads/downloads and anonymous denial in a disposable prefix; then test staging/confirmation/deletion failure handling. Production bucket/IAM changes require separate authorization and a reviewed concrete policy.
5. **Fix category contract/cache behavior (A06-A07).** Align reorder DTO/client naming; verify create/rename/reorder/activation/delete survives reload and appears in the guest menu after commit. Check duplication/seeding behavior; only propose a uniqueness migration if runtime data review supports it and schema change is approved.
6. **Make analytics and concierge persistence reliable (A08-A09, A15).** Verify concurrent aggregates and observed async errors, move AWS network calls outside DB transactions while preserving rechecks/locks, and document cache/revocation timing. Handle retained private/history assets and orphan daily rows safely; avoid production cleanup during development.
7. **Run acceptance checks before broader feature work.** In nonproduction: provision A/B restaurants, complete forced password setup, create/edit/upload/replace dishes, reject cross-tenant operations, configure branding/categories, switch menu privacy, scan venue QR, submit/approve concierge assets, inspect analytics, and test supported device AR. Check both public and preview paths, errors, retries and unauthorized requests.

## Recommended next development task

**Close account and tenant authorization gaps with regression tests.** Concrete scope: common admin account/version enforcement; consistent RestaurantAccess checks for every studio route; tenant-owned category validation; tests proving revoked tokens and foreign category IDs fail. Reuse the current architecture and API shapes. Establish the backend toolchain first so acceptance is executable.

Acceptance criteria:

- Current valid admin/restaurant sessions retain their existing authorized behavior; role/audience separation remains intact.
- Old-version admin tokens fail all platform route families, including list/detail/preview and password-reset/impersonation/delete capabilities, using disposable data.
- Suspended, deleted, revoked and forced-password restaurant sessions cannot use any studio operation outside the intended account/password flow.
- A restaurant cannot assign/read another restaurant's custom category through dish operations; rejected updates do not persist.
- Backend tests pass and frontend builds; any test/tool/service limitation is reported explicitly. No production data/config/schema mutation.

## Incomplete existing capabilities

- Password reset requires real delivery and transaction-safe lockout before enablement. A transport provider is not selected by this audit.
- Category ordering is broken and category publication can be stale.
- Concierge request/history/publication code exists, but private policy, authorization and cleanup need completion. The code supports one-to-five reference photos, not enforced five-photo completion.
- Analytics daily aggregation needs concurrency/reliability work; dashboards are not evidence of load-tested analytics.
- CloudFront support is only a configurable URL base. There is no evidence that a distribution is deployed.
- Guest dietary filtering is absent despite documentation claims. Confirm whether it is desired before implementing; persisted tags alone do not establish a product requirement.

## Future improvements supported by findings

After the blockers: introduce a focused frontend behavior suite and CI release checks; test PostgreSQL rather than relying only on H2; consolidate duplicated deployment definitions when change risk is understood; correct old documentation; measure mobile bundle/load performance and test device AR; define observed retention/cleanup and multi-instance cache requirements. Evaluate dependency updates using current primary advisories at implementation time—no CVE status or upgrade compatibility was verified here.

No roadmap is proposed for ordering, billing, payments, per-table QR tracking, automatic 3D reconstruction, alternate frameworks or other new product features: their necessity is not established by this code audit.
