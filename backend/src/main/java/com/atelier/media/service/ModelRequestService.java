package com.atelier.media.service;

import com.atelier.media.config.AtelierProperties;
import com.atelier.media.dto.Attach3dRequestDto;
import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.ModelRequestConfirmDto;
import com.atelier.media.dto.ModelRequestItemDto;
import com.atelier.media.dto.ModelRequestPresignResponseDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.admin.AdminModelApproveDto;
import com.atelier.media.dto.admin.AdminModelRequestDto;
import com.atelier.media.dto.admin.PageDto;
import com.atelier.media.dto.ModelRequestHistoryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.atelier.media.exception.ConflictException;
import com.atelier.media.exception.InvalidMediaException;
import com.atelier.media.exception.MediaNotFoundException;
import com.atelier.media.exception.RestaurantNotFoundException;
import com.atelier.media.model.Category;
import com.atelier.media.model.Media;
import com.atelier.media.model.ModelRequest;
import com.atelier.media.model.ModelRequestPendingKey;
import com.atelier.media.model.Restaurant;
import com.atelier.media.repository.CategoryRepository;
import com.atelier.media.repository.MediaRepository;
import com.atelier.media.repository.ModelRequestPendingKeyRepository;
import com.atelier.media.repository.ModelRequestRepository;
import com.atelier.media.repository.RestaurantRepository;
import com.atelier.media.security.AdminPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ModelRequestService {

    private static final Set<String> ALLOWED_SLOTS = Set.of("front", "top", "back", "left", "right");

    private final MediaRepository mediaRepository;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ModelRequestRepository modelRequestRepository;
    private final ModelRequestPendingKeyRepository pendingKeyRepository;
    private final S3Storage storage;
    private final MenuCache menuCache;
    private final ObjectMapper objectMapper;
    private final AtelierProperties props;

    private final org.springframework.transaction.support.TransactionTemplate tx;
    private final RestaurantAccess restaurantAccess;
    private final AdminAccess adminAccess;
    private final DurableCleanup cleanup;
    private final java.util.concurrent.atomic.AtomicLong referenceScanCursor=new java.util.concurrent.atomic.AtomicLong();

    public ModelRequestService(MediaRepository mediaRepository,
                               RestaurantRepository restaurantRepository,
                               CategoryRepository categoryRepository,
                               ModelRequestRepository modelRequestRepository,
                               ModelRequestPendingKeyRepository pendingKeyRepository,
                               S3Storage storage,
                               MenuCache menuCache,
                               ObjectMapper objectMapper,
                               AtelierProperties props,
                               org.springframework.transaction.support.TransactionTemplate tx,
                               RestaurantAccess restaurantAccess, AdminAccess adminAccess, DurableCleanup cleanup) {
        this.mediaRepository = mediaRepository;
        this.restaurantRepository = restaurantRepository;
        this.categoryRepository = categoryRepository;
        this.modelRequestRepository = modelRequestRepository;
        this.pendingKeyRepository = pendingKeyRepository;
        this.storage = storage;
        this.menuCache = menuCache;
        this.objectMapper = objectMapper;
        this.props = props; this.tx=tx; this.restaurantAccess=restaurantAccess; this.adminAccess=adminAccess; this.cleanup=cleanup;
    }

    public static String normalizeSlot(String slot) {
        if (slot == null) return null;
        String s = slot.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("front")) return "front";
        if (s.startsWith("top")) return "top";
        if (s.startsWith("back")) return "back";
        if (s.startsWith("left")) return "left";
        if (s.startsWith("right")) return "right";
        return s;
    }

    /** Presigns up to 5 reference photos for a 3D digital twin concierge request. */
    public List<ModelRequestPresignResponseDto> presignPhotos(Long restaurantId, Long dishId, List<ModelRequestItemDto> items) {
        storage.requireConfigured();
        if (items == null || items.isEmpty() || items.size() > 5) {
            throw new InvalidMediaException("Model request requires between 1 and 5 reference photos.");
        }

        Set<String> seenSlots = new HashSet<>();
        for (ModelRequestItemDto item : items) {
            String normSlot = normalizeSlot(item.slot());
            if (normSlot == null || !ALLOWED_SLOTS.contains(normSlot)) {
                throw new InvalidMediaException("Slot '" + item.slot() + "' is invalid. Allowed slots: " + ALLOWED_SLOTS);
            }
            if (!seenSlots.add(normSlot)) {
                throw new InvalidMediaException("Duplicate slot in request: " + item.slot());
            }
            String normCt = MediaRules.normalizeContentType(item.contentType());
            MediaRules.requireImage(normCt);
            MediaRules.requireMaxSize(item.uploadSizeBytes(), props.maxImageBytes(), "photo");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
        Media dish = mediaRepository.findByIdAndRestaurantId(dishId, restaurantId)
                .orElseThrow(() -> new MediaNotFoundException(dishId));

        if ("PENDING".equalsIgnoreCase(dish.getModelRequestStatus()) || "IN_PRODUCTION".equalsIgnoreCase(dish.getModelRequestStatus())) {
            throw new ConflictException("dishId", "A 3D model request is already in progress for this dish.");
        }

        List<ModelRequestPresignResponseDto> out = new ArrayList<>();
        List<ModelRequestPendingKey> pendingKeys = new ArrayList<>();
        String basePrefix = storage.privateTenantPrefix(restaurant.getSlug()) + "requests/" + dishId + "/";

        for (ModelRequestItemDto item : items) {
            String normSlot = normalizeSlot(item.slot());
            String slotClean = normSlot.replaceAll("[^a-z0-9]", "-");
            String key = basePrefix + "ref-" + slotClean + "-" + UUID.randomUUID().toString().substring(0, 8) + ".webp";
            S3Storage.PresignedUpload upload = storage.presignPut(key, item.contentType(), item.uploadSizeBytes());
            out.add(new ModelRequestPresignResponseDto(
                    item.slot(),
                    upload.url(),
                    key,
                    upload.requiredHeaders(),
                    upload.expiresInSeconds()
            ));
            pendingKeys.add(ModelRequestPendingKey.builder()
                    .restaurantId(restaurantId)
                    .dishId(dishId)
                    .s3Key(key)
                    .slot(normSlot)
                    .createdAt(Instant.now())
                    .build());
        }
        pendingKeyRepository.saveAll(pendingKeys);
        return out;
    }

    /** HEAD is detached; publication rechecks current identity and the exact issued operation. */
    public MediaResponseDto confirmRequest(com.atelier.media.security.Tenant tenant, Long dishId, ModelRequestConfirmDto dto) {
        requireDetached();
        Long restaurantId=tenant.restaurantId();
        Restaurant before=restaurantAccess.requireStudioAccess(tenant);
        if(dto.photos()==null || dto.photos().isEmpty() || dto.photos().size()>5) throw new InvalidMediaException("Must provide between 1 and 5 reference photos.");
        Media snapshot=mediaRepository.findByIdAndRestaurantId(dishId,restaurantId).orElseThrow(()->new MediaNotFoundException(dishId));
        var issued=pendingKeyRepository.findByRestaurantIdAndDishId(restaurantId,dishId);
        Set<String> used=new HashSet<>(); Set<String> slots=new HashSet<>();
        String prefix=storage.privateTenantPrefix(before.getSlug())+"requests/"+dishId+"/";
        List<ModelRequestConfirmDto.UploadedPhotoRef> verifiedPhotos=new ArrayList<>();
        for(var photo:dto.photos()) {
            String slot=normalizeSlot(photo.slot());
            if(photo.s3Key()==null || !photo.s3Key().startsWith(prefix) || !used.add(photo.s3Key()) || !ALLOWED_SLOTS.contains(slot) || !slots.add(slot) ||
                issued.stream().noneMatch(k->k.getS3Key().equals(photo.s3Key()) && slot.equals(k.getSlot())) || cleanup.isRetired(photo.s3Key()))
                throw new InvalidMediaException("Reference key is not currently issued for this request.");
            var head=storage.head(storage.bucket(),photo.s3Key());
            MediaRules.requireMaxSize(head.contentLength(),props.maxImageBytes(),"photo"); MediaRules.requireImage(MediaRules.normalizeContentType(head.contentType()));
            verifiedPhotos.add(new ModelRequestConfirmDto.UploadedPhotoRef(slot,photo.s3Key(),storage.publicUrl(photo.s3Key())));
        }
        return tx.execute(status -> {
            Restaurant restaurant=restaurantRepository.findByIdForUpdate(restaurantId).orElseThrow(()->new RestaurantNotFoundException(restaurantId));
            restaurantAccess.requireStudioAccess(tenant);
            Media dish=mediaRepository.findByIdAndRestaurantIdForUpdate(dishId,restaurantId).orElseThrow(()->new MediaNotFoundException(dishId));
            if(!java.util.Objects.equals(snapshot.getVersion(),dish.getVersion()) || !before.getSlug().equals(restaurant.getSlug()) ||
                "PENDING".equals(dish.getModelRequestStatus()) || "IN_PRODUCTION".equals(dish.getModelRequestStatus())) throw new ConflictException("dishId","Request state changed; refresh before confirming.");
            var fresh=pendingKeyRepository.findByRestaurantIdAndDishId(restaurantId,dishId);
            for(var photo:verifiedPhotos) if(cleanup.isRetired(photo.s3Key()) || fresh.stream().noneMatch(k->k.getS3Key().equals(photo.s3Key()) && photo.slot().equals(k.getSlot())))
                throw new ConflictException("dishId","Reference upload is no longer pending.");
            String photosJson = "[]";
            try {
                photosJson = objectMapper.writeValueAsString(verifiedPhotos);
                dish.setModelRequestPhotos(photosJson);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot serialize verified references; publication was cancelled", e);
            }
            dish.setModelRequestStatus("PENDING");
            dish.setModelRequestNotes(dto.notes() != null ? dto.notes().strip() : null);
            dish.setModelRequestedAt(Instant.now());
            Media saved = mediaRepository.save(dish);

            String reqType = dish.hasModel() ? ModelRequest.TYPE_REPLACEMENT : ModelRequest.TYPE_NEW;
            ModelRequest historyEntry = ModelRequest.builder()
                    .restaurantId(restaurantId)
                    .dishId(dishId)
                    .status(ModelRequest.STATUS_PENDING)
                    .requestType(reqType)
                    .referencePhotos(photosJson)
                    .notes(dish.getModelRequestNotes())
                    .requestedAt(Instant.now())
                    .build();
            modelRequestRepository.save(historyEntry);

            for(var abandoned:fresh) if(!used.contains(abandoned.getS3Key())) cleanup.record(restaurantId,storage.bucket(),abandoned.getS3Key(),"UNSELECTED_REFERENCE");
            pendingKeyRepository.deleteByDishId(dishId);

            String categoryName = resolveCategoryName(saved);
            return DishMapper.toDto(saved, categoryName);

        });
    }

    /** Super Admin: list all dishes currently in the 3D production pipeline (capped at 200). */
    @Transactional(readOnly = true)
    public List<AdminModelRequestDto> getPendingRequests() {
        List<Media> dishes = mediaRepository.findByModelRequestStatusOrderByModelRequestedAtDesc("PENDING", PageRequest.of(0, 200));
        Map<Long, Restaurant> restaurantMap = restaurantRepository.findAllById(
                dishes.stream().map(Media::getRestaurantId).distinct().toList()
        ).stream().collect(Collectors.toMap(Restaurant::getId, r -> r));

        List<AdminModelRequestDto> out = new ArrayList<>();
        for (Media d : dishes) {
            Restaurant r = restaurantMap.get(d.getRestaurantId());
            List<AdminModelRequestDto.PhotoSlotDto> photos = parsePhotos(d.getModelRequestPhotos());
            out.add(new AdminModelRequestDto(
                    d.getId(),
                    d.getRestaurantId(),
                    r != null ? r.getName() : "Unknown Restaurant",
                    r != null ? r.getSlug() : "unknown",
                    d.getDishCode(),
                    d.getDishTitle(),
                    d.getS3Url(),
                    d.getModelRequestStatus(),
                    d.getModelRequestNotes(),
                    d.getModelRequestedAt(),
                    d.getModelCompletedAt(),
                    photos
            ));
        }
        return out;
    }

    /** Super Admin: presigns finished .glb upload straight to S3 under restaurant namespace. */
    public PresignResponseDto presignModelUpload(Long dishId, Attach3dRequestDto request) {
        storage.requireConfigured();
        String ct = MediaRules.normalizeContentType(request.contentType());
        if (!"model/gltf-binary".equals(ct) && !"application/octet-stream".equals(ct) && !"model/gltf+json".equals(ct)) {
            throw new InvalidMediaException("3D models must be model/gltf-binary (.glb) or application/octet-stream.");
        }
        MediaRules.requireMaxSize(request.uploadSizeBytes(), props.maxPublishedModelBytes(), "3D model");

        Media dish = mediaRepository.findById(dishId)
                .orElseThrow(() -> new MediaNotFoundException(dishId));
        Restaurant restaurant = restaurantRepository.findById(dish.getRestaurantId())
                .orElseThrow(() -> new RestaurantNotFoundException(dish.getRestaurantId()));
        String s3Key = storage.newKey(restaurant.getSlug(), "models", "twin-", "glb");
        tx.executeWithoutResult(status -> {
            restaurantRepository.findByIdForUpdate(restaurant.getId()).orElseThrow();
            Media current=mediaRepository.findByIdAndRestaurantIdForUpdate(dishId,restaurant.getId()).orElseThrow();
            current.setPendingModelS3Key(s3Key); current.setPendingModelSizeBytes(request.uploadSizeBytes());
            current.setPendingModelContentType(ct); current.setPendingModelCreatedAt(Instant.now());
            mediaRepository.save(current);
        });
        S3Storage.PresignedUpload upload = storage.presignPut(s3Key, request.contentType(), request.uploadSizeBytes());
        return new PresignResponseDto(upload.url(), s3Key, storage.publicUrl(s3Key), dishId,
                upload.expiresAt(), upload.expiresInSeconds(), upload.requiredHeaders());
    }

    /** Detached verification followed by locked, current-account publication. */
    public MediaResponseDto approveModel(Long dishId, AdminModelApproveDto dto, AdminPrincipal admin) {
        requireDetached(); storage.requireConfigured(); adminAccess.requireAccount(admin);
        Media snapshot=mediaRepository.findById(dishId).orElseThrow(()->new MediaNotFoundException(dishId));
        Restaurant before=restaurantRepository.findById(snapshot.getRestaurantId()).orElseThrow();
        if(dto.s3Key()==null || !dto.s3Key().startsWith(storage.tenantPrefix(before.getSlug())+"models/") ||
            !dto.s3Key().equals(snapshot.getPendingModelS3Key()) || cleanup.isRetired(dto.s3Key())) throw new InvalidMediaException("Model upload is no longer issued for this dish.");
        HeadObjectResponse head=storage.head(storage.bucket(),dto.s3Key());
        MediaRules.requireMaxSize(head.contentLength(),props.maxPublishedModelBytes(),"3D model");
        long modelSize=head.contentLength();
        String ct=MediaRules.normalizeContentType(head.contentType());
        if(!Set.of("model/gltf-binary","application/octet-stream","model/gltf+json").contains(ct)) throw new InvalidMediaException("Invalid model content type.");
        return tx.execute(status -> {
            Restaurant restaurant=restaurantRepository.findByIdForUpdate(snapshot.getRestaurantId()).orElseThrow();
            adminAccess.requireAccountForUpdate(admin);
            if(restaurant.isDeleted() || !restaurant.getStatus().canUseStudio()) throw new ConflictException("status","Restaurant is unavailable.");
            Media dish=mediaRepository.findByIdAndRestaurantIdForUpdate(dishId,snapshot.getRestaurantId()).orElseThrow(()->new MediaNotFoundException(dishId));
            if(!java.util.Objects.equals(snapshot.getVersion(),dish.getVersion()) || !before.getSlug().equals(restaurant.getSlug()) || !dto.s3Key().equals(dish.getPendingModelS3Key()) ||
                !java.util.Objects.equals(snapshot.getModelRequestStatus(),dish.getModelRequestStatus()) || cleanup.isRetired(dto.s3Key()))
                throw new ConflictException("dishId","Model publication state changed; refresh before retrying.");
            if(dish.getPendingModelSizeBytes()!=null && dish.getPendingModelSizeBytes()!=modelSize) throw new InvalidMediaException("Model upload size changed.");
            String oldModelKey = dish.getModelS3Key();
            boolean isReplacement = oldModelKey != null && !oldModelKey.equals(dto.s3Key());

            long prevSize = dish.getModelFileSizeBytes() == null ? 0L : dish.getModelFileSizeBytes();
            long delta = modelSize - prevSize;
            int deltaModels = dish.hasModel() ? 0 : 1;

            dish.setPendingModelS3Key(null); dish.setPendingModelSizeBytes(null); dish.setPendingModelContentType(null); dish.setPendingModelCreatedAt(null);
            dish.setModelS3Key(dto.s3Key());
            dish.setModelS3Url(storage.publicUrl(dto.s3Key()));
            dish.setModelFileSizeBytes(modelSize);
            dish.setHasArSupport(true);
            if (dto.arPlacement() != null && !dto.arPlacement().isBlank()) {
                dish.setArPlacement(dto.arPlacement());
            }
            dish.setModelRequestStatus("COMPLETED");
            dish.setModelCompletedAt(Instant.now());
            Media saved = mediaRepository.save(dish);

            Optional<ModelRequest> activeReq = modelRequestRepository.findFirstByDishIdAndStatusOrderByRequestedAtDesc(dishId, ModelRequest.STATUS_PENDING);
            if (activeReq.isEmpty()) {
                activeReq = modelRequestRepository.findFirstByDishIdAndStatusOrderByRequestedAtDesc(dishId, ModelRequest.STATUS_IN_PRODUCTION);
            }
            Long currentReqId;
            if (activeReq.isPresent()) {
                ModelRequest req = activeReq.get();
                req.setStatus(ModelRequest.STATUS_COMPLETED);
                req.setCompletedModelKey(dto.s3Key());
                req.setCompletedModelUrl(storage.publicUrl(dto.s3Key()));
                req.setCompletedAt(Instant.now());
                currentReqId=modelRequestRepository.save(req).getId();
            } else {
                ModelRequest directReq = ModelRequest.builder()
                        .restaurantId(restaurant.getId())
                        .dishId(dishId)
                        .status(ModelRequest.STATUS_COMPLETED)
                        .requestType(isReplacement ? ModelRequest.TYPE_REPLACEMENT : ModelRequest.TYPE_NEW)
                        .completedModelKey(dto.s3Key())
                        .completedModelUrl(storage.publicUrl(dto.s3Key()))
                        .requestedAt(Instant.now())
                        .completedAt(Instant.now())
                        .build();
                currentReqId=modelRequestRepository.save(directReq).getId();
            }

            List<ModelRequest> allReqs = modelRequestRepository.findByDishIdOrderByRequestedAtDesc(dishId);
            for (ModelRequest r : allReqs) {
                if (currentReqId != null && !r.getId().equals(currentReqId) && ModelRequest.STATUS_COMPLETED.equals(r.getStatus())) {
                    r.setStatus(ModelRequest.STATUS_SUPERSEDED);
                    if (isReplacement && oldModelKey != null && oldModelKey.equals(r.getCompletedModelKey())) {
                        r.setNotes((r.getNotes() != null ? r.getNotes() + " " : "") + "[Model file replaced]");
                    }
                    modelRequestRepository.save(r);
                }
            }


            restaurantRepository.adjustUsage(restaurant.getId(), delta, 0, deltaModels);
            menuCache.evict(restaurant.getSlug());

            log.info("Admin {} approved 3D model for dish {} ({}) of restaurant {}",
                    admin.email(), dish.getDishTitle(), dish.getId(), restaurant.getSlug());

            String categoryName = resolveCategoryName(saved);
            return DishMapper.toDto(saved, categoryName);

        });
    }

    /** Returns full 3D model request and completion history for a restaurant. */
    @Transactional(readOnly = true)
    public List<ModelRequestHistoryDto> getHistoryForRestaurant(Long restaurantId) {
        return getAllHistory(restaurantId, null, 0, 100).content();
    }

    /** Returns paginated 3D model request and completion history across all restaurants (or filtered by restaurantId and status). */
    @Transactional(readOnly = true)
    public PageDto<ModelRequestHistoryDto> getAllHistory(Long restaurantId, String status, int page, int size) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.min(100, Math.max(1, size <= 0 ? 50 : size));
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        String sanitizedStatus = (status != null && !status.isBlank()) ? status.strip().toUpperCase(Locale.ROOT) : null;
        Page<ModelRequest> pageResult = modelRequestRepository.findFiltered(restaurantId, sanitizedStatus, pageable);

        Map<Long, Restaurant> restMap = restaurantRepository.findAllById(
                pageResult.getContent().stream().map(ModelRequest::getRestaurantId).distinct().toList()
        ).stream().collect(Collectors.toMap(Restaurant::getId, r -> r));

        Map<Long, Media> dishMap = mediaRepository.findAllById(
                pageResult.getContent().stream().map(ModelRequest::getDishId).distinct().toList()
        ).stream().collect(Collectors.toMap(Media::getId, d -> d));

        List<ModelRequestHistoryDto> out = new ArrayList<>();
        for (ModelRequest r : pageResult.getContent()) {
            Restaurant rest = restMap.get(r.getRestaurantId());
            Media d = dishMap.get(r.getDishId());
            String modelUrl = r.getCompletedModelUrl();
            if ((modelUrl == null || modelUrl.isBlank()) && r.getCompletedModelKey() != null) {
                modelUrl = storage.publicUrl(r.getCompletedModelKey());
            } else if ((modelUrl == null || modelUrl.isBlank()) && d != null) {
                modelUrl = d.getModelS3Url();
            }

            out.add(new ModelRequestHistoryDto(
                    r.getId(),
                    r.getRestaurantId(),
                    rest != null ? rest.getName() : "Unknown Restaurant",
                    rest != null ? rest.getSlug() : null,
                    r.getDishId(),
                    d != null ? d.getDishCode() : null,
                    d != null ? d.getDishTitle() : "Dish #" + r.getDishId(),
                    d != null ? d.getS3Url() : null,
                    r.getStatus(),
                    r.getRequestType(),
                    presignReferencePhotosJson(r.getReferencePhotos()),
                    r.getNotes(),
                    r.getAdminNotes(),
                    r.getCompletedModelKey(),
                    modelUrl,
                    r.getRequestedAt(),
                    r.getCompletedAt()
            ));
        }
        return new PageDto<>(out, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    /** Overload for callers specifying only restaurantId (default page 0, size 50). */
    @Transactional(readOnly = true)
    public PageDto<ModelRequestHistoryDto> getAllHistory(Long restaurantId) {
        return getAllHistory(restaurantId, null, 0, 50);
    }

    /** Release only eligible references; physical cleanup belongs to the durable worker. */
    public int cleanupReferencePhotos() {
        requireDetached();
        Instant cutoff=Instant.now().minus(14,ChronoUnit.DAYS);
        var candidates=mediaRepository.findExpiredReferenceCandidates(referenceScanCursor.get(),cutoff,PageRequest.of(0,100));
        if(candidates.isEmpty()) referenceScanCursor.set(0);
        int scheduled=0;
        for(Media candidate:candidates.stream().limit(100).toList()) {
            try {
            Integer changed=tx.execute(status -> {
                restaurantRepository.findByIdForUpdate(candidate.getRestaurantId()).orElseThrow();
                Media current=mediaRepository.findByIdAndRestaurantIdForUpdate(candidate.getId(),candidate.getRestaurantId()).orElseThrow();
                if(!"COMPLETED".equals(current.getModelRequestStatus()) || current.getModelCompletedAt()==null || !current.getModelCompletedAt().isBefore(cutoff)) return 0;
                Set<String> keys=AssetCleanupListener.photos(current.getModelRequestPhotos());
                try {
                    var raw=objectMapper.readTree(current.getModelRequestPhotos()==null?"[]":current.getModelRequestPhotos());
                    for(var node:raw) if(!node.hasNonNull("s3Key") || node.path("s3Key").asText().isBlank()) return 0;
                } catch(Exception e) { throw new IllegalStateException("Malformed references require manual repair"); }
                if(keys.isEmpty()) return 0;
                // History still retains models; references follow the already-approved 14-day expiry.
                for(ModelRequest request:modelRequestRepository.findByDishIdOrderByRequestedAtDesc(current.getId())) {
                    if(ModelRequest.STATUS_PENDING.equals(request.getStatus()) || ModelRequest.STATUS_IN_PRODUCTION.equals(request.getStatus())) return 0;
                }
                for(ModelRequest request:modelRequestRepository.findByDishIdOrderByRequestedAtDesc(current.getId())) {
                    try {
                        List<Map<String,String>> photos=objectMapper.readValue(request.getReferencePhotos()==null?"[]":request.getReferencePhotos(),new TypeReference<>(){});
                        request.setReferencePhotos(objectMapper.writeValueAsString(photos.stream().filter(p->!keys.contains(p.get("s3Key"))).toList()));
                    } catch(Exception e) { throw new IllegalStateException("Malformed reference history requires manual review"); }
                    modelRequestRepository.save(request);
                }
                current.setModelRequestPhotos("[]"); mediaRepository.save(current); return 1;
            });
            scheduled+=changed==null?0:changed;
            } catch(RuntimeException e) { log.warn("Reference expiry scheduling held for dish {} ({})",candidate.getId(),e.getClass().getSimpleName()); }
            finally { referenceScanCursor.set(candidate.getId()); }
        }
        return scheduled;
    }
    public int expirePendingReferences() {
        requireDetached();
        int expired=0;
        for(var pending:pendingKeyRepository.findTop100ByCreatedAtBefore(Instant.now().minus(props.presignTtl()).minus(Duration.ofHours(1)))) {
            Boolean removed=tx.execute(status -> {
                restaurantRepository.findByIdForUpdate(pending.getRestaurantId()).orElseThrow();
                var current=pendingKeyRepository.findById(pending.getId());
                if(current.isEmpty()) return false;
                cleanup.record(pending.getRestaurantId(),storage.bucket(),pending.getS3Key(),"EXPIRED_REFERENCE_UPLOAD");
                pendingKeyRepository.delete(current.get());return true;
            });
            if(Boolean.TRUE.equals(removed)) expired++;
        }
        return expired;
    }
    private static void requireDetached() {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("S3 workflow requires a detached transaction boundary");
    }

    private List<AdminModelRequestDto.PhotoSlotDto> parsePhotos(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, String>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream().map(m -> {
                String key = m.get("s3Key");
                String url = (key != null && !key.isBlank()) ? storage.presignPrivateGet(key, Duration.ofMinutes(15)) : m.get("s3Url");
                return new AdminModelRequestDto.PhotoSlotDto(
                        m.get("slot"),
                        key,
                        url
                );
            }).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String presignReferencePhotosJson(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return "[]";
        try {
            List<Map<String, String>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            List<Map<String, String>> presigned = raw.stream().map(m -> {
                Map<String, String> copy = new HashMap<>(m);
                String key = copy.get("s3Key");
                if (key != null && !key.isBlank()) {
                    copy.put("s3Url", storage.presignPrivateGet(key, Duration.ofMinutes(15)));
                }
                return copy;
            }).toList();
            return objectMapper.writeValueAsString(presigned);
        } catch (Exception e) {
            return json;
        }
    }

    private String resolveCategoryName(Media m) {
        if (m.getCategoryId() != null) {
            return categoryRepository.findByIdAndRestaurantId(m.getCategoryId(), m.getRestaurantId()).map(Category::getName).orElse(null);
        }
        return m.getDishCategory() != null ? m.getDishCategory().name() : null;
    }
}
