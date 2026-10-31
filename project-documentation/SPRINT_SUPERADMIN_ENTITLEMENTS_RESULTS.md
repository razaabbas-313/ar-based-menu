# Super-admin entitlements sprint results

2026-10-31 · Local implementation only. Baseline: `6c0c9335f2df6f415b04a0af5016ee2c55b8e63f`, branch `atelier-ar-development`. B1–B5 were already checkpointed; only the two backup ZIPs were untracked at sprint start. No commit, push, production configuration change, dependency installation or live AWS/database access.

## Implemented capabilities and screens

- **Plan management:** reusable administrator-created plans; name/description/status editing; typed feature/quota controls; immutable published revisions; explicit assignments pinned to a revision. New revisions never migrate existing assignments. Deactivation prevents new assignments and leaves existing revisions usable. No hardcoded Starter/Growth/Premium catalogue or billing functionality.
- **Admin → Plans & capabilities:** searchable/filterable catalogue, empty/loading/error states, revision editor, dependency/range validation, inactive-plan handling, revision history and confirmation before publishing changes. Confirmed saves update the local catalogue using the actual server response.
- **Admin → Restaurant drawer → Plan & capabilities:** assigned revision, effective values and inheritance, actual dish/model/request usage, over-limit warnings, explicit assignment, permanent/expiring exceptions, reset to inheritance and an actor/reason/before-after history. Private removal reasons travel in a JSON body, not URL query parameters.
- **Admin → New restaurant:** plan selection, inherited capability preview and initial exceptions saved atomically with provisioning. Invalid policy or failed mandatory audit rolls back the account. Existing no-plan provisioning and one-time temporary-password workflow remain compatible. Ordinary profile edits do not silently create legacy permission overrides.
- **Owner → Your plan:** read-only plan/mode/capability/usage overview, explanations for disabled and planned features, expiry-aware refresh. Existing model, concierge, branding and QR controls consume effective policy. Owners cannot edit platform plans or another restaurant’s configuration.
- **Existing guest viewer:** allowed-mode projection suppresses disallowed model/AR actions; the React 18 custom-element bridge removes the actual AR flag while retaining 3D camera controls. Existing public route and printed QR destinations remain. This gates existing viewing infrastructure; it does not build the three future dedicated menu experiences.

## Policy, quotas and legacy compatibility

The stable typed registry supports menu.allowedModes; model.ownerUpload / model.concierge; restaurant/table QR; WhatsApp ordering/waiter requests; Google Reviews; branding; advanced analytics; dish/table/model/monthly-concierge/file-size limits. Unknown keys, incorrect types, negative/fractional limits and conflicting dependencies are rejected. Zero and explicit UNLIMITED are distinct values. SIMPLE remains mandatory; AR requires THREE_D, model grants require 3D viewing and table QR requires venue QR.

Resolution combines legacy or pinned revision values, currently applicable overrides, dependency/implementation masks and global file ceilings. Configuration values and operational availability are distinct. Overrides replace one typed value; same-key replacement is serialized and uniquely constrained. UTC expiry is exclusive and evaluated on reads, without a scheduler/cache dependency. External policy reads use a coherent repeatable-read snapshot; mutation previews/audits stay inside the locked tenant transaction. Config versions reject lost updates.

| Protected resource | Count / enforcement |
|---|---|
| Dishes | All non-FAILED, nondeleted media rows, including PRESIGNED reservations. Tenant lock at issuance and confirmation; concurrent final-slot creation cannot overrun the cap. |
| Active model slots | Distinct dishes with a live/legacy model, pending model or active concierge production request. Replacements of an existing slot do not consume another slot; new slots recheck under the common tenant lock. |
| Concierge | UTC calendar-month submitted-request ledger; issuance checks capacity and confirmation checks/records atomically. Ledger survives dish/request deletion, preventing cancellation/deletion cycling. |
| Model upload bytes | Registry/override ceiling clamped by global owner and publication safety caps. Existing MIME/extension/HEAD validation remains; signing/publication revalidate identity and relevant policy. |
| Branding | Current feature/account checks under tenant lock on mutation and post-HEAD confirmation. |
| Menu viewing | Fresh public allowedModes and masked model projection. Public menu responses use no-store rather than retaining disabled grants in browser/server menu caches. Staff asset previews remain authenticated staff views. |
| Table quota / future actions | Configurable only; no table, ordering, waiter or review creation flow is claimed. Table usage is explicitly unavailable. |

V15 creates nullable legacy policy rows without assigning a premium plan. Legacy model grants continue to derive from canUpload3d, current browsing/concierge/branding/QR behavior is preserved, and previously unenforced growth quotas remain explicit UNLIMITED until an administrator opts into a plan/override. Existing counts, account restrictions, assets, URLs and migration history are unchanged. Lowering a cap preserves resources, reports over-limit usage and blocks additional growth; replacement rules do not delete retained assets. Existing direct-model permission endpoints now create audited policy overrides rather than silently conflicting with pinned plans.

## API contracts

All admin routes require the existing current-admin/role/token-version guard; writes revalidate the actor transactionally. Studio identity is derived only from the verified Tenant. Protected responses are no-store.

| Method | Endpoint |
|---|---|
| GET | /api/v1/admin/features |
| GET, POST | /api/v1/admin/plans |
| PUT | /api/v1/admin/plans/{id} — creates a revision |
| GET | /api/v1/admin/plans/{id}/revisions |
| GET | /api/v1/admin/restaurants/{id}/entitlements |
| POST | /api/v1/admin/restaurants/{id}/entitlements/assignment |
| PUT, DELETE | /api/v1/admin/restaurants/{id}/entitlements/overrides/{key} |
| GET | /api/v1/studio/entitlements — read-only, authenticated tenant only |

Provisioning adds optional planRevisionId and initialOverrides fields to the existing create DTO, preserving old constructors/default behavior. The public menu adds only allowedModes; plan IDs, operator reasons and audit evidence are not public. Existing Spring ProblemDetail/field-error handling and shared React API/session client are reused.

## Database and verification

Additive migrations: `backend/src/main/resources/db/migration/h2/V15__plans_entitlements.sql` and `backend/src/main/resources/db/migration/postgresql/V15__plans_entitlements.sql`.

Six tables: entitlement_plans, entitlement_plan_revisions, restaurant_entitlement_policy, restaurant_entitlement_overrides, entitlement_audit and entitlement_request_usage. Foreign keys, revision/tenant/key uniqueness and tenant/period/expiry/audit indexes provide integrity and bounded lookups. Structured append-only before/after audit evidence complements the existing mandatory admin audit in the same transaction. Initial provisioning records stable initial/final effective snapshots. The request ledger backfills only retained known request records.

- Final full backend: **542 tests across 39 classes, 0 failures, 0 errors, 0 skipped** — `mvn -o test`, 3 min 01 s. This adds 87 backend cases to the 455 baseline. Evidence: `backend/target/entitlements-final-suite.log` and Surefire XML reports (ignored local artifacts).

- New backend classes: EntitlementIntegrationTest (41 cases), EntitlementControllerTest (45 expanded cases) and EntitlementMigrationTest (1). Includes real H2 transactions/concurrency, coherent reads during writes, last-slot races, rollback on audit failure, expiry, actor revocation during signing, downgrade safety, metadata history, legacy permission controls, atomic provisioning and fresh guest-mode projection.
- Actual populated upgrade test migrates a disposable H2 database to V14, seeds legacy grants/restrictions/assets/counts/requests and B4 intent, then migrates V15 and verifies preservation/backfill. V1–V14 were not edited. Historical-role demotion is tested defensively through the real HTTP security chain with mocked account rows; H2’s existing single-role constraint is respected.
- TDD evidence: initial focused compilation failed before EntitlementService existed. Subsequent regressions and report-only review exposed dependency validation via time-filtered new overrides, plan audit metadata loss, catalogue mutation outside the list window and stale legacy-control behavior. Validation now composes submitted values before persistence and uses database-precision timestamps; state/audit changes remain atomic. Two old public-cache assertions were intentionally updated to no-store.
- Frontend focused verification: `node --test frontend/tests/entitlements.test.mjs` — **8 passed, 0 failed/skipped**. Uses installed Node, Vite/esbuild and React only; covers API payload/auth scopes, failed-save errors, accessible false/zero/unlimited controls, planned labels, initial loading markup and AR attribute removal. No DOM/browser coverage percentage is claimed.
- Final frontend production build: **PASS**, 1,814 modules, 9.73s; `npm run build`. Evidence: `backend/target/entitlements-final-build.log`. Existing model-viewer bundle size remains; no dependency change.
- Browser visual/interaction verification: **BLOCKED** by the browser local-origin approval gate for 127.0.0.1. No alternate browser, raw-CDP or permission workaround was attempted. The temporary loopback fixture server was stopped; production build output replaces ignored fixture artifacts. Offline contract/component checks are separate evidence, not a substitute claim for full browser end-to-end testing.
- PostgreSQL-specific migration, locking and concurrency verification: **BLOCKED** — no explicitly confirmed disposable PostgreSQL instance. No live AWS policies, objects or production databases were touched or verified. Test configuration disables optional secrets imports/scheduling and uses synthetic AWS settings and disposable H2 databases.
- Report-only code/security review found no remaining material blocker after fixes to coherent snapshots, dependency checks, plan activation races, branding locking, private reason transport, audit snapshots and legacy controls. No mutation experiments. Existing A01–A03, OTP transaction, B1–B5 storage/authentication tests remain in the full suite; password reset stays disabled by default.

## Remaining limitations and rollout

1. WhatsApp ordering/waiter requests, table QR/table management, Google Reviews and advanced analytics remain **planned**. Their entitlements are configurable but their operational flags remain false. Existing basic analytics and contact links are unchanged. No payments, billing or new self-registration.
2. This sprint implements existing viewer capability gates, not the future three distinct guest menu products. Public S3 URLs, downloaded files and already printed QR destinations cannot be cryptographically revoked by menu/QR UI entitlements. Client QR generation is gated in the application; anyone who knows a public menu URL can independently encode it.
3. Catalogue/revision views are bounded to 200 entries and restaurant audit views to the latest 100; advanced catalogue/history pagination and bulk assignment migration are not provided. Plan writes fetch by exact ID and are not limited by first-page membership. There is no plan deletion or automatic migration of assignments.
4. Historical requests deleted before V15 cannot be reconstructed. Month limits use only known history plus the new durable ledger. Counters describe application resources/reservations, not physical retained S3 bytes; no inaccurate storage quota was introduced.
5. Model binary integrity, late PUTs, legacy cleanup discovery and manual cleanup dispositions retain the documented B4/B5 limitations. Existing server safety limits still apply regardless of entitlement grants.
6. Before deployment: validate V15 and contention on a confirmed disposable PostgreSQL instance, complete approved browser/mobile/accessibility flows, and review public-menu performance after disabling stale policy caches. Deploy compatible backend/schema before the new frontend/plan assignments. No production migration or deployment was performed; roll back application code without destructive down-migrations.

## Exact files changed (42)

Paths relative to the repository root:

```text
backend/src/main/java/com/atelier/media/dto/MediaResponseDto.java
backend/src/main/java/com/atelier/media/dto/PublicMenuDto.java
backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantCreateDto.java
backend/src/main/java/com/atelier/media/service/AdminService.java
backend/src/main/java/com/atelier/media/service/BrandingService.java
backend/src/main/java/com/atelier/media/service/DishService.java
backend/src/main/java/com/atelier/media/service/MediaService.java
backend/src/main/java/com/atelier/media/service/ModelRequestService.java
backend/src/main/java/com/atelier/media/service/PublicMenuService.java
backend/src/main/java/com/atelier/media/web/AdminController.java
backend/src/main/java/com/atelier/media/web/PublicMenuController.java
backend/src/main/java/com/atelier/media/web/RestaurantController.java
backend/src/main/java/com/atelier/media/web/StudioController.java
backend/src/test/java/com/atelier/media/web/RestaurantControllerTest.java
frontend/src/api/client.js
frontend/src/components/ModelStage.jsx
frontend/src/components/admin/RestaurantDrawer.jsx
frontend/src/components/admin/RestaurantFormModal.jsx
frontend/src/components/guest/DishSheet.jsx
frontend/src/components/studio/DishManager.jsx
frontend/src/components/studio/StudioTopBar.jsx
frontend/src/pages/AdminPage.jsx
frontend/src/pages/GuestMenuPage.jsx
frontend/src/pages/StudioPage.jsx
frontend/src/utils/modelViewer.js
backend/src/main/java/com/atelier/media/repository/EntitlementRepository.java
backend/src/main/java/com/atelier/media/service/EntitlementService.java
backend/src/main/java/com/atelier/media/service/FeatureRegistry.java
backend/src/main/java/com/atelier/media/web/OwnerEntitlementController.java
backend/src/main/java/com/atelier/media/web/PlanController.java
backend/src/main/resources/db/migration/h2/V15__plans_entitlements.sql
backend/src/main/resources/db/migration/postgresql/V15__plans_entitlements.sql
backend/src/test/java/com/atelier/media/service/EntitlementIntegrationTest.java
backend/src/test/java/com/atelier/media/service/EntitlementMigrationTest.java
backend/src/test/java/com/atelier/media/web/EntitlementControllerTest.java
frontend/src/components/admin/EntitlementFields.jsx
frontend/src/components/admin/PlansManager.jsx
frontend/src/components/admin/ProvisioningPlanFields.jsx
frontend/src/components/admin/RestaurantEntitlements.jsx
frontend/src/components/studio/OwnerPlanOverview.jsx
frontend/tests/entitlements.test.mjs
project-documentation/SPRINT_SUPERADMIN_ENTITLEMENTS_RESULTS.md
```

## Git and completion classification

Branch/HEAD unchanged from baseline. **25 tracked modified files, 19 untracked files, 0 staged**; this includes 42 sprint files and the two untouched untracked backups (product-evolution.zip, project-documentation.zip). No source changes were discarded, staged, committed or pushed. ZIP hashes match the previous checkpoint. AGENTS.md, dependencies, production configuration and V1–V14 migrations are unchanged. Generated logs, test databases, node caches and dist/target artifacts remain excluded. `git diff --check` passes.

- **Implemented and locally verified:** backend plan/revision/assignment/override/audit APIs, identity and tenant isolation, quota enforcement/reservations, legacy upgrade preservation and guest capability projections; React admin/provisioning/owner screens are implemented, production-built and covered by focused offline checks.
- **Configurable future permissions:** table management/QR, ordering, waiter requests, reviews and advanced analytics; no operational product claims.
- **Outstanding verification:** actual PostgreSQL behavior and browser/device interaction/visual checks. This is not production sign-off.

Next priority: the approved three menu experiences and restaurant/table QR management, followed by WhatsApp guest workflows. No next sprint was started.
