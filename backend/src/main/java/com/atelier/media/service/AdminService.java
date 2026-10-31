package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.AuthResponseDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.RestaurantBrandingDto;
import com.atelier.media.dto.RestaurantDto;
import com.atelier.media.dto.admin.AdminRestaurantCreateDto;
import com.atelier.media.dto.admin.AdminRestaurantCreatedDto;
import com.atelier.media.dto.admin.AdminRestaurantDetailDto;
import com.atelier.media.dto.admin.AdminRestaurantDto;
import com.atelier.media.dto.admin.AdminRestaurantUpdateDto;
import com.atelier.media.dto.admin.AuditEntryDto;
import com.atelier.media.dto.admin.PageDto;
import com.atelier.media.dto.admin.PlatformAnalyticsDto;
import com.atelier.media.dto.admin.RestaurantStatusUpdateDto;
import com.atelier.media.dto.admin.TemporaryPasswordDto;
import com.atelier.media.dto.SlugAvailabilityDto;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidFieldException;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.model.AdminAuditLog;
import com.atelier.media.model.Media;
import com.atelier.media.model.Restaurant;
import com.atelier.media.model.RestaurantStatus;
import com.atelier.media.model.UploadStatus;
import com.atelier.media.repository.AdminAuditLogRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.AdminPrincipal;
import com.atelier.media.security.TokenService;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Super-admin operations across all tenants. Every mutation is written to admin_audit_log. */
@Slf4j
@Service
public class AdminService {
    private EntitlementService entitlements;
    @org.springframework.beans.factory.annotation.Autowired
    public void setEntitlements(EntitlementService entitlements) { this.entitlements=entitlements; }


    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name", "createdAt", "createdAt", "storage", "storageUsedBytes",
            "dishes", "dishCount", "models", "model3dCount", "status", "status");
    static final String TEMP_PREFIX = "Temp-";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final RestaurantRepository restaurantRepository;
    private final MediaRepository mediaRepository;
    private final AdminAuditLogRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final S3Storage storage;
    private final StorageJanitor janitor;
    private final MenuCache menuCache;
    private final AtelierProperties props;
    private final TransactionTemplate tx;
    private final AnalyticsService analyticsService;
    private final SecureRandom random = new SecureRandom();

    public AdminService(RestaurantRepository restaurantRepository,
                        MediaRepository mediaRepository,
                        AdminAuditLogRepository auditRepository,
                        PasswordEncoder passwordEncoder,
                        TokenService tokenService,
                        S3Storage storage,
                        StorageJanitor janitor,
                        MenuCache menuCache,
                        AtelierProperties props,
                        TransactionTemplate tx) {
        this(restaurantRepository, mediaRepository, auditRepository, passwordEncoder, tokenService,
                storage, janitor, menuCache, props, tx, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(RestaurantRepository restaurantRepository,
                        MediaRepository mediaRepository,
                        AdminAuditLogRepository auditRepository,
                        PasswordEncoder passwordEncoder,
                        TokenService tokenService,
                        S3Storage storage,
                        StorageJanitor janitor,
                        MenuCache menuCache,
                        AtelierProperties props,
                        TransactionTemplate tx,
                        AnalyticsService analyticsService) {
        this.restaurantRepository = restaurantRepository;
        this.mediaRepository = mediaRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.storage = storage;
        this.janitor = janitor;
        this.menuCache = menuCache;
        this.props = props;
        this.tx = tx;
        this.analyticsService = analyticsService;
    }

    // ------------------------------------------------------------------ analytics

    @Transactional(readOnly = true)
    public PlatformAnalyticsDto analytics() {
        if (analyticsService != null) {
            return analyticsService.getPlatformAnalytics();
        }
        long total = restaurantRepository.countByDeletedAtIsNull();
        long active = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.ACTIVE);
        long trial = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.TRIAL);
        long pending = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.PENDING)
                + restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.PENDING_ONBOARDING);
        long suspended = restaurantRepository.countByStatusAndDeletedAtIsNull(RestaurantStatus.SUSPENDED);
        Number totalDishes = restaurantRepository.totalDishes();
        Number totalModels = restaurantRepository.totalModels();
        Number totalStorage = restaurantRepository.totalStorageBytes();
        return new PlatformAnalyticsDto(
                total, active, trial, pending, suspended,
                asLong(totalDishes), asLong(totalModels), asLong(totalStorage),
                restaurantRepository.findTop5ByDeletedAtIsNullOrderByStorageUsedBytesDesc().stream().map(AdminRestaurantDto::from).toList(),
                restaurantRepository.findTop5ByDeletedAtIsNullOrderByDishCountDesc().stream().map(AdminRestaurantDto::from).toList(),
                Instant.now(), 0L, asLong(totalModels), 0L, 0L
        );
    }

    // ------------------------------------------------------------------ list / detail

    @Transactional(readOnly = true)
    public PageDto<AdminRestaurantDto> list(String q, RestaurantStatus status, boolean includeDeleted,
                                            int page, int size, String sort, boolean ascending) {
        Specification<Restaurant> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (!includeDeleted) {
                where.add(cb.isNull(root.get("deletedAt")));
            }
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.strip().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("slug")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("ownerName")), like, '\\')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        String property = SORTABLE.getOrDefault(sort == null ? "createdAt" : sort, "createdAt");
        Sort order = Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, property).and(Sort.by("id"));
        Page<Restaurant> result = restaurantRepository.findAll(spec,
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)), order));
        return PageDto.of(result, AdminRestaurantDto::from);
    }

    @Transactional(readOnly = true)
    public AdminRestaurantDetailDto detail(long id) {
        Restaurant r = require(id);
        List<Media> dishes = mediaRepository.findByRestaurantIdAndUploadStatusOrderByCreatedAtDesc(
                id, UploadStatus.COMPLETED, PageRequest.of(0, props.menuLimit()));
        long photoBytes = dishes.stream().mapToLong(m -> m.isLegacyModelOnly() || m.getFileSizeBytes() == null ? 0 : m.getFileSizeBytes()).sum();
        long modelBytes = dishes.stream().mapToLong(m -> m.getModelFileSizeBytes() == null ? 0 : m.getModelFileSizeBytes()).sum();
        long brandingBytes = nz(r.getLogoSizeBytes()) + nz(r.getBannerSizeBytes());
        List<MediaResponseDto> dishDtos = dishes.stream().map(DishMapper::toDto).toList();
        List<AuditEntryDto> audit = auditRepository.findByRestaurantIdOrderByCreatedAtDesc(id, PageRequest.of(0, 20))
                .stream().map(AuditEntryDto::from).toList();
        return new AdminRestaurantDetailDto(AdminRestaurantDto.from(r), RestaurantBrandingDto.from(r),
                photoBytes, modelBytes, brandingBytes,
                mediaRepository.countByRestaurantIdAndUploadStatus(id, UploadStatus.PRESIGNED),
                dishDtos, audit, r.getAdminNotes());
    }

    // ------------------------------------------------------------------ create / edit

    /**
     * Provisions a restaurant: generates a temporary password ({@code Temp-xxxxxxxxxx}), stores only
     * its BCrypt hash, sets {@code must_change_password} and returns the password once.
     */
    public AdminRestaurantCreatedDto create(AdminRestaurantCreateDto dto, AdminPrincipal admin) {
        String email = AuthService.normalizeEmail(dto.email());
        String slug = resolveSlug(dto.slug(), dto.name(), null);
        if (restaurantRepository.existsByEmail(email)) {
            throw new ConflictException("email", "A restaurant with this email already exists.");
        }
        String password = temporaryPassword();
        Restaurant restaurant = Restaurant.builder()
                .name(dto.name().strip())
                .slug(slug)
                .ownerName(dto.ownerName().strip())
                .email(email)
                .phoneNumber(MediaService.blankToNull(dto.phoneNumber()))
                .passwordHash(passwordEncoder.encode(password))
                .mustChangePassword(true)
                .status(dto.status() == null ? RestaurantStatus.ACTIVE : dto.status())
                .canUpload3d(Boolean.TRUE.equals(dto.canUpload3d()))
                .build();
        Restaurant saved;
        try {
            saved = tx.execute(s -> {
                Restaurant r = restaurantRepository.saveAndFlush(restaurant);
                if(entitlements!=null)entitlements.provision(r.getId(),dto.planRevisionId(),dto.initialOverrides(),admin);
                audit(admin, "CREATE", r.getId(), "Provisioned /r/" + r.getSlug() + " (" + r.getStatus() + ") with a temporary password");
                return r;
            });
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("slug", "That email or menu address was just taken. Please try another.");
        }
        log.info("Admin {} provisioned restaurant {} ({})", admin.email(), saved.getSlug(), saved.getId());
        return new AdminRestaurantCreatedDto(AdminRestaurantDto.from(saved), password);
    }

    /** Issues a new temporary password (owner locked out or forgot it). Existing studio sessions stop working. */
    public TemporaryPasswordDto resetOwnerPassword(long id, AdminPrincipal admin) {
        String password = temporaryPassword();
        String hash = passwordEncoder.encode(password);
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            if (r.isDeleted()) {
                throw new ConflictException("status", "Restore this restaurant before resetting its password.");
            }
            r.setPasswordHash(hash);
            r.setMustChangePassword(true);
            r.incrementTokenVersion();
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "RESET_PASSWORD", id, "Issued a new temporary password for /r/" + r.getSlug());
            return updated;
        });
        log.warn("Admin {} reset the owner password of {}", admin.email(), saved.getSlug());
        return new TemporaryPasswordDto(AdminRestaurantDto.from(saved), password);
    }

    /** Live "/r/{slug} is available" check for the provisioning and edit forms. */
    @Transactional(readOnly = true)
    public SlugAvailabilityDto slugAvailability(String requested, Long excludeId) {
        String slug = SlugPolicy.normalize(requested);
        String problem = SlugPolicy.problem(slug);
        if (problem != null) {
            return new SlugAvailabilityDto(slug, false, problem);
        }
        boolean taken = excludeId == null ? restaurantRepository.existsBySlug(slug) : restaurantRepository.existsBySlugAndIdNot(slug, excludeId);
        return taken
                ? new SlugAvailabilityDto(slug, false, "/r/" + slug + " is already taken.")
                : new SlugAvailabilityDto(slug, true, null);
    }

    public AdminRestaurantDto update(long id, AdminRestaurantUpdateDto dto, AdminPrincipal admin) {
        String email = AuthService.normalizeEmail(dto.email());
        String[] oldSlug = new String[1];
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            oldSlug[0] = r.getSlug();
            String slug = resolveSlug(dto.slug(), dto.name(), id);
            if (restaurantRepository.existsByEmailAndIdNot(email, id)) {
                throw new ConflictException("email", "Another restaurant already uses this email.");
            }
            r.setName(dto.name().strip());
            r.setOwnerName(dto.ownerName().strip());
            if (!r.getEmail().equals(email)) {
                r.setEmail(email);
                r.incrementTokenVersion();
            }
            r.setSlug(slug);
            if (dto.adminNotes() != null) {
                r.setAdminNotes(dto.adminNotes().strip());
            }
            if (dto.canUpload3d() != null) {
                if(entitlements!=null)entitlements.setOwnerUploadGrant(id,dto.canUpload3d(),admin);
                r.setCanUpload3d(dto.canUpload3d());
            }
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "UPDATE", id, oldSlug[0].equals(slug) ? "Profile edited" : "Slug /r/" + oldSlug[0] + " -> /r/" + slug);
            return updated;
        });
        menuCache.evict(oldSlug[0], saved.getSlug());
        return AdminRestaurantDto.from(saved);
    }

    public AdminRestaurantDto updateNotes(long id, String notes, AdminPrincipal admin) {
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            r.setAdminNotes(notes != null ? notes.strip() : null);
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "UPDATE_NOTES", id, "Updated internal CRM notes");
            return updated;
        });
        return AdminRestaurantDto.from(saved);
    }

    public AdminRestaurantDto update3dPermission(long id, boolean canUpload3d, AdminPrincipal admin) {
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            if(entitlements!=null)entitlements.setOwnerUploadGrant(id,canUpload3d,admin);
            r.setCanUpload3d(canUpload3d);
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "UPDATE_3D_PERMISSION", id, "Direct 3D model upload permission set to: " + canUpload3d);
            return updated;
        });
        menuCache.evict(saved.getSlug());
        log.info("Admin {} set 3D upload permission for restaurant {} to {}", admin.email(), saved.getSlug(), canUpload3d);
        return AdminRestaurantDto.from(saved);
    }

    public AdminRestaurantDto updateStatus(long id, RestaurantStatusUpdateDto dto, AdminPrincipal admin) {
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            RestaurantStatus previous = r.getStatus();
            r.setStatus(dto.status());
            if (dto.status() == RestaurantStatus.SUSPENDED) {
                r.incrementTokenVersion();
            }
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "STATUS", id, previous + " -> " + dto.status()
                    + (dto.reason() == null || dto.reason().isBlank() ? "" : " (" + dto.reason().strip() + ")"));
            return updated;
        });
        menuCache.evict(saved.getSlug());
        log.info("Admin {} set restaurant {} to {}", admin.email(), saved.getSlug(), dto.status());
        return AdminRestaurantDto.from(saved);
    }

    // ------------------------------------------------------------------ delete / restore

    /** Soft delete: hidden everywhere, sign-in blocked, files kept, restorable. */
    public AdminRestaurantDto softDelete(long id, AdminPrincipal admin) {
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            if (!r.isDeleted()) {
                r.setDeletedAt(Instant.now());
                r.setStatus(RestaurantStatus.SUSPENDED);
                r.incrementTokenVersion();
            }
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "SOFT_DELETE", id, "Soft-deleted /r/" + r.getSlug());
            return updated;
        });
        menuCache.evict(saved.getSlug());
        return AdminRestaurantDto.from(saved);
    }

    public AdminRestaurantDto restore(long id, AdminPrincipal admin) {
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            r.setDeletedAt(null);
            r.setStatus(RestaurantStatus.ACTIVE);
            Restaurant updated = restaurantRepository.save(r);
            audit(admin, "RESTORE", id, "Restored /r/" + r.getSlug());
            return updated;
        });
        menuCache.evict(saved.getSlug());
        return AdminRestaurantDto.from(saved);
    }

    public record HardDeleteResult(long restaurantId, String slug, int objectsScheduled) {
    }

    /**
     * Hard delete: the restaurant row (and, via ON DELETE CASCADE, every dish row) is removed in
     * one transaction; the S3 cleanup then runs asynchronously, outside any transaction.
     */
    public HardDeleteResult hardDelete(long id, AdminPrincipal admin) {
        Set<String> keys = new LinkedHashSet<>();
        String[] slug = new String[1];
        tx.executeWithoutResult(s -> {
            Restaurant r = require(id);
            slug[0] = r.getSlug();
            for (Media m : mediaRepository.findByRestaurantId(id)) {
                keys.add(m.getS3Key());
                if (m.getModelS3Key() != null) keys.add(m.getModelS3Key());
                if (m.getPendingModelS3Key() != null) keys.add(m.getPendingModelS3Key());
                if (m.getPendingImageS3Key() != null) keys.add(m.getPendingImageS3Key());
            }
            if (r.getLogoS3Key() != null) keys.add(r.getLogoS3Key());
            if (r.getBannerS3Key() != null) keys.add(r.getBannerS3Key());
            restaurantRepository.delete(r);
            audit(admin, "HARD_DELETE", id, "Deleted /r/" + r.getSlug() + " and scheduled " + keys.size() + " S3 object(s) for removal");
        });
        menuCache.evict(slug[0]);
        janitor.wipeRestaurantAssets(storage.bucket(), List.copyOf(keys), storage.tenantPrefix(slug[0]));
        log.warn("Admin {} hard-deleted restaurant {} ({}); {} object(s) scheduled for removal", admin.email(), slug[0], id, keys.size());
        return new HardDeleteResult(id, slug[0], keys.size());
    }

    // ------------------------------------------------------------------ maintenance / support

    /** Recomputes counters from the dish rows (repairs drift after manual DB edits). */
    public AdminRestaurantDto recalculateUsage(long id, AdminPrincipal admin) {
        Restaurant saved = tx.execute(s -> {
            Restaurant r = require(id);
            long dishBytes = asLong(mediaRepository.sumStorageBytes(id, UploadStatus.COMPLETED));
            long bytes = dishBytes + nz(r.getLogoSizeBytes()) + nz(r.getBannerSizeBytes());
            int dishes = (int) mediaRepository.countByRestaurantIdAndUploadStatus(id, UploadStatus.COMPLETED);
            int models = (int) mediaRepository.countByRestaurantIdAndUploadStatusAndModelS3KeyIsNotNull(id, UploadStatus.COMPLETED);
            restaurantRepository.setUsage(id, bytes, dishes, models);
            r.setStorageUsedBytes(bytes);
            r.setDishCount(dishes);
            r.setModel3dCount(models);
            audit(admin, "RECALCULATE", id, dishes + " dishes, " + models + " models, " + bytes + " bytes");
            return r;
        });
        return AdminRestaurantDto.from(saved);
    }

    /** Short-lived studio session for support ("log in as owner"). Audited. */
    public AuthResponseDto impersonate(long id, AdminPrincipal admin) {
        Restaurant r = require(id);
        if (r.isDeleted()) {
            throw new ConflictException("status", "Restore this restaurant before opening its studio.");
        }
        if (!r.getStatus().canUseStudio()) {
            throw new ConflictException("status", "This restaurant is suspended. Reactivate it before opening its studio.");
        }
        TokenService.IssuedToken token = tokenService.issueImpersonation(r, admin.email());
        tx.executeWithoutResult(s -> audit(admin, "IMPERSONATE", id, "Opened studio as owner of /r/" + r.getSlug()));
        log.warn("Admin {} opened the studio of {} (impersonation, {}s)", admin.email(), r.getSlug(), token.expiresInSeconds());
        return new AuthResponseDto(token.value(), "Bearer", token.expiresAt(), token.expiresInSeconds(), RestaurantDto.from(r), admin.email());
    }

    // ------------------------------------------------------------------ helpers

    private Restaurant require(long id) {
        return restaurantRepository.findById(id).orElseThrow(() -> new RestaurantNotFoundException("#" + id));
    }

    private String resolveSlug(String requested, String name, Long excludeId) {
        String slug = SlugPolicy.normalize(requested == null || requested.isBlank() ? name : requested);
        String problem = SlugPolicy.problem(slug);
        if (problem != null) {
            throw new InvalidFieldException("slug", problem);
        }
        boolean taken = excludeId == null ? restaurantRepository.existsBySlug(slug) : restaurantRepository.existsBySlugAndIdNot(slug, excludeId);
        if (taken) {
            throw new ConflictException("slug", "/r/" + slug + " is already taken.");
        }
        return slug;
    }

    private void audit(AdminPrincipal admin, String action, Long restaurantId, String detail) {
        auditRepository.save(AdminAuditLog.builder()
                .adminEmail(admin.email())
                .action(action)
                .restaurantId(restaurantId)
                .detail(detail.length() > 500 ? detail.substring(0, 500) : detail)
                .build());
    }

    /** {@code Temp-} plus 10 characters from an unambiguous alphabet (about 57 bits of entropy). */
    String temporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PREFIX);
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static long asLong(Number n) {
        return n == null ? 0 : n.longValue();
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
