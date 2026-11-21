package com.tameio.banking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingEntryId(UUID entryId);

    Optional<Payment> findByDirectionAndEndToEndId(Payment.Direction direction, String endToEndId);

    Page<Payment> findByAccountId(UUID accountId, Pageable pageable);
}
