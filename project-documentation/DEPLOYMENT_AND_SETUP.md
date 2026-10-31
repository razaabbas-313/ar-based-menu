# Deployment, safe setup and developer onboarding

Snapshot: 2026-10-31. These commands document existing entry points; none of the application-start, deployment or cloud-changing commands were executed during this documentation pass. [Master](MASTER_PROJECT_DOCUMENTATION.md) · [Architecture](CODEBASE_ARCHITECTURE.md) · [Security](SECURITY_DOCUMENTATION.md).

## 1. Prerequisites and correct working directory

Backend: Java 21 and Maven 3.9+ (verified previously with Maven 3.9.16 and Temurin 21.0.12.1). Frontend package engines require Node >=18.18; earlier build used Node 24.21.0/npm 11.19.0. No Maven wrapper is checked in. PostgreSQL is required for production behavior; H2 supports a disposable local/test path. Docker is optional for the existing image build and was not used in this pass. No exact PostgreSQL server version is pinned by this repository.

PowerShell commands below assume the supplied Windows workspace. Replace only the root if cloned elsewhere:

```powershell
$atelierRoot = 'C:\Users\IQRA COMPUTER\Desktop\atelier-3d-media-engine-v4\atelier-3d-media-engine'
Set-Location -LiteralPath $atelierRoot
git status --short
java -version
mvn -version
node --version
npm --version
```

If a running desktop process predates a global Windows toolchain installation, restart its terminal or refresh that process's Path/JAVA_HOME from registered machine/user environment values. Maven can use JAVA_HOME even if java.exe is missing from the inherited Path. Do not change application configuration to solve a tool lookup problem.

## 2. Safety preflight

Default profile is prod. Backend startup runs Flyway, admin bootstrap and scheduled cleanup; it is not read-only. `local` uses PostgreSQL unless h2 is selected, and local+h2 defaults to a persistent file database. Never assume those names imply disposable data. No production datasource, tenant deletion, S3 sweep, migration repair/clean/drop or bootstrap reset belongs in an audit smoke test.

Inspect override variable **names** without printing credential values:

```powershell
Get-ChildItem Env: |
  Where-Object Name -Match '^(SPRING|DB_|DATABASE_URL|ATELIER|AWS|JAVA_TOOL_OPTIONS|MAVEN_OPTS)' |
  Select-Object -ExpandProperty Name
```

Review backend/pom.xml, src/test/resources/application-test.yml, test annotations, any Maven user settings and ignored local property files privately. Do not paste secrets.properties, `.env` contents, JWTs, OTPs or signed URLs into reports. Main configuration imports optional secrets.properties from the backend/root working context. External `DATABASE_URL` is adapted by DatabaseUrlEnvironmentPostProcessor and can override a presumed local environment. Validate every selected target before starting.

## 3. Existing complete test command

After confirming test configuration has no external override:

```powershell
Set-Location -LiteralPath "$atelierRoot\backend"
mvn test
```

This runs the existing complete suite. The Spring tests use profile test and in-memory H2; DishSequenceConcurrencyTest explicitly supplies a separate H2 datasource and H2 migration location. It creates a test application context, not a separately launched production API. Scheduling is disabled in test configuration. S3 service tests use mocks and locally signed example credentials; they do not verify live bucket behavior.

Surefire output is under `backend/target/surefire-reports/`. Windows sandbox restrictions previously blocked Mockito/Byte Buddy agent attachment to a named pipe; the same unchanged command succeeded when explicitly approved outside the sandbox. Do not weaken security checks, suppress tests or change JVM/test configuration merely to make a run green.

Historical verified results, confirmed from retained logs and current XML reports:

|Run|Result|Scope|
|---|---|---|
|Pre-remediation baseline|208 passed; 0 failed/errors/skipped; 27 classes; about 1:06|Original test suite, all thirteen H2 migrations|
|After approved A01–A03 fixes|374 passed; 0 failed/errors/skipped; 29 classes; about 1:56|Includes 159 signed-token access cases and seven category isolation executions; all thirteen H2 migrations|

No tests were rerun for this documentation request. Coverage percentages, live PostgreSQL, browser and AWS integration are not established by those totals. Nonfatal warnings include duplicate Commons Logging discovery and dynamic Java-agent loading.

## 4. Disposable local H2 onboarding

Use a fresh PowerShell 7 terminal dedicated to this local run. The following explicit **process-only** overrides select in-memory H2, avoid loading real local secrets, turn off scheduled deletion and disable uploads by leaving the bucket blank. The nonexistent optional import is intentional; do not create it. The development account/password variables are entered/generated privately rather than shown as values. These are instructions, not configuration edits performed by the documentation task.

```powershell
Set-Location -LiteralPath "$atelierRoot\backend"
$env:SPRING_CONFIG_IMPORT = 'optional:file:./onboarding-unused.properties'
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:mem:atelier-onboarding;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'sa'
$env:SPRING_DATASOURCE_PASSWORD = ''
$env:SPRING_FLYWAY_URL = $env:SPRING_DATASOURCE_URL
$env:SPRING_FLYWAY_USER = 'sa'
$env:SPRING_FLYWAY_PASSWORD = ''
$env:SPRING_FLYWAY_LOCATIONS = 'classpath:db/migration/h2'
$env:ATELIER_SCHEDULING_ENABLED = 'false'
$env:AWS_S3_BUCKET = ''
$env:ATELIER_SUPERADMIN_RESET = 'false'
$env:ATELIER_SUPERADMIN_PASSWORD = Read-Host -MaskInput 'New disposable-local admin password'
$env:ATELIER_JWT_SECRET = node -e "process.stdout.write(require('crypto').randomBytes(48).toString('base64'))"
mvn spring-boot:run '-Dspring-boot.run.profiles=local,h2'
```

Inspect the startup output for an H2 memory URL before interacting. This environment cannot demonstrate S3 uploads and answers storage-not-configured where relevant. Stop with Ctrl+C; closing the dedicated terminal discards environment overrides. The DB disappears with the process despite DB_CLOSE_DELAY=-1 keeping it alive inside that process. Generated/entered credentials must not be committed or copied to documentation.

The bare existing `mvn spring-boot:run '-Dspring-boot.run.profiles=local,h2'` uses backend/data/atelier-local instead. Do not use it if the existing file might contain valuable data. Running local profile with hosting markers such as PORT/RENDER/VERCEL can fail intentionally; use a clean local terminal, not a hosted environment disguised as local.

## 5. Local PostgreSQL path

Provision an explicitly disposable PostgreSQL database/account using your local database administration process. Confirm hostname, port, database and permissions privately; do not reuse production accounts. No database creation/migration command was run in this documentation pass. Set DB_URL to a JDBC PostgreSQL URL and DB_USERNAME/DB_PASSWORD privately, or set SPRING_DATASOURCE_* explicitly; ensure SPRING_FLYWAY_* points at the **same disposable database** and use `classpath:db/migration/postgresql`. Do not carry H2 environment overrides into this terminal.

The existing local-profile launcher is:

```powershell
Set-Location -LiteralPath "$atelierRoot\backend"
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

This command writes schema/data through startup migrations/bootstrap; **it must not target production**. There is no configured Maven Flyway CLI plugin workflow; application startup performs migration. Do not invent `flyway:clean`/repair usage or edit applied V1–V13 to reconcile an existing installation. A new approved migration is the normal schema-change mechanism.

## 6. Frontend development and build

In a second terminal:

```powershell
Set-Location -LiteralPath "$atelierRoot\frontend"
npm ci
npm run dev
```

`npm ci` uses package-lock.json and installs dependencies; it can require network/cache access and is not a source edit. Vite serves port 5173 with host=true and proxies `/api` to localhost:8080 unless VITE_DEV_API_PROXY overrides it. Set VITE_API_BASE_URL only when deliberately bypassing that same-origin proxy. The Vite host setting exposes the development UI on the network; do not use real production tokens in this environment.

```powershell
npm run build
npm run preview
```

Preview uses port 4173 per package script and has the same API proxy target logic. Production output is frontend/dist, ES2020 target, no source maps, React/Tailwind CSS. Build success is not a browser/API/AR acceptance test. There are no `test` or `lint` package scripts. `npm run generate:showcase` writes procedural showcase GLB assets; it is not a prerequisite and must only be used for deliberate asset work.

## 7. Environment variable reference

Values below describe purpose/default behavior only; no actual credential values are included. Examples in shipped files are not a secret store.

|Variable/property|Use and caveat|
|---|---|
|SPRING_PROFILES_ACTIVE|prod default; local for development; h2 augments local; test through test annotations|
|PORT|HTTP port default 8080; presence also counts as hosted marker for local-profile startup validator|
|DB_URL / DB_USERNAME / DB_PASSWORD|Datasource input; explicit JDBC URL normally bypasses cloud URL adaptation|
|DATABASE_URL|Cloud postgres/postgresql URI; postprocessor extracts JDBC/Flyway URL and credentials; never log whole value|
|DB_POOL_MAX|Hikari maximum, default ten; local overrides to five|
|SPRING_DATASOURCE_* / SPRING_FLYWAY_*|Spring overrides; verify both targets together and vendor migration location|
|SPRING_CONFIG_IMPORT|Overrides optional local property imports; not a production credential value itself|
|ATELIER_JWT_SECRET|Minimum 32-byte HS256 material; required outside local/test defaults; rotate deliberately|
|ATELIER_JWT_TTL|Studio JWT default P30D|
|ATELIER_JWT_IMPERSONATION_TTL|Support JWT default P7D; admin TTL remains hardcoded separately|
|ATELIER_SUPERADMIN_EMAIL|Bootstrap account identity, not a restaurant owner login|
|ATELIER_SUPERADMIN_PASSWORD|Initial configured password; blank can generate/log once; handle privately|
|ATELIER_SUPERADMIN_RESET|Default false globally, true in local defaults; resets on startup; not an audit toggle|
|ATELIER_PASSWORD_RESET_ENABLED|Default false; do not enable without delivery/lockout remediation|
|ATELIER_CORS_ORIGINS|Comma-separated allowed origin patterns; use actual trusted deployment origins|
|ATELIER_TRUSTED_PROXIES|Exact remote addresses allowed to provide X-Forwarded-For|
|ATELIER_PRIVATE_KEY_PREFIX|Default separate private reference prefix; must match policy scope|
|ATELIER_MAX_PUBLISHED_MODEL_BYTES|Declared in render.yaml for the intended publication property; effective environment binding not separately runtime-verified; source default is 25 MiB|
|ATELIER_SCHEDULING_ENABLED|Enable/disable reaper via Spring conditional configuration|
|AWS_REGION / AWS_S3_BUCKET|S3 signing target; blank bucket disables uploads, otherwise default bucket is configured in application.yml|
|AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY|Optional static pair; absent uses default provider chain; never put into frontend|
|AWS_S3_PUBLIC_BASE_URL|Optional public CDN/base URL; not CloudFront provisioning|
|VITE_API_BASE_URL|Build-time frontend API origin without trailing slash; deployed split frontend/backend needs correct value|
|VITE_DEV_API_PROXY|Development/preview API proxy target, default localhost:8080|
|VITE_PUBLIC_MENU_ORIGIN|Origin used by venue QR export; default current browser origin|
|VITE_SALES_EMAIL|Optional mailto recipient for demo contact; no backend email submission|
|VITE_CURRENCY|Formatting fallback; persisted restaurant currency takes precedence where supplied|

Other atelier typed properties support normal Spring relaxed binding. Fixed source defaults include five-minute PUT TTL, fifteen-minute pending expiry, eight-MiB photo, fifty-MiB direct model, four-MiB branding, menu limit 500 and directory-limit 200 (legacy configuration, no directory endpoint). User settings are distinct from environment variables.

## 8. AWS requirements and operational restrictions

AWS beans use static pair or default credential provider chain; region and bucket must match. API signs locally and calls HEAD/DELETE/LIST; browser needs PUT/GET/HEAD CORS and exposed ETag. B1 corrects the local A05 policy-template contradiction: only the public root has an anonymous GET Allow; the application identity example covers managed public/private object operations and prefix-scoped listing, without a blanket private Deny that blocks its signer. This is a same-account assumption with no other private grants/ACLs, not a production verification. See [deployment policy assumptions and nonproduction checklist](../DEPLOYMENT.md#c-application-permissions-and-private-references), including explicit Deny, Block Public Access, KMS and cross-account caveats. Stop deployment when these prerequisites are unknown. No AWS CLI policy mutation command is provided here. [B2+B3 results](SPRINT_B2_B3_RESULTS.md) cover local private-cache/deletion repairs; existing-object remediation, durable B4 cleanup and B5 transaction changes remain separate work.

Use a deliberately selected nonproduction bucket/prefix for upload/download/replacement/deletion checks. Test allowed/denied private reads, exact signed browser headers, expiry, duplicate confirmation, stale cleanup and failed deletion. Real AWS operations can cost money and delete files; they require explicit environment ownership and authorization. There is no LocalStack endpoint/emulator configuration in this repository.

## 9. Existing deployment definitions

|Definition|Actual behavior|
|---|---|
|Root Dockerfile|Copies backend pom/src from monorepo context; Maven 3.9.9/Temurin21 builder; package with skipped tests; JRE21 runtime, nonroot atelier user, curl health check|
|backend/Dockerfile|Equivalent backend-context build, also skips tests; JVM heap/metaspace/SerialGC flags; total RSS not guaranteed by heap limits|
|render.yaml|Managed PostgreSQL + Docker backend, backend root context, prod profile, DATABASE_URL from DB, generated JWT, separately supplied bootstrap/AWS credentials, Actuator health, Oregon-region plan labels|
|Root vercel.json|Vite framework, `cd frontend && npm install && npm run build`, output frontend/dist, SPA rewrite and headers|
|frontend/vercel.json|Frontend-root rewrite/cache/security headers; lacks root build commands; duplicated policy to keep in sync|

These files describe intent, not a deployed inventory, pricing guarantee or current hosting quota. No hosting provider/API/account was accessed here. Select exactly one Docker build context and correct Vercel root. Backend compile/package example (after safe test setup):

```powershell
Set-Location -LiteralPath "$atelierRoot\backend"
mvn package
```

This runs tests unless skipped by caller; the existing Dockerfiles intentionally skip them, so their image build is not a test gate. Optional image build from the Git root, only with Docker installed:

```powershell
Set-Location -LiteralPath $atelierRoot
docker build -t atelier-ar:local .
```

No image run/push/deploy command is necessary for documentation. Production deployment requires reviewed secrets/target selection, successful test gates, backup/rollback plan and migration compatibility review. Startup applies migrations; rolling back an image does not undo schema changes. Do not deploy the uncommitted working tree without an explicit release decision.

## 10. Health, jobs, logging, monitoring and recovery

`/health` is static UP, while `/actuator/health` is the configured Render/container health target and can include actual DB health. Health/readiness/liveness routes are permitted; exposed detail depends on Actuator defaults. Actuator exposure is health/info, not a configured Prometheus metrics export. No alert routing, tracing backend, centralized error service or CI/CD workflow was found.

StaleUploadReaper uses Spring fixed delay five minutes, initial delay one minute; expires ordinary pending media and cleans current approved reference photos after fourteen days. It is not cron and it does not cover all private pending/history files. StorageJanitor runs @Async; cleanup errors lack durable retries. Analytics has its own virtual-thread executor and ignored future failures. Logs include admin email/actions and object paths; protect logs and avoid copying bootstrap/OTP secrets.

No automated PostgreSQL backup/PITR/restore verification, S3 versioning/lifecycle provisioning or disaster-recovery runbook is implemented. Establish provider-side backup retention, restore drills, object retention and metadata/object reconciliation before production acceptance. Do not treat a documented recommendation as an enabled feature.

## 11. Troubleshooting and safe Git workflow

|Symptom|Source-grounded investigation|
|---|---|
|java/mvn not found|Check JAVA_HOME/Path in the current process; refresh terminal; confirm `mvn -version` reports Java21|
|Mockito test stalls on Windows|Look for Java attach named-pipe denial/Surefire dumpstream; request authorized unsandboxed execution instead of weakening tests|
|Default startup fails|Prod is default; missing datasource/JWT is expected; choose explicit disposable local configuration|
|Local profile rejected|Inspect hosted markers including PORT; do not force local into hosting|
|Schema mismatch/migration failure|Confirm actual vendor/location and history/checksum; never repair/edit applied migrations blindly|
|Upload 403 or private photo failure|Differentiate API role/account, S3 signing/headers/CORS and known IAM/bucket template denial|
|Studio action 401 after reset|Expected token_version revocation; sign in again; current admin password UI returns no replacement token|
|Category reorder fails|Known client orderedCategoryIds/DTO orderedIds mismatch, not missing Java or DB permission|
|Menu privacy holding card absent|Known menuLive/isMenuLive frontend contract issue; API still omits private dishes|
|3D model fails|Check model format/self-contained resources, public read/CORS, HTTPS, worker/decoder policy and actual device support|

Inspect `git status --short` before work. Existing A01–A03 code/test modifications and historical docs are uncommitted and were preserved during this pass. Never reset/clean or stage everything to simplify a handoff. Review scoped diffs, run appropriate tests and `git diff --check`; commit/push only when requested. Secret files, node_modules, target, dist and local data remain ignored. The new documentation is the only content changed by this request.
