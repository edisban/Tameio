package com.tameio.lending;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    Optional<LoanApplication> findByIdAndBusinessId(UUID id, UUID businessId);

    /** The back office never sees drafts: they are the business's own until submitted. */
    Page<LoanApplication> findByStatusIn(Collection<LoanStatus> statuses, Pageable pageable);
}
