# Complete API reference

Snapshot: 2026-10-31, current working tree over commit `330a49c`, including uncommitted A01–A03 fixes. No HTTP requests or tests were run in this documentation pass. Paths below are source references relative to the repository root.

[Master](MASTER_PROJECT_DOCUMENTATION.md) · [Security](SECURITY_DOCUMENTATION.md) · [Database](DATABASE_DOCUMENTATION.md)

## Contract conventions

JSON control plane only; file bytes use presigned S3 PUT directly. Authenticated calls send `Authorization: Bearer <redacted>`; examples never include actual secrets or credentials. No cookies/session/CSRF flow, refresh endpoint, logout endpoint or public registration endpoint exists. URL IDs do not select the studio tenant; it comes from verified JWT claims.

- **Public**: anonymous permitted; invalid supplied bearer credentials can still be rejected by the resource-server filter.
- **Admin**: SUPER_ADMIN role + admin audience + current AdminAccess account/version check. Login is public.
- **Studio**: RESTAURANT role + studio audience + current RestaurantAccess, including status/deletion/version/setup state. StudioController has a controller-wide guard; media services retain their own checks.
- **Account**: RESTAURANT or RESTAURANT_SETUP + studio audience, current account; supports forced password replacement.
- Most protected JSON responses are no-store. Public menu uses HTTP max-age=15 seconds, stale-while-revalidate=60 seconds in addition to server cache.
- Request parameters/DTO declarations below are exact source-derived schemas. `@Valid` on the handler enables declared bean validation; manual service checks also apply. Constraints without NotNull/NotBlank commonly permit null. Do not assume annotations on a DTO are invoked by handlers lacking @Valid.
- Java Instant serializes as ISO text; enum spelling matches declared enum constants. Monetary fields use BigDecimal. Lists and nested DTOs are cataloged below; no fabricated JSON payloads are provided.

## Shared error contract

`GlobalExceptionHandler` and SecurityConfig produce application/problem+json with status/title/detail (and optional fields mapping). Unhandled failures may use Spring defaults; no universal catch-all is present.

| HTTP | Trigger |
|---|---|
|400|Malformed JSON/parameter conversion/bean validation/InvalidFieldException/InvalidMediaException|
|401|Missing/invalid/expired bearer; missing account or revoked token version; invalid login|
|403|Wrong role/audience; suspended tenant; forced password replacement; disabled direct 3D upload|
|404|Missing/hidden restaurant or media; disabled password-reset feature|
|409|Duplicate slug/email/category, business conflict, optimistic lock or database integrity conflict|
|422|Upload HEAD/integrity verification failure|
|429|Login/OTP/analytics throttling; TooManyAttempts includes Retry-After|
|502|AWS SDK operation failure|
|503|StorageNotConfiguredException|

S3 PUT/GET errors are returned by S3, not this exception handler. The client API request timeout is 15 seconds; upload XHR timeout is ten minutes. A transport timeout does not prove a server transaction failed.

## Endpoint inventory

Discovered **59 application endpoints** in eight controllers. Framework Actuator endpoints are listed separately.

|Method|URL|Handler|Access|Success|
|---|---|---|---|---|
|POST|`/api/v1/auth/admin/login`|AdminAuthController.login|Public|200|
|GET|`/api/v1/admin/me`|AdminAuthController.me|Admin|200|
|PUT|`/api/v1/admin/me/password`|AdminAuthController.changePassword|Admin|200|
|GET|`/api/v1/admin/restaurants/{id}/menu-preview`|AdminController.restaurantMenuPreview|Admin|200|
|GET|`/api/v1/admin/analytics`|AdminController.analytics|Admin|200|
|GET|`/api/v1/admin/restaurants`|AdminController.restaurants|Admin|200|
|POST|`/api/v1/admin/restaurants`|AdminController.create|Admin|201|
|GET|`/api/v1/admin/restaurants/{id}`|AdminController.detail|Admin|200|
|PUT|`/api/v1/admin/restaurants/{id}`|AdminController.update|Admin|200|
|PUT|`/api/v1/admin/restaurants/{id}/notes`|AdminController.updateNotes|Admin|200|
|PUT|`/api/v1/admin/restaurants/{id}/permission-3d`|AdminController.update3dPermission|Admin|200|
|PUT|`/api/v1/admin/restaurants/{id}/status`|AdminController.status|Admin|200|
|DELETE|`/api/v1/admin/restaurants/{id}`|AdminController.delete|Admin|200 soft; 202 hard|
|POST|`/api/v1/admin/restaurants/{id}/restore`|AdminController.restore|Admin|200|
|POST|`/api/v1/admin/restaurants/{id}/recalculate-usage`|AdminController.recalculate|Admin|200|
|POST|`/api/v1/admin/restaurants/{id}/reset-password`|AdminController.resetPassword|Admin|200|
|GET|`/api/v1/admin/slug-available`|AdminController.slugAvailable|Admin|200|
|POST|`/api/v1/admin/restaurants/{id}/impersonate`|AdminController.impersonate|Admin|200|
|GET|`/api/v1/admin/3d-requests`|AdminController.get3dRequests|Admin|200|
|GET|`/api/v1/admin/3d-requests/history`|AdminController.getAll3dRequestsHistory|Admin|200|
|GET|`/api/v1/admin/restaurants/{id}/model-requests`|AdminController.getRestaurantModelRequests|Admin|200|
|POST|`/api/v1/admin/3d-requests/{dishId}/presign`|AdminController.presignFinishedModel|Admin|201|
|POST|`/api/v1/admin/3d-requests/{dishId}/approve`|AdminController.approve3dModel|Admin|200|
|GET|`/api/v1/auth/config`|AuthController.config|Public|200|
|POST|`/api/v1/auth/login`|AuthController.login|Public|200|
|GET|`/api/v1/auth/me`|AuthController.me|Account|200|
|POST|`/api/v1/auth/change-password`|AuthController.changePassword|Account|200|
|POST|`/api/v1/auth/forgot-password`|AuthController.forgotPassword|Public|200|
|POST|`/api/v1/auth/reset-password-otp`|AuthController.resetPasswordOtp|Public|200|
|GET|`/health`|HealthController.health|Public|200|
|POST|`/api/v1/media/presign`|MediaController.presign|Studio|201|
|POST|`/api/v1/media/{id}/replace-presign`|MediaController.replacePresign|Studio|200|
|POST|`/api/v1/media/{id}/replace-confirm`|MediaController.replaceConfirm|Studio|200|
|POST|`/api/v1/media/confirm`|MediaController.confirm|Studio|200|
|GET|`/api/v1/public/menu/{slug}`|PublicMenuController.menu|Public|200|
|POST|`/api/v1/public/analytics/event`|PublicMenuController.recordAnalyticsEvent|Public|202|
|GET|`/api/v1/restaurants/{slug}/menu`|RestaurantController.menu|Public|200|
|GET|`/api/v1/studio/menu-preview`|StudioController.menuPreview|Studio|200|
|GET|`/api/v1/studio/dishes`|StudioController.dishes|Studio|200|
|PUT|`/api/v1/studio/dishes/{id}`|StudioController.updateDish|Studio|200|
|PATCH|`/api/v1/studio/dishes/{id}/availability`|StudioController.availability|Studio|200|
|DELETE|`/api/v1/studio/dishes/{id}`|StudioController.deleteDish|Studio|204|
|GET|`/api/v1/studio/categories`|StudioController.getCategories|Studio|200|
|POST|`/api/v1/studio/categories`|StudioController.createCategory|Studio|201|
|PUT|`/api/v1/studio/categories/{id}`|StudioController.updateCategory|Studio|200|
|DELETE|`/api/v1/studio/categories/{id}`|StudioController.deleteCategory|Studio|204|
|PUT|`/api/v1/studio/categories/reorder`|StudioController.reorderCategories|Studio|200 when valid request|
|PUT|`/api/v1/studio/menu-status`|StudioController.updateMenuStatus|Studio|200|
|POST|`/api/v1/studio/dishes/{id}/model-request/presign`|StudioController.presignModelRequest|Studio|200|
|POST|`/api/v1/studio/dishes/{id}/model-request/confirm`|StudioController.confirmModelRequest|Studio|200|
|GET|`/api/v1/studio/model-requests`|StudioController.getStudioModelRequests|Studio|200|
|GET|`/api/v1/studio/analytics`|StudioController.analytics|Studio|200|
|POST|`/api/v1/studio/dishes/{id}/attach-3d`|StudioController.attach3d|Studio|201|
|POST|`/api/v1/studio/dishes/{id}/confirm-3d`|StudioController.confirm3d|Studio|200|
|DELETE|`/api/v1/studio/dishes/{id}/model`|StudioController.removeModel|Studio|200|
|GET|`/api/v1/studio/settings`|StudioController.settings|Studio|200|
|PUT|`/api/v1/studio/settings`|StudioController.updateSettings|Studio|200|
|POST|`/api/v1/studio/branding/{kind}/presign`|StudioController.presignBranding|Studio|201|
|POST|`/api/v1/studio/branding/{kind}/confirm`|StudioController.confirmBranding|Studio|200|

### POST /api/v1/auth/admin/login

- **Handler/source:** [AdminAuthController.login](../backend/src/main/java/com/atelier/media/web/AdminAuthController.java#L35).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminAuthResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
HttpServletRequest httpRequest, @Valid @RequestBody LoginRequestDto request
```

- **Business/validation:** Public super-admin login with separate throttle key, password comparison and admin audience.
- **Persistence/integration:** platform_users lookup/save last_login_at.
- **Frontend consumer:** AdminLogin / AuthContext → api.adminLogin.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 429 (throttled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/me

- **Handler/source:** [AdminAuthController.me](../backend/src/main/java/com/atelier/media/web/AdminAuthController.java#L41).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminProfileDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** AdminAccess checks current account and token version.
- **Persistence/integration:** platform_users lookup.
- **Frontend consumer:** AuthContext → api.adminMe.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/admin/me/password

- **Handler/source:** [AdminAuthController.changePassword](../backend/src/main/java/com/atelier/media/web/AdminAuthController.java#L46).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminProfileDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody ChangePasswordDto dto
```

- **Business/validation:** AdminAccess + current-password verification; update BCrypt hash, clear flag, increment version. Returns profile, not replacement token.
- **Persistence/integration:** platform_users transactional update.
- **Frontend consumer:** admin/ChangePasswordModal → api.adminChangePassword.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/restaurants/{id}/menu-preview

- **Handler/source:** [AdminController.restaurantMenuPreview](../backend/src/main/java/com/atelier/media/web/AdminController.java#L71).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PublicMenuDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@PathVariable long id
```

- **Business/validation:** Staff projection bypasses menu-live flag for selected tenant; still requires current admin.
- **Persistence/integration:** restaurants, completed available media, active categories.
- **Frontend consumer:** api.adminMenuPreview (no direct component call found beyond client declaration).
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/analytics

- **Handler/source:** [AdminController.analytics](../backend/src/main/java/com/atelier/media/web/AdminController.java#L76).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PlatformAnalyticsDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
// No parameters
```

- **Business/validation:** Platform lifecycle counts, storage/dishes/models and engagement totals/top lists.
- **Persistence/integration:** RestaurantRepository aggregates, AnalyticsEventRepository counts.
- **Frontend consumer:** AdminPage → api.analytics.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/restaurants

- **Handler/source:** [AdminController.restaurants](../backend/src/main/java/com/atelier/media/web/AdminController.java#L81).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PageDto<AdminRestaurantDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@RequestParam(required = false) String q,
            @RequestParam(required = false) RestaurantStatus status,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
```

- **Business/validation:** Search name/slug/email/owner, lifecycle/deleted filters; bounded page 0+, size 1..100; allowlisted sort with ID tie-breaker.
- **Persistence/integration:** RestaurantRepository.findAll(Specification,Pageable).
- **Frontend consumer:** AdminPage → api.adminRestaurants.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/restaurants

- **Handler/source:** [AdminController.create](../backend/src/main/java/com/atelier/media/web/AdminController.java#L93).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 201; `ResponseEntity<AdminRestaurantCreatedDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody AdminRestaurantCreateDto dto
```

- **Business/validation:** Provision owner account with generated temporary password; normalize/validate slug/email; set setup flag/status/direct-3D permission; record audit.
- **Persistence/integration:** restaurants insert + admin_audit_log insert in transaction.
- **Frontend consumer:** RestaurantFormModal → api.adminCreateRestaurant.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/restaurants/{id}

- **Handler/source:** [AdminController.detail](../backend/src/main/java/com/atelier/media/web/AdminController.java#L100).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDetailDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@PathVariable long id
```

- **Business/validation:** Restaurant profile/branding, bounded dishes, usage breakdown, pending count and latest 20 audit rows.
- **Persistence/integration:** restaurants, media_details, admin_audit_log.
- **Frontend consumer:** RestaurantDrawer → api.adminRestaurant.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/admin/restaurants/{id}

- **Handler/source:** [AdminController.update](../backend/src/main/java/com/atelier/media/web/AdminController.java#L105).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                     @Valid @RequestBody AdminRestaurantUpdateDto dto
```

- **Business/validation:** Edit profile/slug/email/notes/optional 3D permission; email change bumps token version; slug changes future QR URL and S3 namespace; evict old/new menu.
- **Persistence/integration:** restaurants update + audit.
- **Frontend consumer:** RestaurantFormModal → api.adminUpdateRestaurant.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/admin/restaurants/{id}/notes

- **Handler/source:** [AdminController.updateNotes](../backend/src/main/java/com/atelier/media/web/AdminController.java#L111).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                          @RequestBody AdminNotesUpdateDto dto
```

- **Business/validation:** Store private CRM notes and audit action; body lacks @Valid on handler.
- **Persistence/integration:** restaurants.admin_notes + admin_audit_log.
- **Frontend consumer:** RestaurantDrawer → api.adminUpdateNotes.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/admin/restaurants/{id}/permission-3d

- **Handler/source:** [AdminController.update3dPermission](../backend/src/main/java/com/atelier/media/web/AdminController.java#L117).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                                 @RequestBody com.atelier.media.dto.admin.AdminPermission3dDto dto
```

- **Business/validation:** Set direct studio 3D upload permission; null coerces false; audit.
- **Persistence/integration:** restaurants.can_upload_3d + audit.
- **Frontend consumer:** RestaurantDrawer / RestaurantFormModal → api.adminUpdate3dPermission.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/admin/restaurants/{id}/status

- **Handler/source:** [AdminController.status](../backend/src/main/java/com/atelier/media/web/AdminController.java#L123).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                     @Valid @RequestBody RestaurantStatusUpdateDto dto
```

- **Business/validation:** Change lifecycle status; suspension increments version; audit and menu eviction.
- **Persistence/integration:** restaurants + audit.
- **Frontend consumer:** AdminPage / RestaurantDrawer → api.adminSetStatus.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### DELETE /api/v1/admin/restaurants/{id}

- **Handler/source:** [AdminController.delete](../backend/src/main/java/com/atelier/media/web/AdminController.java#L130).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200 soft; 202 hard; `ResponseEntity<?>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                    @RequestParam(defaultValue = "false") boolean hard
```

- **Business/validation:** Default soft delete sets deleted timestamp/suspended/version; hard=true deletes DB row/cascades and schedules async public asset cleanup. Never use on production for testing.
- **Persistence/integration:** restaurants/media cascades, admin_audit_log; S3 cleanup after DB commit.
- **Frontend consumer:** DeleteRestaurantModal → api.adminDeleteRestaurant.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/restaurants/{id}/restore

- **Handler/source:** [AdminController.restore](../backend/src/main/java/com/atelier/media/web/AdminController.java#L140).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id
```

- **Business/validation:** Clear soft-deleted timestamp; make ACTIVE; audit; evict menu.
- **Persistence/integration:** restaurants update + audit.
- **Frontend consumer:** RestaurantDrawer → api.adminRestore.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/restaurants/{id}/recalculate-usage

- **Handler/source:** [AdminController.recalculate](../backend/src/main/java/com/atelier/media/web/AdminController.java#L145).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AdminRestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id
```

- **Business/validation:** Recompute completed media/branding usage counters.
- **Persistence/integration:** media_details aggregates → restaurants counters + audit.
- **Frontend consumer:** RestaurantDrawer → api.adminRecalculate.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/restaurants/{id}/reset-password

- **Handler/source:** [AdminController.resetPassword](../backend/src/main/java/com/atelier/media/web/AdminController.java#L151).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<TemporaryPasswordDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id
```

- **Business/validation:** Generate owner temporary password, hash it, set setup flag and increment version; return temporary password once; audit.
- **Persistence/integration:** restaurants + admin_audit_log.
- **Frontend consumer:** RestaurantDrawer → api.adminResetPassword.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/slug-available

- **Handler/source:** [AdminController.slugAvailable](../backend/src/main/java/com/atelier/media/web/AdminController.java#L157).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<SlugAvailabilityDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@RequestParam String slug,
                                                             @RequestParam(required = false) Long excludeId
```

- **Business/validation:** Normalize/validate/reserved slug check; optionally exclude existing restaurant ID.
- **Persistence/integration:** restaurants slug existence query.
- **Frontend consumer:** RestaurantFormModal → api.adminSlugAvailable.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/restaurants/{id}/impersonate

- **Handler/source:** [AdminController.impersonate](../backend/src/main/java/com/atelier/media/web/AdminController.java#L164).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AuthResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id
```

- **Business/validation:** Require usable tenant; issue restaurant-audience support token with actor claim; audit. Does not issue admin token.
- **Persistence/integration:** restaurants lookup + admin_audit_log.
- **Frontend consumer:** admin/impersonate.js → api.adminImpersonate.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/3d-requests

- **Handler/source:** [AdminController.get3dRequests](../backend/src/main/java/com/atelier/media/web/AdminController.java#L171).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<List<AdminModelRequestDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
// No parameters
```

- **Business/validation:** List current PENDING dishes capped at 200; batch restaurants; signed private reference URLs.
- **Persistence/integration:** media_details by model_request_status; restaurants batch.
- **Frontend consumer:** AdminPage → api.admin3dRequests.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/3d-requests/history

- **Handler/source:** [AdminController.getAll3dRequestsHistory](../backend/src/main/java/com/atelier/media/web/AdminController.java#L176).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PageDto<com.atelier.media.dto.ModelRequestHistoryDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
```

- **Business/validation:** Filter restaurant/status case-insensitively, page >=0, size 1..100; decorate batched restaurant/dish info and private URLs.
- **Persistence/integration:** model_requests page; restaurants/media batch.
- **Frontend consumer:** AdminPage → api.admin3dRequestsHistory.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/admin/restaurants/{id}/model-requests

- **Handler/source:** [AdminController.getRestaurantModelRequests](../backend/src/main/java/com/atelier/media/web/AdminController.java#L185).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<List<com.atelier.media.dto.ModelRequestHistoryDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@PathVariable long id
```

- **Business/validation:** Return first 100 history records for selected restaurant, not a fully paginated response.
- **Persistence/integration:** model_requests with decoration.
- **Frontend consumer:** RestaurantDrawer → api.adminRestaurantModelRequests.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/3d-requests/{dishId}/presign

- **Handler/source:** [AdminController.presignFinishedModel](../backend/src/main/java/com/atelier/media/web/AdminController.java#L190).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 201; `ResponseEntity<PresignResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@PathVariable long dishId,
            @Valid @RequestBody Attach3dRequestDto request
```

- **Business/validation:** Allow GLB, glTF JSON or octet-stream MIME; cap admin publication at 25 MiB by default; generate public models/twin key and signed PUT. Key is not recorded as pending.
- **Persistence/integration:** media/restaurant read; local S3 signing.
- **Frontend consumer:** Admin3dDeskModal → api.adminPresignModel.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/admin/3d-requests/{dishId}/approve

- **Handler/source:** [AdminController.approve3dModel](../backend/src/main/java/com/atelier/media/web/AdminController.java#L198).
- **Access:** Admin; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
            @PathVariable long dishId,
            @Valid @RequestBody AdminModelApproveDto dto
```

- **Business/validation:** Require restaurant models prefix; HEAD/type/size checks; attach model, update history/supersession/counters; remove replaced model after commit. HEAD runs within transaction.
- **Persistence/integration:** media_details, model_requests, restaurants; S3 HEAD/delete.
- **Frontend consumer:** Admin3dDeskModal → api.adminApproveModel.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 422 (uploaded object could not be verified); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/auth/config

- **Handler/source:** [AuthController.config](../backend/src/main/java/com/atelier/media/web/AuthController.java#L53).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<Map<String, Object>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
// No parameters
```

- **Business/validation:** Feature flag only; no account enumeration.
- **Persistence/integration:** No DB write.
- **Frontend consumer:** AuthModal via api.authConfig.
- **Mapped error branches:** 401 (credentials/token/current account invalid). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/auth/login

- **Handler/source:** [AuthController.login](../backend/src/main/java/com/atelier/media/web/AuthController.java#L60).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AuthResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
HttpServletRequest httpRequest, @Valid @RequestBody LoginRequestDto request
```

- **Business/validation:** Normalize email, IP/key throttle, timing equalizer, BCrypt comparison, status check; issue setup or full studio JWT.
- **Persistence/integration:** RestaurantRepository.findByEmail; no registration.
- **Frontend consumer:** AuthContext.login → api.login.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 429 (throttled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/auth/me

- **Handler/source:** [AuthController.me](../backend/src/main/java/com/atelier/media/web/AuthController.java#L68).
- **Access:** Account; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<RestaurantDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Validate current tenant/version/status; return restaurant profile; setup role allowed.
- **Persistence/integration:** RestaurantAccess → RestaurantRepository.findById.
- **Frontend consumer:** AuthContext session restoration → api.me.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/auth/change-password

- **Handler/source:** [AuthController.changePassword](../backend/src/main/java/com/atelier/media/web/AuthController.java#L76).
- **Access:** Account; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<AuthResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody ChangeOwnPasswordDto request
```

- **Business/validation:** Require current account/current password; disallow support-session password change; hash new password; clear setup flag; increment version; issue fresh token.
- **Persistence/integration:** Short transaction updates restaurants.
- **Frontend consumer:** SetPasswordModal / StudioChangePasswordModal → api.changePassword.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 409 (business/integrity/concurrent-write conflict); 429 (throttled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/auth/forgot-password

- **Handler/source:** [AuthController.forgotPassword](../backend/src/main/java/com/atelier/media/web/AuthController.java#L85).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MessageResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
HttpServletRequest request,
            @Valid @RequestBody ForgotPasswordDto dto
```

- **Business/validation:** Disabled by default (404). Rate-limit requests, normalize email, invalidate older OTPs, persist hash/expiry, call logging sender. Generic response for unknown email.
- **Persistence/integration:** password_reset_otps and restaurants lookup.
- **Frontend consumer:** AuthModal → api.forgotPassword.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 404 (resource hidden/missing or reset disabled); 429 (throttled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/auth/reset-password-otp

- **Handler/source:** [AuthController.resetPasswordOtp](../backend/src/main/java/com/atelier/media/web/AuthController.java#L99).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MessageResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@Valid @RequestBody ResetPasswordOtpDto request
```

- **Business/validation:** Disabled by default (404). Check hash/expiry/attempts; consume OTP, change password and increment token version. Failed-attempt rollback flaw remains (A04).
- **Persistence/integration:** OTP/restaurant transaction.
- **Frontend consumer:** AuthModal → api.resetPasswordOtp.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /health

- **Handler/source:** [HealthController.health](../backend/src/main/java/com/atelier/media/web/HealthController.java#L16).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<Map<String, String>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
// No parameters
```

- **Business/validation:** Static lightweight UP response; does not query database or S3.
- **Persistence/integration:** None.
- **Frontend consumer:** External monitoring/manual; Render config actually uses Actuator health.
- **Mapped error branches:** 401 (credentials/token/current account invalid). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/media/presign

- **Handler/source:** [MediaController.presign](../backend/src/main/java/com/atelier/media/web/MediaController.java#L41).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 201; `ResponseEntity<PresignResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody PresignRequestDto request
```

- **Business/validation:** Current restaurant check and supplied category ownership; photo-only type/extension/size validation; allocate per-tenant dish code under restaurant lock; stage row and sign.
- **Persistence/integration:** restaurants.next_dish_seq + media_details PRESIGNED.
- **Frontend consumer:** upload.uploadDishPhoto → api.presign.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 409 (business/integrity/concurrent-write conflict); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/media/{id}/replace-presign

- **Handler/source:** [MediaController.replacePresign](../backend/src/main/java/com/atelier/media/web/MediaController.java#L53).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PresignResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                             @Valid @RequestBody ReplacePresignDto request
```

- **Business/validation:** Model delegates to existing direct-model permission path; photo stages a new pending key while old stays live.
- **Persistence/integration:** Owned media_details pending fields; S3 signing.
- **Frontend consumer:** VisualAssetReplacer / upload.replaceDishAsset → api.replacePresign.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/media/{id}/replace-confirm

- **Handler/source:** [MediaController.replaceConfirm](../backend/src/main/java/com/atelier/media/web/MediaController.java#L62).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                           @Valid @RequestBody ReplaceConfirmDto confirm
```

- **Business/validation:** Model delegates; photo verifies pending key, size and optional ETag; swaps metadata/counters then deletes previous asset.
- **Persistence/integration:** Owned media_details/restaurant counters; S3 HEAD/delete.
- **Frontend consumer:** upload.replaceDishAsset → api.replaceConfirm.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 422 (uploaded object could not be verified); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/media/confirm

- **Handler/source:** [MediaController.confirm](../backend/src/main/java/com/atelier/media/web/MediaController.java#L71).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody UploadConfirmDto confirm
```

- **Business/validation:** Resolve owned issued key; idempotent completed response; HEAD size; mark failure or publish and adjust usage; evict menu.
- **Persistence/integration:** media_details/restaurant usage transactions; S3 HEAD outside transaction.
- **Frontend consumer:** upload.uploadDishPhoto → api.confirm.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 422 (uploaded object could not be verified); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/public/menu/{slug}

- **Handler/source:** [PublicMenuController.menu](../backend/src/main/java/com/atelier/media/web/PublicMenuController.java#L40).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PublicMenuDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@PathVariable String slug
```

- **Business/validation:** Normalize slug; only ACTIVE/TRIAL and nondeleted restaurant; private menu returns branding/categories/zero dishes; public menu completed available rows up to 500; guest projection.
- **Persistence/integration:** restaurants, categories, media_details; ten-minute process cache.
- **Frontend consumer:** GuestMenuPage → api.publicMenu.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/public/analytics/event

- **Handler/source:** [PublicMenuController.recordAnalyticsEvent](../backend/src/main/java/com/atelier/media/web/PublicMenuController.java#L48).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 202; `ResponseEntity<Void>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
jakarta.servlet.http.HttpServletRequest request,
                                                     @Valid @RequestBody AnalyticsEventDto event
```

- **Business/validation:** Allowlist event, IP limit 60/min, restaurant/dish ownership, 30s dedup; asynchronous DB write; accepted is not durable acknowledgement.
- **Persistence/integration:** restaurants/media reads; event/counters/daily writes asynchronously.
- **Frontend consumer:** GuestMenuPage / DishSheet → api.trackEvent.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 404 (resource hidden/missing or reset disabled); 429 (throttled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/restaurants/{slug}/menu

- **Handler/source:** [RestaurantController.menu](../backend/src/main/java/com/atelier/media/web/RestaurantController.java#L33).
- **Access:** Public; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PublicMenuDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@PathVariable String slug,
                                              @RequestParam(required = false) DishCategory category,
                                              @RequestParam(defaultValue = "false") boolean arOnly
```

- **Business/validation:** Legacy public alias; enum category and arOnly filtering; reconstructs PublicMenuDto using two-arg constructor, losing custom category list/menuLive envelope state.
- **Persistence/integration:** Same cached menu read; filtering in Java.
- **Frontend consumer:** No current frontend consumer; legacy integrations/printed links compatibility.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/studio/menu-preview

- **Handler/source:** [StudioController.menuPreview](../backend/src/main/java/com/atelier/media/web/StudioController.java#L94).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<PublicMenuDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Current restaurant guard; staff projection bypasses live/private flag, still only completed available dishes.
- **Persistence/integration:** restaurants/media/categories.
- **Frontend consumer:** GuestMenuPage owner preview → api.studioMenuPreview.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/studio/dishes

- **Handler/source:** [StudioController.dishes](../backend/src/main/java/com/atelier/media/web/StudioController.java#L102).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<List<MediaResponseDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Current tenant completed dishes capped by menu-limit (500); resolve category names from tenant-scoped batch.
- **Persistence/integration:** media_details + categories.
- **Frontend consumer:** StudioPage → api.studioDishes.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/studio/dishes/{id}

- **Handler/source:** [StudioController.updateDish](../backend/src/main/java/com/atelier/media/web/StudioController.java#L107).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                       @Valid @RequestBody DishUpdateDto update
```

- **Business/validation:** Validate non-null category ownership before mutation; update title/price/category/description/tags/availability/placement/special; evict menu.
- **Persistence/integration:** Tenant-scoped media update; scoped category read.
- **Frontend consumer:** DishEditModal → api.updateDish.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PATCH /api/v1/studio/dishes/{id}/availability

- **Handler/source:** [StudioController.availability](../backend/src/main/java/com/atelier/media/web/StudioController.java#L113).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                         @Valid @RequestBody AvailabilityDto body
```

- **Business/validation:** Toggle tenant-owned completed dish availability and evict menu.
- **Persistence/integration:** media_details update.
- **Frontend consumer:** DishManager → api.setAvailability.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### DELETE /api/v1/studio/dishes/{id}

- **Handler/source:** [StudioController.deleteDish](../backend/src/main/java/com/atelier/media/web/StudioController.java#L119).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 204; `ResponseEntity<Void>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id
```

- **Business/validation:** Delete tenant dish and adjust usage transactionally; delete known objects afterwards; evict menu.
- **Persistence/integration:** media_details removal + restaurant counters; S3 cleanup.
- **Frontend consumer:** DishManager → api.deleteDish.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/studio/categories

- **Handler/source:** [StudioController.getCategories](../backend/src/main/java/com/atelier/media/web/StudioController.java#L127).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<List<CategoryDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Return ordered tenant categories; seeds five defaults when none exist; response deduplicates names. GET can write.
- **Persistence/integration:** categories lookup/conditional inserts.
- **Frontend consumer:** StudioPage → api.categories.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/categories

- **Handler/source:** [StudioController.createCategory](../backend/src/main/java/com/atelier/media/web/StudioController.java#L133).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 201; `ResponseEntity<CategoryDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody CategoryCreateDto dto
```

- **Business/validation:** Trim name; case-insensitive tenant duplicate check; default order count+1; active=true.
- **Persistence/integration:** categories insert.
- **Frontend consumer:** CategoryManagerModal → api.createCategory.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/studio/categories/{id}

- **Handler/source:** [StudioController.updateCategory](../backend/src/main/java/com/atelier/media/web/StudioController.java#L140).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<CategoryDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable Long id,
                                                      @Valid @RequestBody CategoryUpdateDto dto
```

- **Business/validation:** Tenant-scoped category lookup; duplicate-name check; update name/optional order/active flag; no menu eviction.
- **Persistence/integration:** categories update.
- **Frontend consumer:** CategoryManagerModal → api.updateCategory.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### DELETE /api/v1/studio/categories/{id}

- **Handler/source:** [StudioController.deleteCategory](../backend/src/main/java/com/atelier/media/web/StudioController.java#L148).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 204; `ResponseEntity<Void>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable Long id
```

- **Business/validation:** Tenant-scoped delete; FK sets associated media.category_id null. No menu eviction.
- **Persistence/integration:** categories delete; media FK action.
- **Frontend consumer:** CategoryManagerModal → api.deleteCategory.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/studio/categories/reorder

- **Handler/source:** [StudioController.reorderCategories](../backend/src/main/java/com/atelier/media/web/StudioController.java#L155).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200 when valid request; `ResponseEntity<List<CategoryDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody CategoryReorderDto dto
```

- **Business/validation:** DTO requires orderedIds; service updates known owned IDs, ignores others; no menu eviction. Client sends orderedCategoryIds: broken contract.
- **Persistence/integration:** categories ordered saveAll.
- **Frontend consumer:** CategoryManagerModal → api.reorderCategories.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/studio/menu-status

- **Handler/source:** [StudioController.updateMenuStatus](../backend/src/main/java/com/atelier/media/web/StudioController.java#L164).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MenuStatusUpdateDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                                @Valid @RequestBody MenuStatusUpdateDto dto
```

- **Business/validation:** Set current tenant is_menu_live and evict its menu. Guard now covers direct repository access.
- **Persistence/integration:** restaurants update.
- **Frontend consumer:** StudioTopBar / BrandCustomizer → api.setMenuStatus.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/dishes/{id}/model-request/presign

- **Handler/source:** [StudioController.presignModelRequest](../backend/src/main/java/com/atelier/media/web/StudioController.java#L178).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<List<ModelRequestPresignResponseDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody List<ModelRequestItemDto> items
```

- **Business/validation:** Between one and five unique recognized photo slots; image MIME/cap; owned dish; reject active request; persist private issued keys.
- **Persistence/integration:** restaurant/media reads, model_request_pending_keys insert.
- **Frontend consumer:** Request3dModal → api.presignModelRequest.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/dishes/{id}/model-request/confirm

- **Handler/source:** [StudioController.confirmModelRequest](../backend/src/main/java/com/atelier/media/web/StudioController.java#L187).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody ModelRequestConfirmDto dto
```

- **Business/validation:** Owned locked dish; issued private keys/prefix + HEAD/type/cap; write photo refs and pending request/history, delete pending keys.
- **Persistence/integration:** media_details/model_requests/pending_keys transaction; S3 HEAD inside transaction.
- **Frontend consumer:** Request3dModal → api.confirmModelRequest.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 422 (uploaded object could not be verified); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/studio/model-requests

- **Handler/source:** [StudioController.getStudioModelRequests](../backend/src/main/java/com/atelier/media/web/StudioController.java#L196).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<List<com.atelier.media.dto.ModelRequestHistoryDto>>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Tenant history first 100 rows with signed reference URLs.
- **Persistence/integration:** model_requests; decorated batch lookups.
- **Frontend consumer:** No named API client method/component consumer found.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/studio/analytics

- **Handler/source:** [StudioController.analytics](../backend/src/main/java/com/atelier/media/web/StudioController.java#L204).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<StudioAnalyticsDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Current tenant counts and model/menu ratio, top five dishes by views.
- **Persistence/integration:** restaurants counters + media_details top-five.
- **Frontend consumer:** StudioAnalytics → api.studioAnalytics.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/dishes/{id}/attach-3d

- **Handler/source:** [StudioController.attach3d](../backend/src/main/java/com/atelier/media/web/StudioController.java#L212).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 201; `ResponseEntity<PresignResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                       @Valid @RequestBody Attach3dRequestDto request
```

- **Business/validation:** Require can_upload_3d; validate GLB/self-contained glTF MIME/extension and 50 MiB cap; stage pending model and sign direct PUT.
- **Persistence/integration:** Owned completed media pending-model update; S3 local signing.
- **Frontend consumer:** ModelModal / upload.attachModel → api.attach3d.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/dishes/{id}/confirm-3d

- **Handler/source:** [StudioController.confirm3d](../backend/src/main/java/com/atelier/media/web/StudioController.java#L219).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                      @Valid @RequestBody ModelConfirmDto confirm
```

- **Business/validation:** Require can_upload_3d; validate issued pending key and exact HEAD size; swap model/counters in short transaction; cleanup old file and evict menu.
- **Persistence/integration:** media_details + restaurants usage; S3 HEAD outside transaction.
- **Frontend consumer:** upload.attachModel → api.confirm3d.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 422 (uploaded object could not be verified); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### DELETE /api/v1/studio/dishes/{id}/model

- **Handler/source:** [StudioController.removeModel](../backend/src/main/java/com/atelier/media/web/StudioController.java#L225).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<MediaResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable long id
```

- **Business/validation:** Remove tenant model and adjust counters; refuse legacy model-only dish until photo exists; cleanup after commit.
- **Persistence/integration:** media_details + restaurants; S3 delete.
- **Frontend consumer:** DishManager / ModelModal → api.removeModel.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### GET /api/v1/studio/settings

- **Handler/source:** [StudioController.settings](../backend/src/main/java/com/atelier/media/web/StudioController.java#L232).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<StudioSettingsDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt
```

- **Business/validation:** Return current tenant studio settings.
- **Persistence/integration:** RestaurantAccess restaurant read.
- **Frontend consumer:** StudioPage → api.settings.
- **Mapped error branches:** 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### PUT /api/v1/studio/settings

- **Handler/source:** [StudioController.updateSettings](../backend/src/main/java/com/atelier/media/web/StudioController.java#L237).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<StudioSettingsDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody StudioSettingsUpdateDto update
```

- **Business/validation:** Validated branding/contact/theme/layout/currency; optional logo/banner removal; adjust usage and evict menu.
- **Persistence/integration:** restaurants update/counters; S3 deleted assets after transaction.
- **Frontend consumer:** BrandCustomizer → api.updateSettings.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/branding/{kind}/presign

- **Handler/source:** [StudioController.presignBranding](../backend/src/main/java/com/atelier/media/web/StudioController.java#L243).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 201; `ResponseEntity<PresignResponseDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
                                                              @Valid @RequestBody BrandingAssetPresignDto request
```

- **Business/validation:** kind=logo or banner; image type/extension/max 4 MiB; UUID key in tenant branding folder.
- **Persistence/integration:** restaurant read; local signing, no pending branding row.
- **Frontend consumer:** uploadBrandingImage → api.brandingPresign.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 502 (storage SDK/signing operation failure); 503 (storage not configured). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

### POST /api/v1/studio/branding/{kind}/confirm

- **Handler/source:** [StudioController.confirmBranding](../backend/src/main/java/com/atelier/media/web/StudioController.java#L250).
- **Access:** Studio; shared role/account/audience and error rules above apply.
- **Success:** 200; `ResponseEntity<StudioSettingsDto>`. Response DTO fields are cataloged below.
- **Input declaration:**

```java
@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
                                                             @Valid @RequestBody BrandingAssetConfirmDto confirm
```

- **Business/validation:** Require tenant/kind prefix and no traversal; HEAD verifies allowed MIME/positive size/cap; swap branding/counters and clean previous key.
- **Persistence/integration:** restaurants + S3 HEAD/delete.
- **Frontend consumer:** uploadBrandingImage → api.brandingConfirm.
- **Mapped error branches:** 400 (malformed input/DTO or service field rule); 401 (credentials/token/current account invalid); 403 (role/audience/account access denied); 404 (resource hidden/missing or reset disabled); 409 (business/integrity/concurrent-write conflict); 422 (uploaded object could not be verified); 502 (storage SDK/signing operation failure). Generic framework/DB failures can additionally produce 5xx; source branches, not runtime fault injection, establish this list.

## Framework endpoints and unsupported routes

Actuator dependency + application.yml expose `/actuator/health`, health subpaths/probes and `/actuator/info`. They are permitted by SecurityConfig; actual health component details depend on Actuator authorization defaults. `/health` is the application-owned static endpoint. OPTIONS is globally permitted for CORS. All otherwise unmatched requests are denied. There is no application OpenAPI/Swagger generator dependency/configuration, public restaurant directory, registration, billing, email transport API, automatic model reconstruction, or table-ordering API.

## DTO field and validation catalog

Each declaration below lists actual record fields and annotations, not populated responses. Methods/overload constructors are omitted. PageDto also exposes a `content` JSON alias for its `items` through its annotated accessor. Guest MediaResponseDto is the same structural DTO with sensitive fields nulled/counters zeroed by DishMapper.toGuestDto; staff preview uses the full mapper. PublicMenuDto serializes **menuLive**, while guest UI incorrectly reads **isMenuLive** (PD01). DTO Password/token/property names describe the contract only; no values are supplied.

### AnalyticsEventDto

Source: [backend/src/main/java/com/atelier/media/dto/AnalyticsEventDto.java](../backend/src/main/java/com/atelier/media/dto/AnalyticsEventDto.java#L5).

```java
@NotBlank String slug,
        Long dishId,
        @NotBlank String eventType
```

### Attach3dRequestDto

Source: [backend/src/main/java/com/atelier/media/dto/Attach3dRequestDto.java](../backend/src/main/java/com/atelier/media/dto/Attach3dRequestDto.java#L10).

```java
@NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotNull @Positive Long uploadSizeBytes,
        @Pattern(regexp = "table|floor") String arPlacement
```

### AuthResponseDto

Source: [backend/src/main/java/com/atelier/media/dto/AuthResponseDto.java](../backend/src/main/java/com/atelier/media/dto/AuthResponseDto.java#L12).

```java
String token,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds,
        RestaurantDto restaurant,
        String impersonatedBy,
        boolean mustChangePassword
```

### AvailabilityDto

Source: [backend/src/main/java/com/atelier/media/dto/AvailabilityDto.java](../backend/src/main/java/com/atelier/media/dto/AvailabilityDto.java#L6).

```java
@NotNull Boolean available
```

### BrandingAssetConfirmDto

Source: [backend/src/main/java/com/atelier/media/dto/BrandingAssetConfirmDto.java](../backend/src/main/java/com/atelier/media/dto/BrandingAssetConfirmDto.java#L6).

```java
@NotBlank @Size(max = 512) String s3Key
```

### BrandingAssetPresignDto

Source: [backend/src/main/java/com/atelier/media/dto/BrandingAssetPresignDto.java](../backend/src/main/java/com/atelier/media/dto/BrandingAssetPresignDto.java#L9).

```java
@NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotNull @Positive Long uploadSizeBytes
```

### CategoryCreateDto

Source: [backend/src/main/java/com/atelier/media/dto/CategoryCreateDto.java](../backend/src/main/java/com/atelier/media/dto/CategoryCreateDto.java#L6).

```java
@NotBlank @Size(max = 100) String name,
        Integer displayOrder
```

### CategoryDto

Source: [backend/src/main/java/com/atelier/media/dto/CategoryDto.java](../backend/src/main/java/com/atelier/media/dto/CategoryDto.java#L5).

```java
Long id, Long restaurantId, String name, int displayOrder, boolean isActive
```

### CategoryReorderDto

Source: [backend/src/main/java/com/atelier/media/dto/CategoryReorderDto.java](../backend/src/main/java/com/atelier/media/dto/CategoryReorderDto.java#L6).

```java
@NotEmpty List<Long> orderedIds
```

### CategoryUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/CategoryUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/CategoryUpdateDto.java#L6).

```java
@NotBlank @Size(max = 100) String name,
        Integer displayOrder,
        Boolean isActive
```

### ChangeOwnPasswordDto

Source: [backend/src/main/java/com/atelier/media/dto/ChangeOwnPasswordDto.java](../backend/src/main/java/com/atelier/media/dto/ChangeOwnPasswordDto.java#L7).

```java
@NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String newPassword
```

### DishUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/DishUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/DishUpdateDto.java#L16).

```java
@NotBlank @Size(max = 160) String dishTitle,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal priceTag,
        DishCategory dishCategory,
        @Size(max = 2000) String description,
        @Size(max = 8) List<@Size(max = 30) String> dietaryTags,
        @NotNull Boolean available,
        @Pattern(regexp = "table|floor") String arPlacement,
        Boolean dailySpecial,
        Long categoryId
```

### ForgotPasswordDto

Source: [backend/src/main/java/com/atelier/media/dto/ForgotPasswordDto.java](../backend/src/main/java/com/atelier/media/dto/ForgotPasswordDto.java#L6).

```java
@NotBlank @Email String email
```

### LoginRequestDto

Source: [backend/src/main/java/com/atelier/media/dto/LoginRequestDto.java](../backend/src/main/java/com/atelier/media/dto/LoginRequestDto.java#L6).

```java
@NotBlank @Size(max = 160) String email,
        @NotBlank @Size(max = 72) String password
```

### MediaResponseDto

Source: [backend/src/main/java/com/atelier/media/dto/MediaResponseDto.java](../backend/src/main/java/com/atelier/media/dto/MediaResponseDto.java#L22).

```java
Long id,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String dishCode,
        String dishTitle,
        BigDecimal priceTag,
        DishCategory dishCategory,
        MediaType mediaType,
        UploadStatus uploadStatus,
        String contentType,
        String fileExtension,
        String originalFileName,
        String s3Key,
        String s3Url,
        boolean is3d,
        String modelUrl,
        String imageUrl,
        boolean hasArSupport,
        String arPlacement,
        String arScale,
        String arModes,
        Integer widthPx,
        Integer heightPx,
        Long fileSizeBytes,
        Long originalSizeBytes,
        Integer compressionSavedPercent,
        String etag,
        Instant createdAt,
        Instant completedAt,
        String description,
        List<String> dietaryTags,
        boolean available,
        boolean dailySpecial,
        Long modelFileSizeBytes,
        boolean modelPending,
        Long categoryId,
        String categoryName,
        String modelRequestStatus,
        String modelRequestNotes,
        Instant modelRequestedAt,
        Instant modelCompletedAt,
        Long viewsCount,
        Long modelViewsCount,
        Long arLaunchesCount
```

### MenuStatusUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/MenuStatusUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/MenuStatusUpdateDto.java#L5).

```java
@NotNull Boolean isMenuLive
```

### MessageResponseDto

Source: [backend/src/main/java/com/atelier/media/dto/MessageResponseDto.java](../backend/src/main/java/com/atelier/media/dto/MessageResponseDto.java#L3).

```java
String message
```

### ModelConfirmDto

Source: [backend/src/main/java/com/atelier/media/dto/ModelConfirmDto.java](../backend/src/main/java/com/atelier/media/dto/ModelConfirmDto.java#L7).

```java
@NotBlank @Size(max = 500) String s3Key, @Size(max = 128) String etag
```

### ModelRequestConfirmDto

Source: [backend/src/main/java/com/atelier/media/dto/ModelRequestConfirmDto.java](../backend/src/main/java/com/atelier/media/dto/ModelRequestConfirmDto.java#L5).

```java
List<UploadedPhotoRef> photos,
        String notes
```

### UploadedPhotoRef

Source: [backend/src/main/java/com/atelier/media/dto/ModelRequestConfirmDto.java](../backend/src/main/java/com/atelier/media/dto/ModelRequestConfirmDto.java#L9).

```java
String slot, String s3Key, String s3Url
```

### ModelRequestHistoryDto

Source: [backend/src/main/java/com/atelier/media/dto/ModelRequestHistoryDto.java](../backend/src/main/java/com/atelier/media/dto/ModelRequestHistoryDto.java#L5).

```java
Long id,
        Long restaurantId,
        String restaurantName,
        String restaurantSlug,
        Long dishId,
        String dishCode,
        String dishTitle,
        String dishImageUrl,
        String status,
        String requestType,
        String referencePhotos,
        String notes,
        String adminNotes,
        String completedModelKey,
        String completedModelUrl,
        Instant requestedAt,
        Instant completedAt
```

### ModelRequestItemDto

Source: [backend/src/main/java/com/atelier/media/dto/ModelRequestItemDto.java](../backend/src/main/java/com/atelier/media/dto/ModelRequestItemDto.java#L9).

```java
@NotBlank
        @Pattern(regexp = "^(front(_45)?|top(_90)?|back(_45)?|left(_side)?|right(_side)?)$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Slot must be one of: front, top, back, left, right")
        String slot,

        @NotBlank
        @Size(max = 255, message = "Filename must not exceed 255 characters")
        String filename,

        @NotBlank
        @Pattern(regexp = "^(image/webp|image/jpeg|image/png)$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Content type must be image/webp, image/jpeg, or image/png")
        String contentType,

        @NotNull
        @Positive
        Long uploadSizeBytes
```

### ModelRequestPresignResponseDto

Source: [backend/src/main/java/com/atelier/media/dto/ModelRequestPresignResponseDto.java](../backend/src/main/java/com/atelier/media/dto/ModelRequestPresignResponseDto.java#L5).

```java
String slot,
        String uploadUrl,
        String objectKey,
        Map<String, String> requiredHeaders,
        long expiresInSeconds
```

### PresignRequestDto

Source: [backend/src/main/java/com/atelier/media/dto/PresignRequestDto.java](../backend/src/main/java/com/atelier/media/dto/PresignRequestDto.java#L26).

```java
@NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotBlank @Size(max = 160) String dishTitle,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal priceTag,
        DishCategory dishCategory,
        @NotNull MediaType mediaType,
        @Positive Long originalSizeBytes,
        @NotNull @Positive Long uploadSizeBytes,
        @Pattern(regexp = "table|floor") String arPlacement,
        @Size(max = 2000) String description,
        @Size(max = 8) List<@Size(max = 30) String> dietaryTags,
        Boolean available,
        Long categoryId
```

### PresignResponseDto

Source: [backend/src/main/java/com/atelier/media/dto/PresignResponseDto.java](../backend/src/main/java/com/atelier/media/dto/PresignResponseDto.java#L16).

```java
String uploadUrl,
        String objectKey,
        String s3Url,
        Long mediaId,
        Instant expiresAt,
        long expiresInSeconds,
        Map<String, String> requiredHeaders
```

### PublicMenuDto

Source: [backend/src/main/java/com/atelier/media/dto/PublicMenuDto.java](../backend/src/main/java/com/atelier/media/dto/PublicMenuDto.java#L6).

```java
RestaurantBrandingDto restaurant,
        List<MediaResponseDto> items,
        List<CategoryDto> categories,
        boolean menuLive
```

### ReplaceAssetKind

Source: [backend/src/main/java/com/atelier/media/dto/ReplaceAssetKind.java](../backend/src/main/java/com/atelier/media/dto/ReplaceAssetKind.java); enum/type, see its declaration.

### ReplaceConfirmDto

Source: [backend/src/main/java/com/atelier/media/dto/ReplaceConfirmDto.java](../backend/src/main/java/com/atelier/media/dto/ReplaceConfirmDto.java#L12).

```java
@NotNull ReplaceAssetKind assetType,
        @NotBlank @Size(max = 512) String s3Key,
        @Size(max = 128) String etag,
        @Positive Integer widthPx,
        @Positive Integer heightPx
```

### ReplacePresignDto

Source: [backend/src/main/java/com/atelier/media/dto/ReplacePresignDto.java](../backend/src/main/java/com/atelier/media/dto/ReplacePresignDto.java#L15).

```java
@NotNull ReplaceAssetKind assetType,
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotNull @Positive Long uploadSizeBytes,
        @Positive Long originalSizeBytes,
        @Pattern(regexp = "table|floor") String arPlacement
```

### ResetPasswordOtpDto

Source: [backend/src/main/java/com/atelier/media/dto/ResetPasswordOtpDto.java](../backend/src/main/java/com/atelier/media/dto/ResetPasswordOtpDto.java#L7).

```java
@NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 10) String otp,
        @NotBlank @Size(min = 8, max = 100) String newPassword
```

### RestaurantBrandingDto

Source: [backend/src/main/java/com/atelier/media/dto/RestaurantBrandingDto.java](../backend/src/main/java/com/atelier/media/dto/RestaurantBrandingDto.java#L6).

```java
Long id,
        String name,
        String slug,
        String tagline,
        String bio,
        String openingHours,
        String logoUrl,
        String bannerUrl,
        String phoneNumber,
        String whatsappNumber,
        String instagramHandle,
        String facebookUrl,
        String websiteUrl,
        String tiktokHandle,
        String googleMapsUrl,
        String themeAccent,
        String menuLayout,
        String currencyCode,
        String currencySymbol,
        boolean isMenuLive
```

### RestaurantDto

Source: [backend/src/main/java/com/atelier/media/dto/RestaurantDto.java](../backend/src/main/java/com/atelier/media/dto/RestaurantDto.java#L9).

```java
Long id, String name, String slug, String ownerName, String email,
                            RestaurantStatus status, Instant createdAt, boolean mustChangePassword,
                            boolean canUpload3d
```

### SlugAvailabilityDto

Source: [backend/src/main/java/com/atelier/media/dto/SlugAvailabilityDto.java](../backend/src/main/java/com/atelier/media/dto/SlugAvailabilityDto.java#L4).

```java
String slug, boolean available, String reason
```

### StudioAnalyticsDto

Source: [backend/src/main/java/com/atelier/media/dto/StudioAnalyticsDto.java](../backend/src/main/java/com/atelier/media/dto/StudioAnalyticsDto.java#L5).

```java
long totalMenuViews,
        long totalModelViews,
        long totalArLaunches,
        double engagement3dRatePercent,
        List<TopDishDto> topDishes
```

### StudioSettingsDto

Source: [backend/src/main/java/com/atelier/media/dto/StudioSettingsDto.java](../backend/src/main/java/com/atelier/media/dto/StudioSettingsDto.java#L7).

```java
RestaurantBrandingDto branding,
        String ownerName,
        String email,
        RestaurantStatus status,
        long storageUsedBytes,
        int dishCount,
        int model3dCount,
        boolean canUpload3d
```

### StudioSettingsUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/StudioSettingsUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/StudioSettingsUpdateDto.java#L12).

```java
@NotBlank @Size(max = 120) String name,
        @Size(max = 160) String tagline,
        @Size(max = 2000) String bio,
        @Size(max = 500) String openingHours,
        @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{6,30}$", message = "use digits, spaces, + ( ) -") String phoneNumber,
        @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{6,30}$", message = "use digits with country code, e.g. +92 300 1234567") String whatsappNumber,
        @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "use your Instagram handle, e.g. @bistro") String instagramHandle,
        @Pattern(regexp = "^$|^https?://[^\\s<>\"']{3,240}$", message = "must start with https://") String facebookUrl,
        @Pattern(regexp = "^$|^https?://[^\\s<>\"']{3,240}$", message = "must start with https://") String websiteUrl,
        @Pattern(regexp = "^$|^@?[A-Za-z0-9._]{1,30}$", message = "use your TikTok handle, e.g. @bistro") String tiktokHandle,
        @Pattern(regexp = "^$|^https://(www\\.google\\.[a-z.]{2,10}/maps|maps\\.google\\.[a-z.]{2,10}|maps\\.app\\.goo\\.gl|goo\\.gl/maps|maps\\.apple\\.com)[^\\s<>\"']{0,450}$",
                message = "paste a Google Maps or Apple Maps share link (https://maps.app.goo.gl/...)") String googleMapsUrl,
        @Pattern(regexp = "amber|emerald|gold|zinc") String themeAccent,
        @Pattern(regexp = "cards|compact") String menuLayout,
        @Pattern(regexp = "^[A-Z]{3}$", message = "use a 3-letter currency code, e.g. USD") String currencyCode,
        @Size(max = 10) String currencySymbol,
        Boolean removeLogo,
        Boolean removeBanner
```

### TopDishDto

Source: [backend/src/main/java/com/atelier/media/dto/TopDishDto.java](../backend/src/main/java/com/atelier/media/dto/TopDishDto.java#L3).

```java
Long id,
        String title,
        String imageUrl,
        long viewsCount,
        long modelViewsCount,
        long arLaunchesCount
```

### UploadConfirmDto

Source: [backend/src/main/java/com/atelier/media/dto/UploadConfirmDto.java](../backend/src/main/java/com/atelier/media/dto/UploadConfirmDto.java#L11).

```java
@NotBlank @Size(max = 512) String s3Key,
        @Size(max = 128) String etag,
        @Positive Long finalSizeBytes,
        @Positive Integer widthPx,
        @Positive Integer heightPx,
        Boolean hasArSupport
```

### AdminAuthResponseDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminAuthResponseDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminAuthResponseDto.java#L5).

```java
String token, String tokenType, Instant expiresAt, long expiresInSeconds, AdminProfileDto admin
```

### AdminModelApproveDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminModelApproveDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminModelApproveDto.java#L6).

```java
@NotBlank(message = "S3 key is required")
        @Size(max = 512, message = "S3 key must not exceed 512 characters")
        String s3Key,
        String etag,
        String arPlacement
```

### AdminModelRequestDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminModelRequestDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminModelRequestDto.java#L6).

```java
Long dishId,
        Long restaurantId,
        String restaurantName,
        String restaurantSlug,
        String dishCode,
        String dishTitle,
        String photoUrl,
        String status,
        String notes,
        Instant requestedAt,
        Instant completedAt,
        List<PhotoSlotDto> photos
```

### PhotoSlotDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminModelRequestDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminModelRequestDto.java#L26).

```java
String slot, String s3Key, String s3Url
```

### AdminNotesUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminNotesUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminNotesUpdateDto.java#L3).

```java
String notes
```

### AdminPermission3dDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminPermission3dDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminPermission3dDto.java#L3).

```java
Boolean canUpload3d
```

### AdminProfileDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminProfileDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminProfileDto.java#L7).

```java
Long id, String email, String displayName, boolean mustChangePassword, Instant lastLoginAt
```

### AdminRestaurantCreateDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantCreateDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantCreateDto.java#L17).

```java
@NotBlank @Size(max = 120) String name,
        @Size(max = 60) String slug,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 160) String email,
        @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{6,30}$", message = "use digits, spaces, + ( ) -") String phoneNumber,
        RestaurantStatus status,
        Boolean canUpload3d
```

### AdminRestaurantCreatedDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantCreatedDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantCreatedDto.java#L7).

```java
AdminRestaurantDto restaurant, String temporaryPassword
```

### AdminRestaurantDetailDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantDetailDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantDetailDto.java#L9).

```java
AdminRestaurantDto restaurant,
        RestaurantBrandingDto branding,
        long photoBytes,
        long modelBytes,
        long brandingBytes,
        long pendingUploads,
        List<MediaResponseDto> dishes,
        List<AuditEntryDto> recentAudit,
        String adminNotes
```

### AdminRestaurantDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantDto.java#L14).

```java
Long id,
        String name,
        String slug,
        String ownerName,
        String email,
        RestaurantStatus status,
        int dishCount,
        int model3dCount,
        long storageUsedBytes,
        String bandwidthTier,
        String logoUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        boolean mustChangePassword,
        String phoneNumber,
        String adminNotes,
        boolean isMenuLive,
        boolean canUpload3d
```

### AdminRestaurantUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AdminRestaurantUpdateDto.java#L8).

```java
@NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 60) String slug,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 160) String email,
        String adminNotes,
        Boolean canUpload3d
```

### AuditEntryDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/AuditEntryDto.java](../backend/src/main/java/com/atelier/media/dto/admin/AuditEntryDto.java#L7).

```java
String adminEmail, String action, String detail, Instant createdAt
```

### ChangePasswordDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/ChangePasswordDto.java](../backend/src/main/java/com/atelier/media/dto/admin/ChangePasswordDto.java#L6).

```java
@NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 10, max = 72, message = "must be 10-72 characters") String newPassword
```

### PageDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/PageDto.java](../backend/src/main/java/com/atelier/media/dto/admin/PageDto.java#L10).

```java
List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
```

### PlatformAnalyticsDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/PlatformAnalyticsDto.java](../backend/src/main/java/com/atelier/media/dto/admin/PlatformAnalyticsDto.java#L6).

```java
long totalRestaurants,
        long activeRestaurants,
        long trialRestaurants,
        long pendingRestaurants,
        long suspendedRestaurants,
        long totalDishes,
        long total3dModels,
        long totalStorageBytes,
        List<AdminRestaurantDto> topByStorage,
        List<AdminRestaurantDto> topByDishes,
        Instant generatedAt,
        long modelsInProduction,
        long modelsPublished,
        long totalArLaunches,
        long totalMenuViews
```

### RestaurantStatusUpdateDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/RestaurantStatusUpdateDto.java](../backend/src/main/java/com/atelier/media/dto/admin/RestaurantStatusUpdateDto.java#L7).

```java
@NotNull RestaurantStatus status, @Size(max = 300) String reason
```

### TemporaryPasswordDto

Source: [backend/src/main/java/com/atelier/media/dto/admin/TemporaryPasswordDto.java](../backend/src/main/java/com/atelier/media/dto/admin/TemporaryPasswordDto.java#L4).

```java
AdminRestaurantDto restaurant, String temporaryPassword
```


## Enum and compatibility notes

ReplaceAssetKind is PHOTO or MODEL. DishCategory is STARTERS, MAINS, CHEF_SPECIALS, DESSERTS, DRINKS. MediaType is IMAGE_2D, MODEL_3D, DAILY_SPECIAL; new dish presign rejects MODEL_3D. UploadStatus is PRESIGNED, COMPLETED, FAILED. RestaurantStatus is ACTIVE, TRIAL, PENDING, PENDING_ONBOARDING, SUSPENDED; public visibility only ACTIVE/TRIAL/nondeleted. Analytics types are MENU_VIEW, MODEL_3D_VIEW, AR_LAUNCH, parsed case-normalized by service. These values do not establish automatic lifecycle transitions or subscription billing.

A supported signed URL operation can still fail through credential resolution or AWS control-plane behavior; an SDK 502 is not proof of application authorization failure. For public endpoints, 401 can occur if a caller supplies an invalid bearer even though no bearer is required. Health itself has no data/business validation or DB readiness guarantee.
