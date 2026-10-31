# Atelier AR glossary

[Master](MASTER_PROJECT_DOCUMENTATION.md) · [Architecture](CODEBASE_ARCHITECTURE.md).

|Term|Meaning in this repository|
|---|---|
|Atelier AR|Product name for the restaurant AR-menu SaaS; Maven/npm artifacts retain Atelier 3D & Media Engine/Studio naming and version 5.0.0|
|Tenant / restaurant|Restaurant row owning menu/media/categories and also the restaurant owner's credential record|
|Platform user / super admin|Separate platform_users account authorized for provisioning/support/platform operations|
|Guest|Anonymous visitor to `/r/{slug}`; not a stored user account|
|Studio|Restaurant dashboard at `/studio`; not the platform admin console|
|Slug|Normalized venue URL and future S3 namespace component; not a secret or tenant selector accepted from ordinary studio bodies|
|Menu|Projection of restaurant branding/categories and completed available dish rows; no separate menus table|
|Dish / Media|A media_details row holding dish metadata, primary photo and optional attached model; not just an arbitrary file|
|Photo-first|Current creation rule: publish a photo dish before attaching 3D; legacy model-only rows remain supported through special handling|
|Daily special|DAILY_SPECIAL media classification driving merchandising/chef-like display; not a scheduled daily expiry job|
|Dish category|Legacy enum STARTERS/MAINS/CHEF_SPECIALS/DESSERTS/DRINKS, distinct from custom categories table|
|Custom category|Tenant-owned flat category with name/order/active flag; no subcategory hierarchy|
|Dietary tags|Fixed normalized vocabulary persisted as comma-separated string and shown on dishes; guest tag filtering is absent|
|Dish code|Staff identifier such as the DSH format allocated under tenant sequence; assigned at presign, not guaranteed gapless among published dishes|
|PRESIGNED / COMPLETED / FAILED|Media upload lifecycle states; pending attachment/replacement fields have separate lifecycle on the same row|
|Pending key|Server-issued S3 object key staged for attachment/replacement or private reference confirmation; stronger than trusting a client URL|
|Concierge / digital twin|Human-assisted request/photo collection and admin-uploaded finished-model workflow, not automatic photo reconstruction|
|Reference slot|Normalized front/top/back/left/right angle for private concierge photos; one to five photos accepted by service|
|Model request|History row for NEW/REPLACEMENT request with status/timestamps; not an external job provider|
|SUPERSEDED|Historical completed model version replaced by another; history can outlive corresponding object cleanup|
|JWT / bearer|Signed token supplied explicitly by API client; possession grants its allowed role until expiry/revocation/account checks|
|HS256 / Nimbus|HMAC JWT algorithm and Spring JWT implementation used by JwtKeys/TokenService|
|Audience|atelier-studio versus atelier-admin token boundary; checked separately from role|
|Token version|Stored account counter compared with ver claim to revoke older sessions; current restaurant gate reads fresh row|
|Setup role|ROLE_RESTAURANT_SETUP temporary-password session; only account/password endpoints allowed|
|Impersonation / support session|Audited admin-issued restaurant token carrying actor claim; cannot change owner password|
|AdminAccess / RestaurantAccess|Current-account validation services, distinct from static JWT signature/authority checks|
|OTP|Optional six-digit password-reset code stored as hash; attempts transaction flaw and logging-only delivery remain|
|BCrypt|Adaptive password/code hash; password encoder configured with cost 12|
|RBAC|Role-based route authorization; this application has restaurant, setup restaurant and super-admin roles|
|DTO|Validated request or response record; public guest mapper suppresses staff-only fields|
|ProblemDetail|Structured HTTP error body with status/title/detail and optional fields; distinct from S3 error responses|
|JPA / Hibernate|ORM layer mapping scalar-ID entities; schema is owned by Flyway rather than Hibernate updates|
|Flyway|Versioned vendor-specific migration runner executed at startup; V1–V13 histories exist|
|H2|Embedded test/local database in PostgreSQL compatibility mode; not proof of identical PostgreSQL constraints/query behavior|
|Optimistic version|Media @Version field used to detect conflicting updates without holding a lock throughout work|
|Pessimistic lock|Explicit row lock for dish sequence/concierge confirmation; should not span slow network calls|
|S3 presigned PUT|Short-lived delegated object upload URL with signed metadata; browser streams file directly to S3|
|S3 presigned GET|Delegated private download subject to signer's permissions and bucket policy; cannot override explicit deny|
|HEAD|Metadata lookup for size/type/ETag; does not inspect file content|
|ETag|S3 object metadata identifier; used/stored differently by confirmation paths; not universal proof of safe content|
|Public/private prefix|Different S3 key namespaces for published menu media and confidential concierge references|
|Immutable cache|Long cache directive intended for versioned published keys; currently also applied to private references, a documented flaw|
|CloudFront hook|Optional configurable public URL base; no distribution/IaC deployment implemented|
|GLB|Binary glTF 2.0 container used for portable dish models|
|glTF|JSON 3D format; frontend accepts self-contained resources and rejects external resource references|
|model-viewer|Google custom element wrapping interactive 3D/WebGL and supported AR handoff modes|
|WebXR|Browser spatial API requested as one AR mode; actual device support is unverified here|
|Scene Viewer / Quick Look|Native platform AR handoff modes requested by model-viewer; no separate native app in repo|
|AR placement/scale|Existing table/floor setting translates to horizontal placement with fixed/auto scaling; physical scale requires authored geometry/device checks|
|QR handoff|Desktop/unavailable-AR path to mobile dish deep link; distinct from branded venue menu QR|
|Print-to-PDF|Browser print window for QR card export; not a backend PDF generation capability|
|Canvas downsampling|Browser image resizing/reencoding before upload; no automatic S3 thumbnail service|
|Caffeine|Process-local caches for menu/throttling/dedup; not distributed coordination|
|Daily aggregate|Composite analytics counter row per tenant/dish-or-zero/event/day; currently non-atomic update pattern|
|MENU_VIEW / MODEL_3D_VIEW / AR_LAUNCH|Accepted public analytics types; current guest code does not send dish-level MENU_VIEW|
|Bandwidth tier|Starter/Growth/Scale display derived from storage usage, not purchased subscription or bandwidth metering|
|Trial|RestaurantStatus.TRIAL permits studio/public access; no expiration/payment automation|
|Actuator|Spring operational endpoints; health/info exposed, no configured metrics exporter|
|Render blueprint|Checked-in declared backend/PostgreSQL deployment topology; not evidence those resources are active|
|Vercel rewrite|Static host maps SPA paths to index.html; not backend routing or proxy by itself|
|A01–A19|Historical audit IDs; current remediation state is explicitly tracked in SECURITY_DOCUMENTATION.md|
|PD01|Additional source-confirmed menuLive/isMenuLive privacy-presentation contract mismatch documented in this pass|
|Verified / implemented|Executed evidence within a stated scope versus meaningful code without complete runtime acceptance; passing compilation alone does not verify workflows|
|Working-tree baseline|Current uncommitted source changes over Git HEAD; commit hash alone does not reproduce this documentation snapshot|
