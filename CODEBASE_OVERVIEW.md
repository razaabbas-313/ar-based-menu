# Atelier AR: codebase overview

Audit date: 2026-10-31. Baseline commit: `330a49c`. The Git root is this directory, one level below the supplied workspace. Existing README, deployment guide and release notes were inspected and preserved. This document describes source code, not an assertion that a production deployment works.

## Architecture and stack

Atelier AR is an existing multi-tenant restaurant menu SaaS with a React SPA, a Spring Boot modular monolith, PostgreSQL metadata storage and direct browser-to-S3 media transfer. One restaurant row also represents its owner login account. A separate platform-user table represents super admins. There is no separate menu entity: a restaurant's published, available dish-media rows form its menu.

| Area | Implementation |
|---|---|
| Frontend | React 18.3, JavaScript ES modules/JSX, Vite 5, Tailwind 3, lucide-react |
| Navigation/state | Custom History API router; React Context for authentication; page/component hooks for other state |
| Media/exports | `@google/model-viewer` 4, browser Canvas, qrcode, JSZip |
| Backend | Java 21, Spring Boot 3.3.5, Web, Validation, Data JPA, Security, OAuth2 Resource Server, Actuator, Lombok |
| Persistence | PostgreSQL; optional H2; Flyway V1 through V13 in separate vendor directories |
| Security | BCrypt cost 12; Nimbus HS256 JWT; role and audience checks; Caffeine throttles |
| Storage/cache | AWS SDK 2.28.29 S3 client/presigner; Caffeine menu and token-version caches |
| Hosting configuration | Maven/Temurin Docker builds, Render blueprint, Vercel SPA hosting |

## Repository map

```text
backend/
  pom.xml, Dockerfile
  src/main/java/com/atelier/media/
    config/        typed properties, AWS beans, profiles, cloud DB URL adapter, caches/jobs
    security/      JWT keys/claims, role and audience filter, principals, login throttle/IP resolver
    web/           REST controllers and ProblemDetail exception translation
    service/       business operations, DTO mapping, storage adapter, cleanup jobs
    repository/    Spring Data repositories, scoped finders, counters and locking queries
    model/         JPA entities and enums
    dto/           validated request/response records; admin/ platform DTOs
    exception/     domain-specific exceptions
  src/main/resources/
    application*.yml
    db/migration/{postgresql,h2}/V1...V13
    META-INF/      database URL postprocessor registration
  src/test/        27 Java test classes; H2 test profile
frontend/
  src/
    main.jsx, App.jsx, index.css
    pages/         HomePage, GuestMenuPage, StudioPage, AdminPage
    context/       AuthContext
    api/           client.js JSON client + direct S3 XHR uploader
    components/    shared UI; admin/, studio/, guest/, landing/
    utils/         router, upload orchestration, media processing, QR, model-viewer loader
  public/          bundled static assets, including showcase media
  scripts/         procedural showcase GLB generation
  package.json, package-lock.json, vite.config.js, tailwind.config.js, vercel.json
infra/             example IAM, S3 CORS and public/private read policies
Dockerfile, render.yaml, vercel.json
README.md, DEPLOYMENT.md, release-notes.md, .env.example
```

Discovery counted 143 main Java files and 61 frontend source files. Dependencies and generated outputs were excluded from source review. No existing AGENTS.md was found in the workspace discovery; the new instructions accompany this audit.

## Frontend responsibilities

`main.jsx` mounts the application providers. `App.jsx` selects four routes using `utils/router.js`: `/`, `/r/:slug`, `/studio`, `/admin`. Browser history and custom navigation events power `useSyncExternalStore`; React Router is not used.

`AuthContext.jsx` manages independent restaurant/admin bearer sessions, restoration, sign-in, forced restaurant password replacement, profile updates, logout and support-session handoff. Normal sessions persist in localStorage; impersonation uses sessionStorage after a short-lived localStorage handoff. Browser logout clears client state; it does not revoke a JWT at the server.

`api/client.js` defines the API contract, token selection, JSON parsing, 15-second fetch timeout, 401 session clearing and field-error extraction. Public calls omit bearer tokens. `putToS3` uses XHR for upload progress and a ten-minute upload timeout. Vite proxies `/api` to port 8080 in development; deployed frontends require the correct build-time API origin.

| Surface | Important components and behavior |
|---|---|
| Landing | `HomePage`, `landing/ShowcaseViewer`, `TableStandMockup`, `ContactModal`; demo contact composes email rather than storing leads |
| Studio | `StudioPage`, `DishManager`, `NewDishStudio`, `BatchUploadStudio`, `DishFields`, `DishEditModal`, `VisualAssetReplacer`, `PhotoPicker`, `ModelPicker`, `PipelineView` |
| Restaurant settings | `BrandCustomizer`, `CategoryManagerModal`, `StudioTopBar`, `StudioAnalytics`, password modals |
| Admin | `AdminPage`, `RestaurantTable`, `RestaurantFormModal`, `RestaurantDrawer`, `Admin3dDeskModal`, temporary-password/delete/impersonation controls |
| Guest | `GuestMenuPage`, `MenuHero`, `CategoryNav`, `FilterBar`, `GuestDishCard`, `DishSheet`, `SocialBar` |
| 3D/QR | `ModelStage`, `ArHandoffModal`, `RestaurantQrModal`, `utils/modelViewer.js`, `utils/qrCard.js` |

Responsive Tailwind breakpoints, mobile sheets, loading/error states, toasts and progress indicators are implemented. Their existence is source evidence; responsive rendering/accessibility and device AR were not tested in this audit. Guest filters currently implement search, chef picks and AR-only; a `tags` state field exists without dietary filter controls or predicate.

## Backend responsibilities and API map

Controllers delegate most work to services; services use repositories and DTO mappers. Some newer studio handlers bypass the existing access service, and some concierge operations hold transactions during S3 calls; see TECHNICAL_AUDIT.md.

| Controller / route family | Services and persistence |
|---|---|
| `AuthController`: `/api/v1/auth/config`, login, me, change-password, forgot-password, reset-password-otp | `AuthService`, `OtpService`, `RestaurantAccess`, restaurant/OTP repositories |
| `AdminAuthController`: `/api/v1/auth/admin/login`, `/api/v1/admin/me`, `/me/password` | `AdminAuthService`, platform users |
| `AdminController`: `/api/v1/admin/restaurants`, status/notes/permission/reset/restore/delete/recalculate/impersonate, analytics, model desk/history, previews | `AdminService`, `ModelRequestService`, `AnalyticsService`, audit logs |
| `MediaController`: `/api/v1/media/presign`, `/confirm`, `/{id}/replace-presign`, `/{id}/replace-confirm` | `MediaService`, `MediaReplacementService`, S3, media rows and usage counters |
| `StudioController`: `/api/v1/studio/dishes`, categories/reorder, settings, branding, menu-status, menu-preview, analytics, model requests/history and attach/confirm/remove model | `DishService`, `CategoryService`, `BrandingService`, `ModelRequestService`, related repositories |
| `PublicMenuController`: `/api/v1/public/menu/{slug}`, `/analytics/event` | `PublicMenuService`, `AnalyticsService` |
| `RestaurantController`: `/api/v1/restaurants/{slug}/menu` | Legacy public menu alias with enum-category and AR-only filters |
| Health | `/health`, `/actuator/health`, permitted Actuator info; probes configured |

`GlobalExceptionHandler` translates validation, domain, conflict, optimistic-locking and AWS exceptions to HTTP ProblemDetail responses. DTO records apply Jakarta validation. Repository JPQL and Spring Data parameters avoid raw request-string SQL concatenation in the reviewed paths. Admin sort properties are allowlisted.

## Database and relationships

JPA entities mostly store scalar foreign-key IDs rather than navigable ORM associations. SQL migrations own referential integrity; `ddl-auto: none` and `open-in-view: false` are configured.

| Entity/table | Ownership, relationships and purpose |
|---|---|
| `Restaurant` / `restaurants` | Tenant identity, owner credentials, status, public slug, branding/contact/theme/currency, menu-live flag, usage/event counters, token version, next dish sequence and direct-upload permission |
| `Media` / `media_details` | Restaurant FK with cascade delete; photo-first dish, optional model, category enum/custom-category FK, price/tags/availability, pending uploads/replacements, optimistic `version`, unique S3 key and per-tenant dish code |
| `Category` / `categories` | Restaurant FK with cascade; name/order/active flag; media category FK becomes null on deletion |
| `PlatformUser` / `platform_users` | Separate super-admin account, password, role, token version and password-change flag |
| `AdminAuditLog` / `admin_audit_log` | Admin actor/action, optional restaurant ID, detail and timestamp |
| `PasswordResetOtp` / `password_reset_otps` | Email-address lookup, hashed code, expiry/use/attempt fields; not a tenant FK |
| `ModelRequest` / `model_requests` | Restaurant and dish FKs with cascade, status/type/reference-photo JSON stored as text, notes, completed model and timestamps |
| `ModelRequestPendingKey` / `model_request_pending_keys` | Tenant/dish FKs, issued private object key and photo slot |
| `AnalyticsEvent` / `analytics_events` | Restaurant FK, optional scalar dish ID, event type/time |
| `AnalyticsDaily` / `analytics_daily` | Composite restaurant/dish-or-zero/event/day key; aggregate count; no restaurant FK in migration V10 |

Migrations: V1 media; V2 tenants; V3 platform admin/branding/dual media; V4 provisioning/replacements; V5 categories/concierge/OTP/analytics/menu status; V6 request history; V7 dish codes; V8 pending keys and PostgreSQL active-request uniqueness; V9 OTP attempts/hash width; V10 analytics aggregates/indexes; V11 token versions; V12 dish sequence; V13 restaurant direct-model permission. H2 V8 uses a nonunique ordinary index in place of PostgreSQL's unique partial active-request index. Passing H2 tests would not establish PostgreSQL equivalence.

Indexes cover tenant/status/menu, categories/order, pending replacements, OTP lookup, model-request histories and analytics lookups. Reviewed menu/history paths batch related lookups rather than using lazy JPA graph traversal. Remaining query/retention issues are in the audit.

## Authentication and authorization

`JwtKeys` validates minimum key length and configures HS256 issuer/time validation. `TokenService` emits separate `atelier-studio` and `atelier-admin` audiences and role claims. `SecurityConfig` is stateless, denies unspecified routes, permits narrow public routes and requires role/audience matching. CSRF is disabled for the bearer-token design. Restaurant setup tokens only reach me/change-password.

`Tenant.from` obtains restaurant identity from verified claims. `RestaurantAccess` checks the current tenant, deletion/status/password requirements and cached token version. Existing dish/media/branding operations reuse it and tenant-scoped repository finders. Its enforcement is incomplete across other handlers. `AdminAuthService.require` checks admin version only for its own profile/password methods; most platform APIs lack this revalidation.

Actual defaults: restaurant JWT 30 days (configuration), admin JWT 30 days (hardcoded), impersonation seven days (configuration). These differ from release-note TTL claims. Throttles are process-local and ClientIpResolver only trusts forwarded addresses when the remote proxy is explicitly configured.

## Media and 3D data flow

1. Photo picker uses Canvas downsampling (1400-pixel maximum) and WebP/JPEG encoding. Model utilities inspect GLB/glTF on the client; files are not automatically mesh/texture optimized.
2. `utils/upload.js` requests an authorized JSON presign operation. `MediaService` creates a pending photo row, locks the restaurant sequence row and assigns its dish code.
3. `S3Storage.presignPut` signs object key, MIME, exact size and cache header for five minutes. Browser file bytes travel directly to S3, never through an application upload controller.
4. Confirmation resolves the tenant/key and performs S3 HEAD; short database transactions publish media, swap replacements and adjust usage. HEAD validates metadata, not the actual file format or malicious bytes. Checks differ between paths.
5. Published object URLs use `AWS_S3_PUBLIC_BASE_URL` when set, otherwise regional S3 URLs. Public assets are under `restaurant-atelier/{slug}/`; confidentiality is not enforced by the menu-live flag for already-known object URLs.
6. Direct model attachment/replacement reuses the dish model pipeline and requires `canUpload3d`. The general direct cap is 50 MiB; the admin concierge publication cap is 25 MiB.
7. Concierge photos use `restaurant-atelier-private/{slug}/requests/{dishId}/`, issued-key tracking, slot checks, dish locking and request history. Admin list/history generates 15-minute GET URLs; final model approval uses a public model prefix. No automatic photo-to-3D conversion service exists; production is a human/admin workflow.
8. Guest `DishSheet` preloads the model when opened; `ModelStage` lazily imports model-viewer and handles progress/errors. WebXR, Scene Viewer and Quick Look are requested; AR activates through the model-viewer element, with a QR handoff fallback. Device compatibility, scale accuracy and hosted permission policies remain unverified.
9. `StaleUploadReaper` handles pending dish/model/replacement expiry and approved reference cleanup; `StorageJanitor` asynchronously removes known public assets and sweeps a tenant public prefix. Private cleanup gaps are documented separately.

## Restaurant QR and configuration

`RestaurantQrModal` and `qrCard.js` generate one venue QR targeting `/r/{slug}`, optionally using `VITE_PUBLIC_MENU_ORIGIN`. Exports include SVG, rasterized PNG and a print window for browser PDF printing; there is no PDF generation API or per-table QR persistence. Branding/settings, currencies, socials, live/private switch and category configuration persist in database fields. The UI's mailto demo flow requires a mail application and optionally `VITE_SALES_EMAIL`; no backend lead submission is implemented.

## Environment and deployment inventory

Never copy real values into documentation or logs. Configuration variable names include:

- Backend: `SPRING_PROFILES_ACTIVE`, `PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DATABASE_URL`, `DB_POOL_MAX`.
- Auth/application: `ATELIER_JWT_SECRET`, `ATELIER_JWT_TTL`, `ATELIER_JWT_IMPERSONATION_TTL`, `ATELIER_SUPERADMIN_EMAIL`, `ATELIER_SUPERADMIN_PASSWORD`, `ATELIER_SUPERADMIN_RESET`, `ATELIER_CORS_ORIGINS`, `ATELIER_PASSWORD_RESET_ENABLED`, `ATELIER_TRUSTED_PROXIES`, `ATELIER_PRIVATE_KEY_PREFIX`; Spring relaxed binding can override other typed properties.
- AWS: `AWS_REGION`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_PUBLIC_BASE_URL`; default credential chain is used when static credentials are absent.
- Frontend: `VITE_API_BASE_URL`, `VITE_DEV_API_PROXY`, `VITE_PUBLIC_MENU_ORIGIN`, `VITE_SALES_EMAIL`, `VITE_CURRENCY`.

Default profile is prod. Local provides development defaults; local+h2 uses a file database and is not suitable for an audit smoke test against existing data. Optional git-ignored secrets.properties files are imported. StartupProfileValidator blocks local on detected hosted environments and checks production JWT length; it does not validate all AWS/DB/admin secrets as README implies. Cloud database URLs are adapted by DatabaseUrlEnvironmentPostProcessor. Render uses backend Docker context; both Dockerfiles skip tests when packaging. Vercel configurations duplicate SPA/security headers. CloudFront is only an optional URL hook: no distribution, OAC, invalidation or infrastructure provisioning is present.

## Verification boundary

Frontend production compilation and isolated QR/media checks passed using existing dependencies. Java/Maven commands were unavailable, and no Maven wrapper, Docker or psql command was found. Backend compilation/tests, migrations, API behavior, production settings, live S3 permissions and device/browser AR were not verified. See IMPLEMENTATION_STATUS.md for exact classifications and TECHNICAL_AUDIT.md for findings.
