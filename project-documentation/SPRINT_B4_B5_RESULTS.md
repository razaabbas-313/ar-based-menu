# Sprint B4 + B5 results

Date: 2026-10-31. Local implementation; **not production-verified**. Branch: `atelier-ar-development`; HEAD remains `0c86b0d84140fdd1dce5ff4f48081700995beea7`. No commit or push.

## Implemented

**B4:** V14 adds one exact-key cleanup manifest table, without a cascading tenant FK. Reference release and intent commit together through Media/Restaurant JPA lifecycle snapshots and JDBC on the same transaction. Restaurant/dish removal explicitly captures tracked children before database cascades. Removed direct service deletes and the janitor prefix sweep; the janitor now reports logical deletion while the scheduled durable worker owns physical cleanup.

The worker processes at most 100 tasks per batch. UNIQUE(bucket, object_key) deduplicates releases. CAS claims, 15-minute leases, renewal before dispatch and token-fenced settlement coordinate workers. DeleteObjects calls have a two-minute SDK call timeout and execute outside database transactions. Acknowledged keys become DONE; failed/unknown outcomes retain their identifiers and retry with exponential backoff measured from settlement (first delay two minutes, capped at one day). Eight unsuccessful settlements lead to MANUAL. Sanitized failure codes remain in last_error_code; raw service messages and private keys are not logged by cleanup.

Protection checks cover configured bucket/tenant namespace, all current/pending media, branding, issued references, reference JSON and completed model history. Ambiguous ownership, malformed evidence and retained models become HELD. Retired keys cannot be republished through the observed entity transitions or concierge confirmation. History models remain protected after cascade deletion through explicit retained-model manifests. No historical-model purge policy was introduced.

Integrated paths: dish photo/model replacement/removal; branding release; FAILED initial uploads; pending photo/model expiry; unselected and expired concierge upload keys; 14-day completed concierge references; dish/restaurant cascade removal; reaper and janitor. Unknown/untracked objects are never listed or swept.

**B5:** confirmRequest and approveModel validate identity/state, perform HEAD without a transaction, then lock/revalidate current restaurant/account, token version, media version, request state and issued keys before atomic publication/history/usage updates. Admin publication also locks the current admin account. Admin model presigning records its exact key on the existing pending-model track; prefix-only approval is no longer sufficient. Existing routes/DTO shapes and direct browser uploads remain. Existing unbound admin uploads must be presigned again.

Reference expiry performs bounded, keyset-selected database work only, clears eligible history references consistently, and creates cleanup intent with reference release. Its scan cursor advances past held/malformed rows and wraps; restart may rescan candidates without losing durable intent. Direct successive approvals now supersede prior completed history and account only the live replacement delta. Failed verification preserves the old live asset.

## Verification

- Full backend: `mvn -o test` **PASS — 455 tests across 36 classes, 0 failures, 0 errors, 0 skipped**, 2 min 54 s. This adds 27 tests to the reported 428-test baseline. Final evidence: `backend/target/b4b5-final-backend.log` and Surefire XML reports (ignored local artifacts).
- Final timing regression normalizes the expected value to database microsecond precision; an earlier run exposed a 300 ns assertion mismatch, not an application failure.

- TDD evidence: original B5 reproduction had 4 assertion failures (both transactional HEADs; suspension/admin revocation accepted). Initial B4 compilation failed because the durable implementation did not exist; subsequent transaction tests exposed and fixed fixture writes outside transactions with pool auto-commit disabled.
- New regression classes: DurableCleanupTest (15 tests), CleanupProcessRecoveryTest (2), ModelRequestTransactionSafetyTest (10). Existing caller tests now assert deferred deletion; B3 partial/unknown behavior remains covered at storage and durable-worker boundaries.
- Coverage includes real H2 rollback, concurrent confirmations/approvals, repeat rejection, token/account changes during HEAD, failed replacement, partial/unknown results, retry/backoff/manual states, duplicate workers, retained/foreign/active assets, cascade deletion with incomplete cleanup, bounded batches and non-starving reference selection.
- Recovery tests launch separate JVMs against new disposable file-backed H2 databases. One persists an unknown result; another halts the first JVM after a synthetic acknowledgement and before settlement. A fresh JVM recovers the task/lease and settles an idempotent retry. S3 is mocked throughout.
- Frontend: `npm run build` passed, 1,809 modules, 25.71 s. Existing large output bundles remain. No frontend tests/lint script exists and none is claimed.
- PostgreSQL migration/concurrency validation: **BLOCKED**; no explicitly confirmed disposable instance. H2 does not prove PostgreSQL lock, index or SQL behavior.
- AWS policy/access/deletion verification: **NOT PERFORMED**. No actual AWS policy was deployed or tested; no live objects or production databases accessed.
- Tests disable secrets imports, use synthetic AWS settings, disable scheduling and use isolated H2 URLs. No default-production application startup, new dependencies or production configuration changes.
- Report-only code review covered correctness, security, architecture, performance and scope. It identified reference-selection starvation, direct-history supersession, settlement-time backoff and failure-code retention; implementation fixes and regressions address these. No mutation experiments. Focused accidental-disclosure review found no new private-key/non-example AWS key markers; this is not a full-history secret clearance.

## Deployment and remaining limitations

- New migrations: `backend/src/main/resources/db/migration/{h2,postgresql}/V14__durable_s3_cleanup.sql` (both exact files listed below). V1–V13 remain unchanged. H2 fresh migration/reopen validation is local only. PostgreSQL rollout, backup and deployment approval remain separate; no production migration was run.
- Logical reference removal/live usage accounting and acknowledged physical deletion are distinct. Existing counters represent live application references, not total retained/orphan/versioned storage. DONE means S3 acknowledged the delete, not that all historical versions were purged.
- Release waits configured presigned-PUT TTL plus one hour. This reduces replay races but cannot prove an already-started or late PUT will not finish after deletion. No object-version binding, live reconciliation or absolute orphan-freedom is claimed.
- Prior-slug keys outside the captured namespace become HELD. Unknown historical objects and untracked branding uploads (including failed validation without an issued-key record) remain undiscovered; no unsafe prefix sweep or unproven ownership delete replaces that gap.
- JPA snapshots cover these repository entity workflows; future bulk SQL/JPQL reference removal must explicitly enqueue intent. No automatic backfill of previously lost cleanup keys exists.
- JSON protection scans use bounded pages but can still be expensive (tasks × reference rows). Malformed legacy evidence conservatively prevents deletion. HELD/MANUAL tasks require authorized operator investigation/requeue/disposition; no new admin UI or automatic purge of evidence exists. Failed candidate transactions retain their original references and are revisited after cursor wrap.
- Lease recovery and ambiguous acknowledgements can cause repeated idempotent deletes; exactly-once remote execution is not promised. Deterministic mock/H2 tests do not establish deployed AWS or PostgreSQL behavior.
- Password reset remains disabled in application configuration. Existing A01–A03, OTP transaction safety, B1 templates, B2 private caching and B3 truthful deletion contracts are preserved; their regression suites remain part of the full run.

## Exact files changed in this sprint (30)

Paths below are relative to the repository root; earlier uncommitted files are listed separately.

```text
backend/src/main/java/com/atelier/media/model/Media.java
backend/src/main/java/com/atelier/media/model/Restaurant.java
backend/src/main/java/com/atelier/media/repository/MediaRepository.java
backend/src/main/java/com/atelier/media/repository/ModelRequestPendingKeyRepository.java
backend/src/main/java/com/atelier/media/repository/PlatformUserRepository.java
backend/src/main/java/com/atelier/media/service/AdminAccess.java
backend/src/main/java/com/atelier/media/service/AssetCleanupListener.java
backend/src/main/java/com/atelier/media/service/BrandingService.java
backend/src/main/java/com/atelier/media/service/DishService.java
backend/src/main/java/com/atelier/media/service/DurableCleanup.java
backend/src/main/java/com/atelier/media/service/MediaReplacementService.java
backend/src/main/java/com/atelier/media/service/MediaService.java
backend/src/main/java/com/atelier/media/service/ModelRequestService.java
backend/src/main/java/com/atelier/media/service/S3Storage.java
backend/src/main/java/com/atelier/media/service/StaleUploadReaper.java
backend/src/main/java/com/atelier/media/service/StorageJanitor.java
backend/src/main/java/com/atelier/media/web/StudioController.java
backend/src/main/resources/db/migration/h2/V14__durable_s3_cleanup.sql
backend/src/main/resources/db/migration/postgresql/V14__durable_s3_cleanup.sql
backend/src/test/java/com/atelier/media/service/BrandingServiceTest.java
backend/src/test/java/com/atelier/media/service/CategoryIsolationTest.java
backend/src/test/java/com/atelier/media/service/CleanupProcessRecoveryTest.java
backend/src/test/java/com/atelier/media/service/DishServiceTest.java
backend/src/test/java/com/atelier/media/service/DurableCleanupTest.java
backend/src/test/java/com/atelier/media/service/MediaReplacementServiceTest.java
backend/src/test/java/com/atelier/media/service/MediaServiceTest.java
backend/src/test/java/com/atelier/media/service/ModelRequestServiceTest.java
backend/src/test/java/com/atelier/media/service/ModelRequestTransactionSafetyTest.java
backend/src/test/java/com/atelier/media/service/StorageJanitorTest.java
project-documentation/SPRINT_B4_B5_RESULTS.md
```

## Git state and preservation

All changes remain unstaged/uncommitted on the original branch/HEAD. Existing source, migrations, AGENTS.md, product specifications, B1 policy/documentation edits, B2/B3 tests/report and both ZIP archives were preserved. No reset, revert, clean, commit, push or staging operation was used. Generated target/dist files remain ignored.

Git status: 27 tracked modified files, 16 untracked files, 0 staged; 43 total entries. This sprint touched 30 files including this report. The other 13 existing dirty/untracked paths were left unchanged by this sprint:

```text
DEPLOYMENT.md
infra/iam-policy.json
infra/s3-public-read-policy.json
product-evolution/SECURITY_AND_RELIABILITY_PLAN.md
project-documentation/DEPLOYMENT_AND_SETUP.md
project-documentation/SECURITY_DOCUMENTATION.md
backend/src/main/java/com/atelier/media/service/S3DeletionResult.java
backend/src/test/java/com/atelier/media/security/S3PolicyTemplateTest.java
backend/src/test/java/com/atelier/media/service/S3StorageTest.java
product-evolution.zip
project-documentation.zip
project-documentation/MILESTONE_1B_S3_POLICY_AND_LIFECYCLE_PROPOSAL.md
project-documentation/SPRINT_B2_B3_RESULTS.md
```

`git diff --check` passed. Existing migrations V1–V13, dependency manifests, frontend source and AGENTS.md have no sprint edits.

Next recommended sprint: **super-admin plans, features, quotas and restaurant overrides**, without billing, against the approved product-evolution specification. It has not been started.
