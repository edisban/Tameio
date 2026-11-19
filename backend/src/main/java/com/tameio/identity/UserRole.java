package com.tameio.identity;

/** What a person is in Tameio as a whole. Their role within each business is a {@link MembershipRole}. */
public enum UserRole {
    /** Runs or keeps the books of one or more businesses. */
    BUSINESS,
    /** Bank credit analyst: prepares loan decisions. */
    ANALYST,
    /** Bank credit manager: gives the second approval. */
    MANAGER,
    /** Bank administrator. */
    ADMIN
}
