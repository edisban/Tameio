package com.tameio.identity;

import java.time.Instant;
import java.util.UUID;

/** A business as the user sees it, with their own role in it. Returned instead of the JPA entity. */
public record BusinessView(UUID id, String afm, String name, MembershipRole role, Instant createdAt) {

    static BusinessView of(Membership membership) {
        Business business = membership.getBusiness();
        return new BusinessView(business.getId(), business.getAfm(), business.getName(), membership.getRole(), business.getCreatedAt());
    }
}
