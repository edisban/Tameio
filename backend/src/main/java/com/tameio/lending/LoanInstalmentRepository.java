package com.tameio.lending;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface LoanInstalmentRepository extends JpaRepository<LoanInstalment, UUID> {

    Optional<LoanInstalment> findFirstByLoanApplicationIdAndStatusOrderByNumber(UUID loanApplicationId, LoanInstalment.Status status);

    long countByLoanApplicationIdAndStatus(UUID loanApplicationId, LoanInstalment.Status status);

    long countByStatus(LoanInstalment.Status status);

    /** One statement for all of them; running it twice for the same day changes nothing the second time. */
    @Modifying
    @Query("""
        update LoanInstalment i set i.status = com.tameio.lending.LoanInstalment.Status.DUE, i.becameDueAt = :at,
            i.updatedAt = :at, i.version = i.version + 1
        where i.status = com.tameio.lending.LoanInstalment.Status.SCHEDULED and i.dueOn <= :today""")
    int markDue(@Param("today") LocalDate today, @Param("at") Instant at);
}
