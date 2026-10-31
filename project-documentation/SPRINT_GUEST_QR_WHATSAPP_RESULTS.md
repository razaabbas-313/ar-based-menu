# Sprint 4 — Guest menu, table QR and WhatsApp requests

Date: 2026-10-31. Status: implemented with local regression evidence; **not production, browser/device or PostgreSQL sign-off**.

## Delivered behavior

- **One menu, three owner-selected modes.** SIMPLE preserves all photo/description/price/category dishes and removes model URLs and 3D/AR controls. THREE_D adds available interactive models without an AR-launch action. AR adds the existing supported-device launch and phone handoff. Photo-only dishes remain visible in every mode. Model errors have a retry and photo fallback; automatic rotation respects reduced motion.
- **Legacy compatibility.** `/r/{slug}` and existing restaurant QR destinations remain unchanged. Migrated restaurants use an internal LEGACY preference that retains the highest currently allowed existing viewer mode until an owner selects a mode. A saved mode later disallowed by policy falls back to SIMPLE, without deleting models or the stored preference. Menu publication uses the actual `menuLive` response property, with the old client alias retained.
- **Owner screen:** Studio → **Tables & WhatsApp**. Owners choose an entitled mode, configure international order/waiter contacts, opt into requests, and edit a short waiter introduction. Blank separate waiter contact uses the order contact. Contact syntax is validated; ownership and WhatsApp registration are not verified.
- **Tables:** create, rename, disable/reactivate, rotate opaque identity, and preview/export QR cards through the existing PNG/SVG/print exporter. The list has 200-row keyset pages and Load more. Active usage and the effective limit are displayed. Creation/reactivation serialize on the restaurant row and enforce the existing entitlement engine's active-table quota. Disabling pauses the same identity; rotation permanently invalidates the previous identity. QR labels use neutral menu wording, including SIMPLE mode.
- **Guest context:** table links use `?table=<opaque identity>`, never a trusted user-supplied table number. Public context resolves an active table within its own restaurant. Invalid/revoked/restricted links allow menu browsing but disable ordering and waiter actions and offer the plain restaurant menu. AR handoff retains valid table context. Restaurant and table QR controls respect their corresponding entitlements.
- **Guest cart:** Add to request from any priced dish sheet, edit whole-number quantities 1–99, remove lines, add bounded per-dish/request notes, and review itemized current server prices. A floating return control brings a scrolling guest back to the request. At most 50 distinct dishes are accepted. Refresh plus an explicit Use current menu prices action handles repricing; the backend independently validates again.
- **Verified preparations:** server checks fresh account/menu visibility, owner settings, entitlements, table scope, completed/available dish ownership, prices and notes. Decimal totals come from database prices. Stale expected prices return a conflict rather than being silently accepted. A separate repeatable-read transaction supplies a coherent multi-line snapshot. Fractional and quoted quantities are rejected at JSON binding instead of Jackson coercing them.
- **Images:** guest reviews the verified summary, then separately generates paginated 1080×1500 PNG files. Every page carries restaurant name, theme accent, reference, page numbering and **Order Request — Not Yet Accepted**. The full document contains quantities, unit/line prices, estimated total, table/general context, notes and UTC preparation time. Text uses measured width in the real canvas path; long names/notes paginate without discarded lines. Rendering is local and sequential, without external image/font fetches; temporary SVG/preview URLs are revoked.
- **Guest-initiated sharing:** a separate click invokes native file sharing where supported; the guest chooses WhatsApp and the restaurant conversation. Completed handoff is explicitly not evidence of delivery or acceptance. Unsupported sharing, errors and cancellation retain the manual fallback: download every PNG, open the validated restaurant `wa.me` conversation, manually attach every image and press Send. The link supplies a short reference/context summary; it does not attach images.
- **Call Waiter:** server prepares a bounded plain-text assistance message using current validated restaurant/table context and configured contact. The UI previews it and offers an explicit Open WhatsApp link; the guest must Send. A venue link supports general assistance. A local cooldown and server preparation rate limits add friction without claiming delivery or preventing every remote request.

## Data, security and entitlement decisions

V16 is additive for both H2 and PostgreSQL. `restaurant_guest_settings` stores versioned mode/contact/opt-in preferences; `restaurant_tables` stores tenant-scoped unique normalized labels, active state, version, timestamps and QR identity/hash, with foreign keys and scoped indexes. Existing V1–V15 are unchanged. Populated V15 upgrade testing preserves accounts (including restrictions), assignments, overrides, versions and usage counters. New ordering/waiter opt-ins default false.

`qr.table`, `quota.tables`, `ordering.enabled` and `waiter.enabled` are now implemented registry capabilities, using the same plan/revision/override engine. Table usage is the actual active count; downgrades preserve existing data but stop forbidden additions/actions. Google Reviews and advanced analytics remain planned. Existing menu, venue QR, model and account restrictions remain enforced.

QR identities use 256 random bits encoded as 43 URL-safe characters, with a unique SHA-256 lookup. **They are public context capabilities, not authentication or proof of presence.** The owner-only table response can re-export the active identity. For that usability requirement, the database retains the token as well as its lookup hash; database readers can therefore reconstruct a public table link. Public context/preparation responses do not return raw tokens or internal table IDs. Request bodies keep table tokens out of backend API query strings. Frontend URL/access-log/referrer handling still requires deployment review; redact table queries in logging/analytics configuration where appropriate.

The smallest safe order implementation is **stateless verified preparation data**, not an accepted-order database. Returned immutable service maps/lists include a random reference and a ten-minute preparation window. No order records, guest-note retention, sent/received/accepted states, payment entities or durable delivery promises were introduced. A reference/image is not a verifiable signed receipt: a guest can edit or replay a downloaded image. Restaurant confirmation remains necessary; prices are not reserved. No server lookup/audit of sharing or idempotent order record is claimed.

Cart and notes use bounded session storage scoped to restaurant/table, with a two-hour inactivity expiration on active/reopened carts and tab-session storage lifetime. Clear request removes current cart data; old context entries expire logically when revisited or when the browser session ends. Guests are told to avoid sensitive personal details. Downloads and content intentionally handed to WhatsApp are controlled by the guest/device/provider afterward.

Public preparation limits are instance-local bounded Caffeine counters: context 60/minute, order preparation 10/minute, waiter preparation 3/minute, per resolved client IP/operation. IP keys are hashed in memory. Shared networks can share a limit; multi-instance/distributed enforcement is not provided. Requests do not authenticate physical presence or send messages from the server.

## API contracts

Owner routes use the authenticated restaurant identity, current-account guard and existing Spring Security; clients cannot select another tenant. Settings/table updates and rotations use `expectedVersion`; responses are `Cache-Control: no-store`.

| Method | Route | Purpose |
|---|---|---|
| GET / PUT | `/api/v1/studio/experience` | Read/save mode, contacts and opt-ins |
| GET | `/api/v1/studio/tables?after=<id>` | Tenant-scoped table page, up to 200 rows |
| POST | `/api/v1/studio/tables` | Create within active quota |
| PUT | `/api/v1/studio/tables/{id}` | Rename or activate/deactivate owned table |
| POST | `/api/v1/studio/tables/{id}/rotate` | Versioned identity rotation |
| POST | `/api/v1/public/context/{slug}` | Validate public table/action context |
| POST | `/api/v1/public/order-preparations/{slug}` | Return verified prepared summary |
| POST | `/api/v1/public/waiter-requests/{slug}` | Return prepared message/link, not send it |

The three public POST routes are specifically permitted by the existing security chain and return no-store data. No broad owner-route exemption was added. Model uploads still use the existing direct browser-to-S3 path.

## Actual verification

| Check | Evidence and result |
|---|---|
| Initial product TDD RED | Missing guest-service implementation failed compilation in `backend/target/sprint4-red.log`; then 35 focused integration tests passed |
| Quantity boundary RED | Two real MockMvc cases expected 400 but received 200 for `1.5` and `"2"`; `backend/target/sprint4-quantity-red.log` |
| Final full backend | **595 passed, 0 failures/errors/skips** — `mvn -o test`, `backend/target/sprint4-full-tests.log` |
| New backend coverage | 38 isolated transactional integration tests + 1 populated upgrade test + 14 HTTP/security contract cases |
| Frontend | **19 passed, 0 failures/skips** using `node --test frontend/tests/*.test.mjs`; 8 preserved Sprint 3 checks + 11 new checks; `frontend/node_modules/.cache/sprint4-frontend-tests.log` |
| Frontend production build | `npm run build` passed; `frontend/node_modules/.cache/sprint4-build.log` |
| Git whitespace | `git diff --check` passed |
| PostgreSQL | **Outstanding/blocked:** no explicitly confirmed disposable instance; no PostgreSQL concurrency or migration pass claimed |
| Browser/device | **Blocked:** the existing browser-origin approval was declined. It was respected; no alternate browser/native/CDP workaround was used |
| AWS/production | Not accessed, changed or tested |

Transactional integration covers mode selection/downgrades, legacy URLs and model-free dishes, opaque issuance/rotation, cross-restaurant and invalid context, concurrent creation at quota one, reactivation, >200 table pagination, current price/quantity/notes validation, unavailable/foreign dishes, contacts, waiter/general messages, account restriction/publication changes, entitlement overrides, immutable prepared summaries and preparation rate limits. The deterministic price race pauses the second line read while an independent real transaction changes both prices, and verifies the coherent earlier prepared total plus the committed new database prices afterward.

HTTP tests exercise the actual security chain, authenticated-tenant binding, exception mapping, anonymous owner-route denial, no-store headers, malformed JSON and strict quantity binding. They mock the service; actual persistence/account decisions are covered separately by isolated service integration tests.

Frontend checks exercise cart bounds, mode-specific rendered actions, loading/invalid context, client authentication/JSON contracts, complete escaped SVG layout, long/wide text pagination, validated fallback links, cancellation/unsupported share results, and sequential PNG-export orchestration/URL cleanup. React SSR and synthetic canvas/Image stubs are **not browser rendering or actual PNG pixel validation**. Actual exported raster appearance, optical QR scanning, device memory limits, native file sharing, WhatsApp attachment/send flow, keyboard/screen-reader interaction and iOS/Android AR remain unverified until approved browser/device testing.

All backend execution uses test profiles, isolated in-memory H2 databases, scheduling disabled, secret imports bypassed, synthetic AWS settings and mocked storage. No default-profile application was started. H2 concurrency evidence does not establish PostgreSQL locking behavior. New null/pagination cases were added during implementation, so no independent pre-fix RED result is claimed for those follow-ups.

## Review and remaining limitations

Report-only independent review covered correctness, security, tenant isolation, error handling, architecture and frontend state. Findings were fixed: null order entries now yield a controlled error; table listing paginates; preparation has a coherent transaction snapshot; refresh reloads guest context; stale asynchronous preparation/image/waiter results are discarded; previous-table waiter state clears; image wrapping uses measured widths. A final source review found no additional material blocker. The new owner screen also preserves the existing venue-QR export gate. Current sprint literal checks found no unexpected credential/private-key/JWT literals; synthetic fixtures remain explicitly synthetic. This is not a new repository-history publication audit.

Before rollout, use a confirmed disposable PostgreSQL database for V15→V16 upgrade, repeatable-read contention/failure handling and concurrent table/quota/override operations; then complete approved browser/mobile/accessibility/image/QR/AR/share tests. Deploy compatible schema/backend before enabling the new frontend/owner opt-ins. Never treat a QR identity, prepared reference or share promise as proof of presence, delivery or acceptance. No production deployment or migration was performed.

## Exact Sprint 4 files changed (30)

Paths relative to the repository root. Some were already uncommitted Sprint 3 files and receive additive Sprint 4 integration changes here; this list does not attribute all existing dirty work to Sprint 4.

```text
backend/src/main/java/com/atelier/media/dto/PublicMenuDto.java
backend/src/main/java/com/atelier/media/security/SecurityConfig.java
backend/src/main/java/com/atelier/media/service/PublicMenuService.java
backend/src/main/java/com/atelier/media/service/EntitlementService.java
backend/src/main/java/com/atelier/media/service/FeatureRegistry.java
backend/src/main/java/com/atelier/media/service/GuestExperienceService.java
backend/src/main/java/com/atelier/media/web/GuestExperienceController.java
backend/src/main/resources/db/migration/h2/V16__guest_modes_and_tables.sql
backend/src/main/resources/db/migration/postgresql/V16__guest_modes_and_tables.sql
backend/src/test/java/com/atelier/media/service/GuestExperienceIntegrationTest.java
backend/src/test/java/com/atelier/media/service/GuestExperienceMigrationTest.java
backend/src/test/java/com/atelier/media/web/GuestExperienceControllerTest.java
frontend/src/api/client.js
frontend/src/components/ArHandoffModal.jsx
frontend/src/components/ModelStage.jsx
frontend/src/components/RestaurantQrModal.jsx
frontend/src/components/guest/DishSheet.jsx
frontend/src/components/guest/FilterBar.jsx
frontend/src/components/guest/MenuHero.jsx
frontend/src/components/guest/GuestActions.jsx
frontend/src/components/studio/ExperienceManager.jsx
frontend/src/components/studio/OwnerPlanOverview.jsx
frontend/src/components/admin/RestaurantEntitlements.jsx
frontend/src/pages/GuestMenuPage.jsx
frontend/src/pages/StudioPage.jsx
frontend/src/utils/qrCard.js
frontend/src/utils/guestOrders.js
frontend/tests/guestComponents.test.mjs
frontend/tests/guestOrders.test.mjs
project-documentation/SPRINT_GUEST_QR_WHATSAPP_RESULTS.md
```

## Git and preservation

Sprint 3 was uncommitted at the start and remains preserved. Branch `atelier-ar-development`; HEAD `6c0c9335f2df6f415b04a0af5016ee2c55b8e63f` unchanged. Final status: **31 tracked modified files, 32 individual untracked files, 0 staged** (including both sprints and two pre-existing ZIP backups). No files were discarded, staged, committed or pushed. Prior unmodified Sprint 3 sources/report and both backup ZIP hashes match the saved checkpoint; overlapping files retain their earlier implementation with the integrations described above. Historical V1–V15, AGENTS.md, dependencies and production configuration remain unchanged in this sprint; A01–A03/OTP/B1–B5 fixes remain preserved. The full suite retains the earlier regression coverage; password reset remains disabled. Generated logs, test output, caches and builds are ignored.

Recommended next product sprint: model history with quota-safe replacements and proactive administrator model uploads, plus the approved Brand Studio, Google Reviews and actionable analytics features. Keep their entitlement labels accurate and begin with the outstanding approved-environment validation. No next sprint has been started.

Reference: the [W3C Web Share specification](https://www.w3.org/TR/web-share/) defines user-selected share targets, file sharing and capability checks; the implementation retains an explicit manual fallback and never infers restaurant delivery from API completion.
