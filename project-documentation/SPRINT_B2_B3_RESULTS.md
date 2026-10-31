# B2+B3 implementation results

2026-10-31; branch `atelier-ar-development`, HEAD `0c86b0d`. Implementation only, no commit/push. Existing uncommitted B1 policy/test changes, 1B report and both ZIP archives were preserved; deployment documentation retains B1 guidance and adds current sprint caveats. No AWS resource access, production database access, credential change, dependency installation or schema change was involved.

## B2 — private cache security

S3Storage signs PUTs under the configured private root with `private, no-store`. Public PUTs retain `public, max-age=31536000, immutable`. Prefix matching includes the slash boundary and honors custom private-root configuration. Direct browser uploads and required signed headers remain unchanged.

Private reference readers now call explicit `presignPrivateGet`, signing a response-cache-control override even for keyed references under an earlier root. Generic GET signing also protects the configured private root, while public GETs retain their existing behavior. Local tests inspect synthetic signed headers/query parameters without AWS requests or printing bearer URLs.

Limits: existing object metadata, old links and cached/downloaded copies are unchanged. New overrides affect subsequent compliant requests, not previous caches or unkeyed legacy fallback URLs. Browser/CDN behavior, real AWS authorization and existing-object remediation remain unverified. No cache purge/copy/policy deployment occurred.

## B3 — truthful deletion and caller integration

`S3DeletionResult` returns immutable acknowledged key sets, explicit per-key failure codes, unknown-outcome key sets and a prefix-listing completeness flag. Its log representation shows counts, not private keys. Quiet-mode DeleteObjects success acknowledges submitted keys except reported errors. Transport/server/timeouts, missing or inconsistent SDK responses never count as deleted. Clear 4xx rejection is failed; HTTP408 remains unknown. Duplicate/blank/null keys are filtered and requests stay within 1000 keys.

Prefix cleanup processes request-sized batches and preserves discovered outcomes if listing fails; it cannot report complete success after an incomplete listing. Batch results are aggregated once instead of repeatedly copying the full accumulated result. Result metadata still scales with discovered keys: this is not a durable bounded-memory manifest worker. Single deletion is now explicitly named `deleteBestEffort`, returns the same outcome contract and warns on failure/uncertainty. All production callers were migrated; no obsolete deleteQuietly calls remain.

StorageJanitor logs complete acknowledgement only when both known-key and prefix requests complete. Their counts remain separate because they can revisit the same objects. Dish/replacement/reaper messages now distinguish logical deletion/expiry and requested cleanup from acknowledged object removal.

Private reference cleanup no longer blindly clears metadata after a request. It removes only acknowledged keys, retains failed/unknown entries using their original metadata, and can retry eligible retained entries through the existing scheduler. It does not generate or persist signed URLs while deleting. Malformed JSON and unkeyed entries are retained, not falsely counted as cleaned.

Important limits: this is **not B4 durable retry implementation**. Replacement, removal, expiry and hard-delete paths can still lose cleanup intent after their database changes/crashes. Current-reference retention helps where an existing row remains eligible, but is not an independent cleanup journal. Accounting still represents logical live assets, not all physical orphan/retained bytes. Historical private assets, prior slugs, late PUT recreation, analytics orphan cleanup and complete tenant purge remain unresolved. An S3 acknowledgement is not proof that historical versions or externally cached copies were purged. Existing concierge HEAD/DELETE transactions and after-commit resource occupancy remain for B5.

## Test evidence and review

- Initial five regressions ran against the old adapter: four failures reproduced private PUT/GET caching and false partial/transport deletion counts; public immutable caching passed.
- First adapter green run: 14 passed. Expanded caller/adapter run: 40 passed. Subsequent timeout/unkeyed cases are included in the final full suite.
- Final isolated `mvn -o test`: **428 passed across 33 classes, zero failures/errors/skips**, 1m09s. Includes 20 S3StorageTest executions, two StorageJanitorTest executions, and four new reference-cleanup cases in ModelRequestServiceTest (20 executions total in that class). B1's six policy checks, 159 account-access cases, seven category-isolation cases and all OTP regressions passed.
- Frontend `npm run build`: **passed**, 1809 modules, 26.75 seconds. No frontend source or browser behavior change claimed.
- New coverage: configured/private/historical/public cache signing; partial DeleteObjects errors; transport/HTTP errors; unknown/null/inconsistent responses; duplicate/empty keys; 1001-key batching and continuation; incomplete and partial listing; single deletion; safe result formatting; incomplete/successful wipe logs; retained reference retry, transport failure and malformed/unkeyed metadata.
- External S3 calls are mocked; signing uses existing synthetic test credentials. Reference-cleanup tests use mocked repositories/transactions. Existing H2 integration tests remain in the full suite; **no PostgreSQL or actual AWS verification** was performed.
- An earlier standard application-context run loaded optional local AWS configuration into client beans without invoking S3. The final verification process disabled optional secret-file imports via `SPRING_CONFIG_IMPORT=optional:classpath:b2b3-no-secrets.properties`, supplied explicit dummy AWS credentials/region/bucket and disabled scheduling. These were process-only test overrides; no secret values were inspected, reported or changed, and no repository configuration changed.
- An intermediate full run was cancelled for final log-wording corrections; the final full run tests the finished source. No tests were skipped or suppressed.
- Security/TDD compatibility guidance and report-only code review were applied; no mutation experiment. Review corrected misleading removal wording and repeated result copying, and retained conservative timeout/listing semantics. The reviewed version passed both its standard full run and the final isolated full run. No remaining blocking finding in the locally tested B2/B3 scope; deployment and B4/B5 limits remain explicit.

Ignored local evidence: `backend/target/b2b3-red.log`, `b2b3-focused.log`, `b2b3-integration-focused.log`, `b2b3-reviewed-tests.log`, `b2b3-isolated-final-tests.log`, `b2b3-frontend-build.log`, Surefire XML and sprint-entry preservation hashes. These are transient local artifacts, not committed deliverables or coverage percentages.

## Exact sprint files

Paths are relative to repository root; existing B1 changes in other files remain separate.

- `backend/src/main/java/com/atelier/media/service/S3Storage.java` — cache signing and deletion outcomes.
- `backend/src/main/java/com/atelier/media/service/S3DeletionResult.java` — new result contract.
- `backend/src/main/java/com/atelier/media/service/ModelRequestService.java` — explicit private GET, retained unresolved references and single-delete call migration.
- `backend/src/main/java/com/atelier/media/service/StorageJanitor.java` — truthful wipe reporting.
- `backend/src/main/java/com/atelier/media/service/BrandingService.java` — explicit best-effort caller migration.
- `backend/src/main/java/com/atelier/media/service/DishService.java` — caller migration and logical-delete wording.
- `backend/src/main/java/com/atelier/media/service/MediaReplacementService.java` — caller migration and cleanup-request wording.
- `backend/src/main/java/com/atelier/media/service/MediaService.java` — best-effort caller migration; expiry count remains record count.
- `backend/src/main/java/com/atelier/media/service/StaleUploadReaper.java` — expiry versus purge wording.
- `backend/src/test/java/com/atelier/media/service/S3StorageTest.java` — new adapter regressions.
- `backend/src/test/java/com/atelier/media/service/StorageJanitorTest.java` — new log/outcome regressions.
- `backend/src/test/java/com/atelier/media/service/ModelRequestServiceTest.java` — four cleanup cases; existing B1 policy assertion preserved.
- `DEPLOYMENT.md` and `project-documentation/DEPLOYMENT_AND_SETUP.md` — current operational cache/deletion caveats, retaining B1 assumptions.
- `project-documentation/SECURITY_DOCUMENTATION.md` and `product-evolution/SECURITY_AND_RELIABILITY_PLAN.md` — current A10/A13 status.
- This `project-documentation/SPRINT_B2_B3_RESULTS.md` — consolidated implementation evidence.

## Next combined sprint

Recommend **B4+B5**: durable cleanup manifests/retries, complete ownership-aware private/pending/history discovery and safe accounting, coupled with concierge network operations outside short revalidated transactions. Schema additions require explicit approval; no schema or infrastructure change was silently introduced here. Preserve public/tenant contracts and retained assets, and validate concurrency on a confirmed disposable PostgreSQL target. Real AWS verification requires a separately approved nonproduction identity/bucket. This recommendation does not start that sprint.
