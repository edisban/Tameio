package com.tameio.identity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    /** True if the account is active and still on this session version: one primary-key lookup per request. */
    @Query("select count(a) > 0 from UserAccount a where a.id = :id and a.active = true and a.sessionVersion = :version")
    boolean isSessionCurrent(UUID id, long version);

    @Query("select a from UserAccount a where a.role <> com.tameio.identity.UserRole.BUSINESS and (:role is null or a.role = :role)")
    Page<UserAccount> findStaff(UserRole role, Pageable pageable);
}
