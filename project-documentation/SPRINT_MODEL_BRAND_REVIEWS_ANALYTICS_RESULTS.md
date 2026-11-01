# Sprint 5 — Models, Brand Studio, Google Reviews and Analytics

2026-10-31. Implemented locally; **not production, PostgreSQL, browser/device or live AWS sign-off**. Baseline: `4c8f49c940c797199ede83dcdda315652d4d48b6`, branch `atelier-ar-development`. No commit or push in this sprint.

## Implemented screens and workflows

- **Owner → 3D Models:** searchable all/with/without-model dish collection, active capacity/remaining slots, upload/replace through the existing model picker and direct browser-to-S3 helper, concierge creation requests, current/pending status, paginated version/provenance inspection and concierge request history. Photo-only dishes remain present. Legacy model-only dishes retain the existing requirement to add a photo before owner replacement.
- **Admin → Restaurant drawer → Models:** select a managed restaurant and its dish, proactively upload/replace without creating a concierge request, inspect model lineage and continue the existing human concierge fulfillment flow. The new explicit restaurant/dish routes validate the pair and reuse `ModelRequestService`; they are not a duplicate upload pipeline. Admin publication still enforces current account, target availability and model capacity.
- **Brand Studio:** seven curated selections (including preserved legacy appearance), validated colors, existing approved serif/sans font stacks, card/list layouts, card/category/button treatments and reduced/subtle/standard motion. Draft editing, Save draft, Publish saved draft and Discard draft have independent, optimistic versions and clear status. Preview uses the same guest renderer through a React portal in a script-free blank iframe with mobile/desktop CSS viewports. Preview guest requests/analytics are disabled; links do not navigate away from the studio. No production framing/CSP headers were weakened.
- **Logo/hero drafts:** the existing image optimizer and direct-upload helper now support private draft issuance. Verification checks issued metadata and ETag, conditionally copies to a fresh private sealed key, and never PUT-signs that sealed key. Authenticated owner responses supply short-lived preview GETs. Public menu JSON supplies a publication-gated image broker URL; the broker checks current published selection, restaurant/menu visibility and policy before a short-lived no-store redirect. Uploading or saving a draft does not publish its images. Existing profile/contact/legacy-image controls remain available as clearly labeled immediate profile/fallback edits.
- **Guest menu:** published colors, typography, images, layout and treatments apply to both normal and holding renders; broker URLs resolve against the configured API origin. Logo/hero errors have a visual fallback. Menu modes, model-free dishes, `/r/{slug}`, table links, carts, native sharing/manual attachment and waiter handoffs remain in place. Closing a deep-linked dish preserves table context. Request-image preparation uses the effective published accent.
- **Tables & WhatsApp → Google Reviews:** owner enable/disable, destination validation, save/reload, plan explanation and destination preview. Guest footer presents a neutral “Review us on Google” action, independently of ratings or sentiment. There is no scraping, fabricated score, incentive, OAuth, paid Google API or review-submission claim.
- **Owner → Analytics / Admin → Platform insights:** actual V2 API counts, bounded inclusive UTC dates, visit trends, dish-detail rankings, active modes, general/table-link entry context, preparation/image/share step counts, definitions and empty/loading/error states. Premium breakdowns use the existing entitlement engine. Existing cumulative analytics remain a separately labeled legacy section; old scans/placements/visitor-conversion wording was corrected.

## Model history and retention

V17 adds tenant/dish-scoped `model_versions` and a model-upload issuance ledger. New versions allocate monotonically under the restaurant publication lock and retain key/reference, metadata size, origin, publishing actor, known issuing actor/time, optional real concierge request, observation/publication time, assignment state, availability and retention disposition. Owner/admin confirmations check the issued actor; a different actor cannot claim another actor's pending upload.

Origins are RESTAURANT_UPLOAD, ADMIN_DIRECT, CONCIERGE and LEGACY_UNKNOWN. Support sessions without a recoverable individual actor ID are explicitly unknown rather than attributed to an invented owner/admin identity. Direct admin publication no longer fabricates completed concierge rows. A real active request is linked and completed when fulfilled.

Backfill observes only existing current attached models and legacy model-only files. It uses LEGACY_UNKNOWN, null historical actor/publication/issuance provenance and UNVERIFIED availability. It cannot reconstruct files deleted earlier or invent a complete pre-V17 version timeline. Existing concierge history is preserved.

Superseded/detached model references remain **HELD indefinitely** until a separate retention/purge policy is approved. The durable cleanup worker checks version references even when no current dish field points to the old key. There is no restore/purge endpoint or automatic deletion merely because of replacement. Active/pending slots retain the existing quota rules; a replacement does not consume a second active slot. Known retained-version counts/metadata bytes are separate from active slots. Existing restaurant storage counters are not a physical S3 inventory or billing measurement.

Model metadata is retained; this does **not** make existing model object bytes immutable. The established model upload path still has the B5 reusable-PUT/late-write and binary-validation limitations. The new private image sealing mechanism does not claim to repair every legacy model upload/finalization path.

## Draft publication, private images and policy

`restaurant_appearance` keeps versioned draft/published allowlisted JSON and published time. Saving is private to owner preview; publish revalidates the full schema, current account, assets and entitlement inside the common tenant transaction. Concurrent edits return a version conflict rather than overwriting silently. Discard restores the last published selection. Neither action changes menu-live status or dish availability.

Colors are six-digit hex only; text and accents must meet 4.5:1 contrast on background and surfaces. Fonts/layout/motion/card/category/button values are fixed choices. No arbitrary JS, CSS, HTML, external font URL or uncontrolled image reference is accepted. OS reduced motion wins; existing approved font resources/fallbacks are reused.

`brand_studio_assets` binds tenant, purpose, issued upload key, expected metadata, expiry and the sealed key. A durable DRAFT_SEAL_INTENT is committed **before** the detached SDK copy. Successful attachment locks/fences that intent and marks it HELD in the same transaction as verification/accounting. Claimed/completed cleanup prevents late attachment. Failed/abandoned copies retain cleanup evidence; original upload keys are retired through the existing grace/retry mechanism. Expired unused issuances become durable cleanup work. Verification charges confirmed metadata once, has a bounded local 10-seal/minute tenant budget, and caps active/retained studio assets at 100 rather than evicting files unsafely.

Copies remain within the configured bucket/private managed prefix, use source ETag conditions and replacement `private, no-store` metadata, set no ACL and have bounded SDK request/attempt timeouts. No copy, upload, GET or delete was executed against AWS during this sprint. Real deployment still needs the existing same-account private GET/PUT permissions; explicit Deny, KMS, bucket and cross-account assumptions remain unverified. No policy or production configuration was changed.

`branding.enabled` remains the main customization gate. New `branding.premiumOptions` permits/restricts curated advanced options through the existing plan/revision/override interfaces. Downgrades retain saved documents and mask disallowed custom appearance to the original legacy configuration. `reviews.enabled` and `analytics.advanced` are now implemented capabilities, with actual backend/UI behavior; they still default off. Existing model and table/menu policy rules remain.

Google review destinations accept only these exact HTTPS shapes: `g.page/r/<id>/review` and `search.google.com/local/writereview?placeid=<id>`. Userinfo, ports, fragments, deceptive hosts, redirect parameters, duplicate/unknown query parameters and unsupported shapes are rejected without server dereferencing. Venue ownership and the real destination are not verified through Google. Already disclosed external links cannot be revoked by this application.

## Analytics correctness and definitions

Legacy MENU_VIEW / MODEL_3D_VIEW / AR_LAUNCH retain their historical definitions. Writes now complete synchronously in one short transaction with explicit tenant locking, durable hashed 30-second deduplication and observable failure/rollback. Failed writes do not poison retry deduplication. Suspended/private restaurants and unavailable/foreign dishes are rejected. The unmanaged executor/ignored-future write path is removed; constructor compatibility is retained. Exact concurrent raw/counter/daily reconciliation passes in H2. The earlier hypothesis of legacy lost daily increments was **not reproduced**; no unproven lost-update repair is claimed.

V2 uses a separate `guest_insight_events` table and received-UTC day. A tenant/event UUID uniqueness constraint deduplicates retries atomically. It never silently converts legacy counters into new metrics. Ingestion resolves actual mode and valid table context, checks applicable current capabilities and dish ownership/availability, and restricts category keys to owned active IDs or fixed enum buckets. Initial slug lookup is detached so the authoritative locked read cannot reuse a stale ACTIVE entity after concurrent suspension. Reports use a coherent repeatable-read snapshot; the entitlement reader joins an already coherent snapshot instead of requiring another connection.

| Event | Meaning / producer |
|---|---|
| MENU_VISIT | Client reports successful menu display; not a unique person or verified scan |
| DISH_DETAIL_VIEW | Client reports a dish detail opening, independent of model actions |
| CATEGORY_SELECTED | Explicit category selection, not scroll observation |
| MODEL_VIEW_READY | Client reports model ready and visible after user action; hidden preload excluded |
| AR_ATTEMPT | AR launch requested; successful placement is not established |
| CART_ADD | Add-to-request actions; not units sold or revenue |
| ORDER_PREPARED | Server validated a request summary; not acceptance or delivery |
| IMAGE_GENERATED | Client reports generated image files |
| SHARE_FLOW_OPENED | Client share/open-conversation attempt; provider opening/send unconfirmed |
| SHARE_REPORTED_COMPLETED | Native promise resolved; handoff only |
| WAITER_PREPARED | Server prepared a valid assistance message/link |
| WAITER_CHAT_OPENED | Client clicked the prepared conversation link; response unconfirmed |
| REVIEW_LINK_OPENED | Client invoked the configured destination; not a submitted review |

ORDER_PREPARED and WAITER_PREPARED cannot be submitted through the client event allowlist. Server observations contain no guest notes, phone numbers, amounts or table tokens. Client events remain untrusted observations; browser failures/blocking can lose telemetry. No browser visitor/session identifier, fingerprint, cookie or raw IP is stored for reporting. Ephemeral abuse budgets and a short-lived hashed legacy dedup key do not establish unique visitor counts.

The V2 report window is the last 90 received-UTC days. An hourly job drains expired rows and expired dedup keys in short 1,000-row transactions; it does not stop after one batch. Scheduling/database failure can delay physical deletion and surfaces through the scheduler; the API always bounds the logical reporting window. No 13-month aggregate archive or new purge of historical V1 data is claimed. Preview/admin-owner guest observations are excluded by the client workflow, not a cryptographic proof of visitor authenticity.

The request activity panel shows independent step counts within the same date window, **not a linked visitor/request cohort conversion rate**. No paid conversion, sales, delivery, restaurant acceptance, physical presence or AR placement metric is invented.

## API endpoints

All protected endpoints use existing bearer-role/audience and current-account patterns; writes recheck policy/ownership transactionally. Responses, including image redirects, are no-store.

| Method | Route |
|---|---|
| GET | `/api/v1/studio/appearance` |
| PUT | `/api/v1/studio/appearance/draft` |
| POST | `/api/v1/studio/appearance/publish`, `/api/v1/studio/appearance/discard` |
| POST | `/api/v1/studio/appearance/assets/{purpose}/presign`, `/api/v1/studio/appearance/assets/{id}/confirm` |
| GET / PUT | `/api/v1/studio/reviews` |
| GET | `/api/v1/public/appearance/{slug}` |
| GET | `/api/v1/public/brand-assets/{id}` — gated 302 to short-lived private GET |
| GET | `/api/v1/studio/dishes/{dishId}/model-history?after=<id>` |
| GET | `/api/v1/admin/restaurants/{restaurantId}/dishes/{dishId}/model-history?after=<id>` |
| POST | `/api/v1/admin/restaurants/{restaurantId}/dishes/{dishId}/model/presign`, `/model/confirm` |
| POST | `/api/v1/public/insights/event` |
| GET | `/api/v1/studio/insights?from=YYYY-MM-DD&to=YYYY-MM-DD` |
| GET | `/api/v1/admin/insights?from=YYYY-MM-DD&to=YYYY-MM-DD` |

Existing owner attachment, concierge, admin publication and request-history routes remain. Model history uses 100-row keyset pages. Dates are inclusive UTC and bounded to 90 days; basic owner totals/trends remain available while advanced dish/context/mode breakdowns require policy. Platform reports require current admin authorization.

## Database and actual verification

New additive migration in both dialects: `V17__model_versions_brand_reviews_insights.sql`. Historical V1–V16 are unchanged. It creates model lineage/issuance, appearance, review, private asset and V2 event/dedup structures with relevant uniqueness and tenant/date/key indexes. Populated V16→V17 H2 testing preserves accounts, branding, plan policy/overrides, guest mode/table context, dish assets and legacy analytics, and checks truthful legacy backfill. No production migration was run.

- **Full backend:** **719 tests passed, 0 failures, 0 errors, 0 skipped** (5 min 29 sec). Command: `mvn -o test` from backend with isolated test configuration. Evidence: ignored `backend/target/sprint5-full-tests.log` and Surefire reports.
- **New backend coverage:** 124 expanded cases across AppearanceContrastTest (1), AnalyticsReliabilityIntegrationTest (10), GuestInsightIntegrationTest (17), ModelVersionIntegrationTest (17), ProductStudioIntegrationTest (28), ProductStudioMigrationTest (1), S3PrivateSealTest (13), ProductStudioControllerTest (22) and ProductInsightControllerTest (15).
- **Frontend:** 27 checks passed, 0 failures/skips — `node --test frontend/tests/*.test.mjs`. Covers API/auth/version contracts, theme sanitization, split-origin image/layout projection, product loading/empty/quota states and script-free preview markup, alongside preserved Sprint 3/4 tests. Evidence: ignored `frontend/node_modules/.cache/sprint5-frontend-tests.log`.
- **Production build:** passed — `npm run build`, 1,824 modules; existing model-viewer remains lazy and its bundle is unchanged. No package/lockfile or dependency installation changes. Evidence: ignored `frontend/node_modules/.cache/sprint5-build.log`.
- **Whitespace/source inspection:** `git diff --check` passed; changed-source literal checks found no unexpected credential/private-key/JWT material. This is a scoped source check, not a new full-history publication audit.
- **Report-only review:** independent review findings were fixed: private draft exposure, reusable PUT mutation of draft image identity, missing sealed-copy intent, stale image origin/layout projection, cleanup backlog throughput and stale tenant state. Final targeted source review reported no further material blockers. No mutation experiments were performed by the reviewer.

Behavioral RED evidence includes queued acknowledgement before persistence, swallowed SQL failure and retry suppression, retained model/brand deletion despite history, missing/private draft hooks, missing seal intent/fencing, insufficient accent contrast and concurrent suspension accepted through a cached ACTIVE entity. The abrupt-copy Error test demonstrates a committed intent; it is **not an actual process-kill experiment**. SDK builder typing and the upgrade fixture's existing completed-at constraint were test-fixture corrections, not application defects.

Two old B5 direct-publication assertions intentionally moved from fabricated concierge rows to model-version rows. Their one-success concurrent publication, supersession and exact usage-accounting checks remain. No tests were disabled or skipped to pass verification.

Every database execution used isolated H2/test profiles, optional secret imports disabled, synthetic AWS settings and mocked storage/SDK operations; background scheduling was disabled for test contexts. No default production-profile app, unknown PostgreSQL instance, real AWS resource or customer data was accessed.

## Remaining verification and limitations

1. **PostgreSQL outstanding/blocked:** no explicitly confirmed disposable instance. V17 upgrade, uniqueness, concurrent publication/overrides, isolation and query plans need real PostgreSQL verification. H2 results are not PostgreSQL proof.
2. **Browser/device blocked:** the existing declined local-origin approval remains respected. No alternate browser/native/CDP bypass was attempted. Portal viewport/CSP behavior, image pixels, responsive controls, keyboard/screen-reader flows, real share/WhatsApp interactions, optical QR scans and iOS/Android AR have not been validated in a browser/device.
3. **AWS unverified:** private-prefix permissions, conditional copying, KMS/cross-account assumptions, no-store behavior and cleanup outcomes need an approved disposable environment. Previously issued private GETs can remain valid for up to 15 minutes after publication/policy changes; they cannot be instantly recalled. No deployed privacy guarantee is inferred from mock SDK tests.
4. **Model files:** metadata-only publication checks remain; no GLB/image byte-inspection worker, antivirus, restoration UI, purge policy or immutable/version-pinned model object archive was introduced. Existing model PUT TTL/late-write and public-model access limitations remain. Already missing legacy objects cannot be recovered.
5. **Retention/accounting:** model versions and verified studio assets are conservatively held; discarded/unselected verified draft assets are not automatically purged. The 100-asset safety cap requires retention review when reached. Known metadata counters are not actual S3 bytes or monetary quotas. Legacy aggregate/raw retention was not silently rewritten.
6. **Analytics:** client observations can be forged/missing and IP budgets are instance-local/shared-network-sensitive. There is no unique-person measurement, linked cohort funnel, provider delivery status, review completion, AR placement or sales metric. Actual production latency, load behavior and Core Web Vitals remain unmeasured.

Recommended next priority: premium landing-page development and complete guest/admin polish, followed by approved mobile/real-device testing, confirmed-disposable PostgreSQL readiness and end-to-end acceptance. No next sprint, deployment, dependency installation, commit or push was started.

References: [Google Business Profile review-link guidance](https://support.google.com/business/answer/3474122?hl=en) supports neutral external review links; [AWS CopyObject documentation](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CopyObject.html) describes source-read/destination-write permissions, conditional copy and additional KMS requirements. These references do not establish this deployment's live behavior.

## Exact files changed and Git status

Sprint 5 changes **54 files**. Paths are relative to the repository root.

```text
backend/src/main/java/com/atelier/media/security/SecurityConfig.java
backend/src/main/java/com/atelier/media/service/AnalyticsService.java
backend/src/main/java/com/atelier/media/service/AssetCleanupListener.java
backend/src/main/java/com/atelier/media/service/BrandDraftAssets.java
backend/src/main/java/com/atelier/media/service/DishService.java
backend/src/main/java/com/atelier/media/service/DurableCleanup.java
backend/src/main/java/com/atelier/media/service/EntitlementService.java
backend/src/main/java/com/atelier/media/service/FeatureRegistry.java
backend/src/main/java/com/atelier/media/service/GuestExperienceService.java
backend/src/main/java/com/atelier/media/service/GuestInsightService.java
backend/src/main/java/com/atelier/media/service/ModelHistoryService.java
backend/src/main/java/com/atelier/media/service/ModelRequestService.java
backend/src/main/java/com/atelier/media/service/ProductStudioService.java
backend/src/main/java/com/atelier/media/service/S3Storage.java
backend/src/main/java/com/atelier/media/web/BrandDraftAssetController.java
backend/src/main/java/com/atelier/media/web/ProductInsightsController.java
backend/src/main/java/com/atelier/media/web/ProductStudioController.java
backend/src/main/resources/db/migration/h2/V17__model_versions_brand_reviews_insights.sql
backend/src/main/resources/db/migration/postgresql/V17__model_versions_brand_reviews_insights.sql
backend/src/test/java/com/atelier/media/service/AnalyticsReliabilityIntegrationTest.java
backend/src/test/java/com/atelier/media/service/AnalyticsServiceTest.java
backend/src/test/java/com/atelier/media/service/AppearanceContrastTest.java
backend/src/test/java/com/atelier/media/service/GuestInsightIntegrationTest.java
backend/src/test/java/com/atelier/media/service/ModelRequestTransactionSafetyTest.java
backend/src/test/java/com/atelier/media/service/ModelVersionIntegrationTest.java
backend/src/test/java/com/atelier/media/service/ProductStudioIntegrationTest.java
backend/src/test/java/com/atelier/media/service/ProductStudioMigrationTest.java
backend/src/test/java/com/atelier/media/service/S3PrivateSealTest.java
backend/src/test/java/com/atelier/media/web/ProductInsightControllerTest.java
backend/src/test/java/com/atelier/media/web/ProductStudioControllerTest.java
frontend/src/api/client.js
frontend/src/components/admin/RestaurantDrawer.jsx
frontend/src/components/guest/CategoryNav.jsx
frontend/src/components/guest/DishSheet.jsx
frontend/src/components/guest/GuestActions.jsx
frontend/src/components/guest/MenuHero.jsx
frontend/src/components/studio/BrandPreview.jsx
frontend/src/components/studio/BrandStudio.jsx
frontend/src/components/studio/ExperienceManager.jsx
frontend/src/components/studio/InsightDashboard.jsx
frontend/src/components/studio/ModelModal.jsx
frontend/src/components/studio/ModelsManager.jsx
frontend/src/components/studio/ReviewSettings.jsx
frontend/src/components/studio/StudioAnalytics.jsx
frontend/src/index.css
frontend/src/pages/AdminPage.jsx
frontend/src/pages/GuestMenuPage.jsx
frontend/src/pages/StudioPage.jsx
frontend/src/utils/appearance.js
frontend/src/utils/insights.js
frontend/src/utils/upload.js
frontend/tests/appearance.test.mjs
frontend/tests/productStudio.test.mjs
project-documentation/SPRINT_MODEL_BRAND_REVIEWS_ANALYTICS_RESULTS.md
```

Branch atelier-ar-development; HEAD 4c8f49c940c797199ede83dcdda315652d4d48b6 unchanged. **26 tracked modified, 30 individual untracked, 0 staged**. This includes the two untouched local backups product-evolution.zip and project-documentation.zip. Their hashes match the pre-sprint checkpoint. No source was reset, discarded, staged, committed or pushed. AGENTS.md, historical migrations, production configuration, dependencies/lockfiles and prior sprint reports remain unchanged. Build logs, caches and outputs remain ignored.
