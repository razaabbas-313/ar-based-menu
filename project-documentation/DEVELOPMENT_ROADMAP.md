# Prioritized development roadmap

Snapshot: 2026-10-31. These are recommendations, not authorization or claims of implemented features. [Master](MASTER_PROJECT_DOCUMENTATION.md) · [Feature states](FEATURE_INVENTORY.md) · [Security ledger](SECURITY_DOCUMENTATION.md).

A01–A03 are implemented in the current uncommitted working tree and passed the prior 374-test suite; preserve/review those changes before future work. Do not reimplement them based on the historical root audit. Existing React/Spring/PostgreSQL/S3 boundaries remain the default. Every task below requires its own scope/approval; production data/config/schema changes are separate operational decisions.

Priority: P0 = immediate security/availability blocker; P1 = correctness/core reliability; P2 = measured improvement; P3 = conditional product decision. “Dependencies” are prerequisites, not an instruction to install a new library/provider.

## Phase 1: Security and stability

|Task / priority|Objective and dependencies|Affected modules|Acceptance criteria|Required verification|
|---|---|---|---|---|
|R01 / P0 — OTP transaction/consumption|Fix A04 before enabling reset; no email transport required to test|OtpService, OTP repository/tests|Wrong attempts commit despite error response; fifth invalidates; expired/used/concurrently consumed code cannot reset; generic account messaging preserved|Real transaction + concurrent DB tests, then full suite; no production OTP/reset|
|R02 / P0 — Private storage policy review|Resolve A05 in reviewed templates; depends on explicit disposable AWS target|infra policies/CORS, S3 adapter integration tests|Anonymous private reads denied; intended signer can PUT/HEAD/GET/delete only required prefixes; public assets remain intended public|Policy analysis plus authorized nonproduction roundtrip; no production policy application as part of code fix|
|R03 / P1 — Credential lifecycle|Resolve remaining A11; define intended session behavior first|AdminAuthService, SuperAdminBootstrap, AuthContext/admin password modal|Password change produces explicit re-login or approved replacement-token flow; bootstrap resets revoke existing tokens; intended admin setup rule unambiguous|Signed old/new-token route tests; browser session behavior; preserve current role/audience gates|
|R04 / P1 — Upload integrity guarantees|Resolve A12/A13; define publish/replay and private-cache requirements before implementation|MediaService/DishService/ModelRequestService/S3Storage|Consistent documented size/MIME/ETag guarantees, final issued-key ownership, private-safe cache metadata; direct bytes architecture retained|Mocked negative paths and disposable S3 tests for replay, stale keys, corrupt metadata and cache behavior|
|R05 / P1 — Deletion truthfulness and retry|Resolve A10 without destructive production cleanup|S3Storage, StorageJanitor, ModelRequestService, AdminService; aggregate lifecycle|Per-key S3 errors visible/retryable; DB does not falsely mark cleanup complete; private/pending/history/old-slug keys accounted for; tenant daily aggregates handled by approved design|Failure-injection unit tests, DB lifecycle test, optional disposable prefix deletion only|

## Phase 2: Core functionality completion

|Task / priority|Objective and dependencies|Affected modules|Acceptance criteria|Required verification|
|---|---|---|---|---|
|R06 / P1 — Category reorder contract|Fix A06 without unrelated category refactor|api/client.js, CategoryReorderDto/manager tests|Client and DTO use same field; saved ordering survives reload; foreign IDs never alter another tenant|Request-contract + service/browser tests; full backend suite|
|R07 / P1 — Menu privacy contract|Fix PD01 after deciding canonical envelope compatibility|PublicMenuDto, GuestMenuPage, legacy RestaurantController|Private guest sees holding state/zero dishes; owner preview remains authenticated; legacy response preserves intended state/categories|Public/private/preview HTTP and browser regressions; check preserved printed guest paths|
|R08 / P1 — Category consistency|Fix A07 with data-aware uniqueness proposal only if needed|CategoryService, MenuCache; possible separately approved new migration|Post-commit tenant eviction; concurrent default seed/create produces defined unique names; no silent duplicate masking|Concurrency + cache invalidation tests on PostgreSQL and H2 differences; no applied migration editing|
|R09 / P1 — Concierge transaction boundaries|Fix A09 while preserving ownership and concurrency|ModelRequestService and tests|S3 network outside DB locks/transactions; state/key/ownership revalidated before commit; replacement/history/counter correctness preserved|Transaction-order assertions, two-confirmer concurrency, HEAD failure/timeout; no large bytes in JVM|
|R10 / P1 — Analytics correctness|Fix A08/A19; first agree metric definition already intended by dashboard|AnalyticsService, repositories, guest events/StudioAnalytics|Atomic daily writes, observed async outcomes, correct dish-view ranking; dedup semantics explicit; accepted vs durable documented|Concurrent DB increments, failed commit/shutdown handling, guest event/dashboard contract tests|

## Phase 3: User experience and design improvements

Preserve the current visual design. This phase is functional QA and narrow usability work, not authorization to redesign.

|Task / priority|Objective and dependencies|Affected modules|Acceptance criteria|Required verification|
|---|---|---|---|---|
|R11 / P1 — Core browser acceptance|Depends on R06/R07 and selected nonproduction backend|Studio/Admin/Guest, shared API/error controls|Provision/setup → dish/media/category/brand → guest/QR journey works; retries/empty/loading states understandable|Automated core browser flows plus manual responsive keyboard/mobile checks; no production fixtures|
|R12 / P2 — QR/AR compatibility matrix|Resolve A18 and export uncertainty; needs HTTPS test origin/devices|ModelStage/DishSheet/QR/Vercel permissions|Document actual supported browser/device combinations; scan links, GLB load and native/WebXR handoff succeed or give usable fallback; physical scale checked|Real Android/iOS/desktop matrix; exported SVG/PNG/print inspection; no claim based solely on build|
|R13 / P3 — Dietary filter decision|Documentation claims conflict with absent UI; requires product confirmation|GuestMenuPage/FilterBar, tag vocabulary|Either remove unsupported capability claims or implement an approved filter that matches stored tags|If approved: filter/clear/empty results and accessibility checks; no new tag semantics assumed|

## Phase 4: Performance and scalability

|Task / priority|Objective and dependencies|Affected modules|Acceptance criteria|Required verification|
|---|---|---|---|---|
|R14 / P2 — Measured frontend loading|A17; measure representative mobile/network/model first|App imports, Vite chunks, ModelStage/asset strategy|Agreed load/memory budget, no unnecessary 3D on unrelated surfaces; regressions measured; avoid router/framework replacement|Before/after browser performance, asset transfer and low-memory tests; export/AR regression|
|R15 / P2 — PostgreSQL query/concurrency validation|Requires disposable matching-version PostgreSQL|Repositories, migrations, counters/locks|All V1–V13 apply/validate; active-request uniqueness and sequence/contention match intended semantics; explain hot queries at representative counts|PostgreSQL integration/concurrency/EXPLAIN; no production load tests|
|R16 / P2 — Multi-instance consistency|Remaining A15; only when deployment requires >1 instance|MenuCache, login/analytics throttles and async lifecycle|Explicit invalidation staleness/throttle budget across instances, bounded global work and shutdown behavior|Two-instance tests/failure injection; select shared coordination only after requirement, not by default|
|R17 / P2 — Retention and bandwidth budget|Depends on agreed data/privacy/cost policy|Events/OTPs/private media/history and delivery|Explicit retained records/assets and cleanup/retry limits; predictable storage/request/transfer budget|Synthetic retained-data sweeps/disposable fixtures; no pricing or deletion assumptions|

## Phase 5: SaaS readiness

|Task / priority|Objective and dependencies|Affected modules|Acceptance criteria|Required verification|
|---|---|---|---|---|
|R18 / P1 — Release/CI gates|A14; depends on reproducible tests and frontend flow choice|Maven/npm checks, Dockerfiles/hosting workflow|Automated backend/frontend checks before deployment; lockfile reproducibility; no skip-tests-only release confidence|Clean-environment pipeline run, failure blocks release; no deployment secrets printed|
|R19 / P1 — Backup, rollback and observability|Needs operator/provider ownership and selected nonproduction recovery target|Deployment/runbooks, PostgreSQL/S3 lifecycle, logs/health|Successful restore drill; metadata/object reconciliation; alertable failure and credential-log access controls; migration rollback strategy explicit|Disposable restore/recovery exercise; distinguish static /health from DB health|
|R20 / P1 — Real reset delivery|Depends on R01 and approved provider/configuration|EmailSender transport and auth UI|Codes deliver through selected provider; retries/errors/account enumeration controlled; reset can safely be enabled|Provider sandbox, deliverability/error cases, transactional reset and browser acceptance; no unsolicited real-user mail|
|R21 / P2 — Reconcile authoritative docs|Depends on accepted changes/release status|Root README/AGENTS/audit/status/deployment documents|Entry points distinguish historical baseline/current source; commands, migrations, privacy and TTL claims match|Source/doc link checks and fresh onboarding using disposable targets|
|R22 / P3 — Commercial entitlement decision|No current subscriptions or quota enforcement; requires business requirements|Only if approved: tenant access/usage and new schema/API scope|Document decision on whether storage tier labels, trials/expiry and quotas should become enforceable policies; no implementation inferred|Threat/cost/acceptance design before any code, migration or provider selection|

## Phase 6: Future product enhancements

No automatic reconstruction, ordering/payment, billing provider, subcategory hierarchy, per-table sessions or new cloud platform is established as an approved requirement. This phase is a decision gate, not a backlog promise.

|Task / priority|Objective and dependencies|Affected modules|Acceptance criteria|Required verification|
|---|---|---|---|---|
|R23 / P3 — Evidence-led product discovery|After stable core acceptance, gather actual restaurant/operator needs|Product specification; existing studio/guest/admin boundaries|Each proposed enhancement has named users, concrete workflow, ownership/security constraints and acceptance tests; reject unsupported scope|Review with product owner; no code or schema until approved|
|R24 / P3 — Optional delivery enhancement decision|CloudFront is only a hook; establish measured bandwidth/latency needs first|S3Storage public-base-url and separately scoped infrastructure|Decision to retain direct S3 or provision reviewed CDN/access model with cost/rollback plan|If approved: nonproduction CORS/cache/AR checks and load/transfer measurements|

## Recommended immediate next task

**R01: make OTP failure attempts and code consumption transaction-safe with real database regression tests, while keeping password reset disabled.** It addresses the highest remaining source-supported security weakness without selecting an email provider or touching production configuration. Review A05 templates in parallel only as a separately authorized operational scope. R06/R07 are the next concrete guest/studio contract defects.
