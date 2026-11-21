package com.tameio.banking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByIban(String iban);

    Optional<BankAccount> findByCode(String code);

    Optional<BankAccount> findByIdAndBusinessId(UUID id, UUID businessId);

    List<BankAccount> findByBusinessIdOrderByCreatedAt(UUID businessId);

    long countByBusinessId(UUID businessId);

    List<BankAccount> findByKindOrderByCode(BankAccount.Kind kind);

    @Query(value = "select nextval('bank_account_number_seq')", nativeQuery = true)
    long nextAccountNumber();

    @Query("""
        select a from BankAccount a
        where a.kind = com.tameio.banking.BankAccount.Kind.CUSTOMER
          and (:search = '' or lower(a.holderName) like lower(concat('%', :search, '%')) or a.iban like concat('%', upper(:search), '%'))""")
    Page<BankAccount> searchCustomers(String search, Pageable pageable);

    /** Customer deposits, and how many customer accounts hold them. */
    record Deposits(BigDecimal total, long accounts) {
    }

    @Query("""
        select new com.tameio.banking.BankAccountRepository$Deposits(coalesce(sum(a.balance), 0), count(a))
        from BankAccount a where a.kind = com.tameio.banking.BankAccount.Kind.CUSTOMER""")
    Deposits customerDeposits();
}
