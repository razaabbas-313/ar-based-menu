package com.atelier.media.repository;

import com.atelier.media.model.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformUserRepository extends JpaRepository<PlatformUser, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from PlatformUser u where u.id = :id")
    Optional<PlatformUser> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<PlatformUser> findByEmail(String email);

    List<PlatformUser> findByRole(String role);
}
