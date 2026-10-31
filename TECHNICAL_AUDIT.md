# Atelier AR: technical audit

2026-10-31; baseline `330a49c`. Application code, production configuration, credentials and schemas were not changed. Findings distinguish source-proven gaps from runtime reproduction. Frontend compilation and isolated utility checks passed; Java/Maven, database/API, AWS and device checks were unavailable. No production incident or deployed vulnerability is asserted without deployment evidence.

## Executive assessment

This is a substantial existing implementation, not a mock dashboard. Its controller/service/repository structure, tenant-scoped media operations, direct S3 uploads, DTOs, database migrations and tests are reusable. It is not yet justified to call it production-ready: authorization enforcement is inconsistent, tenant category assignment is unchecked, reset security has a transaction flaw, and infrastructure templates contradict private-media behavior. Address these before expanding functionality.

Severity reflects potential impact. CRITICAL means platform-wide privileged access risk; HIGH includes tenant isolation/session controls and blocking media configuration; MEDIUM covers reliability, privacy and functionality gaps; LOW covers maintainability/documentation debt. Static conclusions should become reproducible regression tests in the next approved work.

## CRITICAL

### A01 — Revoked admin tokens retain platform privileges on most routes

Evidence: `backend/src/main/java/com/atelier/media/security/SecurityConfig.java` audience filter and role rules; `service/AdminAuthService.java:71-98`; `web/AdminController.java` (restaurant list/detail/mutations, preview and model routes); `service/AdminService.java`; `security/TokenService.java:67-83`.

`AdminAuthService.require` loads the current platform user and validates token version, but only its me/password methods invoke it. `AdminController` read operations do not pass a principal to any account gate. Mutation methods parse AdminPrincipal and use it for audit attribution, without checking current user/version. The JWT decoder checks signature/issuer/time and the filter checks audience; neither revalidates the admin account. Admin tokens are hardcoded to thirty days.

Impact: an otherwise valid stolen/revoked admin token can continue listing all tenants, resetting passwords, impersonating owners, publishing models and deleting restaurants after a password/version change. A deleted platform user is also not centrally rejected. This is a source-established enforcement gap; no exploit against a running service was attempted.

Recommended approved work: enforce current admin account/version before every platform action; reuse a dedicated account access service/filter rather than scattered profile calls. Test old signed tokens against both read and destructive-capability endpoints on disposable fixtures. Ensure new and existing password-change sessions behave consistently; shorten/configure lifetime only as an explicit policy decision.

## HIGH

### A02 — Several studio routes bypass RestaurantAccess

Evidence: `web/StudioController.java:85` (preview), `:118` onward (categories), `:155` (menu status), `:169-197` (concierge/history/analytics); `service/CategoryService.java`; `ModelRequestService.presignPhotos/confirmRequest/getHistoryForRestaurant`; `PublicMenuService.getStaffPreview`; `AnalyticsService.getStudioAnalytics`. Contrast `DishService`, `MediaService` and `BrandingService`, which call `RestaurantAccess.requireStudioAccess`.

These routes trust tenant identity in the JWT but omit current account/version/status/password checks. A full-access token issued before suspension, password reset or password change remains usable on these paths until token expiry. Role-only setup tokens are restricted correctly; the issue is older full-access tokens. Soft-deleted restaurant rows still exist, so some state-changing routes remain operable. The menu-status controller directly loads and saves the restaurant.

Tenant IDs still come from verified claims; this is not evidence of arbitrary tenant selection. It is a revocation/suspension bypass. Reuse the established RestaurantAccess gate consistently and test every route family with revoked, suspended, deleted and forced-password accounts.

### A03 — Supplied category ID can cross restaurant ownership boundaries

Evidence: `service/MediaService.java:101` accepts request categoryId; `service/DishService.java:107-108` assigns update categoryId and `:124` reads category name using unscoped `findById`; `service/ModelRequestService.java:509` also resolves category unscoped; migration `V5__custom_categories_and_3d_concierge.sql` defines an ordinary category FK.

The FK only proves the category exists. A tenant can associate its own dish with another restaurant's category; update responses can disclose that foreign category's name, and deletion of that foreign category can affect the assignment. No ability to edit another restaurant's dish was found in these paths. Require a `findByIdAndRestaurantId` check for create/update and scope all response lookups. Test rejected foreign-category assignment and unchanged persisted state.

### A04 — OTP failure counters are saved inside a transaction that then rolls back

Evidence: `service/OtpService.java:104-133`, `exception/InvalidFieldException.java` extends RuntimeException; `service/OtpServiceTest.java` directly constructs service with mocked repositories.

The reset method increments attempts/used flags, saves, then throws InvalidFieldException from its `@Transactional` method. Default Spring rollback rules roll these changes back. Thus the database is expected to retain the prior attempts count, undermining the five-attempt lockout. Existing mock-object assertions do not test commit behavior. The reset endpoint itself has no corresponding per-IP limiter; the send-code endpoint does. OTP selection/consumption also lacks a lock/atomic consume operation, leaving concurrent reuse/attempt races to test.

This is a framework-backed source inference, not a database reproduction in this environment. Password reset is disabled by default and production delivery is currently a placeholder, limiting default exposure. See [Spring transaction rollback rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html). Preserve failed-attempt changes in a deliberate transaction boundary and add real transactional tests before enabling reset.

### A05 — S3 templates prevent the private concierge download path

Evidence: `infra/s3-public-read-policy.json` explicitly denies `s3:GetObject` for Principal `*` on the private prefix, without a condition; `infra/iam-policy.json` grants object permissions/ListBucket only for the public prefix; `S3Storage.presignGet`; `ModelRequestService.parsePhotos/presignReferencePhotosJson`.

The blanket deny also applies to the authenticated identity signing a presigned GET; presigning does not override policy denial. Separately, the supplied IAM example cannot PUT/HEAD/GET/DELETE private assets. These are demonstrable template conflicts, not proof that the live bucket uses them. The deployment guide's simpler allow-public-only policy differs from the checked-in policy.

Recommended work: keep private assets private while allowing the application's intended principal; scope object/list permissions to the required public and private prefixes. Verify anonymous denial and authorized presigned PUT/HEAD/GET/deletion in a disposable prefix. Do not solve this by granting anonymous private access. See [AWS policy evaluation explanation](https://aws.amazon.com/blogs/security/iam-policies-and-bucket-policies-and-acls-oh-my-controlling-access-to-s3-resources/) and [S3 presigned access troubleshooting](https://docs.aws.amazon.com/AmazonS3/latest/userguide/troubleshoot-403-errors.html).

## MEDIUM

### A06 — Category ordering client/API contract fails

Evidence: `frontend/src/api/client.js:135-136` sends `{ orderedCategoryIds }`; `backend/.../dto/CategoryReorderDto.java` requires `@NotEmpty List<Long> orderedIds`; `StudioController.reorderCategories` uses the DTO accessor; `CategoryManagerModal.jsx:96` calls this client method.

The expected field is absent in the client's JSON, so validation cannot accept the request. Static contract failure is confirmed; HTTP execution was unavailable. Align the request field and test save/reload/order. The service also silently ignores unknown IDs and accepts incomplete/duplicate ordering; validate these according to the existing ordering behavior.

### A07 — Category changes do not invalidate published menus

Evidence: `service/CategoryService.java` has no MenuCache dependency/eviction; `PublicMenuService.getMenu` caches category DTOs/names; `config/CacheConfig.java` expires entries after ten minutes.

Rename/reorder/activation changes can leave guests with stale categories until cache expiry. Browser/CDN public caching adds another layer. Evict the owning restaurant after successful category changes. Seeding defaults from a GET is also a write with no unique category-name constraint; concurrent empty-category requests can create duplicates, currently hidden by response deduplication.

### A08 — Daily analytics aggregate updates are not atomic

Evidence: `service/AnalyticsService.java:139,173-181`; `model/AnalyticsDaily.java`; `repository/AnalyticsDailyRepository.java`; migration V10.

Read-increment-save has neither an entity version nor an atomic upsert. Concurrent requests can lose increments or race inserting the same composite key. Exceptions are caught within the transaction, whereas commit-time failures can occur later in an unobserved CompletableFuture. The private virtual-thread executor has no explicit lifecycle/shutdown or global queue bound; per-IP throttling is process-local. Use database-safe atomic aggregation and observed async outcomes; test with real concurrent PostgreSQL transactions. Raw event storage remains unbounded and dashboards still use event counts/counters rather than the daily table for their main summaries.

### A09 — Concierge S3 network calls hold database transactions and locks

Evidence: `ModelRequestService.confirmRequest` is transactional and performs HEAD at `:191` after taking a pessimistic dish lock; `approveModel` is transactional and HEADs at `:297`; `cleanupReferencePhotos` is transactional and calls deleteKeys at `:459`. `S3Storage`'s class comment explicitly requires avoiding network calls in database transactions.

Slow AWS requests hold limited Hikari connections and, in confirmation, locks. Reuse the established media service pattern: read/validate, HEAD outside a transaction, then short transaction with state revalidation and cleanup after commit. Preserve race safety rather than merely deleting annotations.

### A10 — Private media cleanup and deletion acknowledgement are incomplete

Evidence: `AdminService.hardDelete` gathers photo/model/replacement/branding keys but not model-request photo/history/pending keys and sweeps only `tenantPrefix`; `StorageJanitor.wipeRestaurantAssets`; `S3Storage.deleteKeys:174-182`; `ModelRequestService.cleanupReferencePhotos:447-470`; migration V10 lacks restaurant FK on analytics_daily.

Hard deletion can leave private reference photos and pending private uploads, especially across slug changes. Pending private uploads are not expired by the ordinary media reaper. Approved-photo cleanup processes the current media photo JSON, not all historical/superseded request records. The S3 adapter ignores DeleteObjects per-key errors, catches SDK failures and returns submitted count; caller cleanup can clear references despite failed deletion. A hard-deleted tenant's daily analytics rows also lack cascade removal.

Use durable cleanup tracking/retries and truthful success counts; enumerate both prefixes and retained history before deleting rows. Add orphan checks with fixtures; no destructive validation was performed here.

### A11 — Long-lived browser tokens and incomplete credential lifecycle

Evidence: `frontend/src/context/AuthContext.jsx` localStorage tokens and handoff; `application.yml` thirty-day restaurant/seven-day impersonation TTL; `TokenService.java:69` thirty-day admin TTL; `AdminAuthService.changePassword` returns only a profile; `components/admin/ChangePasswordModal.jsx` updates profile without a replacement token/logout; `SuperAdminBootstrap` changes hashes without incrementing token version.

Tokens are exposed to successful same-origin script compromise and logout is client-only. Admin password change leaves the UI holding an old-version token: me/password calls fail while most other routes still accept it (A01). Generated admin must-change-password flags do not restrict token roles or platform actions. Bootstrap reset also does not revoke prior tokens. Review lifetime and storage as a bounded security change, make password-change session behavior coherent, and enforce any intended admin setup requirement explicitly.

### A12 — Upload metadata verification is weaker than documentation implies

Evidence: `MediaService.confirmUpload` verifies length and stores S3 ETag without comparing the submitted ETag; `DishService.confirmModelUpload` checks pending key/length; `MediaReplacementService.confirm` additionally compares ETags; `ModelRequestService.approveModel` accepts a matching restaurant models prefix without an issued final-key record; `S3Storage.presignPut` signs MIME/size/cache metadata.

Signed metadata limits uploads but HEAD does not establish image/GLB content validity. Browser validation can be bypassed. A PUT URL remains reusable within its TTL; same-size content can overwrite an already-confirmed object, undermining immutable caching and stored integrity metadata. Different paths enforce different caps (50 MiB direct, 25 MiB concierge), and not all paths perform the same MIME/ETag checks. Make these guarantees explicit and test them; choose a validation/finalization strategy consistent with direct uploads rather than introducing large request buffers into Java.

### A13 — Private references receive public immutable cache metadata

Evidence: `S3Storage.java:49,115-123` uses one `public, max-age=31536000, immutable` header for every presigned PUT, including private concierge photos.

Private authorization and cache policy are separate controls. A successful authorized GET can expose long-lived cacheability beyond the fifteen-minute signed URL window. Use separate private-object cache metadata and verify intermediary/browser behavior. Published assets intentionally remain public when the menu-live switch hides dishes; this is existing behavior, not enforced asset confidentiality.

### A14 — No frontend behavioral suite, PostgreSQL verification or automated release gate found

Evidence: frontend package scripts contain dev/build/preview/showcase generation only; no checked-in CI pipeline or Maven wrapper found; Dockerfiles package with `-DskipTests`. Existing 208 Java test annotations are not evidence of executed coverage. Most services mock repositories/S3/transactions; the H2 concurrency integration test cannot prove PostgreSQL partial-index semantics, and H2 V8 omits active-request uniqueness.

Add focused regression tests for the specific audit defects first, then an isolated PostgreSQL migration/API path and browser core-flow checks. Preserve current test helpers but use real transactions where commit/rollback is the behavior under test.

### A15 — Menu invalidation and session throttling are instance-local

Evidence: `CacheConfig` ten-minute local menu cache; `RestaurantAccess` thirty-second local version cache; Caffeine login and analytics throttles. Admin password resets and OTP reset do not call RestaurantAccess.invalidate, unlike owner password change.

Protected service paths can retain old token versions for up to thirty seconds; new tokens may temporarily conflict with an old cached version. Category updates have the additional missing eviction in A07. Multiple backend instances can serve old menus for ten minutes after a change and have separate throttle budgets. No distributed deployment is asserted; document single-instance assumptions and add multi-instance coordination only when required.

## LOW

### A19 — Top-dish views are not populated by the current guest UI

Evidence: `frontend/src/pages/GuestMenuPage.jsx:49` sends MENU_VIEW without dishId; `frontend/src/components/guest/DishSheet.jsx:70,78` sends only MODEL_3D_VIEW and AR_LAUNCH with dishId. `AnalyticsService.persistEvent` increments dish views only when MENU_VIEW includes dishId, and `getStudioAnalytics` sorts top dishes by viewsCount.

The current frontend does not produce the event shape needed to increment individual dish views, so the top-dish view ranking can remain zero/tied even while model/AR counters increase. Confirm the intended existing metric, then align event production and dashboard labeling; do not invent a new tracking requirement.

### A16 — Documentation/configuration drift obscures the real state

README migration filenames/descriptions through V7 do not match actual SQL, and it omits V13. Release notes claim four-hour studio/one-hour admin TTLs but code uses thirty days. Docs imply five mandatory reference angles, while service accepts one to five. DEPLOYMENT.md uses `/menu/{slug}` in its walkthrough, while routing is `/r/{slug}`. Configuration comments still describe local as default although prod is default. README contains trailing mixed-encoding text. Neither production JWT checks nor Docker memory limits prove all secrets are validated or total RSS stays below 512 MB. Existing docs were preserved; these new documents supply corrected onboarding facts.

### A17 — Frontend size, duplicate config and incomplete guest filtering

Build output includes a 579.61 kB main app chunk and a separately lazy 1072.76 kB model-viewer chunk before gzip. Pages are statically imported; mobile performance was not measured. Vercel headers and Docker build definitions are duplicated. Guest dietary tags persist/display, but no tag-filter behavior is implemented. These do not justify redesigning the interface or replacing the router. Measure load cost and resolve shared-config drift before considering narrow improvements.

### A18 — Hosted AR and security headers need device validation

Both Vercel configs deny camera permission while allowing xr-spatial-tracking; interaction with actual WebXR versus external Scene Viewer/Quick Look must be tested on the target devices. CSP allows broad HTTPS connections and unsafe script modes to accommodate 3D workers/decoders; a header being present is not proof of an effective restrictive policy. Do not remove 3D-required directives without browser checks. ModelStage's floor placement maps to horizontal surfaces, including tables; actual model scale depends on source geometry and was not measured.

## Positive controls and limits

- Narrow public API allowlist, default-deny security rules, issuer/signature/time/audience validation and BCrypt passwords exist.
- Tenant-scoped media finders and pessimistic/optimistic locking protect important existing paths; guest DTOs remove private notes/keys/codes rather than returning JPA entities.
- DTO link validation restricts guest-facing URLs; admin sorting is allowlisted; reviewed JPQL uses parameters. No SQL-injection defect was established.
- Flyway owns schema and API services mostly keep AWS calls outside short transactions; preserve these patterns.
- No real credential value was identified in the reviewed configuration/source. Development/test/example literals are present; no full-history secret scan or live AWS permission audit was performed.
- No unapproved source, dependency, schema or production-config fix was applied. Source findings remain candidates for regression tests and approved fixes, not claimed runtime test results.
