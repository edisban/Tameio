package com.tameio.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MembershipRepository extends JpaRepository<Membership, UUID> {

    // Fetches each business in the same query: listing n businesses must not cost n + 1 queries
    @Query("""
        select m from Membership m join fetch m.business b
        where m.userId = :userId
        order by b.name""")
    List<Membership> findAllWithBusinessByUserId(String userId);

    @Query("""
        select m from Membership m join fetch m.business
        where m.userId = :userId and m.business.id = :businessId""")
    Optional<Membership> findWithBusiness(String userId, UUID businessId);
}
