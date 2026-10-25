package com.atelier.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request a presigned PUT URL for a .glb/.gltf that will be linked to an existing dish. */
public record Attach3dRequestDto(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 64) String contentType,
        @NotNull @Positive Long uploadSizeBytes,
        @Pattern(regexp = "table|floor") String arPlacement
) {
}
