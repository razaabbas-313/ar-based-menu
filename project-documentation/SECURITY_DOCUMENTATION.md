# Authentication, tenant isolation and security assessment

Snapshot: 2026-10-31. Current source includes the approved, uncommitted A01–A03 remediation. Historical [TECHNICAL_AUDIT.md](../TECHNICAL_AUDIT.md) is preserved; its original wording is not a statement that remediated defects still exist. [Master](MASTER_PROJECT_DOCUMENTATION.md) · [API access rules](API_REFERENCE.md) · [Roadmap](DEVELOPMENT_ROADMAP.md).

Milestone 1A update: OTP transaction/consumption repair is implemented under the user's separate approval. See [actual evidence and limitations](MILESTONE_1A_VERIFICATION.md). Historical test totals elsewhere describe earlier snapshots; password reset remains disabled by default and real delivery remains unapproved.

B2+B3 sprint update: private-cache signing and result-bearing deletion are implemented locally; [consolidated results](SPRINT_B2_B3_RESULTS.md) record 428 passing backend tests and the frontend build. AWS/PostgreSQL deployment behavior remains unverified. Existing B1 policy corrections are preserved; durable cleanup and transaction-boundary completion remain B4+B5.

## 1. Identity and access model

| Actor | Identity and access |
|---|---|
| Anonymous visitor/guest | Public landing, single-venue public menu by slug, public analytics submission, login/config and enabled reset APIs; health/info allowlist |
| Restaurant owner | Restaurant row is account and tenant; ROLE_RESTAURANT + atelier-studio audience; current version/status/deletion checks; tenant-scoped operations |
| Setup owner | ROLE_RESTAURANT_SETUP instead of full role; only auth/me and auth/change-password allowed; full studio blocked |
| Platform owner | platform_users row; ROLE_SUPER_ADMIN + atelier-admin audience; AdminAccess current account/version/role/usable-password checks |
| Support impersonation | Separate restaurant-audience JWT with actor claim; audited issuance; scoped to selected restaurant; permitted to bypass owner password-setup requirement, not suspension or deletion; cannot change owner's password |

There are no staff membership tables, per-feature role assignments, externally federated identities, MFA or RBAC administration APIs. No public registration route exists: accounts are provisioned by platform administration. Knowing a public slug grants guest access only; slugs are addresses, not secret authorization credentials.

## 2. JWT creation, validation and storage

`security/JwtKeys.java` builds HS256 Nimbus encoder/decoder, rejects insufficient key length, and validates issuer/time. `TokenService` creates role/audience, subject, UUID jti, issued/expiry, email, version and identity claims. Studio claims include rid/slug; admin claims include aid; support adds imp. Principal parsers derive identity only from the verified JWT and default absent version to 1 for existing compatibility; they do not trust request bodies to choose tenant.

`SecurityConfig.securityFilterChain` disables basic/form login and sessions, disables CSRF for explicitly supplied bearer tokens, enables CORS, checks audiences on protected paths and requires role authorities; unmatched routes are denied. Security headers include CSP, frame denial and nosniff. Allowed APIs return ProblemDetail through the security handlers or GlobalExceptionHandler. Existing no-store intent has explicit public menu cache exceptions.

Actual TTLs are thirty days for restaurant by application configuration, thirty days hardcoded for admin, seven days configurable for support. These are not the four-hour/one-hour values in release notes. AuthContext stores normal restaurant/admin tokens in localStorage and active impersonation in sessionStorage; handoff temporarily uses localStorage. A same-origin script compromise can read these tokens. Logout clears browser state only; there is no revocation/logout/refresh endpoint.

Passwords use BCrypt cost 12 in SecurityConfig. Login uses a dummy hash to reduce unknown-account timing differences. Admin login records last_login_at. Owner password change checks current password, prevents support sessions, hashes new password, clears setup and increments token_version, returning a fresh full-access token. Admin password change increments version but returns only a profile; its current UI retains an invalid old token until the next 401, then signs out. This behavior remains a UX/session-lifecycle gap, not the old A01 bypass.

## 3. Current account enforcement and A01–A03 changes

`service/AdminAccess.requireAccount` reads the current platform row and rejects absence, null/mismatched tokenVersion, non-super-admin current role or unusable password. `AdminController.requireAdminAccess` is a controller-wide @ModelAttribute guard; it covers reads, previews, provisioning, resets, impersonation, deletion and model actions. `AdminAuthService.me/changePassword` reuse AdminAccess; public login remains public. A new controller elsewhere must explicitly preserve this boundary.

`service/RestaurantAccess.requireAccount` now reads the current restaurant row once and compares its version directly; the old thirty-second version cache and invalidation hook were removed. It then rejects deletion and disallowed status. `requireStudioAccess` additionally enforces password setup except the existing support case. `StudioController.requireStudioAccess` guards every studio handler, including scalar-ID service calls; media/dish/branding service checks remain. Account me/password routes intentionally retain setup access. No token TTL, password hashing, audience or role requirement was weakened.

`MediaService.generatePresignedUploadUrl` and `DishService.updateDish` validate a supplied custom category through `CategoryRepository.findByIdAndRestaurantId` before mutation/save/signing. Dish and concierge category-name resolution now uses the same scoped finder. The database still has an ordinary category FK; historical inconsistent rows are not migrated. Their foreign names are not resolved in those responses. No schema repair or production cleanup was run.

These changes are covered by prior completed signed-bearer route regressions (159 executions) and category isolation tests (7). This documentation pass inspected source/results; it did not rerun them or perform live penetration testing.

## 4. Password reset and bootstrap

Reset is disabled by default and AuthController exposes that flag. Enabled send-code requests use a generic unknown-account response, per-IP five-request/15-minute limit and sixty-second resend cooldown. Existing unused codes are invalidated; a six-digit SecureRandom code is BCrypt-hashed, expires in ten minutes and is sent through EmailSender. Only LoggingEmailSender exists: local/test debug logging contains the code, production performs no email transport. Do not interpret “dispatched” logging as delivery.

Milestone 1A reproduced the old rollback defect with real H2 transactions: wrong attempts reloaded as zero, the fifth failure as four and four concurrent valid redeemers all succeeded. OtpService now uses explicit REQUIRES_NEW transactions; failed-code outcomes commit before the public validation exception is thrown. Both issuance and redemption acquire RestaurantRepository.findByEmailForUpdate before accessing OTP state. This serializes attempts, resend/replacement and consumption even when no OTP exists yet. Successful consumption, sibling invalidation, BCrypt password change, setup-flag clearing and token-version increment commit together. A real SQL failure test verifies rollback of that successful path. Deleted/suspended accounts cannot recover; permitted account status is not changed.

The reset endpoint/service request and response shapes remain unchanged. Reset requests now also have a process-local, normalized-email ten-request budget, expiring after fifteen minutes without another request; exceeding it returns 429 before DB/BCrypt work. The durable per-code five-attempt cap remains the authoritative lockout. Cache eviction/restart/multiple instances can weaken the extra budget; it is not a distributed anti-abuse guarantee. Existing send-IP limiting and resend cooldown remain. EmailSender is called only after the issuance transaction commits, outside its account lock; a future delivery failure cannot undo that issuance and needs a separately reviewed delivery/retry design.

H2 verifies local commits/rollback and concurrent single-use behavior; PostgreSQL concurrency remains **BLOCKED**, with no confirmed disposable target. H2 rejects PENDING_ONBOARDING although PostgreSQL V5 permits it, so actual H2 status tests cover ACTIVE/TRIAL/PENDING and service unit tests also cover PENDING_ONBOARDING. No historical migration was altered. Generic payloads still do not prove full timing/enumeration resistance: resend cooldown is account-dependent. Keep reset disabled pending approved real delivery, PostgreSQL verification and complete recovery acceptance.

SuperAdminBootstrap activates a seeded unusable account or applies an explicitly enabled reset. When initial password is absent it generates a password and logs it; generated secrets and OTP logs must be protected. The bootstrap reset path does not increment token_version. Its must-change-password flag is not represented by a restricted admin setup role. Development profile provides known development defaults and enables reset-on-start; never deploy local settings.

## 5. Rate limiting, CORS and headers

- CaffeineLoginThrottle: configured five key+IP failures in fifteen minutes, twenty failures per IP across keys; progressive sleeps of .5/1/2 seconds; process-local cache maximums. Sleeps run in login transaction boundaries in some services and are not a distributed abuse control.
- ClientIpResolver trusts X-Forwarded-For only when remoteAddr exactly matches configured trusted proxies. Without correct proxy configuration clients may share a proxy IP and rate budget; a trusted chain still requires operational validation. No live proxy setup was inspected.
- Analytics: sixty events/minute/IP, thirty-second tuple dedup; process-local and spoofable as business analytics, not trustworthy billing evidence. It allows events for nondeleted accounts even if not publicly live/suspended.
- API CORS uses configured origin patterns, GET/POST/PUT/PATCH/DELETE/OPTIONS, explicit allowed/exposed headers, no credential cookies. Local wildcard and hosted wildcard examples are development/convenience settings, not proof of restricted production origins.
- S3 CORS is separate from API CORS. ETag exposure is required by browser confirmation. Templates allow several host patterns; actual bucket/account policy and origins are unverified.
- Vercel has duplicated CSP/frame/nosniff/referrer/permissions headers. Camera is denied and XR tracking allowed; native AR handoff and browser WebXR need target-device testing. CSP permits broad HTTPS/blob connections and unsafe script modes; do not remove model decoder/worker requirements without checks.

## 6. Private media and input protection

Public menu assets use a public prefix and signed PUT; private concierge references use a distinct prefix and short GET signature. Current supplied public-read JSON includes an unconditional private GetObject deny for everyone; that also denies the signer identity. IAM example omits private prefix permissions. A presigned URL is delegated access subject to IAM/bucket evaluation, not an override. Live deployment may differ; no AWS call verified the template impact.

UUID/prefix ownership, MIME allowlists, extension compatibility, configured caps and HEAD size checks exist. No binary malware/image/model validation service exists; browser checks are bypassable. Some confirmations compare ETags, others only store them; final concierge approval checks restaurant prefix instead of a recorded issued model key. Presigned PUT remains reusable until expiry and all objects receive a public immutable cache header, even private references. Media-private mode hides API dishes but does not revoke public object reads.

Reviewed JPQL uses parameters, admin sort fields are allowlisted and DTO URLs restrict HTTP(S)/maps patterns. No SQL-injection defect was established. Category DTO annotations do not establish DB uniqueness; admin notes/permission bodies lack @Valid. Input exceptions are mapped with generic integrity/AWS response detail; server logs can still contain operational PII/object paths and must not be public.

## 7. Threat model

| Threat/actor | Protected assets/boundary | Existing defense | Residual risk |
|---|---|---|---|
| Unauthenticated attacker | Admin/studio metadata and mutations | Default deny, JWT signature/issuer/time, role/audience | Login/reset abuse; public event spoofing; no production penetration test |
| Restaurant A targeting B | Media/category/settings and private references | JWT tenant, scoped finders, A03 ownership validation, issued reference keys | Scalar-ID internal services depend on guarded callers; DB does not encode composite category ownership |
| Stolen old admin/studio token | Privileged operations after revoke | A01/A02 current-row version checks | Long validity before revocation; browser localStorage exposure; bootstrap reset does not revoke |
| Compromised browser/script | JWT, signed URL and uploaded files | URL/DTO restrictions, CSP headers, short upload signature | Broad/unsafe script directives and persistent browser tokens; signed PUT replay |
| Misconfigured storage operator | Private reference confidentiality/availability | Prefix separation, intended presigned GET | Broken policy templates, public cache metadata, incomplete lifecycle deletion |
| Resource/cost abuse | DB pool, S3 capacity/transfer, analytics | Upload caps, process throttles, async work, bounded query pages | No tenant storage quota, global queue bound, durable analytics delivery or retention enforcement |
| Operator/deployment mistake | Production data and account credentials | Prod default, local-hosted check, Flyway ownership, ignored secrets | No backup/restore automation, production checks don't validate every secret, generated bootstrap password logs |

## 8. Audit finding validation and remediation ledger

All source paths in this table are under `backend/src/main/java/com/atelier/media/` unless explicitly prefixed. “Confirmed” means source-confirmed or retained prior test evidence as described, not a live exploit. Historical severity is retained even when resolved; no issue is silently downgraded or marked fixed.

|ID / original severity|Affected files and root cause / impact|Verification and current status|Recommended next work|
|---|---|---|---|
|A01 CRITICAL|AdminController, AdminAuthService, SecurityConfig: profile-only current admin/version checking allowed revoked privilege|**REMEDIATED in current working tree** by AdminAccess + controller guard; prior signed-token tests cover every admin operation and missing/revoked accounts|Preserve gate on any future admin controller; review production rollout separately|
|A02 HIGH|StudioController, RestaurantAccess: categories/preview/menu-status/concierge/analytics bypassed account gate|**REMEDIATED in current working tree**; all studio handler states exercised with signed tokens; fresh version check also removes stale-cache window|Keep service checks, setup/support exceptions and caller identity rules intact|
|A03 HIGH|MediaService, DishService, ModelRequestService, CategoryRepository: FK existence without tenant check/unscoped names|**REMEDIATED in current working tree**; seven isolation executions reject assignments/no writes and suppress legacy foreign names; own category succeeds|Do not infer DB legacy cleanup/composite FK enforcement; assess existing data only in approved safe work|
|A04 HIGH|OtpService + InvalidFieldException: old failed-attempt rollback and concurrent reuse|**IMPLEMENTED / H2 VERIFIED** in Milestone 1A: explicit committed outcomes, shared account lock, atomic consume/password/version update and real regression tests; PostgreSQL verification **BLOCKED**|Preserve disabled default; confirm disposable PostgreSQL concurrency and separately approve real delivery/recovery activation; see milestone evidence|
|A05 HIGH|infra/s3-public-read-policy.json, infra/iam-policy.json: blanket private read deny and missing private IAM permissions|**OPEN**, static template conflict; deployed policy unknown|Review least-privilege templates, test authorized private operations and anonymous denial in disposable prefix|
|A06 MEDIUM|frontend/src/api/client.js vs dto/CategoryReorderDto: orderedCategoryIds vs orderedIds|**OPEN / broken contract**, source comparison; no HTTP reproduction in documentation pass|Align field name, test persisted reorder/reload; do not conflate with resolved ownership finding|
|A07 MEDIUM|CategoryService vs PublicMenuService/CacheConfig: category writes never evict menu; GET seeds without uniqueness|**OPEN**, source confirmed|Post-commit tenant menu eviction; transactional duplicate/default-seed handling; migration only if approved|
|A08 MEDIUM|AnalyticsService, AnalyticsDaily: read/increment/save lacks lock/version/upsert; ignored async failure|**OPEN**, source confirmed, no load/DB-race reproduction|Atomic DB aggregate update, observed future failures and bounded executor lifecycle; real concurrency tests|
|A09 MEDIUM|ModelRequestService.confirmRequest/approveModel/cleanupReferencePhotos: AWS network inside transaction/lock|**OPEN**, annotations and calls confirmed|Read/HEAD outside short transaction then revalidate locked state; preserve existing race protections|
|A10 MEDIUM|AdminService.hardDelete, StorageJanitor, S3Storage deletion, reference cleanup, V10|**PARTIALLY IMPLEMENTED**: B3 distinguishes acknowledged/failed/unknown outcomes, reports incomplete wipes and retains unresolved current reference metadata; mocked failure regressions pass|B4 durable manifests/retries, complete private/history/pending enumeration, accounting and aggregate lifecycle remain open; no live deletion verified|
|A11 MEDIUM|AuthContext, TokenService/application.yml, AdminAuthService, SuperAdminBootstrap: long browser sessions, profile-only password response, bootstrap reset lacks version bump|**PARTIALLY REMEDIATED** only old-token continued admin privilege via A01; long TTL/storage/admin setup/bootstrap and password-change UX remain|Define coherent re-login/replacement token policy, bootstrap revocation and any admin setup requirement; do not claim this was fixed wholesale|
|A12 MEDIUM|S3Storage/MediaService/DishService/MediaReplacementService/ModelRequestService: inconsistent ETag/MIME guarantees, replayable PUT and final model prefix-only approval|**OPEN**, source confirmed; no live S3 replay exercise|Define publish integrity guarantees/issued-key tracking and common checks; retain direct bytes architecture|
|A13 MEDIUM|S3Storage private upload/download caching|**IMPLEMENTED / LOCALLY VERIFIED**: B2 signs private PUT with private,no-store and explicit private GET response override; public immutable caching preserved|Existing object metadata/copies unchanged; legacy unkeyed/fallback metadata and browser/CDN behavior need separate review; no AWS cache experiment performed|
|A14 MEDIUM|Frontend scripts, Dockerfiles, H2 tests: no frontend suite/CI/PostgreSQL gate|**PARTIALLY IMPROVED**: actual 374-test result exists, 166 security regressions added; frontend/PostgreSQL/CI gaps remain|Targeted browser + PostgreSQL tests and CI gate; don't equate H2/mocks with complete integration coverage|
|A15 MEDIUM|CacheConfig/login/analytics Caffeine and former RestaurantAccess cache: multi-instance invalidation/rate isolation|**PARTIALLY REMEDIATED**: restaurant version cache removed in A02; menu cache and throttles remain instance-local|Document single-instance scope; distributed coordination only when deployment requirements justify it|
|A16 LOW|README/release notes/DEPLOYMENT/AGENTS/config comments: wrong migrations/TTLs/routes and stale baseline|**OPEN in historical docs**, current documentation reconciles; root files intentionally preserved|Use this dated evidence set; update authoritative entry points separately to avoid conflicting onboarding|
|A17 LOW|App imports/Vite output, duplicate Docker/Vercel files, guest FilterBar: large bundles and absent dietary filter|**OPEN**, source/build artifact evidence; no performance benchmark|Measure loading; reconcile duplicated configs; confirm dietary filtering requirement before adding it|
|A18 LOW|Vercel permissions/CSP + ModelStage: camera/XR/device compatibility unknown|**UNVERIFIED runtime risk**, headers confirmed, no supported-device tests|Run HTTPS device matrix for WebXR/native handoff before editing policies|
|A19 LOW|GuestMenuPage/DishSheet vs AnalyticsService: frontend never sends dish MENU_VIEW used for dish views ranking|**OPEN**, source producer/consumer comparison|Define intended existing metric then align events/ranking/labels; validate dedup|

### Additional finding PD01 — Private-menu envelope/UI mismatch (functional HIGH)

`dto/PublicMenuDto.java` declares `menuLive`; `frontend/src/pages/GuestMenuPage.jsx` assigns restaurant.isMenuLive from `menu.isMenuLive !== false`, overriding the nested restaurant flag. The expected top-level field is undefined and therefore becomes true. The private holding-screen condition can fail even though the API correctly withholds dishes. `RestaurantController.menu` also reconstructs the legacy envelope using its two-argument constructor, discarding categories and forcing menuLive=true. Status: **OPEN**, independently source-confirmed, not browser/API reproduced. Align the documented contract and test public/private/owner preview and legacy behavior. This is not evidence that private dish data is leaked.

## 9. Priorities and verification limits

No still-open CRITICAL finding was established in this snapshot after A01 remediation; that is not a general security certification. After Milestone 1A's local repair, retain PostgreSQL/delivery gates for recovery, then propose milestone 1B private-S3 review, followed by PD01/A06 and the remaining reliability work. No later milestone is authorized by the 1A approval. Full prioritized acceptance criteria are in the product-evolution roadmap.

No production database, AWS account/bucket, hosted deployment, real credential value, complete Git secret history, browser XSS surface or physical AR device was inspected. Config file locations and secret variable names are documented; credential contents are deliberately excluded. External semantic references, corroborated in the earlier audit: [Spring transaction rules](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html), [AWS deny precedence](https://aws.amazon.com/blogs/security/iam-policies-and-bucket-policies-and-acls-oh-my-controlling-access-to-s3-resources/), [S3 presigned permissions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/troubleshoot-403-errors.html). They do not substitute for runtime verification of this application.
