# Atelier AR engineering skills — review and installation proposal

Reviewed 2026-10-31. **Awaiting approval: nothing installed, no custom skill created.** This document supplements SKILL_INSTALLATION_PLAN.md; it does not replace that earlier design-skill plan or approve application work. Upstream files were downloaded into a review cache outside the application repository and inspected as data. No third-party helper, dependency installer, browser, application server, database operation, or deployment was executed.

## Recommendation and approval choices

1. **Initial engineering set: eight global skills:** `test-driven-development`, `security-and-hardening`, `code-review-and-quality`, `vercel-react-best-practices`, `supabase-postgres-best-practices`, `api-and-interface-design`, `performance-optimization`, `observability-and-instrumentation`. Install selected folders and required shared reference documents, rather than complete upstream plugins.
2. **Later/on demand: two global skills:** `vercel-composition-patterns`, `ci-cd-and-automation`. Both are compatible as guidance with the qualifications below, but are less urgent than verification, tenant isolation, contract consistency and measured performance.
3. **Optional media pair, project level:** `model-viewer-ar`, `3d-asset-optimization`. These complement the existing renderer; they do not authorize a new renderer, model processing pipeline, dependency, CDN script, or asset conversion.
4. **Do not install agent-browser yet.** Retain the existing webapp-testing skill and available computer-use/browser capabilities. Separately choose/provision a browser-testing runtime when an approved task needs it.
5. **Four custom project skill proposals only:** `atelier-tenant-security`, `atelier-analytics-verification`, `atelier-guest-experience`, `atelier-brand-studio`. Their proposed scope and acceptance checks appear below. Creating their SKILL.md files and metadata requires separate approval; none is created here.

The first eight are a deliberately staged recommendation, not a claim the remaining requested skills are incompatible. Approve named groups or individual names. Installation approval would cover copied instructions, supporting references and invocation metadata only. Application changes, new testing dependencies, telemetry vendors, database work, CI workflows, cloud access and deployment remain separately scoped work.

Continue the previously approved **explicit-only** invocation convention for any selected new skills: preserve existing metadata and set `policy.allow_implicit_invocation: false` in each installed `agents/openai.yaml`. This is proposed configuration, not changed during this review. Do not replace global config.toml, install hooks, enable plugin session-start scripts, or alter existing nine skills. Skills supply guidance; they do not enforce security or prove correctness by being installed.

## Installed inventory and duplication check

The user/global `C:/Users/IQRA COMPUTER/.agents/skills` contains exactly the earlier nine: `emil-design-eng`, `animate`, `review-animations`, `animation-performance`, `apple-design`, `frontend-design`, `interface-design`, `web-design-guidelines`, `webapp-testing`. Preserve these installations and their existing policies. The legacy `.codex/skills` contains the system skill directory. None of the requested engineering candidates or the proposed AR/media pair was found by manifest name in either global tree or the plugin cache. No enabled skills.config entries were present in the inspected user configuration. Neither the outer workspace nor the nested Git root has a project `.agents/skills` directory. The proposed shared `C:/Users/IQRA COMPUTER/.agents/references` directory is absent.

This inventory covers the configured user, workspace, nested repository and plugin-cache locations, not unrelated directories or every possible profile. `agent-browser` was not found on PATH. Node reports `v24.21.0`; no CLI or browser runtime was installed for this review. The earlier installation confirmed Python Playwright missing in bundled Python; it has not been rechecked in every interpreter here.

Overlap is managed through task selection:

|Responsibility|Lead skill|Bounded specialist / existing skill|
|---|---|---|
|Regression-driven implementation|test-driven-development|webapp-testing for browser acceptance once a runtime is approved|
|Authorization and abuse analysis|security-and-hardening|proposed atelier-tenant-security for actual Spring/tenant/media invariants|
|Diff review|code-review-and-quality|security, performance or PostgreSQL skill only for relevant findings|
|Application performance investigation|performance-optimization|vercel-react-best-practices for React; animation-performance for frames; Postgres guide for SQL|
|HTTP and DTO compatibility|api-and-interface-design|proposed analytics skill for event-to-dashboard meaning|
|Operational telemetry|observability-and-instrumentation|analytics skill verifies product metrics, a different responsibility|
|Visual and interaction work|existing frontend-design/interface-design/Emil skills|engineering guides constrain correctness and cost, not art direction|

Do not add duplicate design, deployment, native React, Supabase platform, browser-devtools, or orchestrator skills merely because the upstream repositories include them. Cross-referenced skill names are not a mandate to install those skills.

## Verified upstream identity and exact paths

Direct GitHub API inspection resolved canonical requested repositories, default-branch commits and complete recursive trees; all five reported `fork=false`, `archived=false`, and non-truncated trees. These observations establish source identity at review time, not author endorsement, malware certification, or runtime reliability.

|Repository|Reviewed immutable revision|Scope|
|---|---|---|
|[vercel-labs/agent-skills](https://github.com/vercel-labs/agent-skills/tree/1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62)|`1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62`|Two requested manifests, their rules and compiled guides|
|[addyosmani/agent-skills](https://github.com/addyosmani/agent-skills/tree/8bfcc1f9916f2eea55494d8c1fa7f4bde867dd9e)|`8bfcc1f9916f2eea55494d8c1fa7f4bde867dd9e`|Seven requested manifests, their bundled pattern references, root shared checklists|
|[supabase/agent-skills](https://github.com/supabase/agent-skills/tree/c9be0e931b7930f7d02126d04774d904c381e7d7)|`c9be0e931b7930f7d02126d04774d904c381e7d7`|Postgres manifest and reference rules; separate platform skill identified but excluded|
|[vercel-labs/agent-browser](https://github.com/vercel-labs/agent-browser/tree/5868f7c05020e047c00e68e4abf9253cce864f41)|`5868f7c05020e047c00e68e4abf9253cce864f41`|Discovery stub, core/specialized instructions, package metadata, JS launcher/postinstall, browser-install Rust source|
|[ahmettasdemirusa/threejs-product-skills](https://github.com/ahmettasdemirusa/threejs-product-skills/tree/a8150ad12ca565c36fcc609ede06bed0acc74523)|`a8150ad12ca565c36fcc609ede06bed0acc74523`|All five manifests, viewer/configurator HTML templates and demo-export helper|

|Exact manifest / `$invocation` name|Upstream directory|Priority and installation recommendation|Atelier compatibility and purpose|
|---|---|---|---|
|`test-driven-development`|addyosmani: `skills/test-driven-development`|P0, initial/global|Red-green regression work transfers to Java/JUnit/Mockito/Maven. It explicitly says discover the repository test command first. JS examples are not a request for Jest/Vitest or TypeScript. Broad test/refactor language must not expand a low-impact or read-only task.|
|`security-and-hardening`|addyosmani: `skills/security-and-hardening`|P0, initial/global|Useful for current-account/token-version validation, tenant ownership, media boundaries and OTP/storage risks. Map principles to Spring Security/Bean Validation/JPA. Cookie-only, 422, bcrypt-cost and Node examples are not automatic changes to the current auth/API contract.|
|`code-review-and-quality`|addyosmani: `skills/code-review-and-quality`|P0, initial/global with read-only review constraint|Correctness/security/architecture review fits the uncommitted fixes. Its mutation-testing instruction requires an explicitly approved isolated experiment; never edit a user's working file merely because a review was requested. No automatic deletion, refactor, model delegation, commit or push.|
|`vercel-react-best-practices`|Vercel: `skills/react-best-practices`|P1, initial/global|React 18 rerender, effect, async and loading guidance is useful. Filter Next.js/RSC, React 19/19.2, dependency-specific and browser-specific examples; preserve Vite/custom routing/model-viewer. Folder name differs from manifest name.|
|`supabase-postgres-best-practices`|Supabase: `skills/supabase-postgres-best-practices`|P1, initial/global|Explicitly covers Postgres anywhere. Useful for indexes, locks, pooling, constraints and N+1 checks. Does not require Supabase hosting/Auth/Storage, SDK, CLI or MCP. Do not apply Supabase auth.uid()/auth roles to this plain Spring application.|
|`api-and-interface-design`|addyosmani: `skills/api-and-interface-design`|P1, initial/global|Helps protect public menu/branding contracts and presign/confirm DTOs. Preserve controller-record DTO-service-repository and existing ProblemDetail semantics; its sample status/naming/PATCH/TypeScript conventions are not grounds to rename existing routes or change error statuses.|
|`performance-optimization`|addyosmani: `skills/performance-optimization`|P1, initial/global|Coordinates evidence across bundles, media delivery, queries, cache and backend latency. Complements rather than duplicates the narrower existing animation guide. No automatic benchmark tools, load traffic, indexes, caching, commits or destructive reverts.|
|`observability-and-instrumentation`|addyosmani: `skills/observability-and-instrumentation`|P1, initial/global|Useful for async analytics, S3 operations and cleanup failures. Start with existing SLF4J/Actuator facilities; its Node/OTel/Prometheus examples do not justify new dependencies/vendors. Operational telemetry does not establish product metric validity.|
|`vercel-composition-patterns`|Vercel: `skills/composition-patterns`|P2, later/global|Compound components/providers/explicit variants can guide approved component changes. Skip react19-* rules on React 18; retain forwardRef/useContext where needed. The guide's React 19 preferences are not a requirement to replace still-valid APIs. No opportunistic decomposition.|
|`ci-cd-and-automation`|addyosmani: `skills/ci-cd-and-automation`|P2, later/global|Useful when CI work is approved; no .github workflows were found in the nested root. Templates assume Node/TypeScript/Prisma and root package.json: do not copy them into this Java/JS monorepo unchanged. No automatic workflow publication, deployment, secrets or branch-policy changes.|
|`model-viewer-ar`|threejs-product-skills: `skills/model-viewer-ar`|P1, optional/project|Closest fit to existing Google model-viewer wrapper, GLB, native handoffs and true scale. Reviewed examples target 4.3.1; installed lock resolves 4.4.0. Use existing npm/lazy loader, not its CDN script. Confirm actual API/device support; do not replace the wrapper.|
|`3d-asset-optimization`|threejs-product-skills: `skills/3d-asset-optimization`|P2, optional/project|Useful when approved GLB performance/quality work begins. Instruction-only folder, but commands download/run glTF tooling and create output. Keep originals and compare copies; validate decoder support and iOS/Android AR before recommending compression. Do not adopt its sample budgets as contractual product requirements.|
|`agent-browser`|agent-browser: `skills/agent-browser`|Defer skill and runtime|Current manifest is a hidden discovery stub; CLI supplies actual instructions. Not a standalone drop-in skill. Browser permissions/tool naming, native binary trust and overlap require a separate decision.|

The remaining three 3D collection skills are **not recommended**: `threejs-product-viewer` introduces a separate renderer; `product-configurator` addresses color/part/pricing workflows not established for Atelier dishes; `360-product-spin` adds Spin360/frame generation, an unrequested capability. Their presence is not evidence Atelier needs them. Retain the existing photo-first/GLB/AR approach.

## Stack compatibility verified from source

Source anchors below are relative to the actual Git root, `C:/Users/IQRA COMPUTER/Desktop/atelier-3d-media-engine-v4/atelier-3d-media-engine`.

|Existing implementation|Evidence|Constraint for all new skills|
|---|---|---|
|React 18.3.1, Vite 5.4.21, model-viewer 4.4.0 resolved versions|`frontend/package-lock.json`; manifest ranges in `frontend/package.json`|Do not introduce Next.js, React 19, TypeScript or alternate data/state libraries to satisfy examples. React.lazy/Suspense or dynamic import only in approved changes.|
|Tailwind 3, functional JSX, custom router, API client, dual auth sessions|`frontend/package.json`, `src/App.jsx`, `src/utils/router.js`, `src/api/client.js`, `src/context/AuthContext.jsx`|Reuse these boundaries; React guides do not become backend security rules. Auth currently uses bearer tokens and browser storage; flag tradeoffs rather than silently migrate to cookies.|
|React 18 custom-element bridge and lazy renderer|`frontend/src/components/ModelStage.jsx:14-19,86-116`, `frontend/src/utils/modelViewer.js`|Keep addEventListener/removeEventListener and imperative boolean attributes. Preserve lazy import/error retry, progress, AR modes and viewport behavior. New JSX event assumptions may break this bridge.|
|Java 21, Spring Boot 3.3.5, Security, JPA, Flyway, Caffeine, Actuator, AWS SDK 2.28.29|`backend/pom.xml`, `AGENTS.md`|Use JUnit/Mockito and mvn test; do not transplant Express/Prisma/Zod/Node telemetry code. Preserve short transactions and existing presigning checksum compatibility.|
|PostgreSQL production metadata, H2-specific migrations/tests|`backend/src/main/resources/db/migration/{postgresql,h2}`; test profile|PostgreSQL-specific constraints/locking require disposable PostgreSQL verification. H2 success does not establish RLS, partial-index or concurrent transaction behavior. No schema change or DB connection is authorized here.|
|Direct browser-to-S3 uploads and replacement confirmation|`frontend/src/utils/upload.js`, `backend/.../service/MediaService.java`, `ModelRequestService.java`, `service/S3Storage.java`|No binary proxy through Java, public private-prefix exposure, or unapproved model/thumbnail conversion path.|

Vercel React rules needing exclusions include `bundle-dynamic-imports` (`next/dynamic`), `server-*` RSC/server-action/cache/after examples, `rendering-activity`, `rendering-resource-hints` and `advanced-use-latest`/`useEffectEvent`. Do not install SWR/better-all solely because a guide mentions them. Composition's React 19 section explicitly says skip on React 18. Rule suggestions still need source inspection and evidence; they are not blanket modernization requirements.

`ModelStage` maps horizontal placement to `ar-placement="floor"` and sets scale according to existing metadata. The AR guide's wall/scale examples are not a request to change those semantics. Desktop browser automation can verify fallback/launch intent but cannot prove native Quick Look, Scene Viewer, physical scale or camera placement; real devices and a confirmed HTTPS nonproduction origin remain necessary.

## Security and packaging review

### Instruction-only engineering candidates

No executable helper files were found inside the ten requested engineering skill folders at reviewed revisions. They contain Markdown rules/examples and, for Vercel, compiled guides/metadata. Examples can nevertheless instruct an agent to execute commands or change files. Installing a folder does not authorize the commands in it.

- **Addy hooks excluded:** the repository also includes session-start, source-driven cache and simplification shell hooks, unrelated scripts and other skills. Do not install its entire plugin, AGENTS.md, using-agent-skills router or hook configuration. Those surfaces were inventoried, not fully audited for execution.
- **Read-only review risk:** `code-review-and-quality/SKILL.md:164` instructs inverting a condition and restoring it from a copy. On a dirty tree this can damage work or run mutating behavior. Invoke reviews with an explicit report-only/no-edit constraint. Any approved mutation test belongs in a disposable copy/worktree and must preserve the user's existing edits. No automatic `git reset`, checkout, clean or source overwrite.
- **Workflow scope:** TDD recommends subagents for complex testing; review discusses multiple models. These are optional techniques, not permission to create agents or change models in every session. Performance guidance says keep/commit or revert; CI shows auto-fix/commit/push and deployment examples. User scope and repository Git rules take precedence. No style refactor, auto-commit, push, provider install or release is implied.
- **Security controls need mapping:** hardening's cookies-only storage, blanket 422 responses, CSP/header prescriptions and package-manager policies require reviewed architectural changes if applied. Existing bearer auth, DTO/error codes and AR worker/CSP needs must be inspected. Report conflicts and design changes; never weaken current account/tenant checks, hide security findings, or rewrite auth during an unrelated feature task.
- **Database side effects:** Supabase references include CREATE/ALTER/INDEX, RLS/auth.uid(), maintenance and EXPLAIN ANALYZE examples. EXPLAIN ANALYZE actually executes SQL. Use only an explicitly disposable target and approved query; no production queries, restores, extensions or migration changes as skill verification. Retain Flyway as owner of schema, never Hibernate auto-update or Supabase migrations.
- **Telemetry risks:** no tokens, presigned URLs, raw request bodies or tenant PII in logs/traces/screenshots. Avoid tenant IDs as unbounded metric labels. External collectors and induced failures/alert tests need separately approved nonproduction scope.

### Addy shared-reference layout: required installation detail

Several manifests refer to `../../references/*.md`, and nested pattern guides use `../../../references/*.md`. An installer copying only `skills/<name>` cannot preserve those references by itself. A global destination `C:/Users/IQRA COMPUTER/.agents/skills/<name>/SKILL.md` resolves them to **`C:/Users/IQRA COMPUTER/.agents/references/`**.

Proposed minimal solution, included in any approval of the Addy group:

- Use the supported skill installer for each selected folder at the pinned commit.
- Copy only the four required, reviewed upstream shared Markdown documents to the above user-level references directory: `testing-patterns.md`, `security-checklist.md`, `performance-checklist.md`, `observability-checklist.md`. Preserve their bytes, upstream provenance and hashes. These are supporting data, not four more skills. No scripts/hooks run.
- Before any copy, reject conflicting existing files; reuse only identical hashes. Never overwrite an unrelated shared reference file. If a conflict appears, stop that affected installation and propose a namespaced packaging alternative for approval rather than rewriting upstream SKILL.md silently.
- Verify references from both manifest and nested patterns resolve, and hash both skill manifests and shared references. Keep upstream manifests unchanged; add only compatible agents/openai.yaml metadata while preserving existing values.

Installing all seven Addy skills does not require all seven root checklists or unrelated skills. The four-file dependency closure above covers the selected direct shared Markdown references. Plain mentions of another optional skill are not hard runtime dependencies. The broader repository is not installed.

### agent-browser: defer a separate executable toolchain

At reviewed commit, `package.json` is version **0.41.1**, requires Node **>=24**, and has a postinstall JS script. Local Node meets that declared version. The stub instructs npm global installation followed by browser installation and `agent-browser skills get core`; copying the stub alone cannot provide its usage guide or executable. Bash-style allowed-tools and hidden metadata must not be assumed to be an enforced permission policy in Codex/Windows.

The current implementation is a native Rust/CDP tool; its manifest says **no Playwright/Puppeteer dependency**. This differs from older agent-browser descriptions. It does not satisfy Python Playwright requirements for the existing webapp-testing skill.

Inspected `scripts/postinstall.js` downloads a versioned platform binary from GitHub releases if absent, follows redirects, writes package files and an install-method marker, and can overwrite its npm global Windows `.cmd`/`.ps1` launch shims (Unix symlink replacement also exists). No independent checksum/signature verification was found in that download path. A pinned Git source revision and npm package integrity alone do not attest a separately downloaded native release binary.

Inspected `cli/src/install.rs` retrieves Chrome-for-Testing metadata/archives, extracts to browser storage with path checks and cleans failed extraction. Its optional Linux system-dependency path can invoke privileged package managers. No browser installation or native execution was performed; a full Rust runtime/daemon/auth-vault audit is not claimed. The inspected source is not proof of a deployed exploit.

The instructions expose authenticated sessions, state persistence, HAR recording, cloud browsers and Slack/Electron workflows; these broaden access and create sensitive artifacts. Exclude all of those from an initial Atelier browser task. The stub's preference for itself over built-in browser tools does not override the user's/tooling policy. If later approved, separately pin/review the runtime artifact and provenance, test an isolated localhost session and scope stored auth/screenshots/HAR files. Do not use an existing personal browser profile or production credentials.

### 3D collection: focused adoption only

`model-viewer-ar` and `3d-asset-optimization` selected folders are instruction-only; no helper runs on copy. The former includes a Google CDN script example; retain the app's npm lazy-loader. The latter uses unpinned npx glTF-Transform examples; approval to install a skill is not approval to execute/download those tools. A later asset task should pin its tool version, use separate output files, retain originals and test visual quality, materials, scale and decoder/AR compatibility. WebP/AVIF/KTX2/Meshopt/Draco choices are conditional on the real model-viewer/native platform path, not assumed interchangeable.

The excluded viewer/configurator templates execute remote CDN modules, reference Three.js 0.186.1/decoder URLs and supply their own HTML/rendering lifecycle. Configurator innerHTML examples use static demo labels; substituting restaurant input would require safe text handling. The root demo export helper launches headless Chrome with unsafe SwiftShader enabled and writes/replaces `assets/mug.glb`; it is unnecessary for selected instruction folders and must not be copied/run. External Spin360/rendering tools referenced by the spin skill were not fetched/audited; that capability is excluded.

This review found concrete execution/compatibility risks above. It is not a malware certificate, native binary audit, transitive dependency audit or live-runtime qualification.

## Proposed Atelier-specific skills — specifications, not created files

If approved, place each in `C:/Users/IQRA COMPUTER/Desktop/atelier-3d-media-engine-v4/atelier-3d-media-engine/.agents/skills/<name>/`. Keep them project-local because they encode business/security invariants. Open Codex at the nested Git root for predictable repository instructions/discovery; do not duplicate them globally or at both workspace levels. Use concise SKILL.md entrypoints with source-path references; no initial scripts, hooks, copied manuals, sample production data or credential files. Existing AGENTS.md remains authoritative, with current code/tests resolving historical audit drift.

### atelier-tenant-security — P0

**Trigger and scope:** review or implement an explicitly scoped change touching admin/studio auth, tenant-owned entities, private media, impersonation, upload confirmation or password/session lifecycle. Does not implement fixes during a report-only audit.

**Repository anchors:** `AGENTS.md`; `project-documentation/SECURITY_DOCUMENTATION.md`; historical `TECHNICAL_AUDIT.md`; current `backend/src/main/java/com/atelier/media/service/{AdminAccess,RestaurantAccess,AdminAuthService,AuthService,DishService,MediaService,ModelRequestService}.java`; `web/{AdminController,StudioController}.java`; `security/{SecurityConfig,Tenant,AdminPrincipal,TokenService}.java`; relevant repositories and DTOs. Tests: `security/AccountAccessSecurityTest`, `security/TokenSecurityTest`, `service/CategoryIsolationTest`, MVC controller tests.

**Workflow:** inspect actual route and service gates; trace principal identity and current DB state; check role/audience/token version/password setup/impersonation rules; check tenant-scoped resource/category/pending key access; inspect public DTO/private reference boundaries; propose minimal change; when implementation is authorized, add focused negative regressions and run appropriate full suite against safe fixtures.

**Invariants and acceptance:** current admin account/version validated on every admin operation; current restaurant status/version/deletion/setup restrictions checked on every relevant studio operation; restaurant identity derived from verified claims; foreign category/dish/key cannot cross tenant boundaries. Rejected requests must not mutate storage/DB. Preserve account-level password setup exceptions and intentional admin impersonation semantics. No production DB/S3 probes or auth-storage migration implied.

**Required evidence/output:** endpoint/service/repository ownership matrix, abuse cases, source references and tested/untested claims; regressions for revoked tokens, missing admin, suspended/soft-deleted/missing restaurant, setup accounts and foreign categories/keys as relevant. A01–A03 already have remediation in the working tree: do not reimplement based on the old root audit. Do not mark further issues resolved without current tests/evidence.

### atelier-analytics-verification — P0/P1

**Trigger and scope:** analytics event, summary, dashboard, rate/dedup, aggregate or metric-label changes. Focus on truth of existing business metrics; no new tracking/vendor/data collection requirement implied.

**Repository anchors:** `frontend/src/pages/GuestMenuPage.jsx:49`; `components/guest/DishSheet.jsx:66-90`; `components/studio/StudioAnalytics.jsx`; `components/admin/KpiCards.jsx`; `api/client.js`; backend `service/AnalyticsService.java`, `model/{AnalyticsEvent,AnalyticsDaily,AnalyticsEventType}.java`, related DTOs/repositories, `db/migration/{postgresql,h2}/V10__analytics_aggregates.sql`, `service/AnalyticsServiceTest`; audit A13/A19 and current feature/security ledgers.

**Workflow:** map user action -> event producer -> client payload -> HTTP validation -> tenant/dish status -> throttling/dedup -> async submission -> counters/raw/daily persistence -> query/denominator -> UI label. Separate accepted HTTP response, committed event, distinct person and successful AR session. Check duplicates, retries, render/reopen effects, null dish IDs, time/day boundaries, concurrent updates, executor failure, rollback and deleted-tenant handling.

**Current evidence to retain:** MENU_VIEW is sent once after menu loading without dishId; MODEL_3D_VIEW is sent by the 3D reveal action; AR_LAUNCH is sent before activateAR succeeds or falls back. Backend MENU_VIEW increments dish views only if a dishId is present; studio top dishes use viewsCount. Therefore do not label those numbers unique guests, confirmed AR sessions or reliable dish popularity without a verified contract. Daily read-modify-save and asynchronous error handling need real transaction/concurrency evidence, not just mocked repository tests.

**Acceptance/output:** an event dictionary with trigger, denominator, dedup window, persistence and UI meaning; deterministic expected-count fixtures; invalid type/foreign dish rejection; duplicate/retry/concurrency and async failure tests in isolated resources. Browser capture may prove one emitted request but not completed persistence. No synthetic traffic to production, no fabricated uplift/conversion claims, no raw IP/token logging or added PII. Operational tracing skill can support diagnostics but cannot establish metric correctness.

### atelier-guest-experience — P1

**Trigger and scope:** guest menu, dish sheet, search/filter, QR entry, 3D loading or AR handoff changes and responsive acceptance reviews. Preserve photo-first browsing, printed `/r/{slug}` routes and current identity; no renderer/router replacement or new commerce features.

**Repository anchors:** `frontend/src/pages/GuestMenuPage.jsx`, `components/guest/{GuestDishCard,DishSheet,CategoryNav,FilterBar}.jsx`, `components/{ModelStage,ArHandoffModal,RestaurantQrModal}.jsx`, `utils/{router,modelViewer,qrCard,media}.js`, `api/client.js`; backend `service/PublicMenuService.java`, public DTO mapper and `web/PublicMenuController.java`; feature inventory, roadmap R06/R11/R12/R14, audit A17/A18 and contract issue PD01.

**Workflow:** trace real public/preview data; confirm availability/private-menu/empty/error handling; test QR/slug and filters; inspect library/model loading and React 18 custom-element events; verify keyboard/focus/scroll/body locking, safe areas, contrast, reduced motion and long content. Proposed viewport samples 360/390/768/1440 CSS pixels are test choices, not existing product guarantees; include actual device viewport and zoom where available.

**Acceptance/output:** report-only issue list or, for approved implementation, screenshots and reproducible flow assertions with console/network observations for photo-only/model/slow/error/unsupported-AR cases. Separate mocked fixtures from connected backend checks. Preserve imperative event and boolean-attribute bridge, lazy loading and AR fallback. `menuLive` vs `isMenuLive` is a source-contract concern to verify, not silently repair during a design task. Real iOS/Android HTTPS checks are required for native AR/scale claims; desktop emulation cannot substitute.

### atelier-brand-studio — P1

**Trigger and scope:** restaurant branding/theme/layout/currency/logo/banner/QR preview changes. This is the existing branding workflow, not authorization to redesign all studio screens or add a design system dependency.

**Repository anchors:** `frontend/src/components/studio/BrandCustomizer.jsx`, `utils/{theme,upload,qrCard}.js`, `pages/{StudioPage,GuestMenuPage}.jsx`, `components/RestaurantQrModal.jsx`, shared UI; backend `service/BrandingService.java`, `dto/StudioSettingsUpdateDto.java`, restaurant/public branding mapping and studio settings endpoints; `project-documentation/{API_REFERENCE,FEATURE_INVENTORY,CODEBASE_ARCHITECTURE}.md`.

**Workflow:** inspect existing accent/currency/layout options and defaults; trace form -> DTO -> ownership/access -> save -> cache/public DTO -> guest preview -> QR export; preserve direct upload/pending replacement/usage accounting and validated link fields. Compare approved visual choices across studio preview and guest rendering. Reuse existing design skills only for their applicable scope.

**Acceptance/output:** all existing allowed values, saved/reloaded settings, validation/error/loading states and missing asset fallbacks verified; cache/public-preview consistency recorded; mobile/touch/keyboard and long names/currency formats covered; QR still resolves to the same restaurant. No arbitrary brand choice, silent copy change, new fonts/assets, overwrite of private media, broader tenant access or false live-save claim from a local preview.

### Custom-skill validation after separate approval

Validate each name/frontmatter/relative reference and explicit-only metadata, then confirm user/project scope through Codex skills/list. Use read-only trigger prompts to check routing and output: foreign-category review; trace AR_LAUNCH meaning; mobile guest error-state review; saved accent/QR contract review. Verify exclusion cases (marketing review should not invoke tenant tooling, telemetry should not imply analytics correctness). Do not start services, execute mutation tests or create application fixtures merely to prove a skill loads. Record limitations instead of claiming end-to-end verification.

## Safe installation procedure after approval

Global skills belong under `C:/Users/IQRA COMPUTER/.agents/skills/<exact-manifest-name>`; optional media/custom skills belong under the nested repository `.agents/skills`. No double installation under legacy .codex/skills.

1. Recheck inventory, Git status and file conflicts. Resolve pinned sources from the manifest below; never install moving main or a marketplace mirror. Preserve all existing uncommitted security work.
2. Use bundled supported `C:/Users/IQRA COMPUTER/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py` with bundled Python, explicit --repo, --ref, --path, --dest. Use individual `--name vercel-react-best-practices` / `--name vercel-composition-patterns` so folder names match manifest names. Source folders themselves remain react-best-practices/composition-patterns.
3. Copy only required reviewed shared Markdown data for selected Addy skills as described above. No upstream root AGENTS/plugin/hooks/npm installer copied. Treat shared-reference conflicts as a stop for the affected skill.
4. Compare each untouched installed SKILL.md hash and supporting-file inventory against the pinned cache; validate references/licenses. Add/merge explicit-only metadata, preserving existing UI/dependency keys; do not overwrite unrelated user files or config.
5. Use installed Codex's read-only skills/list with forceReload to confirm names, paths, scope, enabled status and parse errors. Codex 0.166.0-alpha.5.1 and explicit-only support were verified during the earlier installation; recheck the running version if it changes. A file-system copy is not proof of discovery or successful runtime work.
6. Make skills available on the next turn, restart only if needed. Read-only smoke prompts must explicitly forbid edits/servers/DB/browser runtime/dependency installs. Do not execute third-party commands just to verify discovery.
7. Report installed source revisions, hash results, policy, location, unresolved reference/runtime requirements and any skipped/conflicting files. No automatic update of already installed skills, commit or push.

Example installer form, **not executed**:

```powershell
$skillPython = 'C:/Users/IQRA COMPUTER/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$skillInstaller = 'C:/Users/IQRA COMPUTER/.codex/skills/.system/skill-installer/scripts/install-skill-from-github.py'
& $skillPython $skillInstaller --repo vercel-labs/agent-skills --ref 1a2c79d4e6c4a2283dc2c614b6538cbe1ed5ca62 --path skills/react-best-practices --name vercel-react-best-practices --dest 'C:/Users/IQRA COMPUTER/.agents/skills'
```

This one example intentionally does not install the whole approval set or fix the Addy reference layout; execution should follow the selected named groups and the complete procedure.

## Readiness, verification limits and next development sequence

This pass verifies source identity, manifest names/hashes, candidate instructions/supporting source, installed inventory and stack applicability. It does **not** verify execution of uninstalled skills, native binaries, all optional hooks, transitive packages, live AWS/PostgreSQL, CI hosting or mobile AR. No application build/test was rerun for an instruction-only review. Prior 208/374-test figures in the project history are historical results, not results of this task. The current feature ledger records the later 374-test run and remediation; root AGENTS/audit retain older baseline claims.

For future authorized development: review/preserve A01–A03 work first; then address the current roadmap's OTP transaction/storage-policy blockers in separately scoped work; align menu/analytics contracts with regression evidence; provision one approved browser-testing runtime for responsive acceptance; measure 3D/bundle/query bottlenecks before optimization; add CI/telemetry only with explicit scope. Installing skills does not itself complete any roadmap task.

Suggested future invocations after approval/installation:

- `$code-review-and-quality review the existing security diff; report only, no edits, mutation experiments, delegation, commits or pushes.`
- `$test-driven-development implement only the approved regression fix using existing Maven/JUnit conventions; inspect the safe test profile first.`
- `$vercel-react-best-practices review guest rendering for React 18/Vite applicability; report only; exclude Next.js/React 19 rules.`
- `$performance-optimization propose a measurement plan for guest 3D loading; do not start services or modify code.`
- `$supabase-postgres-best-practices review repository query/index design from source only; no database connection or migration changes.`

If the custom skills are later created: use `$atelier-tenant-security` for authorization work, `$atelier-analytics-verification` for event/dashboard integrity, `$atelier-guest-experience` with existing design/motion review for guest acceptance, and `$atelier-brand-studio` with interface-design for bounded branding workflows. Marketing landing work remains led by the existing frontend-design/Emil skills, with React/performance review as relevant.

Official format/discovery/invocation reference: [Codex skills](https://learn.chatgpt.com/docs/build-skills). Exact upstream file locations are reproducible from the repository/commit/path tables above. The following hashes lock the reviewed inputs; they are not digital signatures or evidence that commands were run.

## Reviewed SHA256 manifest

<!-- Generated from the downloaded pinned source files; no upstream skill is installed. -->

|Manifest name|Pinned source path|SKILL.md SHA256|
|---|---|---|
|`api-and-interface-design`|`addyosmani/agent-skills/skills/api-and-interface-design/SKILL.md`|`cc5e2166c484b2c297e8253f56e7525e5031f81d66101ca4e760ef378ea6bad0`|
|`ci-cd-and-automation`|`addyosmani/agent-skills/skills/ci-cd-and-automation/SKILL.md`|`5e0e55c059670a7838dc9d58dc20a3a645c0d43c974416490112af7de522df95`|
|`code-review-and-quality`|`addyosmani/agent-skills/skills/code-review-and-quality/SKILL.md`|`2db1e850379f255fd3091ef278cde24cd6c7d2ba23e360cc52ea522c40d4396e`|
|`observability-and-instrumentation`|`addyosmani/agent-skills/skills/observability-and-instrumentation/SKILL.md`|`e7fcb0820306d46268de9594d676ad98e35040858926c412b7000faa52a61f87`|
|`performance-optimization`|`addyosmani/agent-skills/skills/performance-optimization/SKILL.md`|`82af71540457cf853893685211f64509cd1e781c0de09d0186f16960f2d6eeba`|
|`security-and-hardening`|`addyosmani/agent-skills/skills/security-and-hardening/SKILL.md`|`358566859a3d791c474eb9fa36939b45875c7480d7d4650eaa092dceae2c1c37`|
|`test-driven-development`|`addyosmani/agent-skills/skills/test-driven-development/SKILL.md`|`8fcaa57cf445ca6111fd20d2321bcfc0f424db7cfe526836db7b9bf8828fee39`|
|`360-product-spin`|`ahmettasdemirusa/threejs-product-skills/skills/360-product-spin/SKILL.md`|`16fc7a9404ae26a3c859606c8323289c8957974322f57d0e3c35b3d205fd99bd`|
|`3d-asset-optimization`|`ahmettasdemirusa/threejs-product-skills/skills/3d-asset-optimization/SKILL.md`|`15f2a51f1e798cdc758560ceb89d64b007ff287c70d540c705473b8c1478fe30`|
|`model-viewer-ar`|`ahmettasdemirusa/threejs-product-skills/skills/model-viewer-ar/SKILL.md`|`80bdf0b6dd1f15a903f3f5b3959000609c3911b2a32d78758d69bb06c21f0343`|
|`product-configurator`|`ahmettasdemirusa/threejs-product-skills/skills/product-configurator/SKILL.md`|`e847a5899b6308ae5eff84b3df021199d98369098b678b6163e73f7a06df580a`|
|`threejs-product-viewer`|`ahmettasdemirusa/threejs-product-skills/skills/threejs-product-viewer/SKILL.md`|`2c396b561d1eeafe40c1c762f7088474eff03f59435f2ee03524a1872b4534ba`|
|`supabase-postgres-best-practices`|`supabase/agent-skills/skills/supabase-postgres-best-practices/SKILL.md`|`ad65e776f45e761bafc609d3f506c77c1032225aa65e0c4c02f241b8071e5855`|
|`agent-browser`|`vercel-labs/agent-browser/skills/agent-browser/SKILL.md`|`fd4ddd37ddb69f37ebc62e086566fcf606bbd09a5b4e4e7d7a6c4f0f94fdee47`|
|`vercel-composition-patterns`|`vercel-labs/agent-skills/skills/composition-patterns/SKILL.md`|`e38e0eaa609316b10423a9a138ed95e35099accd3f735585295c8a8f165c28a3`|
|`vercel-react-best-practices`|`vercel-labs/agent-skills/skills/react-best-practices/SKILL.md`|`71ed7794962fa6e803ee83030517b5b93a9f70fbfeb431ec4535c5480a8d8355`|

Shared reference documents from Addy revision `8bfcc1f9916f2eea55494d8c1fa7f4bde867dd9e`:

|File|SHA256|
|---|---|
|`references/testing-patterns.md`|`8607fda9150f639eae3097d322399e08c5978cce56fe3a3c6291553cb89edf88`|
|`references/security-checklist.md`|`84ed77cbba419c95390618f857eca2fadfeec12f2e5d39af4cc1833b94d82a39`|
|`references/performance-checklist.md`|`ce946457d4a502846e781a2747cf6af50bcc79b1957a66dc024f50557e8982e1`|
|`references/observability-checklist.md`|`25fcd0e854596a5ab6f11b002050d245bf78f5ae0fcbd1df699b89451ae0e1e8`|
