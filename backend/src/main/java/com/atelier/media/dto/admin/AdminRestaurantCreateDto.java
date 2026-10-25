package com.atelier.media.dto.admin;

import com.atelier.media.model.RestaurantStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Restaurant provisioning by the super admin (the only way to create a restaurant since v4).
 * The server always generates the temporary password; the owner must replace it on first sign-in.
 *
 * @param slug        desired menu address (/r/{slug}); generated from {@code name} when blank
 * @param phoneNumber optional contact phone, shown on the guest menu as a "Call" chip
 * @param status      initial status, ACTIVE when omitted
 */
public record AdminRestaurantCreateDto(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 60) String slug,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Email @Size(max = 160) String email,
        @Pattern(regexp = "^$|^\\+?[0-9 ()\\-]{6,30}$", message = "use digits, spaces, + ( ) -") String phoneNumber,
        RestaurantStatus status,
        Boolean canUpload3d
) {
    public AdminRestaurantCreateDto(String name, String slug, String ownerName, String email, String phoneNumber, RestaurantStatus status) {
        this(name, slug, ownerName, email, phoneNumber, status, false);
    }
}
