package com.atelier.media.repository;

import com.atelier.media.model.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, Instant now);

    @Modifying
    @Query("UPDATE PasswordResetOtp o SET o.used = TRUE WHERE o.email = :email AND o.used = FALSE")
    int invalidateExistingOtps(@Param("email") String email);
}
