package com.atelier.media.web;

import com.atelier.media.dto.MediaResponseDto;
import com.atelier.media.dto.PresignRequestDto;
import com.atelier.media.dto.PresignResponseDto;
import com.atelier.media.dto.ReplaceConfirmDto;
import com.atelier.media.dto.ReplacePresignDto;
import com.atelier.media.dto.UploadConfirmDto;
import com.atelier.media.security.Tenant;
import com.atelier.media.service.MediaReplacementService;
import com.atelier.media.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * New-dish photo pipeline and in-place photo / 3D model replacement (ROLE_RESTAURANT). Dish edits,
 * deletes and the attach-3d track live under /api/v1/studio (see StudioController).
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;
    private final MediaReplacementService replacementService;

    public MediaController(MediaService mediaService, MediaReplacementService replacementService) {
        this.mediaService = mediaService;
        this.replacementService = replacementService;
    }

    /** Signed PUT URL for the dish photo, in this tenant's S3 folder. Creates the dish (PRESIGNED). */
    @PostMapping("/presign")
    public ResponseEntity<PresignResponseDto> presign(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody PresignRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(mediaService.generatePresignedUploadUrl(Tenant.from(jwt), request));
    }

    /**
     * In-place replacement, step 1: signs a PUT for a NEW key in this tenant's folder. The current
     * photo / model keeps serving guests until replace-confirm succeeds.
     */
    @PostMapping("/{id}/replace-presign")
    public ResponseEntity<PresignResponseDto> replacePresign(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                             @Valid @RequestBody ReplacePresignDto request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(replacementService.presign(Tenant.from(jwt), id, request));
    }

    /** In-place replacement, step 3: S3 HEAD (size + ETag), swap, delete the old object after commit. */
    @PostMapping("/{id}/replace-confirm")
    public ResponseEntity<MediaResponseDto> replaceConfirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                                           @Valid @RequestBody ReplaceConfirmDto confirm) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(replacementService.confirm(Tenant.from(jwt), id, confirm));
    }

    /** The browser got 200 OK from S3: verify ownership + S3 HEAD and publish the dish. */
    @PostMapping("/confirm")
    public ResponseEntity<MediaResponseDto> confirm(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody UploadConfirmDto confirm) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mediaService.confirmUpload(Tenant.from(jwt), confirm));
    }
}
