# Database and API change plan

Status: proposed schema/contracts only; no migrations or SQL executed. [Index](README.md).

## Current declarations

[Entities](../backend/src/main/java/com/atelier/media/model), [repositories](../backend/src/main/java/com/atelier/media/repository), [DTOs](../backend/src/main/java/com/atelier/media/dto), [controllers](../backend/src/main/java/com/atelier/media/web), [PostgreSQL migrations](../backend/src/main/resources/db/migration/postgresql), [H2 migrations](../backend/src/main/resources/db/migration/h2) and [database dictionary](../project-documentation/DATABASE_DOCUMENTATION.md) describe the existing metadata plane. Ten mapped entities cover restaurants, platform users, media, categories, audit, OTP, requests/pending keys and analytics event/daily data. V1–V13 exist per vendor; H2 V8 does not enforce PostgreSQL’s unique partial active-request index. Public DTO privacy and scalar tenant IDs require deliberate service checks.

## Proposed smallest schema by milestone

| Concept | Recommended persistence and integrity | Lifecycle/index |
|---|---|---|
| Feature registry/global values | Code-supported definitions; persisted global state only for known typed keys | Unknown keys rejected; config version; audited writes |
| Plans/revisions | plan_templates + immutable plan_revisions; typed validated revision values (bounded JSON or child values) | Unique plan/revision; active/visibility; no billing IDs |
| Assignment | One restaurant_plan_assignment per restaurant FK, pinned revision FK, config version | Unique restaurant; explicit migration history in audit |
| Overrides | restaurant_entitlement_overrides with key/type/value, actor/reason/start/end/version | Tenant/key lookup; nonoverlap/serialized replacement; expiry index |
| Quota usage/reservations | Reuse restaurant counters where correct; quota_reservations bound to operation/resource/bytes/expiry/state | Unique operation ID; positive amounts; tenant+state+expiry; lock tenant policy first |
| Mode/contact/reviews | Add selected mode and validated enable/channel/review/contact-verification-state fields or bounded settings object | Legacy-safe defaults; independent owner preference versus grants |
| Tables | restaurant_tables, tenant FK, label/zone/state, token hash/version/timestamps | Unique token hash; tenant/state; archive references rather than cascade history away |
| Order snapshots | order_requests + immutable bounded order_request_items, optional observations | Tenant/reference + idempotency uniqueness; payload hash; hashed read capability; created/expiry index |
| Issued uploads/model versions | Extend issued-upload tracking by origin; model_versions + current version pointer/projection | Tenant+expected dish composite integrity, dish/version unique, pending expiry; preserve old objects |
| Appearance/QR | restaurant_appearance draft/published schema-versioned JSON; bounded QR configs keyed tenant/template/context | One appearance row/tenant; optimistic version; no arbitrary HTML/CSS |
| Analytics | Extend raw schema/unique event ID and atomic aggregates; add integrity/retention as approved | Tenant/time/type/dish; unique dedup key; decide UTC daily versus hourly before schema |
| Audit/cleanup | Reuse audit; add structured config before/after/reason where necessary; durable cleanup operations if selected | Actor/target/time, bounded payload; pending retry index and truthful state |

No new table is required for a cart, payment, price transaction, separate menu per mode, automated model converter or WhatsApp session in the guest-share MVP. Persist only needed structured snapshot/history. Avoid turning every setting into a relational table or stuffing mutable business state into unvalidated JSON. JSON fields require a known schema and version; searchable/foreign-key/uniqueness fields remain typed columns.

Use bigint IDs consistent with current entities; timestamptz/Instant for instants; date only for declared report buckets; PostgreSQL numeric/Java BigDecimal for prices and monetary snapshots. Match current priceTag precision after code/schema review; snapshot currency code and rounding rules. Retain dish ID where present plus immutable dish-name/price snapshots; deletion/archive policy must preserve request meaning. Composite tenant/resource constraints require an eligible unique parent key. Index tenant-led list/ownership paths and FK joins; verify actual query plans on fixture-scale PostgreSQL before adding speculative indexes. No deployed PostgreSQL version is inferred from Java dependencies.

## Proposed API additions (not implemented endpoints)

| Audience | Proposed contract family | Authorization/semantics |
|---|---|---|
| Admin | `/api/v1/admin/plans`, plan revisions, global capability settings | Current AdminAccess; typed DTO validation, versioned edits and atomic audit |
| Admin | `/api/v1/admin/restaurants/{id}/entitlements` and overrides/history/usage | Explicit target, reason/expiry/config version; preview then apply |
| Studio | `/api/v1/studio/entitlements`, menu-mode/settings | Current RestaurantAccess; effective values/provenance, permitted preference writes only |
| Studio | `/api/v1/studio/tables` and table QR identity rotation | Tenant-owned CRUD, quota lock, token returned only when required |
| Public | Menu optional context resolution under existing public route family | Resolve slug+token, safe DTO, cache/referrer/token redaction |
| Public | `/api/v1/public/menu/{slug}/order-requests` plus scoped snapshot read/attempt observations | Rate-limited anonymous snapshot creation; server prices; idempotency; expiring read capability |
| Admin/model | Explicit restaurant/dish model upload issuance/confirmation/history | Current admin plus target relationship/issued-key/origin checks |
| Studio | Appearance draft/preview/publish and QR configs | Schema/entitlement validation; optimistic version; live state preserved |
| Analytics | Additive versioned event payload and date/range report filters | Tenant scope, bounded date windows; metric version/zone/freshness |

Use existing ProblemDetail/field-error conventions, not a new competing error envelope. Return machine-readable conflict/feature/quota reasons through approved additive extensions. New lists are paginated/bounded with allowed sort/filter fields. Do not expose private keys, audit reasons or bearer read capabilities in public menu/event payloads. Public snapshot creation ignores/rejects client prices and restaurant IDs. Mutations honor stable idempotency keys where retry could duplicate work; same key/different payload fails, in-flight behavior and expiry are explicit. Preserve existing API signatures/legacy constructor behavior unless a focused compatibility fix is approved.

## Migration and rollout strategy

Append new Flyway migrations only after each milestone’s schema is approved; never rewrite applied V1–V13. Do not reserve speculative V14+ numbers now. Define PostgreSQL and H2 behavior explicitly rather than weakening production constraints to pass H2. Test fresh install and upgrade from V13 with representative existing rows, null/default behavior, uniqueness and rollback of failed transactions on disposable databases.

Expand → backfill → validate → switch reads/writes → retire compatibility only after adoption evidence. Proposed backfills: legacy plan revision preserving canUpload3d/existing viewing; mode mapping approved per existing behavior; current model version LEGACY_UNKNOWN with only known metadata; appearance published from existing settings; analytics cutover labeled, not invented historical events. Data irregularities become reviewed repair reports, never destructive silent cleanup.

Original photo quality requires more than removing downsampling. Introduce master+delivery-variant metadata, preserve accepted supported originals via direct upload, validate dimensions/orientation/content and metadata privacy, then serve responsive variants; never force original downloads to every guest. Existing transformed uploads cannot recreate missing originals. Recommend initial supported master JPEG/PNG/WebP; HEIC acceptance needs an approved decode/derivative strategy. Bounded worker/S3 processing, storage/compute/egress costs, cache keys and immutable publication require separate review before removing compression. Count master+variants in storage policy; retain replacement safety.

## Dependencies, decisions, security and acceptance

Depends on architecture/entitlement/request/model/appearance decisions. Resolve bounded JSON versus values rows, request retention, tenant composite constraints, variant worker, idempotency retry window and legacy defaults before implementation. No live schema inspection, production migration, RLS/auth-system replacement, dependency installation or cloud operation is authorized here.

Acceptance: current API contract tests remain green; new DTO validation covers unknown/foreign/oversized values; upgrade fixtures preserve photos/models/QR/account access. PostgreSQL proves constraints/locks/atomic quotas/aggregates/OTP consumption; H2 is a separate fast gate. Test optimistic conflicts, duplicate retries, failed audit persistence, partial backfill restart and app compatibility during rollout. Review EXPLAIN/latency on safe representative datasets and document rollback as application rollback plus a forward-compatible schema strategy, not destructive down-migrations.
