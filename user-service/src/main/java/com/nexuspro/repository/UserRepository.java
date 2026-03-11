package com.nexuspro.repository;

import com.nexuspro.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :id")
    void incrementFailedAttempts(UUID id);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockoutUntil = null WHERE u.id = :id")
    void resetFailedAttempts(UUID id);

    @Modifying
    @Query("UPDATE User u SET u.lockoutUntil = :until WHERE u.id = :id")
    void lockAccount(UUID id, Instant until);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginAt, u.lastLoginIp = :ip WHERE u.id = :id")
    void updateLoginMeta(UUID id, Instant loginAt, String ip);
}
