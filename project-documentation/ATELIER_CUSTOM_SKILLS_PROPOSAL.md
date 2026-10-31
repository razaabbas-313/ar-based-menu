# Atelier AR custom skills — exact proposed instructions

Status: **PROPOSAL ONLY — NOT CREATED OR INSTALLED**. Prepared 2026-10-31 after approval of the eight global engineering skills. That approval does not authorize these four custom skills or application changes.

Each fenced SKILL.md and openai.yaml below is the complete proposed file content, not an installed file. No scripts, hooks, external tools, dependencies, additional skill references, or generated project memory are proposed. Once approved, create only the eight files listed below under the actual nested Git root. Relative repository paths in the proposed instructions are resolved against that root, not the skill directory.

Proposed files:

- `.agents/skills/atelier-tenant-security/SKILL.md`
- `.agents/skills/atelier-tenant-security/agents/openai.yaml`
- `.agents/skills/atelier-analytics-verification/SKILL.md`
- `.agents/skills/atelier-analytics-verification/agents/openai.yaml`
- `.agents/skills/atelier-guest-experience/SKILL.md`
- `.agents/skills/atelier-guest-experience/agents/openai.yaml`
- `.agents/skills/atelier-brand-studio/SKILL.md`
- `.agents/skills/atelier-brand-studio/agents/openai.yaml`

Use the nested repository `atelier-3d-media-engine` as Codex's project root. Do not create duplicate global copies. Explicit-only metadata is invocation guidance, not an operating-system permission boundary. Approval to create these instruction files would not authorize implementation of the application findings they describe.

## 1. atelier-tenant-security

Exact proposed SKILL.md:

```markdown
---
name: atelier-tenant-security
description: Review or implement explicitly scoped Atelier AR authorization changes involving admin or studio accounts, token revocation, tenant-owned resources, impersonation, password setup, or private media. Use for tenant-security reviews and regression design; not for unrelated UI polish.
---

# Atelier tenant security

Locate the Git root containing frontend/, backend/ and AGENTS.md. Read its instructions and the current task authorization. Read project-documentation/SECURITY_DOCUMENTATION.md and the relevant current source; consult TECHNICAL_AUDIT.md as historical evidence. Previously authorized work remains authorized, but a roadmap or old audit is not implementation permission.

For a review or investigation, report only. Do not edit files, invert conditions, run auto-fixes, change configuration, create fixtures in live systems, or start services. For an authorized implementation, change only the approved behavior and its necessary regression tests. Preserve existing uncommitted work. Do not commit, push, reset or restore user files without authorization.

## Trace the boundary

Inspect the relevant controller, DTO, service, repository, security principal and response mapper. Start with backend/src/main/java/com/atelier/media/:

- web/AdminController.java and web/StudioController.java.
- service/AdminAccess.java, RestaurantAccess.java, AdminAuthService.java and AuthService.java.
- security/SecurityConfig.java, Tenant.java, AdminPrincipal.java and TokenService.java.
- DishService, MediaService, ModelRequestService, BrandingService and the repositories they actually call.

Do not infer authorization from UI visibility, a valid JWT signature, audit attribution, or an entity foreign key alone. Verify that the current account and resource checks run on every applicable request path, including reads, preview, categories, menu status, analytics, concierge and upload confirmation.

## Preserve the existing invariants

- Derive restaurant identity from verified claims. Never trust a request-supplied restaurant ID or slug as ownership authority.
- Validate current admin existence, usable role/account and token version for every admin operation.
- Validate current restaurant existence, token version, deletion/status and studio password-setup restrictions for relevant studio operations. Preserve explicitly intended account-level password-change access and authenticated admin impersonation semantics; inspect their code before changing exceptions.
- Scope dish, category and pending-key access to the authenticated restaurant. Reject a foreign assignment before persistence and suppress foreign names in mapped responses.
- Keep public guest DTOs free of account data, internal notes, private keys and private reference URLs. Preserve separate private/public S3 access and pending replacement confirmation.
- Preserve default-deny routes, role/audience separation, current security headers, validation, locking and usage accounting. Do not migrate auth storage, change token lifetimes, add RLS, or alter AWS policies as an incidental fix.

The current working tree contains A01-A03 remediation. Verify its actual state and regression coverage rather than reimplementing the historical audit wording. New controllers or internal service entry points must preserve equivalent checks.

## Verification and output

For each affected route, identify principal source, current-account gate, ownership finder, permitted exception, rejection status and possible side effects. Report findings with source references, impact and a minimal proposed remedy.

For approved implementation, follow the existing JUnit/Mockito/MVC conventions. Inspect the test profile and environment before Maven execution. Add relevant negative cases for revoked/missing admin, revoked/suspended/deleted/missing restaurant, forced password setup, foreign category/dish/key and role/audience confusion. Verify rejected operations produce no forbidden writes. Reuse AccountAccessSecurityTest, TokenSecurityTest, CategoryIsolationTest and controller test patterns where applicable; do not assume mocks prove transactional rollback or PostgreSQL constraints.

Use only confirmed disposable resources. No production DB, S3 cleanup, real OTP delivery, destructive SQL or live account actions. Preserve Flyway history and direct browser-to-S3 uploads. Never expose tokens, passwords, OTPs, hashes, secrets or presigned URLs. State exactly which checks ran, their results and remaining limits; source review alone does not prove runtime behavior.
```

Exact proposed agents/openai.yaml:

```yaml
interface:
  display_name: "Atelier Tenant Security"
  short_description: "Verify account gates and restaurant isolation"
  default_prompt: "Use $atelier-tenant-security to review the requested authorization paths and report findings only; do not edit files, run mutation experiments, or access production services."
policy:
  allow_implicit_invocation: false
```

## 2. atelier-analytics-verification

Exact proposed SKILL.md:

```markdown
---
name: atelier-analytics-verification
description: Trace Atelier AR guest events through validation, persistence, aggregates and dashboard labels to verify metric meaning, deduplication, concurrency and failure handling. Use for existing analytics contracts and their regressions; not as permission to add tracking or vendors.
---

# Atelier analytics verification

Locate the nested Git root. Read AGENTS.md, the current task, project-documentation/FEATURE_INVENTORY.md and the analytics-related security/roadmap findings. Verify current source rather than assuming historical findings remain unresolved. A report-only request authorizes no file edits, synthetic production traffic, database actions, telemetry changes or service startup. Implement changes only within an explicitly authorized task and preserve existing work.

## Establish the event contract

Inspect frontend/src/pages/GuestMenuPage.jsx, components/guest/DishSheet.jsx, components/studio/StudioAnalytics.jsx, components/admin/KpiCards.jsx and api/client.js. Trace the matching backend controller/DTO through backend/src/main/java/com/atelier/media/service/AnalyticsService.java, model/AnalyticsEventType.java, AnalyticsEvent/AnalyticsDaily, and their repositories. Inspect the V10 analytics migrations in both dialect folders and existing AnalyticsServiceTest.

Build an event dictionary from current code: user action, producer, event type, slug/dish identity, validation, throttle, deduplication key/window, async boundary, raw event, counter, daily aggregate, query, denominator and displayed label. Identify whether each value is cumulative or time-bounded and whether deleted tenants/dishes are included.

Do not confuse request acceptance, durable persistence, unique people, launch intent, native AR success or business conversion. At the reviewed baseline, MENU_VIEW is emitted without dishId after menu loading; MODEL_3D_VIEW follows the 3D reveal action; AR_LAUNCH is emitted before AR success/fallback is known. Reconfirm these triggers before relying on them. Dish popularity derived from viewsCount must have a matching producer; do not fabricate one or relabel a metric silently.

## Inspect correctness under failure

- Validate allowed event types and restaurant/dish ownership; inspect null dish IDs, unavailable/deleted data and public-event spoofability.
- Check retries, repeated clicks, reopening, effects, IP throttling and deduplication. A deduplication key is not a unique-person identifier.
- Trace executor rejection, background exceptions, transaction rollback and partial writes. An HTTP success does not by itself prove the async transaction committed.
- Examine daily increments for lost updates/first-row races and counters for consistent tenant scope. Inspect actual UTC/day rules instead of assuming the browser timezone.
- Check dashboard numerator/denominator and top-dish sort inputs against their producers. Report zero-denominator and missing-data behavior.
- Keep operational observability separate from product metric accuracy. Do not add PII, vendors, collection events, retention policies or consent requirements as invented product features.

## Verification and output

For a review, return the event dictionary, evidence-backed mismatches, impact, minimal proposed fixes and verification gaps. Do not mutate source to prove a finding during a read-only review.

For an approved implementation, create deterministic fixtures with expected raw/counter/daily/dashboard outcomes. Include relevant duplicate/retry, invalid type, foreign dish, null ID, day boundary, concurrent increment and async failure cases. Use real transaction/concurrency tests where required; label mocked or synchronous-executor tests accurately. Inspect effective datasource/test settings and use only disposable resources. Preserve existing API/error contracts unless the approved task changes them.

Browser evidence may show emitted requests; database assertions establish persistence; native device evidence establishes AR outcomes. Label fixtures, connected-runtime checks and inferred behavior separately. Never claim unique guests, successful AR sessions, conversion uplift or durable counts without corresponding evidence. Never expose real credentials, JWTs, presigned URLs, raw IPs or private tenant data in reports and artifacts. Do not commit or push without authorization.
```

Exact proposed agents/openai.yaml:

```yaml
interface:
  display_name: "Atelier Analytics Verification"
  short_description: "Trace events into trustworthy dashboard metrics"
  default_prompt: "Use $atelier-analytics-verification to trace the requested event-to-dashboard contract and report evidence and gaps only; do not edit files, generate traffic, or connect to a database."
policy:
  allow_implicit_invocation: false
```

## 3. atelier-guest-experience

Exact proposed SKILL.md:

```markdown
---
name: atelier-guest-experience
description: Review or implement scoped Atelier AR guest-menu, dish-sheet, QR entry, responsive behavior, 3D loading and AR fallback changes while preserving the existing photo-first experience and model-viewer integration. Use for guest acceptance workflows; not for a wholesale redesign.
---

# Atelier guest experience

Locate the nested Git root and read AGENTS.md and the approved task. Consult project-documentation/FEATURE_INVENTORY.md, API_REFERENCE.md and the relevant roadmap findings, then inspect current source. For a review, report only: do not edit, start services, install browser tooling, submit forms, or mutate backend data. Browser checks require an approved target and permitted actions. Implement only explicitly approved application changes.

## Follow the existing journey

Trace frontend/src/pages/GuestMenuPage.jsx, components/guest/GuestDishCard.jsx, DishSheet.jsx, CategoryNav.jsx and FilterBar.jsx; components/ModelStage.jsx, ArHandoffModal.jsx and RestaurantQrModal.jsx; utils/router.js, modelViewer.js, qrCard.js and media.js; and api/client.js. Follow relevant backend PublicMenuController/PublicMenuService, public DTOs and mapping. Preserve /r/{slug}, printed QR compatibility, public/preview separation and existing photo-only dishes.

Verify actual DTO fields for menu visibility, branding, categories, price and model availability. Treat menuLive/isMenuLive and historical audit findings as contracts to inspect, not permission to repair unrelated behavior. Do not invent filters, orders, table tracking or dietary semantics that the existing code does not implement.

## Preserve React and AR boundaries

- Use React 18/JavaScript JSX and Vite with the existing router, API client, shared UI and theme utilities. Exclude Next.js/RSC and React 19 APIs; add no competing state/rendering library.
- Keep the lazy model-viewer import and its retry/error behavior. Preserve the React 18 addEventListener/removeEventListener bridge, imperative boolean attributes, loading/progress callbacks, user-interaction handling and viewer refs.
- Preserve current AR modes, placement/scale metadata and fallback flow unless the task explicitly changes them. A launch button or activateAR call does not prove a native AR session succeeded.
- Verify loading, model failure, slow network, photo-only, empty menu, unavailable dish and unsupported-WebGL/AR cases. Do not hide a usable photo menu behind a failed 3D dependency.

## Responsive and accessible acceptance

Check representative narrow/mobile, tablet and desktop layouts; record actual viewport sizes and zoom rather than claiming a universal device guarantee. Include long dish/restaurant names, prices, categories and empty results. Check horizontal overflow, scrolling inside the dish sheet, safe areas, touch targets, keyboard/focus restoration, Escape/close behavior, labels, contrast and reduced motion as relevant to the change.

Use existing approved browser tools only when available. Clearly mark fixture/API mocks versus a connected backend. Never install Playwright or start Spring Boot merely because a skill was invoked; inspect the environment and obtain the required task authorization first. Default backend startup may run migrations/bootstrap writes.

## Evidence and completion

For a review, report source-backed risks and observed defects, reproduction steps, severity and minimal proposed fixes. For approved implementation, preserve visual identity and reusable components, run relevant available checks and capture scoped screenshots/console/network evidence where permitted. No frontend test/lint script should be reported as passed unless it actually exists and ran.

Real iOS Safari/Quick Look and Android WebXR/Scene Viewer on a confirmed HTTPS nonproduction origin are required for native handoff, camera placement and physical-scale claims. Desktop emulation can verify layouts and fallbacks, not those capabilities. State untested devices, flows and environment limits. Do not expose private asset URLs or credentials, change AWS/headers/configuration incidentally, or commit/push without authorization.
```

Exact proposed agents/openai.yaml:

```yaml
interface:
  display_name: "Atelier Guest Experience"
  short_description: "Verify responsive menus, QR and AR fallbacks"
  default_prompt: "Use $atelier-guest-experience to review the requested guest flow and responsive behavior; report only, preserve the existing design, and do not start services or install browser dependencies."
policy:
  allow_implicit_invocation: false
```

## 4. atelier-brand-studio

Exact proposed SKILL.md:

```markdown
---
name: atelier-brand-studio
description: Review or implement scoped Atelier AR branding workflows for restaurant accents, layouts, currency, logo/banner uploads, guest previews and QR exports. Use to verify saved settings and visual consistency; not to redesign the entire studio or invent branding capabilities.
---

# Atelier brand studio

Locate the nested Git root. Read AGENTS.md, the task authorization and relevant project-documentation API/feature/architecture sections. Inspect actual code before relying on documentation. Reviews are report-only: no edits, settings saves, uploads, browser form submission, service startup or cloud/database actions. An approved implementation authorizes only its specified changes; preserve existing security fixes and other user work.

## Trace settings through the system

Start with frontend/src/components/studio/BrandCustomizer.jsx, pages/StudioPage.jsx, pages/GuestMenuPage.jsx, components/RestaurantQrModal.jsx, utils/theme.js, upload.js, qrCard.js and api/client.js. Inspect backend/src/main/java/com/atelier/media/service/BrandingService.java, dto/StudioSettingsUpdateDto.java, studio settings endpoints, Restaurant fields and public branding mapping. Follow cache invalidation and actual public/preview consumers rather than treating local preview state as persistence.

Record current allowed accents, layouts, currencies, defaults, validation and upload constraints from source. Do not invent additional options. Map form fields to DTO fields, tenant/current-account authorization, saved values, public DTO, guest rendering and QR/export output. Check relevant API names and error semantics before proposing a change.

## Preserve the existing architecture and identity

- Reuse React 18/Vite/JavaScript JSX, Tailwind/shared UI, theme utilities, API client and router. No framework, font, state library, brand copy or global redesign is implied.
- Preserve current RestaurantAccess and ownership checks. Do not let an editable restaurant ID or slug substitute for authenticated identity.
- Keep direct browser-to-S3 upload, pending replacement confirmation, old-media availability until success, usage accounting and public/private prefix separation. Never proxy binary uploads through Java or expose a private prefix to make a preview work.
- Preserve validated social/contact URLs, error states and existing layout/currency behavior. Do not treat a displayed preview or successful presign request as proof of saved settings or uploaded content.
- Retain /r/{slug} and printed QR compatibility. Do not change restaurant URL identity as an incidental visual adjustment.

## Verification and output

For a review, produce a field-to-render/save matrix, source references, observed inconsistencies, impact, minimal proposed fixes and missing runtime evidence. Do not mutate the application to prove a review finding.

For approved implementation, check each affected allowed/default value, validation rejection, save/reload behavior, failed save, asset replacement failure, missing image fallback, concurrent update where relevant, cache refresh and public/preview consistency. Test long names, currency display and representative mobile/desktop layouts; check keyboard controls, focus, contrast and loading/disabled states. Verify QR output still targets the same restaurant and distinguish generated content from a physically scanned test.

Use existing test/build commands and confirmed disposable resources only. New frontend test dependencies, real AWS uploads, production setting changes, schema/config changes and service startup need their own authorized scope. Never expose credentials, JWTs, private keys or presigned URLs. Report exactly which source/build/browser/API/persistence/export checks ran and which remain unverified. Do not commit or push without authorization.
```

Exact proposed agents/openai.yaml:

```yaml
interface:
  display_name: "Atelier Brand Studio"
  short_description: "Verify saved branding, previews and QR identity"
  default_prompt: "Use $atelier-brand-studio to review the requested branding-to-guest workflow and report gaps only; do not edit files, save settings, upload assets, or redesign the studio."
policy:
  allow_implicit_invocation: false
```

## Implementation and verification proposal after approval

1. Recheck AGENTS.md, current source paths, existing .agents directories and Git status. Preserve user work and reject conflicting destination files.
2. Create only the four proposed skill folders and the exact SKILL.md/openai.yaml contents above. No global duplicates, scripts or hooks; do not change AGENTS.md or application configuration.
3. Validate frontmatter, name/description, metadata and every source anchor against the current repository. Record any source drift and propose instruction adjustments before claiming those exact approved instructions were installed.
4. Confirm all four names, enabled states, project scope, paths and zero parse errors through local Codex skills/list from the nested root; verify explicit-only policy. Do not silently install at the outer workspace merely to resolve discovery.
5. Use report-only prompts on synthetic examples or source excerpts to check: foreign category ownership; AR_LAUNCH intent versus success; unavailable 3D fallback; preview versus saved brand state. Check that no source edit, server startup, dependency install, cloud/DB operation or native AR verification is implied by invocation.
6. Report installed hashes, paths, discovery results and limitations. Smoke checks establish instruction routing, not application security, analytics accuracy or device compatibility. No application tests need to run merely to validate Markdown installation.

Suggested approval granularity: approve any named subset, or request changes to the exact instruction blocks. No approval for these four is assumed from the global engineering installation.
