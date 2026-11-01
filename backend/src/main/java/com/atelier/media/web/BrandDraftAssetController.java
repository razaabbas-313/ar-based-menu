package com.atelier.media.web;
import com.atelier.media.service.BrandDraftAssets;
import com.atelier.media.security.Tenant;
import com.atelier.media.dto.BrandingAssetPresignDto;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/studio/appearance/assets")
public class BrandDraftAssetController {
 private final BrandDraftAssets assets;
 public BrandDraftAssetController(BrandDraftAssets assets){this.assets=assets;}
 @PostMapping("/{purpose}/presign") public ResponseEntity<?> issue(@AuthenticationPrincipal Jwt jwt,@PathVariable String purpose,@jakarta.validation.Valid @RequestBody BrandingAssetPresignDto dto){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assets.issue(Tenant.from(jwt),purpose,dto));}
 @PostMapping("/{id}/confirm") public ResponseEntity<?> confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(assets.verify(Tenant.from(jwt),id));}
}
