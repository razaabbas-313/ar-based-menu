# Atelier AR project instructions

## Scope and architecture

This is an inherited SaaS, not a starter project. Preserve working behavior, React design/themes, the custom router, Spring Boot modules and direct browser-to-S3 uploads. Read CODEBASE_OVERVIEW.md, IMPLEMENTATION_STATUS.md and TECHNICAL_AUDIT.md before relying on README/release-note claims. Audit baseline: 2026-10-31, commit `330a49c`. No prior AGENTS.md was found during this audit.

The Git root is this directory (`atelier-3d-media-engine`), nested below the supplied `atelier-3d-media-engine-v4` workspace. Run Git/build commands from the correct root/subdirectory. Frontend is React 18/Vite 5/Tailwind 3/JavaScript JSX; backend is Java 21/Spring Boot 3.3.5/Spring Security/JPA; PostgreSQL metadata uses Flyway, with H2-specific local/test migrations. Model rendering uses Google model-viewer. AWS SDK is pinned to 2.28.29 for existing browser presign compatibility; do not casually change it.

## Current authorization boundary

The user authorized an audit and five onboarding documents only. Application code, business logic, dependencies, credentials, production configuration and schema fixes require explicit approval of the audit/next development work. DEVELOPMENT_ROADMAP.md is a recommendation, not permission to implement. Preserve existing documentation unless asked to update it, and inspect it before changing it.

## Existing patterns and conventions

- Keep the controller → validated record DTO → service → Spring Data repository flow. Existing naming: `*Controller`, `*Service`, `*Repository`, request/response `*Dto`.
- Backend package root is `com.atelier.media`. Constructor injection, domain exceptions/ProblemDetail responses, Lombok entity conventions and DTO mapping are established patterns.
- Keep business decisions in services. Use short transactions/TransactionTemplate for persistence; keep S3 HEAD/DELETE outside open DB transactions and revalidate state when committing. Preserve pessimistic/optimistic locks and atomic usage updates.
- Frontend uses functional components/hooks, AuthContext for independent restaurant/admin sessions, `api/client.js` for JSON and auth/error handling, `utils/upload.js` for media workflows, and shared UI/toasts. Reuse these rather than adding competing clients/state/router systems.
- Preserve `/r/{slug}` guest URLs and the legacy API menu route; printed QR codes depend on compatibility. Public DTOs must not expose staff notes, audit data, object keys or account credentials.
- Keep photo-first dishes and pending replacement semantics: old media stays live until confirmation succeeds. Upload binaries directly browser → S3; never route large file bodies through Java as a shortcut.
- Avoid stylistic rewrites, global reformatting, technology replacement, new dependencies and unrelated refactors. Add narrow changes within current modules only when authorized.

## Commands and verification

From `frontend/`:

```powershell
npm ci
npm run dev
npm run build
npm run preview
```

`npm ci` installs dependencies and requires network/cache availability; use existing dependencies when appropriate. `npm run generate:showcase` writes generated demo assets; run only for intentional asset work. No frontend test/lint scripts currently exist; never report them as passed.

From `backend/` with Java 21 and Maven 3.9+:

```powershell
mvn test
mvn package
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
mvn spring-boot:run '-Dspring-boot.run.profiles=local,h2'
```

There is no checked-in Maven wrapper. Maven test profile uses in-memory H2; inspect effective environment/properties before running integration tests. Local uses PostgreSQL unless h2 is added; local+h2 uses a persistent file under data/, not an automatically disposable database. Default profile is prod. Never start the backend with an unknown/production datasource for an audit: Flyway and bootstrap routines can write on startup. Existing environment variables can override expected local/test settings.

Audit environment had Node 24.21.0/npm 11.19.0 and passed frontend build plus isolated QR/media helper checks. Java, Maven, Docker and psql were unavailable on PATH. Do not inherit this as evidence that backend tests passed or failed. There are 27 Java test classes and 208 `@Test` annotations; no coverage percentage is established. Mock repositories/transaction managers cannot prove database rollback/locking semantics. PostgreSQL constraints differ from H2 (notably unique partial active-request index).

## Security rules

- Never print or commit real credentials, JWTs, OTPs, password hashes, presigned URLs or secrets.properties/.env contents. Document variable names and redacted observations only. Do not treat example/test credentials as evidence of a live leak, or lack of a found credential as a full-history clearance.
- Restaurant identity must come from verified JWT Tenant claims, not a caller-supplied tenant ID/slug. Every studio action needs current RestaurantAccess validation; known gaps are documented in A02.
- Every admin action must validate current admin account/token version as well as signature/role/audience; current code is incomplete (A01). Parsing AdminPrincipal for audit attribution is not current-account authorization.
- Scope dish/category/pending-key lookups to the authenticated restaurant. Validate category ownership on create/update; a foreign-key existence check alone is insufficient (A03).
- Preserve role/audience/setup-password isolation, default-deny routes, BCrypt and generic credential errors. Do not enable password reset until real delivery and transaction-safe lockout are verified (A04).
- Public media and private reference prefixes have different access/cache needs. Do not grant public private-prefix access to fix failed presigned downloads. Supplied S3 policies require repair/review before use (A05).
- Validate allowed media MIME/size/key ownership server-side; browser checks and S3 HEAD metadata do not validate actual binary content. Preserve required signed browser-settable headers and pending upload tracking.
- Do not change production IAM/bucket/CORS/security-header configuration without authorization and a concrete reviewed change. Test AR-related CSP/permissions on supported devices before altering them.

## Database and cloud safety

- No destructive SQL, Flyway clean, reset/drop, production test data, tenant deletion or S3 cleanup during audit/read-only work.
- Flyway owns schema; never enable Hibernate create/update as a workaround. Do not edit already-applied migration files. Approved schema work uses a new migration and explicit PostgreSQL/H2 compatibility decisions.
- Validate migrations only against a confirmed disposable database. Do not point integration tests at live accounts/data. Do not assume local application startup is read-only: migrations, default category seeding, bootstrap password reset and scheduled cleanup can write/delete.
- Avoid network AWS checks with unknown credentials/buckets. Prefer mocked unit tests, local signing and deliberately provisioned nonproduction prefixes. Never perform cleanup to validate a code-review finding against existing assets.
- Preserve tenant boundaries, cascade behavior and usage accounting; inspect both public/private histories/pending keys when changing lifecycle code. Use real transaction tests for rollback/concurrency concerns.

## Git workflow

Inspect `git status --short` and existing diffs first; preserve user changes. Work on the requested scope, review `git diff --check` and final changed-file list, and run relevant available checks. Do not reset, clean, force-push, mass-delete, auto-commit or push without authorization. Generated `dist/`, `target/`, data and credentials remain ignored. Never stage everything blindly. Report exactly what was tested and what was blocked; do not convert source review into a runtime verification claim.
