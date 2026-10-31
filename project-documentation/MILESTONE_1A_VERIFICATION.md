# Milestone 1A: OTP transaction safety and single-use consumption

Implemented and verified locally on 2026-10-31 over HEAD `330a49c` plus the pre-existing uncommitted A01–A03 changes. User approval covers Milestone 1A only. **Reset remains disabled by default; real delivery is not configured or approved. PostgreSQL concurrency verification is BLOCKED, not passed.**

[Security status](SECURITY_DOCUMENTATION.md) · [Approved scope/roadmap](../product-evolution/IMPLEMENTATION_ROADMAP.md) · [Security plan](../product-evolution/SECURITY_AND_RELIABILITY_PLAN.md).

## Problem and reproduced failures

The original transactional reset method saved attempts/used state, then threw a runtime validation exception. A real transaction rolled that save back. Concurrent callers also read the same unused OTP before either consumed it.

New tests ran against the original production source before the repair, using real repositories, committed fixture rows and independent H2 transactions/connections. No test-wide rollback or mocked transaction manager was used for this reproduction:

| Regression | Required outcome | Observed RED outcome |
|---|---|---|
| One wrong attempt | Reloaded attempts = 1 | Reloaded attempts = 0 |
| Fifth wrong attempt | Reloaded attempts = 5, used = true | Reloaded attempts = 4 |
| Four simultaneous correct submissions | Exactly one success | Four successes |

The first focused run had three failures, zero errors. An expanded pre-repair run had 13 executions, ten failures and zero errors, covering additional replay, account, resend and transaction cases. These were behavioral failures, not compilation failures or mock-object assertions.

## Repair and transaction boundaries

[OtpService](../backend/src/main/java/com/atelier/media/service/OtpService.java) now owns a short explicit TransactionTemplate with REQUIRES_NEW propagation. The reset callback returns a failure message instead of throwing a validation exception after modifying attempt state. The public InvalidFieldException is raised only after that transaction commits. An enclosing caller's rollback therefore cannot erase a committed wrong attempt; a real outer-rollback test verifies this boundary.

Both issuance and redemption first call [RestaurantRepository.findByEmailForUpdate](../backend/src/main/java/com/atelier/media/repository/RestaurantRepository.java), which uses PESSIMISTIC_WRITE on the normalized-email account row. The lock is acquired before OTP lookup/invalidation/mutation and works even when no OTP row exists. This avoids relying on locking an absent OTP row, adding a distributed lock service, or weakening PostgreSQL constraints. No OTP repository/schema change was required: both OTP write entry points share the account lock.

Under that lock, reset rechecks deletion/status and selects an unused, unexpired code. Wrong attempts and last-attempt timestamps persist; the fifth failure marks the code used. A legacy unused record already at five attempts is also invalidated. A successful reset atomically consumes the selected code, invalidates sibling codes, BCrypt-hashes the new password, clears setup, sets password-change time and increments token_version. The existing fresh current-account gate rejects the old token version. Other allowed account statuses remain unchanged; suspended/deleted accounts cannot recover.

The success test also induces a real SQL password-hash column-length violation. It requires DataIntegrityViolationException with SQLState 22001, then independently reloads the OTP/account to prove consumption, hash/version/setup changes all rolled back. PasswordEncoder is a real BCrypt spy, with only this fault-injection return overridden. Test BCrypt cost is 4 for speed; production SecurityConfig remains unchanged at cost 12.

Issuance commits replacement/invalidation before invoking EmailSender, outside its DB transaction/account lock in the existing nontransactional HTTP entry flow. The mock sender asserts this ordering. If a future real transport fails, committed issuance cannot be rolled back; a delivery/retry/outbox policy requires separate approval. Current HTTP entry points must remain standalone credential operations; REQUIRES_NEW is not a promise that calling while holding the same account lock in an enclosing transaction is safe.

## Preserved API and bounded abuse controls

Controller mappings, request/response DTOs, success text, wrong-code field errors, generic send response, disabled 404 behavior, ten-minute code expiry, SecureRandom generation and BCrypt hashing are preserved. No token is issued by reset; the guest returns to sign-in as before. Unknown/unavailable accounts receive a generic invalid-code result for redemption and generic send response, with no password mutation.

The existing send-IP limit and 60-second resend cooldown remain. Reset additionally applies a Caffeine budget of ten calls per normalized email before database/BCrypt work, returning 429 on further calls. Expire-after-write gives fifteen-minute **inactivity** expiry, refreshed by calls; this is not a fixed calendar window. The durable per-code five-attempt limit is separate. The cache is bounded/process-local, can evict keys, resets on restart and is not coordinated across instances; it does not establish a complete anti-automation or denial-of-service defense. No new dependency, service or configuration field was added.

No new OTP/password/hash/token logging was introduced. The reset success log no longer includes the account email and runs after commit. LoggingEmailSender is unchanged: it remains a placeholder, including existing local/test debug-code behavior, and must not be treated as actual delivery or enabled for production recovery under this milestone.

## Final executed evidence

| Check | Result | Evidence/scope |
|---|---|---|
| Final `mvn -o test` | **396 passed; 0 failures, errors or skips; 30 report classes; BUILD SUCCESS; 2m24s** | Existing cached dependencies; fresh final Surefire XML/log; no live services |
| OtpTransactionTest | **18 passed** | Real H2 Flyway schema, repositories, row locks, commits/rollback and independent threaded callers |
| OtpServiceTest | **9 passed** | Unit coverage of disabled/default behavior, generic response, cooldown, attempts, allowed statuses and reset request budget |
| Existing security regressions | **159 AccountAccessSecurityTest + 7 CategoryIsolationTest passed** | A01–A03 signed-token/isolation coverage preserved in full run |
| Frontend `npm run build` | **Passed**, 1809 modules, 50.02s | Existing dependencies; no frontend code/UI changes; compilation only |
| PostgreSQL concurrency | **BLOCKED — not executed** | No confirmed disposable PostgreSQL target supplied/discovered; no production connection or automatic provisioning |
| Report-only review | Completed; no remaining blocking scoped-source finding | Correctness, readability, architecture, security, performance and verification reviewed; no condition-inversion/mutation experiment |
| Diff/preservation/document checks | Passed at completion | Existing A01–A03 files byte-identical to milestone entry; local links and whitespace checked |

Real H2 cases cover persisted wrong attempts, fifth-attempt lockout, pre-existing lockout, no lost concurrent failures, exactly one of four concurrent valid redemptions, expiry, used-code replay, sibling invalidation, SQL-failure atomic rollback, outer-caller rollback, revoked token version, suspended/deleted accounts, supported H2 account statuses, delivery-after-commit, simultaneous resends and resend/redemption races. Random six-digit replacement collisions are handled explicitly in the tests rather than introducing rare false failures.

An earlier full run had 394 executions and one fixture error: H2 V3 permits ACTIVE/TRIAL/PENDING/SUSPENDED, while PostgreSQL V5 additionally permits PENDING_ONBOARDING. No application defect was hidden and no migration/constraint was weakened. The corrected H2 fixtures use its supported statuses; parameterized unit tests separately retain PENDING_ONBOARDING service coverage. PostgreSQL persistence of that status was not rerun. The final full run includes the corrected fixtures and the strengthened SQL-failure assertion.

Existing nonfatal H2/Flyway-version and Java-agent warnings remain. The intentional SQL fault produces expected test-only SQL error logs. These are distinct from the zero failures/errors reported by the final suite. No frontend behavioral/lint suite, browser/reset journey, live email, cloud or PostgreSQL result is implied by compilation/H2 success.

Local ignored logs: `backend/target/milestone1a-red.log`, `milestone1a-expanded-red.log`, `milestone1a-focused-green.log`, `milestone1a-full-tests.log` (fixture error), `milestone1a-full-tests-final.log`, `milestone1a-frontend-build.log`, plus final Surefire XML. Logs are transient local evidence, not committed artifacts or a coverage percentage. Windows Java-agent access required approved outside-sandbox runs; all Maven commands used offline mode and only already-installed dependencies.

## Exact milestone file changes

| File | Change |
|---|---|
| [backend/src/main/java/com/atelier/media/service/OtpService.java](../backend/src/main/java/com/atelier/media/service/OtpService.java) | Explicit committed outcomes, shared account locking, atomic consumption/version changes, unavailable-account checks, bounded reset requests, post-commit sender/log |
| [backend/src/main/java/com/atelier/media/repository/RestaurantRepository.java](../backend/src/main/java/com/atelier/media/repository/RestaurantRepository.java) | Parameterized pessimistic account lookup by email |
| [backend/src/test/java/com/atelier/media/service/OtpServiceTest.java](../backend/src/test/java/com/atelier/media/service/OtpServiceTest.java) | Constructor/locked-lookup wiring, allowed-status cases and normalized-email request limit |
| [backend/src/test/java/com/atelier/media/service/OtpTransactionTest.java](../backend/src/test/java/com/atelier/media/service/OtpTransactionTest.java) | New real persistence/transaction/concurrency regressions and fault injection |
| [SECURITY_DOCUMENTATION.md](SECURITY_DOCUMENTATION.md) | Current OTP/A04 status and verified boundaries |
| [product-evolution/SECURITY_AND_RELIABILITY_PLAN.md](../product-evolution/SECURITY_AND_RELIABILITY_PLAN.md) | Implemented 1A update versus future recovery gates |
| [product-evolution/IMPLEMENTATION_ROADMAP.md](../product-evolution/IMPLEMENTATION_ROADMAP.md) | Records 1A-only authorization, local result, blocked PostgreSQL gate and next proposal |
| [product-evolution/DECISIONS_AND_OPEN_QUESTIONS.md](../product-evolution/DECISIONS_AND_OPEN_QUESTIONS.md) | Records user's D20 approval without approving later milestones |
| This MILESTONE_1A_VERIFICATION.md | New dated implementation, test and review evidence |

All other pre-existing files, including A01–A03 source/tests, AGENTS.md, migrations, dependencies, production configuration, credentials and pre-existing archives are preserved. Generated backend/target and frontend/dist artifacts were refreshed by tests/build. No commit, push, provider setup or later product feature was performed.

## Review and remaining work

Report-only review found the original generic RuntimeException assertion too broad; the implementation pass strengthened it to require the real SQL integrity error, and the final full suite verified that correction. No code was mutated as a review experiment. Tests assert state and committed outcomes, not merely method invocation counts, for the transaction guarantees. The source change stays within OTP service/repository boundaries without new dependencies or schema. One account lock serializes recovery work; bounded BCrypt under that lock intentionally trades same-account parallelism for correctness. No performance benchmark beyond test execution is claimed.

Remaining gates: confirmed disposable PostgreSQL transaction/race tests; H2/PostgreSQL migration drift review as a separately scoped task; approved real email transport and sender identity; delivery retry/failure semantics; enumeration/timing, IP/distributed abuse controls and complete recovery UX acceptance before activation. The preserved disabled default is not evidence of a deployed environment's flag, which was not inspected or changed.

Recommended next milestone: **1B, read-only private-S3 policy/lifecycle proposal and a concrete disposable-prefix verification plan**. Do not apply cloud policy changes under 1A. If an approved disposable PostgreSQL target becomes available, complete the outstanding 1A verification before enabling password recovery. Real email/provider activation and all later product work still need separate approval.
