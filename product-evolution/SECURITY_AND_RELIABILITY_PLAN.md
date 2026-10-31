# Security and reliability plan

Status: future remediation proposal with Milestone 1A implementation update, 2026-10-31; A01–A03 preserved. [Index](README.md). [Milestone 1A actual evidence](../project-documentation/MILESTONE_1A_VERIFICATION.md) supersedes the original OTP proposal below where implemented. Reset remains disabled; PostgreSQL concurrency is blocked, not passed.

## Current evidence and audit disposition

Historical [technical audit](../TECHNICAL_AUDIT.md) is superseded for current status by source inspection and the [security ledger](../project-documentation/SECURITY_DOCUMENTATION.md). [AdminAccess](../backend/src/main/java/com/atelier/media/service/AdminAccess.java), controller guards, fresh [RestaurantAccess](../backend/src/main/java/com/atelier/media/service/RestaurantAccess.java) and tenant-scoped category checks now exist. The fresh 374-test suite passes, including signed-token/current-account and category isolation regressions; this is not a blanket security certification.

| Finding | Current disposition | Required next evidence/work |
|---|---|---|
| A01 | Remediated working tree: admin current account/version/role/usability guard | Preserve coverage for every new admin controller |
| A02 | Remediated working tree: studio guard and fresh tenant account/version/status | Preserve setup/support exceptions and service-level checks |
| A03 | Remediated working tree: scoped category assignment/response lookup | No claim legacy DB rows were repaired; future composite integrity review |
| A04 | Milestone 1A implemented and H2 verified: committed failure outcomes and account-locked single-use consumption; PostgreSQL blocked | Retain regression coverage; verify disposable PostgreSQL before recovery activation; real delivery remains separate approval |
| A05 | Open static S3 template conflict; deployed policy unknown | Concrete least-privilege proposal and approved disposable-prefix verification |
| A06 | Open: orderedCategoryIds client versus orderedIds DTO | Align contract and persisted reorder regression |
| A07 | Open: category changes lack menu invalidation; default seeding races | Post-commit eviction and real duplicate/concurrency analysis |
| A08 | Open: daily read/increment/save and unobserved async completion | Atomic PostgreSQL aggregation with exact concurrent-count assertions |
| A09 | Open: concierge S3 calls under DB transaction/lock | Network validation outside short transaction with locked revalidation |
| A10 | Open: private/pending/history cleanup gaps, false delete acknowledgement, daily orphans | Durable retry/lifecycle accounting; no production cleanup experiment |
| A11 | Partly improved by A01; TTL/storage/bootstrap/admin session UX remain | Coherent password-change/re-login behavior, bootstrap revocation and explicit session policy |
| A12 | Open: inconsistent metadata/ETag checks, prefix-only admin confirmation, reusable PUT | Issued-upload binding and content/finalization design |
| A13 | Open: private uploads use public immutable cache metadata | Separate private caching and nonproduction verification |
| A14 | Partly improved: 374 executed regressions; browser/PostgreSQL/CI still missing | Meaningful integration/release gates |
| A15 | Partly improved: token-version cache removed; menu/throttle caches remain local | Explicit single-instance assumption, multi-instance coordination before scale |
| A16 | Historical documentation drift persists | New blueprint and actual-state docs linked; no unauthorized AGENTS/root-doc edits |
| A17 | Open: large bundles, duplicate deployment definitions, absent dietary-filter behavior | Measure then optimize; no framework replacement |
| A18 | Runtime risk unverified: deployed camera/XR/CSP interaction and physical scale | Real supported-device HTTPS matrix before header change |
| A19 | Open: guest produces no per-dish MENU_VIEW used by top-dish count | Explicit dish-detail event/metric cutover |
| PD01 | Open: menuLive/isMenuLive mismatch and legacy constructor drops live/categories | Focused public/private/preview/legacy contract repair; no private dish leak established |

## Password recovery foundations

Current [OtpService](../backend/src/main/java/com/atelier/media/service/OtpService.java) retains SecureRandom six-digit codes, BCrypt hashes, ten-minute expiry, five attempts, a 60-second resend cooldown and process-local send-IP limiting. Milestone 1A adds explicit REQUIRES_NEW transactions and a shared account lock for send/reset; failed attempts commit before public errors, while successful consumption, sibling invalidation, password/version/setup changes are atomic. It also bounds reset calls by normalized email using a process-local ten-request cache budget with fifteen-minute inactivity expiry. Real H2 transaction/concurrency tests now supplement mocks; PostgreSQL remains blocked. [LoggingEmailSender](../backend/src/main/java/com/atelier/media/service/LoggingEmailSender.java) still does not send email; reset remains disabled by default. Never enable it as delivered email.

Implemented portion of the original proposal: the transaction returns a verification outcome after locking the account before OTP reads/writes. It commits invalid/locked outcomes before the public error. Four simultaneous correct submissions now yield one reset in H2; resend/redemption and simultaneous resend tests cover serialization. Account deletion/status is rechecked under the same lock; suspended/deleted accounts cannot recover, and allowed statuses are unchanged. Database rollback and token revocation are exercised explicitly. Remaining PostgreSQL/delivery/abuse-control gates are not inferred from H2 success.

Bound reset as well as send by IP and account, with generic outward semantics that do not expose account existence through cooldown/error shapes. Avoid a broad noRollbackFor workaround that could commit unrelated partial changes. Preserve existing DTO/error contract unless a separately reviewed generic-response adjustment is required. Logs contain neither codes/hashes nor submitted secrets. Confirm password reset revokes existing tokens immediately through the fresh account gate.

Real email delivery is a later separately approved adapter: provider/sender-domain approval, secure configuration, generic failure behavior, observable retry/outbox decision and actual nonproduction delivery evidence. Never send network email while holding a DB lock. Production credentials/costs are excluded from the first milestone.

## New threat boundaries

| Boundary/asset | Abuse case | Required control |
|---|---|---|
| Public table/order/event HTTP | Foreign IDs, forged totals, copied table QR, spam | Scoped lookup, server prices, bounded inputs/rates, truthful context semantics |
| Configuration/admin | Unknown feature keys, stale config, audit omission | Typed registry, hard ceilings, current admin gate, optimistic version and atomic audit |
| Quota/create/presign/confirm | Concurrent final slot or repeated settlement | Tenant-first locks, durable reservation/idempotency, revalidation |
| S3 upload bytes/publication | MIME spoofing, replay/same-key overwrite, external model URIs | Issued-key binding, actual content validation/finalization and immutable published key strategy |
| Private photos/notes/history | Public DTO/link/cache leak, indefinite retention | Separate private access/cache, short authorized URLs, explicit retention and retryable cleanup |
| Appearance/QR exports | Script/style injection and unsafe destination | Schema allowlist, escaped plain text, controlled templates/route builder |
| Sessions/support | Revoked/deleted account, bootstrap reset, token theft | Preserve A01–A03, auditable support actor, explicit session policy; no incidental auth rewrite |

S3 presigned URLs can be reused until expiry and same-key uploads replace an object; a confirmed key is not automatically immutable. [AWS presigned URL guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html) supports this remaining risk. Recommend a separately reviewed staging→validated immutable publication design, with browser direct upload retained. A bounded worker/stream inspector may read from S3; do not introduce a large binary HTTP body through Spring Boot. Until validated finalization exists, document actual metadata-only guarantees.

## Operations, dependencies and acceptance

No production connections, migrations, S3 experiments, cleanup, paid providers, credential edits or infrastructure mutations in Phase 0. Use isolated fixtures and confirmed disposable PostgreSQL/S3 targets for future verification. Do not edit V1–V13. Resolve compatibility and backup/restore/forward-migration plans before production schema rollout. CI, observability, durable cleanup and recovery rehearsal are launch requirements, not established deployment facts.

Retention proposal: order notes 30 days, raw events 90 days, non-identifying aggregates 13 months; audit/model/reference retention remains an approval decision. Legal jurisdiction/consent decisions require owner review; these are data-minimization engineering proposals, not a compliance certification.

Acceptance: every finding closes only with its specified source/transaction/API/browser/cloud/device evidence. New capabilities run revoked/foreign/disabled/concurrent/stale/retry cases. Dependency versions are measured from the repo; no current CVE clearance is claimed. Performance and security-header changes require measured approved targets. Existing build/test success coexists with unresolved transaction/integration defects.
