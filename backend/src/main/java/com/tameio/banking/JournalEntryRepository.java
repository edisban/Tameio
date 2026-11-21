package com.tameio.banking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    @Query("select e from JournalEntry e left join fetch e.postings where e.initiatedBy = :initiatedBy and e.idempotencyKey = :key")
    Optional<JournalEntry> findByIdempotencyKey(String initiatedBy, String key);
}
