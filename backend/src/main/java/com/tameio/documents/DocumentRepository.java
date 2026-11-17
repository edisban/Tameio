package com.tameio.documents;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByBusinessId(UUID businessId, Pageable pageable);

    Optional<Document> findByIdAndBusinessId(UUID id, UUID businessId);

    boolean existsByBusinessIdAndSha256(UUID businessId, String sha256);
}
