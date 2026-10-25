# Release Notes: Atelier 3D & Media Engine 5.0.0

**Release Date:** October 2026  
**Backend:** Spring Boot 3.3.5 / Java 21 / PostgreSQL & H2 / Flyway V1–V12  
**Frontend:** React 18 / Vite 5 / Tailwind CSS / `@google/model-viewer` 4.0  

---

### Key Enhancements & Security Fixes

#### 1. Zero-Server-RAM Architecture & Direct S3 Streaming
- Retained strict zero-server-RAM design: no image or 3D binary data ever enters or buffers inside the JVM heap.
- All uploads stream directly from the client browser to AWS S3 using authenticated, presigned PUT URLs with verified content types and length bounds.
- Asset verification on confirmation utilizes non-buffering S3 `HEAD` calls.

#### 2. Authentication & Session Security (Issue 1, 6, 11)
- **Menu Preview Isolation**: Eliminated unauthenticated `?preview=true` parameter on public endpoints. Added authenticated endpoints:
  - `GET /api/v1/studio/menu-preview` (Role: `ROLE_RESTAURANT`, tenant resolved strictly from JWT subject).
  - `GET /api/v1/admin/restaurants/{id}/menu-preview` (Role: `ROLE_SUPER_ADMIN`).
- **Token Versioning & Instant Revocation**: Added `token_version` column to `restaurants` and `platform_users` (Migration `V11`). All JWTs carry a `ver` claim. Password change, reset, or suspension immediately increments version and invalidates existing sessions with a 30-second Caffeine cache for high throughput.
- **Audience Enforcement**: Enforced distinct audiences (`atelier-studio` vs `atelier-admin`). Tokens cannot be cross-used between admin and studio endpoints.
- **Reduced TTLs**: Studio JWT lifespan default reduced to 4 hours; super-admin token TTL to 1 hour.
- **Strict Headers**: Configured Content-Security-Policy (CSP), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, and default `Cache-Control: no-store` on all `/api/**` endpoints and Vercel routing rules.

#### 3. 3D Model Concierge Upload Hardening (Issue 2, 8)
- **Content-Type & Slot Allowlist**: Enforced strict image allowlist (`image/webp`, `image/jpeg`, `image/png`) and 5 mandatory slot identifiers (`front`, `top`, `back`, `left`, `right`).
- **Server-Side Pending Key Registry**: Stored presigned keys in `model_request_pending_keys` (Migration `V8`). Confirmation validates that only issued keys matching the specific tenant and dish can be finalized.
- **Pessimistic Dish Locking**: Added `findByIdAndRestaurantIdForUpdate` to eliminate race conditions during concurrent confirmation attempts.
- **Published Model Size Cap**: Configured `atelier.max-published-model-bytes` (25 MB default) with clear rejection responses for oversized 3D models.
- **Private Reference Photos**: Reference photos relocated to private S3 prefix `restaurant-atelier-private/{slug}/requests/{dishId}/`. Public access is blocked at the bucket policy level. Authorized viewers (studio & admin) receive short-lived (15-min) presigned GET URLs.
- **Safe Replacement Cleanup**: Outdated models are safely scheduled for S3 deletion after the database transaction commits.

#### 4. Hardened Password Reset OTP (Issue 3)
- **Feature Flag**: Controlled via `atelier.password-reset.enabled` (defaults to false).
- **BCrypt Hashing**: 6-digit OTP codes are stored exclusively as BCrypt hashes in `password_reset_otps` (Migration `V9`).
- **Enumeration Protection**: Generic response returned whether the email exists or not.
- **Brute-Force Lockout**: Max 5 wrong attempts per OTP before permanent invalidation. Constant-time comparison prevents timing attacks.
- **Rate Limiting**: Enforced 60-second resend cooldown per email and per-IP throttling.
- **MailSender Abstraction**: Pluggable `EmailSender` interface with development `LoggingEmailSender`.

#### 5. Dual-Tier Login Rate Limiting (Issue 4)
- **`LoginThrottle` Architecture**: Pluggable interface implemented with `CaffeineLoginThrottle`.
- **Dual Tracking**: Tracks failed attempts by both `(email + client IP)` (max 5) and `(client IP)` alone (max 20 per 15 min).
- **Progressive Delay**: Introduces synthetic delays (500ms, 1000ms, 2000ms) on repeated failures to resist automated brute force.
- **Trusted Proxy Resolution**: `ClientIpResolver` securely extracts client IP address respecting configured `atelier.trusted-proxies`.

#### 6. Analytics Security & Aggregates (Issue 5)
- **Allowlist Enum**: Supported event types restricted to `AnalyticsEventType` (`MENU_VIEW`, `MODEL_3D_VIEW`, `AR_LAUNCH`).
- **Dish Ownership Verification**: Ensures logged dish IDs belong to the targeted restaurant.
- **Asynchronous Virtual-Thread Processing**: Events dispatched asynchronously to virtual threads without blocking request threads.
- **Daily Aggregation**: Daily rollups persisted in `analytics_daily` (Migration `V10`).

#### 7. Atomic Dish Code Sequence Generator (Issue 7)
- **Deterministic Counter**: Replaced legacy max-code substring scanning with `next_dish_seq` counter on `restaurants` (Migration `V12`).
- **Row-Level Lock**: Uses `findByIdForUpdate` to allocate contiguous, strictly unique `DSH-0001` codes under high multi-threaded concurrency.

#### 8. Secure Default Profile & Fail-Fast Validator (Issue 9)
- **Default Profile**: Default profile set to `prod` (secure by default).
- **No Insecure Fallback Secrets**: Removed hardcoded database and admin secrets from `application.yml`.
- **`StartupProfileValidator`**: Verifies that development profile `local` is never accidentally deployed to hosted cloud environments (detects `RENDER`, `VERCEL`, `PORT`, `ATELIER_ENV=production`).

#### 9. Paginated 3D History & Capped Pipeline Queries (Issue 10)
- **Paginated History**: `getAllHistory` upgraded with `PageDto`, supporting `page`, `size` (default 50, max 100), and optional `restaurantId` and `status` filters.
- **Pending Desk Cap**: `getPendingRequests` capped at 200 dishes to prevent memory exhaustion.
- **Frontend Controls**: Added pagination and live filtering to 3D Concierge History tab in `AdminPage.jsx`.
