package com.atelier.media.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Profile edit by the super admin. Changing the slug changes the public menu URL. */
public record AdminRestaurantUpdateDto(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 60) String slug,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 160) String email,
        String adminNotes,
        Boolean canUpload3d
) {
    public AdminRestaurantUpdateDto(String name, String slug, String ownerName, String email, String adminNotes) {
        this(name, slug, ownerName, email, adminNotes, null);
    }

    public AdminRestaurantUpdateDto(String name, String slug, String ownerName, String email) {
        this(name, slug, ownerName, email, null, null);
    }
}
